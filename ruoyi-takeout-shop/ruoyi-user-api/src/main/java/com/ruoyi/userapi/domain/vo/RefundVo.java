package com.ruoyi.userapi.domain.vo;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 用户端退款记录 VO（图片凭证已拼好完整 URL）
 *
 * @author 阿婆干饭社
 */
public class RefundVo
{
    private Long id;

    private String orderNo;

    /** 退款金额（=订单实付，含配送费快照） */
    private BigDecimal refundAmount;

    /** 退款原因 */
    private String reason;

    /** 图片凭证（完整 URL 数组） */
    private List<String> evidenceImages;

    /** 审核状态（0待审核 1已通过 2已驳回） */
    private Integer auditStatus;

    /** 驳回理由（用户可见；含超时自动驳回文案） */
    private String rejectReason;

    /** 是否系统超时自动驳回 */
    private Integer isAutoReject;

    /** 退款成功时间（已通过时有值） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date refundTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public List<String> getEvidenceImages() { return evidenceImages; }
    public void setEvidenceImages(List<String> evidenceImages) { this.evidenceImages = evidenceImages; }

    public Integer getAuditStatus() { return auditStatus; }
    public void setAuditStatus(Integer auditStatus) { this.auditStatus = auditStatus; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public Integer getIsAutoReject() { return isAutoReject; }
    public void setIsAutoReject(Integer isAutoReject) { this.isAutoReject = isAutoReject; }

    public Date getRefundTime() { return refundTime; }
    public void setRefundTime(Date refundTime) { this.refundTime = refundTime; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
