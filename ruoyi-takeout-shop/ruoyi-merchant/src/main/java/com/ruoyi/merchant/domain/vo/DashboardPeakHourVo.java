package com.ruoyi.merchant.domain.vo;

import java.math.BigDecimal;

/**
 * 数据看板 - 高峰时段 VO（T19）。
 *
 * 口径：区间内跨天按「小时 of day」聚合有效订单（主状态 5，按 create_time 过滤）；
 * 服务层补齐 0-23 全部小时，并标记订单数最高的时段（peak=true，并列时都标）。
 *
 * @author 阿婆干饭社
 */
public class DashboardPeakHourVo
{
    /** 小时标签（0-23，展示为 "8点"、"18点"） */
    private Integer hour;

    /** 该小时累计有效订单数（跨天相加） */
    private long orderCount;

    /** 该小时累计营业额（跨天相加，已完成订单实付合计） */
    private BigDecimal revenue = BigDecimal.ZERO;

    /** 是否高峰时段（订单数最高的小时，并列都为 true） */
    private Boolean peak = false;

    public Integer getHour() { return hour; }
    public void setHour(Integer hour) { this.hour = hour; }

    public long getOrderCount() { return orderCount; }
    public void setOrderCount(long orderCount) { this.orderCount = orderCount; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public Boolean getPeak() { return peak; }
    public void setPeak(Boolean peak) { this.peak = peak; }
}
