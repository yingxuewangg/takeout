package com.ruoyi.merchant.domain.vo;

import java.math.BigDecimal;

/**
 * 数据看板 - 趋势点 VO（T14）。
 * 按天或按小时的时间序列点；空区间由服务层补零，保证折线图连续。
 *
 * @author 阿婆干饭社
 */
public class DashboardTrendVo
{
    /** 时间标签（按天：yyyy-MM-dd；按小时：yyyy-MM-dd HH:00） */
    private String timeLabel;

    /** 营业额（该时间点已完成订单实付合计，含配送费） */
    private BigDecimal revenue = BigDecimal.ZERO;

    /** 订单量（该时间点已完成订单数） */
    private long orderCount;

    /** 客单价（T19：该时间点营业额/订单量，服务层计算，无订单为 0） */
    private BigDecimal avgOrderAmount = BigDecimal.ZERO;

    /** 退款成功订单数（T19：该时间点 refund_status=2 的订单数） */
    private long refundedCount;

    /** 退款率（T19：退款成功订单数/(有效订单数+退款成功订单数)，百分数，服务层计算） */
    private BigDecimal refundRate = BigDecimal.ZERO;

    public DashboardTrendVo() { }

    public DashboardTrendVo(String timeLabel)
    {
        this.timeLabel = timeLabel;
    }

    public String getTimeLabel() { return timeLabel; }
    public void setTimeLabel(String timeLabel) { this.timeLabel = timeLabel; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public long getOrderCount() { return orderCount; }
    public void setOrderCount(long orderCount) { this.orderCount = orderCount; }

    public BigDecimal getAvgOrderAmount() { return avgOrderAmount; }
    public void setAvgOrderAmount(BigDecimal avgOrderAmount) { this.avgOrderAmount = avgOrderAmount; }

    public long getRefundedCount() { return refundedCount; }
    public void setRefundedCount(long refundedCount) { this.refundedCount = refundedCount; }

    public BigDecimal getRefundRate() { return refundRate; }
    public void setRefundRate(BigDecimal refundRate) { this.refundRate = refundRate; }
}
