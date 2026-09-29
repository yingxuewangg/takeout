package com.ruoyi.merchant.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 退款单对象 biz_order_refund（驳回后可再次申请，每次申请新增一条记录，历史保留）。
 * 审核状态字典（与 SQL 注释/两端展示一致）：
 *   audit_status 0待审核 1已通过(模拟退款成功) 2已驳回/超时自动驳回
 * 用户自助申请范围：订单主状态 1-3 且订单 refund_status=0；配送中(4)不可自助申请。
 *
 * @author 阿婆干饭社
 */
@TableName("biz_order_refund")
public class BizOrderRefund implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 退款单ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 申请用户ID */
    private Long memberId;

    /** 退款金额（=订单实付金额，含配送费快照，仅整单退款） */
    private BigDecimal refundAmount;

    /** 退款原因（必填） */
    private String reason;

    /** 图片凭证（JSON数组存相对路径，经统一上传模块上传） */
    private String evidenceImages;

    /** 审核状态（0待审核 1已通过(模拟退款成功) 2已驳回/超时自动驳回） */
    private Integer auditStatus;

    /** 驳回理由（用户可见） */
    private String rejectReason;

    /** 是否系统超时自动驳回（0否 1是；24小时未审核自动驳回） */
    private Integer isAutoReject;

    /** 模拟退款标记（1模拟；接真实支付后为0） */
    private Integer isMockRefund;

    /** 退款成功时间（审核通过时写入） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date refundTime;

    /** 申请时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getEvidenceImages() { return evidenceImages; }
    public void setEvidenceImages(String evidenceImages) { this.evidenceImages = evidenceImages; }

    public Integer getAuditStatus() { return auditStatus; }
    public void setAuditStatus(Integer auditStatus) { this.auditStatus = auditStatus; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public Integer getIsAutoReject() { return isAutoReject; }
    public void setIsAutoReject(Integer isAutoReject) { this.isAutoReject = isAutoReject; }

    public Integer getIsMockRefund() { return isMockRefund; }
    public void setIsMockRefund(Integer isMockRefund) { this.isMockRefund = isMockRefund; }

    public Date getRefundTime() { return refundTime; }
    public void setRefundTime(Date refundTime) { this.refundTime = refundTime; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
