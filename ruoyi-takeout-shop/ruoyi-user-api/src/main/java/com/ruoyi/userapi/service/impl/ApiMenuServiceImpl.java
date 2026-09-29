package com.ruoyi.userapi.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.merchant.config.TakeoutCacheKeys;
import com.ruoyi.merchant.domain.BizCategory;
import com.ruoyi.merchant.domain.BizGoods;
import com.ruoyi.merchant.domain.BizGoodsFlavor;
import com.ruoyi.merchant.domain.BizGoodsSpec;
import com.ruoyi.merchant.domain.BizGoodsStockDaily;
import com.ruoyi.merchant.mapper.BizCategoryMapper;
import com.ruoyi.merchant.mapper.BizGoodsFlavorMapper;
import com.ruoyi.merchant.mapper.BizGoodsMapper;
import com.ruoyi.merchant.mapper.BizGoodsSpecMapper;
import com.ruoyi.merchant.service.StockService;
import com.ruoyi.userapi.domain.vo.GoodsDetailVo;
import com.ruoyi.userapi.domain.vo.MenuCategoryVo;
import com.ruoyi.userapi.domain.vo.MenuCategoryVo.MenuItemVo;
import com.ruoyi.userapi.service.ApiMenuService;
import com.ruoyi.userapi.util.FileUrlBuilder;

/**
 * 小程序端菜单服务实现
 *
 * @author 阿婆干饭社
 */
@Service
public class ApiMenuServiceImpl implements ApiMenuService
{
    private static final Logger log = LoggerFactory.getLogger(ApiMenuServiceImpl.class);

    /** 缓存 TTL 兜底（正常由管理端写操作删除缓存） */
    private static final long CACHE_TTL_HOURS = 24;

    @Autowired
    private BizCategoryMapper categoryMapper;

    @Autowired
    private BizGoodsMapper goodsMapper;

    @Autowired
    private BizGoodsSpecMapper goodsSpecMapper;

    @Autowired
    private BizGoodsFlavorMapper goodsFlavorMapper;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private FileUrlBuilder fileUrlBuilder;

    /** 每日限量库存服务（T12）：详情页剩余库存与有效售罄 */
    @Autowired
    private StockService stockService;

    @Override
    public List<MenuCategoryVo> getMenuList()
    {
        // getCacheObject 反序列化损坏缓存时也可能抛错，一并容错：视为 miss 删除后回填，避免接口 500
        String cached = null;
        try
        {
            cached = redisCache.getCacheObject(TakeoutCacheKeys.GOODS_LIST);
        }
        catch (Exception e)
        {
            log.warn("[菜单缓存] 读取反序列化失败，删除缓存后回填：{}", e.getMessage());
            redisCache.deleteObject(TakeoutCacheKeys.GOODS_LIST);
        }
        if (StringUtils.hasText(cached))
        {
            try
            {
                return JSON.parseArray(cached, MenuCategoryVo.class);
            }
            catch (Exception e)
            {
                // 缓存内容损坏（如脏数据/结构变更）：视为 miss，删除后走回填，避免接口 500
                log.warn("[菜单缓存] 内容解析失败，删除缓存后回填：{}", e.getMessage());
                redisCache.deleteObject(TakeoutCacheKeys.GOODS_LIST);
            }
        }
        // miss：查库回填（正常状态分类 + 其下在售菜品，图片 URL 拼好入库前缀）
        List<BizCategory> categories = categoryMapper.selectList(new LambdaQueryWrapper<BizCategory>()
                .eq(BizCategory::getStatus, "0").orderByAsc(BizCategory::getSort));
        List<BizGoods> goodsList = goodsMapper.selectList(new LambdaQueryWrapper<BizGoods>()
                .eq(BizGoods::getStatus, "1").orderByAsc(BizGoods::getSort).orderByDesc(BizGoods::getId));
        Map<Long, List<BizGoods>> goodsByCategory = goodsList.stream()
                .collect(Collectors.groupingBy(BizGoods::getCategoryId));
        // 批量取规格/口味存在性（首页"+"按钮：无规格口味直接加购，有则进详情选择）；
        // 规格售罄数用于「全部规格售罄 → 菜品列表展示为售罄」（T11，菜品级售罄优先级更高）
        List<Long> allGoodsIds = goodsList.stream().map(BizGoods::getId).collect(Collectors.toList());
        List<BizGoodsSpec> allSpecs = allGoodsIds.isEmpty() ? List.of()
                : goodsSpecMapper.selectList(new LambdaQueryWrapper<BizGoodsSpec>().in(BizGoodsSpec::getGoodsId, allGoodsIds));
        Map<Long, Long> specCountMap = allSpecs.stream()
                .collect(Collectors.groupingBy(BizGoodsSpec::getGoodsId, Collectors.counting()));
        Map<Long, Long> specSoldCountMap = allSpecs.stream()
                .filter(s -> "1".equals(s.getSoldOut()) || "1".equals(s.getAutoSoldOut()))
                .collect(Collectors.groupingBy(BizGoodsSpec::getGoodsId, Collectors.counting()));
        Map<Long, Long> flavorCountMap = allGoodsIds.isEmpty() ? Map.of()
                : goodsFlavorMapper.selectList(new LambdaQueryWrapper<BizGoodsFlavor>().in(BizGoodsFlavor::getGoodsId, allGoodsIds))
                        .stream().collect(Collectors.groupingBy(BizGoodsFlavor::getGoodsId, Collectors.counting()));

        List<MenuCategoryVo> result = new ArrayList<>();
        for (BizCategory category : categories)
        {
            MenuCategoryVo vo = new MenuCategoryVo();
            vo.setCategoryId(category.getId());
            vo.setCategoryName(category.getName());
            vo.setGoodsList(goodsByCategory.getOrDefault(category.getId(), List.of()).stream()
                    .map(g -> toMenuItem(g, specCountMap, specSoldCountMap, flavorCountMap)).collect(Collectors.toList()));
            result.add(vo);
        }
        redisCache.setCacheObject(TakeoutCacheKeys.GOODS_LIST, JSON.toJSONString(result), (int) CACHE_TTL_HOURS, TimeUnit.HOURS);
        return result;
    }

    @Override
    public List<MenuItemVo> searchGoods(String keyword)
    {
        if (!StringUtils.hasText(keyword))
        {
            return List.of();
        }
        // 从缓存列表内存过滤（列表数据走 Redis 缓存，不查库）
        List<MenuItemVo> hits = new ArrayList<>();
        String kw = keyword.trim().toLowerCase();
        for (MenuCategoryVo category : getMenuList())
        {
            for (MenuItemVo item : category.getGoodsList())
            {
                String name = item.getName() == null ? "" : item.getName().toLowerCase();
                String desc = item.getDescription() == null ? "" : item.getDescription().toLowerCase();
                if (name.contains(kw) || desc.contains(kw))
                {
                    hits.add(item);
                }
            }
        }
        return hits;
    }

    @Override
    public GoodsDetailVo getGoodsDetail(Long id)
    {
        BizGoods goods = goodsMapper.selectById(id);
        if (goods == null || !"1".equals(goods.getStatus()))
        {
            // 下架菜品对小程序不可见；售罄菜品可看详情
            throw new ServiceException("菜品不存在或已下架");
        }
        BizCategory category = categoryMapper.selectById(goods.getCategoryId());

        GoodsDetailVo vo = new GoodsDetailVo();
        vo.setId(goods.getId());
        vo.setCategoryId(goods.getCategoryId());
        vo.setCategoryName(category == null ? "" : category.getName());
        vo.setName(goods.getName());
        vo.setImage(fileUrlBuilder.build(goods.getImage()));
        vo.setPrice(goods.getPrice());
        vo.setDescription(goods.getDescription());
        vo.setStatus(goods.getStatus());
        vo.setSales(goods.getSales());

        // T12：今日库存（菜品级 + 各规格）；未启用每日限量时 map 为空、相关字段为 null
        boolean limitEnabled = "1".equals(goods.getDailyLimitEnabled());
        java.util.Map<Long, BizGoodsStockDaily> stockMap = limitEnabled
                ? stockService.getGoodsStockMap(id, today()) : java.util.Map.of();
        vo.setDailyLimitEnabled(limitEnabled);
        BizGoodsStockDaily goodsLevelStock = stockMap.get(BizGoodsStockDaily.SPEC_ID_GOODS_LEVEL);
        if (goodsLevelStock != null)
        {
            vo.setRemainQty(goodsLevelStock.remainQty());
            vo.setLimitQty(goodsLevelStock.getLimitQty());
        }

        List<GoodsDetailVo.SpecVo> specVos = goodsSpecMapper.selectList(new LambdaQueryWrapper<BizGoodsSpec>()
                        .eq(BizGoodsSpec::getGoodsId, id).orderByAsc(BizGoodsSpec::getSort))
                .stream().map(s -> {
                    GoodsDetailVo.SpecVo sv = new GoodsDetailVo.SpecVo();
                    sv.setId(s.getId());
                    sv.setName(s.getName());
                    sv.setPriceDelta(s.getPriceDelta());
                    BizGoodsStockDaily specStock = stockMap.get(s.getId());
                    // 有效售罄：手动 或 自动（库存耗尽）
                    boolean sold = "1".equals(s.getSoldOut()) || "1".equals(s.getAutoSoldOut());
                    if (specStock != null)
                    {
                        sv.setRemainQty(specStock.remainQty());
                        sv.setLimitQty(specStock.getLimitQty());
                        if (specStock.remainQty() <= 0)
                        {
                            sold = true;
                        }
                    }
                    sv.setSoldOut(sold ? "1" : "0");
                    return sv;
                }).collect(Collectors.toList());
        vo.setSpecs(specVos);

        // 有效售罄：手动 或 自动；有规格且全部规格售罄时同样视为售罄（T11+T12）
        boolean goodsSold = "1".equals(goods.getSoldOut()) || "1".equals(goods.getAutoSoldOut());
        if (!goodsSold && !specVos.isEmpty())
        {
            goodsSold = specVos.stream().allMatch(s -> "1".equals(s.getSoldOut()));
        }
        vo.setSoldOut(goodsSold ? "1" : "0");

        vo.setFlavors(goodsFlavorMapper.selectList(new LambdaQueryWrapper<BizGoodsFlavor>()
                        .eq(BizGoodsFlavor::getGoodsId, id).orderByAsc(BizGoodsFlavor::getSort))
                .stream().map(f -> {
                    GoodsDetailVo.FlavorVo fv = new GoodsDetailVo.FlavorVo();
                    fv.setId(f.getId());
                    fv.setName(f.getName());
                    fv.setSelectType(f.getSelectType());
                    // JSON 数组字符串 -> 字符串数组（解析失败按单元素兜底）
                    try
                    {
                        fv.setOptions(JSON.parseArray(f.getOptions(), String.class));
                    }
                    catch (Exception e)
                    {
                        fv.setOptions(StringUtils.hasText(f.getOptions()) ? List.of(f.getOptions()) : List.of());
                    }
                    return fv;
                }).collect(Collectors.toList()));
        return vo;
    }

    /** 实体 -> 菜单项 VO（图片拼完整 URL，规格/口味存在性标记；有效售罄=菜品级 或 全部规格售罄） */
    private MenuItemVo toMenuItem(BizGoods goods, Map<Long, Long> specCountMap, Map<Long, Long> specSoldCountMap,
                                  Map<Long, Long> flavorCountMap)
    {
        MenuItemVo item = new MenuItemVo();
        item.setId(goods.getId());
        item.setCategoryId(goods.getCategoryId());
        item.setName(goods.getName());
        item.setImage(fileUrlBuilder.build(goods.getImage()));
        item.setPrice(goods.getPrice());
        item.setDescription(goods.getDescription());
        item.setSoldOut(effectiveSoldOut(goods, specCountMap, specSoldCountMap));
        item.setHasSpec(specCountMap.getOrDefault(goods.getId(), 0L) > 0);
        item.setHasFlavor(flavorCountMap.getOrDefault(goods.getId(), 0L) > 0);
        item.setDailyLimitEnabled("1".equals(goods.getDailyLimitEnabled()));
        return item;
    }

    /** 当天日期（与 date 列比较） */
    private java.util.Date today()
    {
        return com.ruoyi.common.utils.DateUtils.parseDate(com.ruoyi.common.utils.DateUtils.getDate());
    }

    /** 有效售罄：菜品级手动售罄 或 菜品级自动售罄（T12）优先；无则看规格——有规格且全部规格售罄同样视为售罄（T11） */
    private String effectiveSoldOut(BizGoods goods, Map<Long, Long> specCountMap, Map<Long, Long> specSoldCountMap)
    {
        // 手动售罄优先级最高；自动售罄（库存耗尽）次之
        if ("1".equals(goods.getSoldOut()) || "1".equals(goods.getAutoSoldOut()))
        {
            return "1";
        }
        long specCount = specCountMap.getOrDefault(goods.getId(), 0L);
        if (specCount > 0 && specCount == specSoldCountMap.getOrDefault(goods.getId(), 0L))
        {
            return "1";
        }
        return "0";
    }
}
