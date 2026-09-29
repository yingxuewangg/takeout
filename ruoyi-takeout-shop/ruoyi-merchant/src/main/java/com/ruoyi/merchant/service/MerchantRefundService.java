package com.ruoyi.merchant.service;

import java.util.Date;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.merchant.domain.BizOrderRefund;

/**
 * 管理端退款审核服务接口（方案 3.3）。
 * 通过=模拟退款成功（订单 refund_status=2 终态）；驳回=主状态不变、流转恢复、可再次申请；
 * 24 小时未审核由定时任务自动驳回（TakeoutTask.rejectTimeoutRefunds）。
 *
 * @author 阿婆干饭社
 */
public interface MerchantRefundService
{
    /**
     * 退款单分页列表（审核状态/订单号筛选；VO 含用户联系电话与订单信息）
     */
    IPage<RefundManageVo> listRefunds(Integer auditStatus, String orderNo, long pageNum, long pageSize);

    /**
     * 待审核数量（管理端角标提醒）
     */
    long pendingCount();

    /**
     * 退款单详情（凭证图片 URL 已拼接）
     */
    RefundManageVo getRefund(Long id);

    /**
     * 审核通过（模拟退款成功）：退款单 0->1 + 订单 refund_status 1->2（终态），事务内条件更新幂等
     */
    void approve(Long id);

    /**
     * 驳回（理由必填、用户可见）：退款单 0->2 + 订单 refund_status 1->0（主状态不变、流转恢复、可再次申请）
     */
    void reject(Long id, String reason);

    /** 管理端退款单 VO */
    class RefundManageVo
    {
        private Long id;

        private String orderNo;

        /** 订单ID */
        private Long orderId;

        /** 履约方式（1堂食 2外卖） */
        private Integer deliveryType;

        /** 订单主状态 */
        private Integer orderStatus;

        /** 用户联系电话（订单预留电话） */
        private String contactPhone;

        /** 用户联系人 */
        private String contactName;

        private java.math.BigDecimal refundAmount;

        private String reason;

        /** 图片凭证（完整 URL 数组） */
        private List<String> evidenceImages;

        /** 审核状态（0待审核 1已通过 2已驳回） */
        private Integer auditStatus;

        private String rejectReason;

        private Integer isAutoReject;

        private Date refundTime;

        private Date createTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }

        public Integer getDeliveryType() { return deliveryType; }
        public void setDeliveryType(Integer deliveryType) { this.deliveryType = deliveryType; }

        public Integer getOrderStatus() { return orderStatus; }
        public void setOrderStatus(Integer orderStatus) { this.orderStatus = orderStatus; }

        public String getContactPhone() { return contactPhone; }
        public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

        public String getContactName() { return contactName; }
        public void setContactName(String contactName) { this.contactName = contactName; }

        public java.math.BigDecimal getRefundAmount() { return refundAmount; }
        public void setRefundAmount(java.math.BigDecimal refundAmount) { this.refundAmount = refundAmount; }

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
}
