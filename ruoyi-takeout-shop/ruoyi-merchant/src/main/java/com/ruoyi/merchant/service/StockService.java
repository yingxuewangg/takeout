package com.ruoyi.merchant.service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import com.ruoyi.merchant.domain.BizGoodsStockDaily;

/**
 * 每日库存服务（T12 每日限量 + 自动售罄）。
 *
 * 职责边界：库存是订单流转的"附加动作"，不参与订单主状态机/退款状态机/支付逻辑；
 * 所有库存操作均条件更新（影响行数校验）保证并发安全与幂等。
 *
 * @author 阿婆干饭社
 */
public interface StockService
{
    /**
     * 下单扣减库存（核心，防超卖）。
     * 对每个启用每日限量的明细执行条件扣减（where sold_qty + N <= limit_qty），
     * 任一条失败立即抛异常（由调用方事务回滚）。未启用限量的菜品跳过。
     *
     * @param items 待扣减明细（菜品/规格/数量）
     */
    void deductForOrder(List<StockDeductItem> items);

    /**
     * 释放订单占用的库存（幂等，四个入口共用：超时关单/定时兜底/用户取消/退款成功）。
     * 以订单号 + biz_order.stock_released 标记做幂等键，同一订单只释放一次；
     * 库存回退到"下单当天"那一行（跨天释放语义正确）。
     *
     * @param orderNo 订单号
     */
    void releaseByOrderNo(String orderNo);

    /**
     * 初始化指定日期的库存行（读取限量配置模板）。
     * 依赖唯一索引，已存在的行自动忽略（幂等，可重复调度）。
     *
     * @param stockDate 库存日期（当天 00:00 由定时任务调用）
     * @return 本次新增的行数
     */
    int initDailyStock(Date stockDate);

    /**
     * 惰性初始化：确保「菜品+规格+当天」的库存行存在（下单/查询前兜底）。
     * 未启用限量的菜品返回 null（表示不参与库存管理）。
     *
     * @param goodsId   菜品ID
     * @param specId    规格ID（null 或 0 表示菜品级）
     * @param stockDate 库存日期
     * @return 当天库存行；未启用限量时返回 null
     */
    BizGoodsStockDaily ensureDailyRow(Long goodsId, Long specId, Date stockDate);

    /**
     * 查询某菜品当天库存（含菜品级 + 各规格），供小程序端展示剩余库存。
     *
     * @return key：specId（0=菜品级）；未启用限量时不包含该 key
     */
    Map<Long, BizGoodsStockDaily> getGoodsStockMap(Long goodsId, Date stockDate);

    /**
     * 批量查询多个菜品当天的库存（列表页用，避免 N+1）。
     *
     * @return key：goodsId -> (specId -> 库存行)
     */
    Map<Long, Map<Long, BizGoodsStockDaily>> getGoodsStockMapBatch(List<Long> goodsIds, Date stockDate);

    /**
     * 管理端：查询当天全部库存行（含菜品名/规格名，供今日库存页面展示）。
     */
    List<StockView> listTodayStock();

    /**
     * 管理端：手动重置某菜品（或某规格）当天库存（临时补货）。
     * 重置后 sold_qty 归零，并按新限量值重新计算自动售罄。
     *
     * @param goodsId   菜品ID
     * @param specId    规格ID（null/0 表示菜品级）
     * @param limitQty  新的限量值（null 表示沿用配置模板值）
     */
    void resetStock(Long goodsId, Long specId, Integer limitQty);

    /** 下单扣减明细项 */
    class StockDeductItem
    {
        private final Long goodsId;
        private final Long specId;
        private final int quantity;

        public StockDeductItem(Long goodsId, Long specId, int quantity)
        {
            this.goodsId = goodsId;
            this.specId = specId;
            this.quantity = quantity;
        }

        public Long getGoodsId() { return goodsId; }
        public Long getSpecId() { return specId; }
        public int getQuantity() { return quantity; }
    }

    /** 管理端今日库存展示项 */
    class StockView
    {
        private Long id;
        private Long goodsId;
        private String goodsName;
        private Long specId;
        private String specName;
        private Integer limitQty;
        private Integer soldQty;
        private Integer remainQty;
        private String manualSoldOut;
        private String autoSoldOut;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Long getGoodsId() { return goodsId; }
        public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

        public String getGoodsName() { return goodsName; }
        public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

        public Long getSpecId() { return specId; }
        public void setSpecId(Long specId) { this.specId = specId; }

        public String getSpecName() { return specName; }
        public void setSpecName(String specName) { this.specName = specName; }

        public Integer getLimitQty() { return limitQty; }
        public void setLimitQty(Integer limitQty) { this.limitQty = limitQty; }

        public Integer getSoldQty() { return soldQty; }
        public void setSoldQty(Integer soldQty) { this.soldQty = soldQty; }

        public Integer getRemainQty() { return remainQty; }
        public void setRemainQty(Integer remainQty) { this.remainQty = remainQty; }

        public String getManualSoldOut() { return manualSoldOut; }
        public void setManualSoldOut(String manualSoldOut) { this.manualSoldOut = manualSoldOut; }

        public String getAutoSoldOut() { return autoSoldOut; }
        public void setAutoSoldOut(String autoSoldOut) { this.autoSoldOut = autoSoldOut; }
    }
}
