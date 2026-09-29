package com.ruoyi.userapi.domain.vo;

import java.math.BigDecimal;

/**
 * 下单结果 VO（跳转收银台用）
 *
 * @author 阿婆干饭社
 */
public class OrderCreateVo
{
    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 应付金额（菜品合计+配送费快照） */
    private BigDecimal payAmount;

    /** 待支付超时分钟数（10） */
    private Integer timeoutMinutes;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public BigDecimal getPayAmount() { return payAmount; }
    public void setPayAmount(BigDecimal payAmount) { this.payAmount = payAmount; }

    public Integer getTimeoutMinutes() { return timeoutMinutes; }
    public void setTimeoutMinutes(Integer timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }
}
