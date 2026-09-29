package com.ruoyi.merchant.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.merchant.domain.BizGoods;
import com.ruoyi.merchant.domain.BizGoodsSpec;
import com.ruoyi.merchant.domain.BizGoodsStockDaily;
import com.ruoyi.merchant.domain.BizOrder;
import com.ruoyi.merchant.domain.BizOrderItem;
import com.ruoyi.merchant.mapper.BizGoodsMapper;
import com.ruoyi.merchant.mapper.BizGoodsSpecMapper;
import com.ruoyi.merchant.mapper.BizGoodsStockDailyMapper;
import com.ruoyi.merchant.mapper.BizOrderItemMapper;
import com.ruoyi.merchant.mapper.BizOrderMapper;
import com.ruoyi.merchant.service.StockService;

/**
 * 每日库存服务实现（T12 每日限量 + 自动售罄）。
 *
 * 关键设计：
 *  1) 扣减/释放全部条件更新 + 影响行数校验（防超卖、防负数）；
 *  2) 自动售罄写独立字段 auto_sold_out，绝不改写手动 sold_out（手动售罄优先级更高）；
 *  3) 释放以订单号 + biz_order.stock_released 幂等，且回退到"下单当天"的库存行；
 *  4) 初始化：定时任务预初始化 + 惰性兜底（下单/查询时补），依赖唯一索引保证幂等。
 *
 * @author 阿婆干饭社
 */
@Service
public class StockServiceImpl implements StockService
{
    private static final Logger log = LoggerFactory.getLogger(StockServiceImpl.class);

    private static final long GOODS_LEVEL = BizGoodsStockDaily.SPEC_ID_GOODS_LEVEL;

    @Autowired
    private BizGoodsStockDailyMapper stockMapper;

    @Autowired
    private BizGoodsMapper goodsMapper;

    @Autowired
    private BizGoodsSpecMapper goodsSpecMapper;

    @Autowired
    private BizOrderMapper orderMapper;

    @Autowired
    private BizOrderItemMapper orderItemMapper;

    // ==================== 下单扣减 ====================

    @Override
    public void deductForOrder(List<StockDeductItem> items)
    {
        if (items == null || items.isEmpty())
        {
            return;
        }
        Date today = today();
        for (StockDeductItem item : items)
        {
            long specId = item.getSpecId() == null ? GOODS_LEVEL : item.getSpecId();
            // 未启用每日限量（菜品级与规格级都未配置）→ 不参与扣减
            BizGoodsStockDaily row = ensureDailyRow(item.getGoodsId(), specId, today);
            if (row == null)
            {
                continue;
            }
            int rows = stockMapper.deduct(item.getGoodsId(), specId, today, item.getQuantity());
            if (rows == 0)
            {
                // 条件更新失败 = 库存不足（并发下也不会超卖）；抛错让调用方事务回滚
                throw new ServiceException("【" + goodsName(item.getGoodsId()) + "】今日库存不足，仅剩 " +
                        remainFor(item.getGoodsId(), specId, today) + " 份");
            }
            // 扣减后：耗尽则自动售罄（只写 auto_sold_out，不动手动 sold_out）
            refreshAutoSoldOut(item.getGoodsId(), specId, today);
        }
    }

    // ==================== 库存释放（幂等） ====================

    @Override
    @Transactional
    public void releaseByOrderNo(String orderNo)
    {
        // 幂等键：条件更新 stock_released 0->1，行数=0 表示已释放过（或订单不存在）
        int claimed = orderMapper.update(null, new LambdaUpdateWrapper<BizOrder>()
                .eq(BizOrder::getOrderNo, orderNo)
                .eq(BizOrder::getStockReleased, "0")
                .set(BizOrder::getStockReleased, "1")
                .set(BizOrder::getUpdateTime, DateUtils.getNowDate()));
        if (claimed == 0)
        {
            return;
        }
        BizOrder order = orderMapper.selectOne(new LambdaQueryWrapper<BizOrder>()
                .eq(BizOrder::getOrderNo, orderNo).last("limit 1"));
        if (order == null)
        {
            return;
        }
        // 回退到"下单当天"的库存行（跨天释放语义正确：昨天占用的还回昨天）
        Date orderDate = order.getCreateTime() == null ? today() : order.getCreateTime();
        List<BizOrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<BizOrderItem>()
                .eq(BizOrderItem::getOrderId, order.getId()));
        for (BizOrderItem item : items)
        {
            long specId = resolveSpecId(item);
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            if (qty <= 0)
            {
                continue;
            }
            // 条件释放：where sold_qty >= N（防负数）；行数=0 表示该行不存在/已售为0，跳过
            int rows = stockMapper.release(item.getGoodsId(), specId, orderDate, qty);
            if (rows > 0)
            {
                // 释放后不再耗尽 → 取消自动售罄（手动售罄不受影响）
                refreshAutoSoldOut(item.getGoodsId(), specId, orderDate);
            }
        }
        log.info("[库存释放] orderNo={} 释放明细 {} 条（下单日期 {}）", orderNo, items.size(), orderDate);
    }

    // ==================== 初始化 ====================

    @Override
    public int initDailyStock(Date stockDate)
    {
        // 启用每日限量的菜品
        List<BizGoods> goodsList = goodsMapper.selectList(new LambdaQueryWrapper<BizGoods>()
                .eq(BizGoods::getDailyLimitEnabled, "1"));
        int created = 0;
        for (BizGoods goods : goodsList)
        {
            List<BizGoodsSpec> specs = goodsSpecMapper.selectList(new LambdaQueryWrapper<BizGoodsSpec>()
                    .eq(BizGoodsSpec::getGoodsId, goods.getId()));
            if (specs.isEmpty())
            {
                // 无规格：菜品级一行
                created += insertRowIfAbsent(goods.getId(), GOODS_LEVEL, stockDate, limitOf(goods, null));
            }
            else
            {
                for (BizGoodsSpec spec : specs)
                {
                    created += insertRowIfAbsent(goods.getId(), spec.getId(), stockDate, limitOf(goods, spec));
                }
            }
        }
        if (created > 0)
        {
            log.info("[每日库存] 初始化完成：日期={} 新增 {} 行", stockDate, created);
        }
        return created;
    }

    @Override
    public BizGoodsStockDaily ensureDailyRow(Long goodsId, Long specId, Date stockDate)
    {
        long sid = specId == null ? GOODS_LEVEL : specId;
        BizGoodsStockDaily existing = stockMapper.selectOne(new LambdaQueryWrapper<BizGoodsStockDaily>()
                .eq(BizGoodsStockDaily::getGoodsId, goodsId)
                .eq(BizGoodsStockDaily::getSpecId, sid)
                .eq(BizGoodsStockDaily::getStockDate, stockDate)
                .last("limit 1"));
        if (existing != null)
        {
            return existing;
        }
        // 不存在：判定是否启用限量，启用则惰性插入（唯一索引冲突时忽略，随后重查）
        BizGoods goods = goodsMapper.selectById(goodsId);
        if (goods == null || !"1".equals(goods.getDailyLimitEnabled()))
        {
            return null;
        }
        BizGoodsSpec spec = sid == GOODS_LEVEL ? null : goodsSpecMapper.selectById(sid);
        int limit = limitOf(goods, spec);
        insertRowIfAbsent(goodsId, sid, stockDate, limit);
        return stockMapper.selectOne(new LambdaQueryWrapper<BizGoodsStockDaily>()
                .eq(BizGoodsStockDaily::getGoodsId, goodsId)
                .eq(BizGoodsStockDaily::getSpecId, sid)
                .eq(BizGoodsStockDaily::getStockDate, stockDate)
                .last("limit 1"));
    }

    // ==================== 查询 ====================

    @Override
    public Map<Long, BizGoodsStockDaily> getGoodsStockMap(Long goodsId, Date stockDate)
    {
        BizGoods goods = goodsMapper.selectById(goodsId);
        if (goods == null || !"1".equals(goods.getDailyLimitEnabled()))
        {
            return Map.of();
        }
        // 惰性初始化兜底：保证当天该菜品（菜品级/各规格）的库存行存在
        ensureRowsForGoods(goods, stockDate);
        List<BizGoodsStockDaily> rows = stockMapper.selectList(new LambdaQueryWrapper<BizGoodsStockDaily>()
                .eq(BizGoodsStockDaily::getGoodsId, goodsId)
                .eq(BizGoodsStockDaily::getStockDate, stockDate));
        Map<Long, BizGoodsStockDaily> map = new LinkedHashMap<>();
        for (BizGoodsStockDaily row : rows)
        {
            map.put(row.getSpecId(), row);
        }
        return map;
    }

    @Override
    public Map<Long, Map<Long, BizGoodsStockDaily>> getGoodsStockMapBatch(List<Long> goodsIds, Date stockDate)
    {
        if (goodsIds == null || goodsIds.isEmpty())
        {
            return Map.of();
        }
        // 仅启用限量的菜品
        List<BizGoods> limited = goodsMapper.selectList(new LambdaQueryWrapper<BizGoods>()
                .in(BizGoods::getId, goodsIds)
                .eq(BizGoods::getDailyLimitEnabled, "1"));
        if (limited.isEmpty())
        {
            return Map.of();
        }
        List<Long> limitedIds = limited.stream().map(BizGoods::getId).collect(Collectors.toList());
        List<BizGoodsStockDaily> rows = stockMapper.selectList(new LambdaQueryWrapper<BizGoodsStockDaily>()
                .in(BizGoodsStockDaily::getGoodsId, limitedIds)
                .eq(BizGoodsStockDaily::getStockDate, stockDate));
        Map<Long, Map<Long, BizGoodsStockDaily>> result = new LinkedHashMap<>();
        for (BizGoodsStockDaily row : rows)
        {
            result.computeIfAbsent(row.getGoodsId(), k -> new LinkedHashMap<>()).put(row.getSpecId(), row);
        }
        return result;
    }

    @Override
    public List<StockView> listTodayStock()
    {
        Date today = today();
        // 先确保当天所有启用限量的菜品都有库存行（管理端面板完整）
        initDailyStock(today);
        List<BizGoodsStockDaily> rows = stockMapper.selectList(new LambdaQueryWrapper<BizGoodsStockDaily>()
                .eq(BizGoodsStockDaily::getStockDate, today)
                .orderByAsc(BizGoodsStockDaily::getGoodsId)
                .orderByAsc(BizGoodsStockDaily::getSpecId));
        if (rows.isEmpty())
        {
            return List.of();
        }
        List<Long> goodsIds = rows.stream().map(BizGoodsStockDaily::getGoodsId).distinct().collect(Collectors.toList());
        Map<Long, BizGoods> goodsMap = goodsMapper.selectBatchIds(goodsIds).stream()
                .collect(Collectors.toMap(BizGoods::getId, Function.identity()));
        List<Long> specIds = rows.stream().map(BizGoodsStockDaily::getSpecId)
                .filter(id -> id != null && id != GOODS_LEVEL).distinct().collect(Collectors.toList());
        Map<Long, BizGoodsSpec> specMap = specIds.isEmpty() ? Map.of()
                : goodsSpecMapper.selectBatchIds(specIds).stream()
                        .collect(Collectors.toMap(BizGoodsSpec::getId, Function.identity()));

        List<StockView> views = new ArrayList<>();
        for (BizGoodsStockDaily row : rows)
        {
            BizGoods goods = goodsMap.get(row.getGoodsId());
            BizGoodsSpec spec = row.getSpecId() == GOODS_LEVEL ? null : specMap.get(row.getSpecId());
            StockView v = new StockView();
            v.setId(row.getId());
            v.setGoodsId(row.getGoodsId());
            v.setGoodsName(goods == null ? ("菜品#" + row.getGoodsId()) : goods.getName());
            v.setSpecId(row.getSpecId());
            v.setSpecName(row.getSpecId() == GOODS_LEVEL ? "（菜品级）" : (spec == null ? "规格已删除" : spec.getName()));
            v.setLimitQty(row.getLimitQty());
            v.setSoldQty(row.getSoldQty());
            v.setRemainQty(row.remainQty());
            v.setManualSoldOut(row.getSpecId() == GOODS_LEVEL
                    ? (goods == null ? "0" : goods.getSoldOut())
                    : (spec == null ? "0" : spec.getSoldOut()));
            v.setAutoSoldOut(row.getSpecId() == GOODS_LEVEL
                    ? (goods == null ? "0" : goods.getAutoSoldOut())
                    : (spec == null ? "0" : spec.getAutoSoldOut()));
            views.add(v);
        }
        return views;
    }

    @Override
    @Transactional
    public void resetStock(Long goodsId, Long specId, Integer limitQty)
    {
        long sid = specId == null ? GOODS_LEVEL : specId;
        BizGoods goods = goodsMapper.selectById(goodsId);
        if (goods == null)
        {
            throw new ServiceException("菜品不存在");
        }
        Date today = today();
        BizGoodsSpec spec = sid == GOODS_LEVEL ? null : goodsSpecMapper.selectById(sid);
        // 限量值：入参优先，否则用配置模板
        int newLimit = limitQty != null ? limitQty : limitOf(goods, spec);
        int rows = stockMapper.update(null, new LambdaUpdateWrapper<BizGoodsStockDaily>()
                .eq(BizGoodsStockDaily::getGoodsId, goodsId)
                .eq(BizGoodsStockDaily::getSpecId, sid)
                .eq(BizGoodsStockDaily::getStockDate, today)
                .set(BizGoodsStockDaily::getLimitQty, newLimit)
                .set(BizGoodsStockDaily::getSoldQty, 0)
                .set(BizGoodsStockDaily::getUpdateTime, DateUtils.getNowDate()));
        if (rows == 0)
        {
            // 当天还没有该行：直接补建（等价于初始化）
            insertRowIfAbsent(goodsId, sid, today, newLimit);
        }
        // 重置后不再耗尽 → 取消自动售罄（手动售罄不动）
        refreshAutoSoldOut(goodsId, sid, today);
        log.info("[库存重置] goodsId={} specId={} limit={}（sold_qty 归零）", goodsId, sid, newLimit);
    }

    // ==================== 内部工具 ====================

    /** 惰性初始化某菜品当天的全部库存行（无规格→菜品级一行；有规格→逐规格一行） */
    private void ensureRowsForGoods(BizGoods goods, Date stockDate)
    {
        List<BizGoodsSpec> specs = goodsSpecMapper.selectList(new LambdaQueryWrapper<BizGoodsSpec>()
                .eq(BizGoodsSpec::getGoodsId, goods.getId()));
        if (specs.isEmpty())
        {
            insertRowIfAbsent(goods.getId(), GOODS_LEVEL, stockDate, limitOf(goods, null));
        }
        else
        {
            for (BizGoodsSpec spec : specs)
            {
                insertRowIfAbsent(goods.getId(), spec.getId(), stockDate, limitOf(goods, spec));
            }
        }
    }

    /** 自动售罄刷新：耗尽置 1、未耗尽置 0（只写 auto_sold_out，绝不覆盖手动售罄） */
    private void refreshAutoSoldOut(Long goodsId, Long specId, Date stockDate)
    {
        // 注意：stock_date 为 DATE 列，此处入参可能是订单下单时间（含时分秒），
        // 必须先归一化为当天零点，否则 DATE 列与 DATETIME 值比较不相等、查不到行。
        Date dateOnly = DateUtils.parseDate(DateUtils.parseDateToStr("yyyy-MM-dd", stockDate));
        BizGoodsStockDaily row = stockMapper.selectOne(new LambdaQueryWrapper<BizGoodsStockDaily>()
                .eq(BizGoodsStockDaily::getGoodsId, goodsId)
                .eq(BizGoodsStockDaily::getSpecId, specId)
                .eq(BizGoodsStockDaily::getStockDate, dateOnly)
                .last("limit 1"));
        if (row == null)
        {
            return;
        }
        String auto = row.remainQty() <= 0 ? "1" : "0";
        Date now = DateUtils.getNowDate();
        if (specId == GOODS_LEVEL)
        {
            goodsMapper.update(null, new LambdaUpdateWrapper<BizGoods>()
                    .eq(BizGoods::getId, goodsId)
                    .set(BizGoods::getAutoSoldOut, auto)
                    .set(BizGoods::getUpdateTime, now));
        }
        else
        {
            goodsSpecMapper.update(null, new LambdaUpdateWrapper<BizGoodsSpec>()
                    .eq(BizGoodsSpec::getId, specId)
                    .set(BizGoodsSpec::getAutoSoldOut, auto));
        }
    }

    /** 插入库存行（唯一索引冲突时忽略，返回是否新增） */
    private int insertRowIfAbsent(Long goodsId, long specId, Date stockDate, int limitQty)
    {
        try
        {
            BizGoodsStockDaily row = new BizGoodsStockDaily();
            row.setGoodsId(goodsId);
            row.setSpecId(specId);
            row.setStockDate(stockDate);
            row.setLimitQty(limitQty);
            row.setSoldQty(0);
            row.setCreateTime(DateUtils.getNowDate());
            row.setUpdateTime(DateUtils.getNowDate());
            return stockMapper.insert(row);
        }
        catch (org.springframework.dao.DuplicateKeyException e)
        {
            // 并发/重复调度下已存在：幂等忽略
            return 0;
        }
    }

    /** 限量值：规格级优先，回落菜品级 */
    private int limitOf(BizGoods goods, BizGoodsSpec spec)
    {
        if (spec != null && spec.getDailyLimitQty() != null)
        {
            return Math.max(spec.getDailyLimitQty(), 0);
        }
        return goods.getDailyLimitQty() == null ? 0 : Math.max(goods.getDailyLimitQty(), 0);
    }

    /** 明细的规格ID（快照 JSON 里的 spec.id；无规格为菜品级 0） */
    private long resolveSpecId(BizOrderItem item)
    {
        String json = item.getSpecFlavorJson();
        if (json == null || json.isEmpty())
        {
            return GOODS_LEVEL;
        }
        try
        {
            com.alibaba.fastjson2.JSONObject obj = com.alibaba.fastjson2.JSON.parseObject(json);
            com.alibaba.fastjson2.JSONObject spec = obj.getJSONObject("spec");
            if (spec != null && spec.getLong("id") != null)
            {
                return spec.getLong("id");
            }
        }
        catch (Exception e)
        {
            log.warn("[库存] 明细规格快照解析失败，按菜品级处理：itemId={}", item.getId());
        }
        return GOODS_LEVEL;
    }

    private int remainFor(Long goodsId, long specId, Date stockDate)
    {
        BizGoodsStockDaily row = stockMapper.selectOne(new LambdaQueryWrapper<BizGoodsStockDaily>()
                .eq(BizGoodsStockDaily::getGoodsId, goodsId)
                .eq(BizGoodsStockDaily::getSpecId, specId)
                .eq(BizGoodsStockDaily::getStockDate, stockDate)
                .last("limit 1"));
        return row == null ? 0 : row.remainQty();
    }

    private String goodsName(Long goodsId)
    {
        BizGoods goods = goodsMapper.selectById(goodsId);
        return goods == null ? ("菜品#" + goodsId) : goods.getName();
    }

    /** 当天日期（只取日期部分，与 date 列比较） */
    private Date today()
    {
        return DateUtils.parseDate(DateUtils.getDate());
    }
}
