package com.ruoyi.merchant.domain.vo;

import java.math.BigDecimal;

/**
 * 数据看板 - 出餐效率 VO（T19）。
 *
 * 口径：仅统计对应时间字段非 NULL 的订单（即真实发生过该环节的订单），不做状态过滤——
 * 已退款订单的环节耗时同样是真实经营耗时；样本量并列展示。
 *   接单耗时   = pay_time -> accept_time（T19 字段）
 *   制作耗时   = pay_time -> ready_time（T18 字段）
 *   取餐等待   = ready_time -> picked_up_time（外卖）
 *   配送耗时   = picked_up_time -> delivered_time（外卖）
 * 平均值以秒计，前端格式化为分秒展示。
 *
 * @author 阿婆干饭社
 */
public class DashboardEfficiencyVo
{
    /** 平均接单耗时（秒，null=无样本） */
    private BigDecimal avgAcceptSeconds;

    /** 接单样本数 */
    private long acceptSamples;

    /** 平均制作耗时（秒，null=无样本） */
    private BigDecimal avgMakeSeconds;

    /** 制作样本数 */
    private long makeSamples;

    /** 平均取餐等待（秒，null=无样本） */
    private BigDecimal avgPickupWaitSeconds;

    /** 取餐等待样本数 */
    private long pickupWaitSamples;

    /** 平均配送耗时（秒，null=无样本） */
    private BigDecimal avgDeliverSeconds;

    /** 配送样本数 */
    private long deliverSamples;

    public BigDecimal getAvgAcceptSeconds() { return avgAcceptSeconds; }
    public void setAvgAcceptSeconds(BigDecimal avgAcceptSeconds) { this.avgAcceptSeconds = avgAcceptSeconds; }

    public long getAcceptSamples() { return acceptSamples; }
    public void setAcceptSamples(long acceptSamples) { this.acceptSamples = acceptSamples; }

    public BigDecimal getAvgMakeSeconds() { return avgMakeSeconds; }
    public void setAvgMakeSeconds(BigDecimal avgMakeSeconds) { this.avgMakeSeconds = avgMakeSeconds; }

    public long getMakeSamples() { return makeSamples; }
    public void setMakeSamples(long makeSamples) { this.makeSamples = makeSamples; }

    public BigDecimal getAvgPickupWaitSeconds() { return avgPickupWaitSeconds; }
    public void setAvgPickupWaitSeconds(BigDecimal avgPickupWaitSeconds) { this.avgPickupWaitSeconds = avgPickupWaitSeconds; }

    public long getPickupWaitSamples() { return pickupWaitSamples; }
    public void setPickupWaitSamples(long pickupWaitSamples) { this.pickupWaitSamples = pickupWaitSamples; }

    public BigDecimal getAvgDeliverSeconds() { return avgDeliverSeconds; }
    public void setAvgDeliverSeconds(BigDecimal avgDeliverSeconds) { this.avgDeliverSeconds = avgDeliverSeconds; }

    public long getDeliverSamples() { return deliverSamples; }
    public void setDeliverSamples(long deliverSamples) { this.deliverSamples = deliverSamples; }
}
