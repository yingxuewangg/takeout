package com.ruoyi.userapi.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.merchant.domain.BizGoods;
import com.ruoyi.merchant.domain.BizGoodsSpec;
import com.ruoyi.merchant.domain.BizShopInfo;
import com.ruoyi.merchant.mapper.BizGoodsMapper;
import com.ruoyi.merchant.mapper.BizGoodsSpecMapper;
import com.ruoyi.merchant.mapper.BizShopInfoMapper;
import com.ruoyi.merchant.service.StockService;
import com.ruoyi.userapi.domain.BizAddress;
import com.ruoyi.userapi.domain.BizCart;
import com.ruoyi.userapi.domain.vo.CartItemVo;
import com.ruoyi.userapi.domain.vo.CheckoutVo;
import com.ruoyi.userapi.mapper.BizAddressMapper;
import com.ruoyi.userapi.mapper.BizCartMapper;
import com.ruoyi.userapi.service.ApiAddressService;
import com.ruoyi.userapi.service.ApiCartService;
import com.ruoyi.userapi.util.FileUrlBuilder;

/**
 * 购物车服务实现
 *
 * @author 阿婆干饭社
 */
@Service
public class ApiCartServiceImpl implements ApiCartService
{
    private static final int MAX_QUANTITY = 99;

    private static final long SHOP_ID = 1L;

    @Autowired
    private BizCartMapper cartMapper;

    @Autowired
    private BizGoodsMapper goodsMapper;

    @Autowired
    private BizGoodsSpecMapper goodsSpecMapper;

    @Autowired
    private BizShopInfoMapper shopInfoMapper;

    @Autowired
    private BizAddressMapper addressMapper;

    @Autowired
    private ApiAddressService addressService;

    @Autowired
    private FileUrlBuilder fileUrlBuilder;

    /** 每日限量库存服务（T12）：购物车行库存充足性校验 */
    @Autowired
    private StockService stockService;

    @Override
    public List<CartItemVo> getCartList(Long memberId)
    {
        List<BizCart> carts = cartMapper.selectList(new LambdaQueryWrapper<BizCart>()
                .eq(BizCart::getMemberId, memberId).orderByDesc(BizCart::getId));
        if (carts.isEmpty())
        {
            return List.of();
        }
        // 批量查菜品，得到实时名称/图片/价格/状态
        List<Long> goodsIds = carts.stream().map(BizCart::getGoodsId).distinct().collect(Collectors.toList());
        Map<Long, BizGoods> goodsMap = goodsMapper.selectBatchIds(goodsIds).stream()
                .collect(Collectors.toMap(BizGoods::getId, Function.identity()));
        List<CartItemVo> result = new ArrayList<>();
        for (BizCart cart : carts)
        {
            BizGoods goods = goodsMap.get(cart.getGoodsId());
            if (goods == null)
            {
                continue;
            }
            result.add(toCartItem(cart, goods));
        }
        return result;
    }

    @Override
    @Transactional
    public void addToCart(Long memberId, Long goodsId, Long specId, String flavorJson, Integer quantity)
    {
        if (goodsId == null || quantity == null || quantity < 1 || quantity > MAX_QUANTITY)
        {
            throw new ServiceException("加购参数不正确");
        }
        // 后端二次校验：在售、未售罄（防前端缓存或绕过）
        BizGoods goods = goodsMapper.selectById(goodsId);
        if (goods == null || !"1".equals(goods.getStatus()))
        {
            throw new ServiceException("菜品已下架，无法加购");
        }
        if ("1".equals(goods.getSoldOut()) || "1".equals(goods.getAutoSoldOut()))
        {
            throw new ServiceException("【" + goods.getName() + "】已售罄，无法加购");
        }
        // 规格校验与快照（T11：规格级售罄后端二次校验，防前端缓存或绕过）
        String specName = null;
        if (specId != null)
        {
            BizGoodsSpec spec = goodsSpecMapper.selectById(specId);
            if (spec == null || !spec.getGoodsId().equals(goodsId))
            {
                throw new ServiceException("规格不正确");
            }
            if ("1".equals(spec.getSoldOut()) || "1".equals(spec.getAutoSoldOut()))
            {
                throw new ServiceException("【" + goods.getName() + "】的【" + spec.getName() + "】已售罄，请选择其他规格");
            }
            specName = spec.getName();
        }
        // T12：每日限量库存校验（后端二次校验，防前端缓存或绕过）
        if ("1".equals(goods.getDailyLimitEnabled()))
        {
            Date today = DateUtils.parseDate(DateUtils.getDate());
            Long specKey = specId == null ? com.ruoyi.merchant.domain.BizGoodsStockDaily.SPEC_ID_GOODS_LEVEL : specId;
            java.util.Map<Long, com.ruoyi.merchant.domain.BizGoodsStockDaily> stockMap =
                    stockService.getGoodsStockMap(goodsId, today);
            com.ruoyi.merchant.domain.BizGoodsStockDaily row = stockMap.get(specKey);
            if (row != null && row.remainQty() < quantity)
            {
                throw new ServiceException("【" + goods.getName() + "】今日仅剩 " + row.remainQty() + " 份，无法加购");
            }
        }
        // 口味格式校验（入参必须是合法 JSON 数组结构）
        if (StringUtils.isNotEmpty(flavorJson))
        {
            try
            {
                JSON.parseArray(flavorJson, CartItemVo.FlavorItem.class);
            }
            catch (Exception e)
            {
                throw new ServiceException("口味数据格式不正确");
            }
            if (flavorJson.length() > 2000)
            {
                throw new ServiceException("口味信息过长");
            }
        }
        // 同组合合并数量（合计仍不超过上限）。
        // 注意：flavor_json 是 JSON 列，MySQL 存储时会规范化文本形态（如加空格），
        // 直接用 SQL 字符串等值比较不可靠——取出候选行后在 Java 层做规范化比对。
        List<BizCart> candidates = cartMapper.selectList(new LambdaQueryWrapper<BizCart>()
                .eq(BizCart::getMemberId, memberId)
                .eq(BizCart::getGoodsId, goodsId)
                .eq(specId != null, BizCart::getSpecId, specId)
                .isNull(specId == null, BizCart::getSpecId));
        String incomingFlavor = canonicalizeFlavor(flavorJson);
        BizCart existing = candidates.stream()
                .filter(c -> canonicalizeFlavor(c.getFlavorJson()).equals(incomingFlavor))
                .findFirst().orElse(null);
        Date now = DateUtils.getNowDate();
        if (existing != null)
        {
            int total = existing.getQuantity() + quantity;
            if (total > MAX_QUANTITY)
            {
                throw new ServiceException("数量超出限制（最多" + MAX_QUANTITY + "件）");
            }
            existing.setQuantity(total);
            existing.setUpdateTime(now);
            cartMapper.updateById(existing);
            return;
        }
        BizCart cart = new BizCart();
        cart.setMemberId(memberId);
        cart.setGoodsId(goodsId);
        cart.setSpecId(specId);
        cart.setSpecName(specName);
        cart.setFlavorJson(StringUtils.isEmpty(incomingFlavor) ? null : incomingFlavor);
        cart.setQuantity(quantity);
        cart.setCreateTime(now);
        cart.setUpdateTime(now);
        cartMapper.insert(cart);
    }

    @Override
    public void changeQuantity(Long memberId, Long id, Integer quantity)
    {
        if (quantity == null || quantity < 1 || quantity > MAX_QUANTITY)
        {
            throw new ServiceException("数量需在 1-" + MAX_QUANTITY + " 之间");
        }
        BizCart cart = getOwnedCart(memberId, id);
        cart.setQuantity(quantity);
        cart.setUpdateTime(DateUtils.getNowDate());
        cartMapper.updateById(cart);
    }

    @Override
    public void removeItem(Long memberId, Long id)
    {
        getOwnedCart(memberId, id);
        cartMapper.deleteById(id);
    }

    @Override
    public void clearCart(Long memberId)
    {
        cartMapper.delete(new LambdaQueryWrapper<BizCart>().eq(BizCart::getMemberId, memberId));
    }

    @Override
    public CheckoutVo checkout(Long memberId, Integer deliveryType, String tableNo)
    {
        if (deliveryType == null || (deliveryType != 1 && deliveryType != 2))
        {
            throw new ServiceException("履约方式不正确");
        }
        BizShopInfo shop = shopInfoMapper.selectById(SHOP_ID);
        List<CartItemVo> items = getCartList(memberId);
        if (items.isEmpty())
        {
            throw new ServiceException("购物车是空的，先去挑点好吃的吧～");
        }

        // 菜品合计（仅有效项）
        BigDecimal goodsAmount = BigDecimal.ZERO;
        boolean hasInvalid = false;
        for (CartItemVo item : items)
        {
            if ("normal".equals(item.getStatus()))
            {
                goodsAmount = goodsAmount.add(item.getSubtotal());
            }
            else
            {
                hasInvalid = true;
            }
        }
        goodsAmount = goodsAmount.setScale(2, RoundingMode.HALF_UP);

        boolean businessClosed = shop == null || shop.getBusinessStatus() == null || shop.getBusinessStatus() != 1;
        BigDecimal deliveryFee = BigDecimal.ZERO;
        BigDecimal amountShort = BigDecimal.ZERO;
        BigDecimal minDeliveryAmount = BigDecimal.ZERO;
        if (deliveryType == 2)
        {
            deliveryFee = shop == null || shop.getDeliveryFee() == null ? BigDecimal.ZERO : shop.getDeliveryFee();
            minDeliveryAmount = shop == null || shop.getMinDeliveryAmount() == null ? BigDecimal.ZERO : shop.getMinDeliveryAmount();
            if (goodsAmount.compareTo(minDeliveryAmount) < 0)
            {
                amountShort = minDeliveryAmount.subtract(goodsAmount).setScale(2, RoundingMode.HALF_UP);
            }
        }
        BigDecimal totalAmount = goodsAmount.add(deliveryFee).setScale(2, RoundingMode.HALF_UP);

        CheckoutVo vo = new CheckoutVo();
        vo.setItems(items);
        vo.setHasInvalid(hasInvalid);
        vo.setDeliveryType(deliveryType);
        vo.setTableNo(tableNo);
        vo.setGoodsAmount(goodsAmount);
        vo.setDeliveryFee(deliveryFee);
        vo.setTotalAmount(totalAmount);
        vo.setMinDeliveryAmount(minDeliveryAmount);
        vo.setAmountShort(amountShort);
        vo.setBusinessClosed(businessClosed);
        vo.setClosedTip(businessClosed ? "店铺已打烊，暂时不能下单" : null);
        vo.setCanSubmit(!hasInvalid && !businessClosed && amountShort.compareTo(BigDecimal.ZERO) <= 0
                && (deliveryType == 1 ? StringUtils.isNotEmpty(tableNo) : true));
        if (deliveryType == 2)
        {
            vo.setDefaultAddress(addressService.getDefaultAddress(memberId));
        }
        return vo;
    }

    /** 归属校验：只能操作自己的购物车项 */
    private BizCart getOwnedCart(Long memberId, Long id)
    {
        BizCart cart = cartMapper.selectById(id);
        if (cart == null || !cart.getMemberId().equals(memberId))
        {
            throw new ServiceException("购物车项不存在");
        }
        return cart;
    }

    /** 购物车记录 -> VO（联查实时价格与状态） */
    private CartItemVo toCartItem(BizCart cart, BizGoods goods)
    {
        CartItemVo vo = new CartItemVo();
        vo.setId(cart.getId());
        vo.setGoodsId(goods.getId());
        vo.setGoodsName(goods.getName());
        vo.setImagePath(goods.getImage());
        vo.setImage(fileUrlBuilder.build(goods.getImage()));
        vo.setSpecId(cart.getSpecId());
        vo.setSpecName(cart.getSpecName());
        vo.setFlavors(parseFlavors(cart.getFlavorJson()));
        // 实时单价 = 当前基础价 + 规格差价（菜品改价后购物车展示跟随，下单时以订单快照为准）
        BigDecimal price = goods.getPrice() == null ? BigDecimal.ZERO : goods.getPrice();
        BizGoodsSpec spec = null;
        if (cart.getSpecId() != null)
        {
            spec = goodsSpecMapper.selectById(cart.getSpecId());
            if (spec != null && spec.getPriceDelta() != null)
            {
                price = price.add(spec.getPriceDelta());
            }
        }
        vo.setPrice(price.setScale(2, RoundingMode.HALF_UP));
        vo.setQuantity(cart.getQuantity());
        vo.setSubtotal(price.multiply(BigDecimal.valueOf(cart.getQuantity())).setScale(2, RoundingMode.HALF_UP));
        // 有效性：下架 > 售罄（手动/自动，T11+T12）> 库存不足 > 正常
        boolean specSoldOut = spec != null && ("1".equals(spec.getSoldOut()) || "1".equals(spec.getAutoSoldOut()));
        if (!"1".equals(goods.getStatus()))
        {
            vo.setStatus("offshelf");
        }
        else if ("1".equals(goods.getSoldOut()) || "1".equals(goods.getAutoSoldOut()) || specSoldOut)
        {
            vo.setStatus("soldout");
        }
        else if (!stockEnough(goods, cart))
        {
            // T12：每日限量库存不足（含规格级与菜品级）
            vo.setStatus("soldout");
        }
        else
        {
            vo.setStatus("normal");
        }
        return vo;
    }

    /**
     * T12 库存校验：该购物车行对应库存（规格级优先，否则菜品级）是否足够。
     * 未启用每日限量 → 视为足够（不参与库存管理）。
     */
    private boolean stockEnough(BizGoods goods, BizCart cart)
    {
        if (!"1".equals(goods.getDailyLimitEnabled()))
        {
            return true;
        }
        Date today = DateUtils.parseDate(DateUtils.getDate());
        Long specKey = cart.getSpecId() == null
                ? com.ruoyi.merchant.domain.BizGoodsStockDaily.SPEC_ID_GOODS_LEVEL : cart.getSpecId();
        java.util.Map<Long, com.ruoyi.merchant.domain.BizGoodsStockDaily> stockMap =
                stockService.getGoodsStockMap(goods.getId(), today);
        com.ruoyi.merchant.domain.BizGoodsStockDaily row = stockMap.get(specKey);
        if (row == null)
        {
            return true;
        }
        int need = cart.getQuantity() == null ? 0 : cart.getQuantity();
        return row.remainQty() >= need;
    }

    /** 口味 JSON 解析为展示结构 */
    @SuppressWarnings("unchecked")
    private List<CartItemVo.FlavorItem> parseFlavors(String flavorJson)
    {
        if (StringUtils.isEmpty(flavorJson))
        {
            return List.of();
        }
        try
        {
            return JSON.parseArray(flavorJson, CartItemVo.FlavorItem.class);
        }
        catch (Exception e)
        {
            return List.of();
        }
    }

    /**
     * 口味 JSON 规范化：解析 → 组名排序 → 值排序 → 紧凑序列化。
     * 用于入参与库存值的双向规范化比对（解析失败兜底返回原串，保证历史数据仍可按原样合并）。
     */
    private String canonicalizeFlavor(String flavorJson)
    {
        if (StringUtils.isEmpty(flavorJson))
        {
            return "";
        }
        try
        {
            List<CartItemVo.FlavorItem> items = JSON.parseArray(flavorJson, CartItemVo.FlavorItem.class);
            if (items == null || items.isEmpty())
            {
                return "";
            }
            items.sort((a, b) -> String.valueOf(a.getName()).compareTo(String.valueOf(b.getName())));
            items.forEach(item -> {
                if (item.getValues() != null)
                {
                    item.setValues(item.getValues().stream().sorted().collect(Collectors.toList()));
                }
            });
            return JSON.toJSONString(items);
        }
        catch (Exception e)
        {
            return flavorJson.trim();
        }
    }
}
