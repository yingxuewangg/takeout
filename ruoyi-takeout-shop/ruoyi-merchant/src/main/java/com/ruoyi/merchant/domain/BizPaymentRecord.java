package com.ruoyi.merchant.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 模拟支付流水对象 biz_payment_record（订单号唯一约束用于支付幂等）
 *
 * @author 阿婆干饭社
 */
@TableName("biz_payment_record")
public class BizPaymentRecord implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 流水ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单号（唯一约束：重复支付插入冲突即幂等拦截） */
    private String orderNo;

    /** 支付用户ID */
    private Long memberId;

    /** 支付金额（以库内订单实付金额为准，不信任前端） */
    private BigDecimal amount;

    /** 支付方式（1微信支付-模拟） */
    private Integer payType;

    /** 支付状态（1成功） */
    private Integer payStatus;

    /** 模拟支付标记（1模拟；接真实支付后为0） */
    private Integer isMock;

    /** 模拟流水号 */
    private String mockTradeNo;

    /** 支付时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date payTime;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Integer getPayType() { return payType; }
    public void setPayType(Integer payType) { this.payType = payType; }

    public Integer getPayStatus() { return payStatus; }
    public void setPayStatus(Integer payStatus) { this.payStatus = payStatus; }

    public Integer getIsMock() { return isMock; }
    public void setIsMock(Integer isMock) { this.isMock = isMock; }

    public String getMockTradeNo() { return mockTradeNo; }
    public void setMockTradeNo(String mockTradeNo) { this.mockTradeNo = mockTradeNo; }

    public Date getPayTime() { return payTime; }
    public void setPayTime(Date payTime) { this.payTime = payTime; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
