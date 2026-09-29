package com.ruoyi.merchant.domain.vo;

import java.math.BigDecimal;

/**
 * 数据看板 - 菜品销量排行项 VO（T14）。
 *
 * 口径（已与用户确认）：仅统计主状态 5（已完成）且 refund_status≠2 的订单明细，
 * 数据来源 biz_order_item 聚合（不用 biz_goods.sales，避免口径不一致）。
 *
 * @author 阿婆干饭社
 */
public class DashboardGoodsRankVo
{
    private Long goodsId;

    private String goodsName;

    /** 销量（明细 quantity 合计） */
    private long quantity;

    /** 销售额（明细 subtotal 合计，已含规格差价快照） */
    private BigDecimal amount = BigDecimal.ZERO;

    /** 退款关联数（T19：该菜品所在订单中已退款订单（refund_status=2）的明细件数合计，单独查询合并） */
    private long refundCount;

    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public long getRefundCount() { return refundCount; }
    public void setRefundCount(long refundCount) { this.refundCount = refundCount; }
}
