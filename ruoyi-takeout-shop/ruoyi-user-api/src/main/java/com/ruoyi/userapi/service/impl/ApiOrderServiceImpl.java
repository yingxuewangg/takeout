package com.ruoyi.userapi.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.merchant.config.TakeoutCacheKeys;
import com.ruoyi.merchant.domain.BizGoods;
import com.ruoyi.merchant.domain.BizGoodsFlavor;
import com.ruoyi.merchant.domain.BizGoodsSpec;
import com.ruoyi.merchant.domain.BizGoodsStockDaily;
import com.ruoyi.merchant.domain.BizOrder;
import com.ruoyi.merchant.domain.BizOrderItem;
import com.ruoyi.merchant.domain.BizShopInfo;
import com.ruoyi.merchant.mapper.BizGoodsFlavorMapper;
import com.ruoyi.merchant.mapper.BizGoodsMapper;
import com.ruoyi.merchant.mapper.BizGoodsSpecMapper;
import com.ruoyi.merchant.mapper.BizOrderItemMapper;
import com.ruoyi.merchant.mapper.BizOrderMapper;
import com.ruoyi.merchant.mapper.BizShopInfoMapper;
import com.ruoyi.merchant.service.StockService;
import com.ruoyi.userapi.domain.BizAddress;
import com.ruoyi.userapi.domain.BizCart;
import com.ruoyi.userapi.domain.vo.CartItemVo;
import com.ruoyi.merchant.domain.BizGoodsComment;
import com.ruoyi.merchant.mapper.BizGoodsCommentMapper;
import com.ruoyi.userapi.domain.vo.OrderCreateVo;
import com.ruoyi.userapi.domain.vo.OrderDetailVo;
import com.ruoyi.userapi.domain.vo.OrderListVo;
import com.ruoyi.userapi.domain.vo.RepurchaseResultVo;
import com.ruoyi.userapi.mapper.BizAddressMapper;
import com.ruoyi.userapi.mapper.BizCartMapper;
import com.ruoyi.userapi.mq.TakeoutMqProducer;
import com.ruoyi.userapi.service.ApiAddressService;
import com.ruoyi.userapi.service.ApiCartService;
import com.ruoyi.userapi.service.ApiOrderService;
import com.ruoyi.userapi.util.FileUrlBuilder;

/**
 * 小程序端订单服务实现
 *
 * @author 阿婆干饭社
 */
@Service
public class ApiOrderServiceImpl implements ApiOrderService
{
    private static final Logger log = LoggerFactory.getLogger(ApiOrderServiceImpl.class);

    /** 待支付超时分钟数（方案 3.3：10 分钟未支付自动关闭） */
    private static final int TIMEOUT_MINUTES = 10;

    /** 履约方式 */
    private static final int TYPE_DINE = 1;
    private static final int TYPE_TAKEOUT = 2;

    private static final long SHOP_ID = 1L;

    @Autowired
    private BizOrderMapper orderMapper;

    @Autowired
    private BizOrderItemMapper orderItemMapper;

    @Autowired
    private BizShopInfoMapper shopInfoMapper;

    @Autowired
    private BizAddressMapper addressMapper;

    @Autowired
    private BizGoodsSpecMapper goodsSpecMapper;

    @Autowired
    private BizCartMapper cartMapper;

    @Autowired
    private ApiCartService cartService;

    @Autowired
    private ApiAddressService addressService;

    @Autowired
    private FileUrlBuilder fileUrlBuilder;

    /** 每日限量库存服务（T12）：下单扣减 + 取消释放 */
    @Autowired
    private StockService stockService;

    /** 菜品与口味（T13 再次购买校验：下架/售罄/口味是否仍存在） */
    @Autowired
    private BizGoodsMapper goodsMapper;

    @Autowired
    private BizGoodsFlavorMapper goodsFlavorMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** T20 评价提醒：查订单是否已存在本用户关联留言 */
    @Autowired
    private BizGoodsCommentMapper commentMapper;

    /** MQ 生产者：rocketmq.enabled=false 时不注入（降级运行，仅靠定时任务兜底） */
    @Autowired(required = false)
    private TakeoutMqProducer mqProducer;

    @Override
    @Transactional
    public OrderCreateVo createOrder(Long memberId, OrderCreateBody body)
    {
        // 1. 下单防重锁：takeout:order:lock:{memberId}（setnx + 5 秒过期）
        String lockKey = TakeoutCacheKeys.ORDER_LOCK_KEY + memberId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(5));
        if (!Boolean.TRUE.equals(locked))
        {
            throw new ServiceException("操作太快啦，请稍候几秒再下单");
        }
        try
        {
            return doCreateOrder(memberId, body);
        }
        catch (RuntimeException e)
        {
            // 业务失败立即释放锁（成功后保留至自然过期，防连点重复下单）
            stringRedisTemplate.delete(lockKey);
            throw e;
        }
    }

    private OrderCreateVo doCreateOrder(Long memberId, OrderCreateBody body)
    {
        // 2. 履约方式与必填校验
        Integer deliveryType = body.getDeliveryType();
        if (deliveryType == null || (deliveryType != TYPE_DINE && deliveryType != TYPE_TAKEOUT))
        {
            throw new ServiceException("履约方式不正确");
        }
        // T20 备注长度校验（前端 maxlength 200，后端二次校验防绕过）
        if (StringUtils.isNotEmpty(body.getRemark()) && body.getRemark().length() > 200)
        {
            throw new ServiceException("订单备注最多 200 字");
        }
        String tableNo = null;
        BizAddress address = null;
        if (deliveryType == TYPE_DINE)
        {
            if (StringUtils.isEmpty(body.getTableNo()))
            {
                throw new ServiceException("堂食必须选择桌号");
            }
            tableNo = body.getTableNo().trim();
        }
        else
        {
            if (body.getAddressId() == null)
            {
                throw new ServiceException("请选择收货地址");
            }
            address = addressMapper.selectById(body.getAddressId());
            if (address == null || !address.getMemberId().equals(memberId))
            {
                throw new ServiceException("收货地址不存在");
            }
        }

        // 3. 店铺状态：打烊可浏览不可下单
        BizShopInfo shop = shopInfoMapper.selectById(SHOP_ID);
        if (shop == null || shop.getBusinessStatus() == null || shop.getBusinessStatus() != 1)
        {
            throw new ServiceException("店铺已打烊，暂时不能下单");
        }

        // 4. 购物车明细（后端二次校验：售罄/下架在 getCartList 中已按实时状态标注）
        List<CartItemVo> items = cartService.getCartList(memberId);
        if (items.isEmpty())
        {
            throw new ServiceException("购物车是空的，先去挑点好吃的吧～");
        }
        BigDecimal goodsAmount = BigDecimal.ZERO;
        for (CartItemVo item : items)
        {
            if (!"normal".equals(item.getStatus()))
            {
                throw new ServiceException("【" + item.getGoodsName() + "】已" +
                        ("soldout".equals(item.getStatus()) ? "售罄" : "下架") + "，请移除后再下单");
            }
            goodsAmount = goodsAmount.add(item.getSubtotal());
        }
        goodsAmount = goodsAmount.setScale(2, RoundingMode.HALF_UP);

        // 5. 金额：配送费快照（堂食 0；外卖=店铺当前值）
        BigDecimal deliveryFee = BigDecimal.ZERO;
        if (deliveryType == TYPE_TAKEOUT)
        {
            deliveryFee = shop.getDeliveryFee() == null ? BigDecimal.ZERO : shop.getDeliveryFee();
            // 起送价校验：菜品合计未达起送价不可下单
            BigDecimal minAmount = shop.getMinDeliveryAmount() == null ? BigDecimal.ZERO : shop.getMinDeliveryAmount();
            if (goodsAmount.compareTo(minAmount) < 0)
            {
                throw new ServiceException("菜品合计未达起送价 ¥" + minAmount + "，还差 ¥" +
                        minAmount.subtract(goodsAmount).setScale(2, RoundingMode.HALF_UP));
            }
        }
        BigDecimal payAmount = goodsAmount.add(deliveryFee).setScale(2, RoundingMode.HALF_UP);

        // 5.1 每日限量扣减（T12）：条件更新防超卖，任一明细不足即抛错回滚整个下单事务
        //     （未启用每日限量的菜品在 StockService 内部跳过，不影响一期流程）
        List<StockService.StockDeductItem> deductItems = new ArrayList<>();
        for (CartItemVo item : items)
        {
            deductItems.add(new StockService.StockDeductItem(item.getGoodsId(), item.getSpecId(), item.getQuantity()));
        }
        stockService.deductForOrder(deductItems);

        // 6. 联系人与地址快照（外卖：快照不随后续地址修改变化）
        String contactName;
        String contactPhone;
        String addressSnapshot = null;
        if (deliveryType == TYPE_TAKEOUT)
        {
            contactName = StringUtils.isNotEmpty(body.getContactName()) ? body.getContactName() : address.getContactName();
            contactPhone = StringUtils.isNotEmpty(body.getContactPhone()) ? body.getContactPhone() : address.getContactPhone();
            java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
            snapshot.put("name", contactName);
            snapshot.put("phone", contactPhone);
            snapshot.put("province", address.getProvince());
            snapshot.put("city", address.getCity());
            snapshot.put("district", address.getDistrict());
            snapshot.put("detail", address.getDetail());
            addressSnapshot = JSON.toJSONString(snapshot);
        }
        else
        {
            contactName = body.getContactName();
            contactPhone = body.getContactPhone();
        }

        // 7. 组装订单
        Date now = DateUtils.getNowDate();
        BizOrder order = new BizOrder();
        order.setOrderNo(generateOrderNo(now));
        order.setMemberId(memberId);
        order.setDeliveryType(deliveryType);
        order.setTableNo(tableNo);
        order.setContactName(contactName);
        order.setContactPhone(contactPhone);
        order.setAddressSnapshot(addressSnapshot);
        order.setGoodsAmount(goodsAmount);
        order.setDeliveryFee(deliveryFee);
        order.setPayAmount(payAmount);
        order.setStatus(0);
        order.setRefundStatus(0);
        order.setRemark(body.getRemark());
        order.setPayStatus(0);
        order.setTimeoutCloseTime(new Date(now.getTime() + TIMEOUT_MINUTES * 60_000L));
        order.setCreateTime(now);
        order.setUpdateTime(now);
        orderMapper.insert(order);

        // 8. 明细快照（名称/图片相对路径/单价快照/规格口味 JSON/小计）
        for (CartItemVo item : items)
        {
            BizOrderItem oi = new BizOrderItem();
            oi.setOrderId(order.getId());
            oi.setGoodsId(item.getGoodsId());
            oi.setGoodsName(item.getGoodsName());
            oi.setGoodsImage(item.getImagePath());
            oi.setSpecFlavorJson(buildSpecFlavorJson(item));
            oi.setUnitPrice(item.getPrice());
            oi.setQuantity(item.getQuantity());
            oi.setSubtotal(item.getSubtotal());
            orderItemMapper.insert(oi);
        }

        // 9. 清空购物车（整单提交）
        cartMapper.delete(new LambdaQueryWrapper<BizCart>().eq(BizCart::getMemberId, memberId));

        // 10. 事务提交后发 10 分钟延迟消息（MQ 关闭时降级：仅靠定时任务兜底）
        if (mqProducer != null)
        {
            final String orderNo = order.getOrderNo();
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit()
                        {
                            mqProducer.sendOrderTimeoutClose(orderNo);
                        }
                    });
        }
        else
        {
            log.info("[下单] rocketmq.enabled=false，跳过延迟消息发送（定时任务兜底）：orderNo={}", order.getOrderNo());
        }

        OrderCreateVo vo = new OrderCreateVo();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setPayAmount(payAmount);
        vo.setTimeoutMinutes(TIMEOUT_MINUTES);
        log.info("[下单] 订单创建成功：orderNo={} member={} type={} payAmount={}",
                order.getOrderNo(), memberId, deliveryType, payAmount);
        return vo;
    }

    @Override
    public OrderDetailVo getOrderDetail(Long memberId, Long orderId)
    {
        BizOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getMemberId().equals(memberId))
        {
            throw new ServiceException("订单不存在");
        }
        OrderDetailVo vo = new OrderDetailVo();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setDeliveryType(order.getDeliveryType());
        vo.setTableNo(order.getTableNo());
        vo.setContactName(order.getContactName());
        vo.setContactPhone(order.getContactPhone());
        vo.setAddressSnapshot(order.getAddressSnapshot());
        vo.setGoodsAmount(order.getGoodsAmount());
        vo.setDeliveryFee(order.getDeliveryFee());
        vo.setPayAmount(order.getPayAmount());
        vo.setStatus(order.getStatus());
        vo.setRefundStatus(order.getRefundStatus());
        vo.setPayStatus(order.getPayStatus());
        vo.setPayTime(order.getPayTime());
        vo.setRemark(order.getRemark());
        vo.setCreateTime(order.getCreateTime());
        // 待支付剩余秒数（供收银台倒计时）
        if (order.getStatus() != null && order.getStatus() == 0 && order.getTimeoutCloseTime() != null)
        {
            long remain = (order.getTimeoutCloseTime().getTime() - System.currentTimeMillis()) / 1000;
            vo.setRemainSeconds(Math.max(remain, 0));
        }
        else
        {
            vo.setRemainSeconds(0L);
        }
        List<OrderDetailVo.ItemVo> itemVos = new ArrayList<>();
        for (BizOrderItem oi : orderItemMapper.selectList(new LambdaQueryWrapper<BizOrderItem>()
                .eq(BizOrderItem::getOrderId, orderId).orderByAsc(BizOrderItem::getId)))
        {
            OrderDetailVo.ItemVo iv = new OrderDetailVo.ItemVo();
            iv.setGoodsId(oi.getGoodsId());
            iv.setGoodsName(oi.getGoodsName());
            iv.setImage(fileUrlBuilder.build(oi.getGoodsImage()));
            iv.setSpecFlavorJson(oi.getSpecFlavorJson());
            iv.setUnitPrice(oi.getUnitPrice());
            iv.setQuantity(oi.getQuantity());
            iv.setSubtotal(oi.getSubtotal());
            itemVos.add(iv);
        }
        vo.setItems(itemVos);
        // T20 评价提醒：该订单是否已有本用户关联留言
        vo.setHasCommented(commentMapper.selectCount(new LambdaQueryWrapper<BizGoodsComment>()
                .eq(BizGoodsComment::getMemberId, memberId)
                .eq(BizGoodsComment::getOrderId, orderId)) > 0);
        return vo;
    }

    @Override
    public IPage<OrderListVo> listOrders(Long memberId, Integer status, long pageNum, long pageSize)
    {
        LambdaQueryWrapper<BizOrder> wrapper = new LambdaQueryWrapper<BizOrder>()
                .eq(BizOrder::getMemberId, memberId)
                .eq(status != null, BizOrder::getStatus, status)
                .orderByDesc(BizOrder::getId);
        IPage<BizOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<BizOrder> orders = page.getRecords();
        List<OrderListVo> vos = new ArrayList<>();
        if (!orders.isEmpty())
        {
            // 批量查明细（摘要用）
            List<Long> orderIds = orders.stream().map(BizOrder::getId).collect(Collectors.toList());
            Map<Long, List<BizOrderItem>> itemsByOrder = orderItemMapper.selectList(new LambdaQueryWrapper<BizOrderItem>()
                            .in(BizOrderItem::getOrderId, orderIds).orderByAsc(BizOrderItem::getId))
                    .stream().collect(Collectors.groupingBy(BizOrderItem::getOrderId));
            // T20 评价提醒：批量查本用户已关联留言的订单（一条 in 查询，避免逐单查询）
            java.util.Set<Long> commentedOrderIds = commentMapper.selectList(new LambdaQueryWrapper<BizGoodsComment>()
                            .eq(BizGoodsComment::getMemberId, memberId).in(BizGoodsComment::getOrderId, orderIds))
                    .stream().map(BizGoodsComment::getOrderId).collect(Collectors.toSet());
            for (BizOrder order : orders)
            {
                OrderListVo lvo = toListVo(order, itemsByOrder.getOrDefault(order.getId(), List.of()));
                lvo.setHasCommented(commentedOrderIds.contains(order.getId()));
                vos.add(lvo);
            }
        }
        Page<OrderListVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(vos);
        return result;
    }

    @Override
    public void cancelOrder(Long memberId, Long orderId)
    {
        BizOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getMemberId().equals(memberId))
        {
            throw new ServiceException("订单不存在");
        }
        // 仅待支付可取消：条件更新（where status=0）幂等；取消后延迟消息/定时任务触达时自然跳过
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<BizOrder>()
                .eq(BizOrder::getId, orderId)
                .eq(BizOrder::getStatus, 0)
                .set(BizOrder::getStatus, 6)
                .set(BizOrder::getUpdateTime, DateUtils.getNowDate()));
        if (rows == 0)
        {
            BizOrder current = orderMapper.selectById(orderId);
            if (current != null && current.getStatus() != null && current.getStatus() == 6)
            {
                throw new ServiceException("订单已取消，请勿重复操作");
            }
            throw new ServiceException("订单当前状态不可取消");
        }
        // T12：取消成功后释放每日限量库存（以订单号幂等，只释放一次）
        stockService.releaseByOrderNo(order.getOrderNo());
        log.info("[取消订单] 用户取消成功：orderNo={} member={}", order.getOrderNo(), memberId);
    }

    @Override
    public RepurchaseResultVo repurchase(Long memberId, Long orderId)
    {
        BizOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getMemberId().equals(memberId))
        {
            throw new ServiceException("订单不存在");
        }
        // 与前端展示条件一致：主状态 1-5 且未退款成功；后端同样拦截（防绕过）
        int st = order.getStatus() == null ? -1 : order.getStatus();
        int rs = order.getRefundStatus() == null ? 0 : order.getRefundStatus();
        if (st < 1 || st > 5 || rs == 2)
        {
            throw new ServiceException("该订单当前不支持再次购买");
        }
        List<BizOrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<BizOrderItem>()
                .eq(BizOrderItem::getOrderId, orderId).orderByAsc(BizOrderItem::getId));
        if (items.isEmpty())
        {
            throw new ServiceException("订单中没有可再次购买的商品");
        }

        Date today = DateUtils.parseDate(DateUtils.getDate());
        RepurchaseResultVo result = new RepurchaseResultVo();
        for (BizOrderItem item : items)
        {
            addItemForRepurchase(memberId, item, today, result);
        }
        result.setSuccessCount(result.getAdded().size());
        result.setSkipCount(result.getSkipped().size());
        result.setAllFailed(result.getAdded().isEmpty());
        log.info("[再次购买] member={} orderNo={} 成功 {} 项 / 跳过 {} 项",
                memberId, order.getOrderNo(), result.getSuccessCount(), result.getSkipCount());
        return result;
    }

    /**
     * 单条明细的再次购买处理：逐项校验 → 成功加购 / 记录跳过原因。
     * 校验顺序（短路）：菜品存在与在售 → 菜品级售罄 → 规格是否仍存在 → 规格级售罄
     *                  → 口味按名称比对（缺失即剔除，不整项失败）→ 每日限量库存（不足按剩余量加购）。
     */
    private void addItemForRepurchase(Long memberId, BizOrderItem item, Date today, RepurchaseResultVo result)
    {
        String goodsName = item.getGoodsName();
        // 解析快照（规格 id/名称 + 口味组）
        Long specId = null;
        String specName = null;
        List<CartItemVo.FlavorItem> snapshotFlavors = List.of();
        String json = item.getSpecFlavorJson();
        if (StringUtils.isNotEmpty(json))
        {
            try
            {
                com.alibaba.fastjson2.JSONObject obj = JSON.parseObject(json);
                com.alibaba.fastjson2.JSONObject spec = obj.getJSONObject("spec");
                if (spec != null)
                {
                    specId = spec.getLong("id");
                    specName = spec.getString("name");
                }
                if (obj.getJSONArray("flavors") != null)
                {
                    snapshotFlavors = obj.getJSONArray("flavors").toJavaList(CartItemVo.FlavorItem.class);
                }
            }
            catch (Exception e)
            {
                log.warn("[再次购买] 明细快照解析失败，按无规格口味处理：itemId={}", item.getId());
            }
        }
        int originQty = item.getQuantity() == null ? 0 : item.getQuantity();
        if (originQty <= 0)
        {
            result.getSkipped().add(new RepurchaseResultVo.SkippedItem(
                    item.getGoodsId(), goodsName, specName, "数量异常"));
            return;
        }

        // 1) 菜品存在且在售
        BizGoods goods = item.getGoodsId() == null ? null : goodsMapper.selectById(item.getGoodsId());
        if (goods == null || !"1".equals(goods.getStatus()))
        {
            result.getSkipped().add(new RepurchaseResultVo.SkippedItem(
                    item.getGoodsId(), goodsName, specName, "商品已下架"));
            return;
        }
        // 以最新菜品名展示（快照名可能已过期）
        goodsName = goods.getName();
        // 2) 菜品级售罄（手动 或 自动）
        if ("1".equals(goods.getSoldOut()) || "1".equals(goods.getAutoSoldOut()))
        {
            result.getSkipped().add(new RepurchaseResultVo.SkippedItem(
                    item.getGoodsId(), goodsName, specName, "商品已售罄"));
            return;
        }
        // 3) 规格是否仍存在（规格 ID 稳定：T11 起管理端编辑改为按 ID upsert）
        if (specId != null)
        {
            BizGoodsSpec spec = goodsSpecMapper.selectById(specId);
            if (spec == null || !spec.getGoodsId().equals(goods.getId()))
            {
                result.getSkipped().add(new RepurchaseResultVo.SkippedItem(
                        item.getGoodsId(), goodsName, specName, "规格已下架或变更"));
                return;
            }
            specName = spec.getName();
            // 4) 规格级售罄（手动 或 自动）
            if ("1".equals(spec.getSoldOut()) || "1".equals(spec.getAutoSoldOut()))
            {
                result.getSkipped().add(new RepurchaseResultVo.SkippedItem(
                        item.getGoodsId(), goodsName, specName, "该规格已售罄"));
                return;
            }
        }

        // 5) 口味按名称比对当前定义：组名或选项已不存在则剔除（口味为附属属性，不整项失败）
        String flavorJson = null;
        if (!snapshotFlavors.isEmpty())
        {
            List<CartItemVo.FlavorItem> validFlavors = filterExistingFlavors(goods.getId(), snapshotFlavors);
            if (validFlavors.size() != snapshotFlavors.size())
            {
                result.setFlavorAdjusted(true);
            }
            if (!validFlavors.isEmpty())
            {
                flavorJson = JSON.toJSONString(validFlavors);
            }
        }

        // 6) 每日限量库存：不足则按剩余量加购（剩余 0 跳过）
        int qty = originQty;
        boolean reduced = false;
        String adjustTip = null;
        if ("1".equals(goods.getDailyLimitEnabled()))
        {
            long specKey = specId == null ? BizGoodsStockDaily.SPEC_ID_GOODS_LEVEL : specId;
            Map<Long, BizGoodsStockDaily> stockMap = stockService.getGoodsStockMap(goods.getId(), today);
            BizGoodsStockDaily row = stockMap.get(specKey);
            if (row != null)
            {
                int remain = row.remainQty();
                if (remain <= 0)
                {
                    result.getSkipped().add(new RepurchaseResultVo.SkippedItem(
                            item.getGoodsId(), goodsName, specName, "今日已售罄"));
                    return;
                }
                if (remain < originQty)
                {
                    qty = remain;
                    reduced = true;
                    adjustTip = "原 " + originQty + " 份，今日仅剩 " + remain + " 份，已加购 " + remain + " 份";
                }
            }
        }

        // 7) 复用既有加购（含在售/售罄/库存/合并/上限校验，绝不绕过库存规则）
        try
        {
            cartService.addToCart(memberId, goods.getId(), specId, flavorJson, qty);
        }
        catch (ServiceException e)
        {
            // 加购二次校验失败（如并发下库存被抢）：记为跳过，原因用后端提示
            result.getSkipped().add(new RepurchaseResultVo.SkippedItem(
                    item.getGoodsId(), goodsName, specName, e.getMessage()));
            return;
        }
        RepurchaseResultVo.AddedItem added = new RepurchaseResultVo.AddedItem();
        added.setGoodsId(goods.getId());
        added.setGoodsName(goodsName);
        added.setSpecName(specName);
        added.setOriginQuantity(originQty);
        added.setAddedQuantity(qty);
        added.setQuantityReduced(reduced);
        added.setAdjustTip(adjustTip);
        result.getAdded().add(added);
    }

    /**
     * 口味快照 -> 仅保留当前仍存在的口味组与选项（按组名/选项值比对，因口味组 ID 会随管理端编辑变化）。
     */
    private List<CartItemVo.FlavorItem> filterExistingFlavors(Long goodsId, List<CartItemVo.FlavorItem> snapshotFlavors)
    {
        List<BizGoodsFlavor> currentFlavors = goodsFlavorMapper.selectList(new LambdaQueryWrapper<BizGoodsFlavor>()
                .eq(BizGoodsFlavor::getGoodsId, goodsId));
        Map<String, List<String>> currentMap = new java.util.LinkedHashMap<>();
        for (BizGoodsFlavor f : currentFlavors)
        {
            List<String> options = List.of();
            try
            {
                options = JSON.parseArray(f.getOptions(), String.class);
            }
            catch (Exception ignored)
            {
                // 选项 JSON 异常按空处理（该组无可选项）
            }
            currentMap.put(f.getName(), options == null ? List.of() : options);
        }
        List<CartItemVo.FlavorItem> valid = new ArrayList<>();
        for (CartItemVo.FlavorItem snap : snapshotFlavors)
        {
            List<String> available = currentMap.get(snap.getName());
            if (available == null || snap.getValues() == null)
            {
                continue;
            }
            List<String> kept = snap.getValues().stream()
                    .filter(available::contains)
                    .collect(Collectors.toList());
            if (!kept.isEmpty())
            {
                CartItemVo.FlavorItem fi = new CartItemVo.FlavorItem();
                fi.setName(snap.getName());
                fi.setValues(kept);
                valid.add(fi);
            }
        }
        return valid;
    }

    /** 订单实体 -> 列表 VO（明细摘要/地址摘要/倒计时） */
    private OrderListVo toListVo(BizOrder order, List<BizOrderItem> items)
    {
        OrderListVo vo = new OrderListVo();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setRefundStatus(order.getRefundStatus());
        vo.setDeliveryType(order.getDeliveryType());
        vo.setTableNo(order.getTableNo());
        // T13：再次购买按钮展示条件——主状态 1-5（已支付且未取消）且未退款成功
        int st = order.getStatus() == null ? -1 : order.getStatus();
        int rs = order.getRefundStatus() == null ? 0 : order.getRefundStatus();
        vo.setCanRepurchase(st >= 1 && st <= 5 && rs != 2);
        if (order.getDeliveryType() != null && order.getDeliveryType() == 2 && StringUtils.isNotEmpty(order.getAddressSnapshot()))
        {
            try
            {
                com.alibaba.fastjson2.JSONObject addr = JSON.parseObject(order.getAddressSnapshot());
                String detail = addr.getString("detail");
                vo.setAddressBrief(detail != null && detail.length() > 12 ? detail.substring(0, 12) + "…" : detail);
            }
            catch (Exception e)
            {
                vo.setAddressBrief(null);
            }
        }
        vo.setPayAmount(order.getPayAmount());
        int count = 0;
        List<OrderListVo.ItemVo> itemVos = new ArrayList<>();
        for (BizOrderItem oi : items)
        {
            count += oi.getQuantity() == null ? 0 : oi.getQuantity();
            OrderListVo.ItemVo iv = new OrderListVo.ItemVo();
            iv.setGoodsId(oi.getGoodsId());
            iv.setGoodsName(oi.getGoodsName());
            iv.setImage(fileUrlBuilder.build(oi.getGoodsImage()));
            iv.setQuantity(oi.getQuantity());
            itemVos.add(iv);
        }
        vo.setGoodsCount(count);
        vo.setCreateTime(order.getCreateTime());
        vo.setItems(itemVos);
        if (order.getStatus() != null && order.getStatus() == 0 && order.getTimeoutCloseTime() != null)
        {
            vo.setRemainSeconds(Math.max((order.getTimeoutCloseTime().getTime() - System.currentTimeMillis()) / 1000, 0));
        }
        else
        {
            vo.setRemainSeconds(0L);
        }
        return vo;
    }

    /** 订单号：yyyyMMddHHmmssSSS + 3 位随机（order_no 唯一索引兜底） */
    private String generateOrderNo(Date now)
    {
        String ts = DateUtils.parseDateToStr("yyyyMMddHHmmssSSS", now);
        int rand = (int) (Math.random() * 900) + 100;
        return ts + rand;
    }

    /** 规格口味快照 JSON：{"spec":{...},"flavors":[...]} */
    private String buildSpecFlavorJson(CartItemVo item)
    {
        java.util.Map<String, Object> json = new java.util.LinkedHashMap<>();
        if (item.getSpecId() != null)
        {
            BizGoodsSpec spec = goodsSpecMapper.selectById(item.getSpecId());
            if (spec != null)
            {
                java.util.Map<String, Object> specMap = new java.util.LinkedHashMap<>();
                specMap.put("id", spec.getId());
                specMap.put("name", spec.getName());
                specMap.put("priceDelta", spec.getPriceDelta());
                json.put("spec", specMap);
            }
        }
        if (item.getFlavors() != null && !item.getFlavors().isEmpty())
        {
            json.put("flavors", item.getFlavors());
        }
        return json.isEmpty() ? null : JSON.toJSONString(json);
    }
}
