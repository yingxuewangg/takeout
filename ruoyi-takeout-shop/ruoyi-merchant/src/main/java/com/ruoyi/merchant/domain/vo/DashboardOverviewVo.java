package com.ruoyi.merchant.domain.vo;

import java.math.BigDecimal;

/**
 * 数据看板 - 概览指标 VO（T14）。
 *
 * 口径（已与用户确认）：
 *  - 营业额：主状态 5（已完成）订单的实付金额合计（= 菜品收入 + 配送费收入）；
 *  - 净营业额：营业额 - 已退款金额（refund_status=2）；退款审核中(1)不扣减，单独展示；
 *  - 有效订单数：主状态 5 的订单数；客单价 = 净营业额 / 有效订单数；
 *  - 时间维度按订单 create_time（下单时间，方案 A，不新增字段）。
 *
 * @author 阿婆干饭社
 */
public class DashboardOverviewVo
{
    /** 营业额（已完成订单实付合计，含配送费） */
    private BigDecimal revenue = BigDecimal.ZERO;

    /** 菜品收入（已完成订单 goods_amount 合计） */
    private BigDecimal goodsRevenue = BigDecimal.ZERO;

    /** 配送费收入（已完成订单 delivery_fee 合计） */
    private BigDecimal deliveryRevenue = BigDecimal.ZERO;

    /** 退款金额（展示口径：全部已退款订单的实付合计，供看板展示真实退款规模） */
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    /** 退款扣减额（扣减口径：仅"已完成且已退款"的金额，才真正计入营业额又需扣回） */
    private BigDecimal refundedInRevenue = BigDecimal.ZERO;

    /** 退款中金额（refund_status=1 的订单实付合计；不计入扣减，仅展示） */
    private BigDecimal refundingAmount = BigDecimal.ZERO;

    /** 净营业额 = 营业额 - 退款扣减额 */
    private BigDecimal netRevenue = BigDecimal.ZERO;

    /** 有效订单数（主状态 5） */
    private long orderCount;

    /** 客单价 = 净营业额 / 有效订单数（无订单时为 0） */
    private BigDecimal avgOrderAmount = BigDecimal.ZERO;

    /** 已取消订单数（主状态 6） */
    private long cancelledCount;

    /** 退款成功订单数（refund_status=2） */
    private long refundedCount;

    /** 退款率（T19：退款成功订单数/(有效订单数+退款成功订单数)，百分数，服务层计算） */
    private BigDecimal refundRate = BigDecimal.ZERO;

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public BigDecimal getGoodsRevenue() { return goodsRevenue; }
    public void setGoodsRevenue(BigDecimal goodsRevenue) { this.goodsRevenue = goodsRevenue; }

    public BigDecimal getDeliveryRevenue() { return deliveryRevenue; }
    public void setDeliveryRevenue(BigDecimal deliveryRevenue) { this.deliveryRevenue = deliveryRevenue; }

    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; }

    public BigDecimal getRefundedInRevenue() { return refundedInRevenue; }
    public void setRefundedInRevenue(BigDecimal refundedInRevenue) { this.refundedInRevenue = refundedInRevenue; }

    public BigDecimal getRefundingAmount() { return refundingAmount; }
    public void setRefundingAmount(BigDecimal refundingAmount) { this.refundingAmount = refundingAmount; }

    public BigDecimal getNetRevenue() { return netRevenue; }
    public void setNetRevenue(BigDecimal netRevenue) { this.netRevenue = netRevenue; }

    public long getOrderCount() { return orderCount; }
    public void setOrderCount(long orderCount) { this.orderCount = orderCount; }

    public BigDecimal getAvgOrderAmount() { return avgOrderAmount; }
    public void setAvgOrderAmount(BigDecimal avgOrderAmount) { this.avgOrderAmount = avgOrderAmount; }

    public long getCancelledCount() { return cancelledCount; }
    public void setCancelledCount(long cancelledCount) { this.cancelledCount = cancelledCount; }

    public long getRefundedCount() { return refundedCount; }
    public void setRefundedCount(long refundedCount) { this.refundedCount = refundedCount; }

    public BigDecimal getRefundRate() { return refundRate; }
    public void setRefundRate(BigDecimal refundRate) { this.refundRate = refundRate; }
}
