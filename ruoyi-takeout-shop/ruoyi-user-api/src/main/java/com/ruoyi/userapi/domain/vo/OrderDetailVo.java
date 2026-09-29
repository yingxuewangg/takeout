package com.ruoyi.userapi.domain.vo;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 订单详情 VO（T5 供收银台/支付成功页展示；T6 订单中心在此基础上扩展状态展示）
 *
 * @author 阿婆干饭社
 */
public class OrderDetailVo
{
    private Long id;

    private String orderNo;

    /** 履约方式（1堂食 2外卖） */
    private Integer deliveryType;

    private String tableNo;

    private String contactName;

    private String contactPhone;

    /** 地址快照（JSON 字符串，堂食为空） */
    private String addressSnapshot;

    private BigDecimal goodsAmount;

    private BigDecimal deliveryFee;

    private BigDecimal payAmount;

    /** 主状态（0待支付 1待接单 ... 6已取消） */
    private Integer status;

    /** 退款状态（0无 1退款审核中 2已退款；展示优先级高于主状态） */
    private Integer refundStatus;

    private Integer payStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date payTime;

    /** 待支付剩余秒数（status=0 时用于收银台倒计时展示；已支付/已关闭为 0） */
    private Long remainSeconds;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 是否已评价（T20 评价提醒） */
    private Boolean hasCommented;

    private List<ItemVo> items;

    public static class ItemVo
    {
        private Long goodsId;

        private String goodsName;

        /** 图片完整 URL（后端拼接） */
        private String image;

        /** 规格口味快照（JSON 字符串） */
        private String specFlavorJson;

        private BigDecimal unitPrice;

        private Integer quantity;

        private BigDecimal subtotal;

        public Long getGoodsId() { return goodsId; }
        public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

        public String getGoodsName() { return goodsName; }
        public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }

        public String getSpecFlavorJson() { return specFlavorJson; }
        public void setSpecFlavorJson(String specFlavorJson) { this.specFlavorJson = specFlavorJson; }

        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public BigDecimal getSubtotal() { return subtotal; }
        public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public Integer getDeliveryType() { return deliveryType; }
    public void setDeliveryType(Integer deliveryType) { this.deliveryType = deliveryType; }

    public String getTableNo() { return tableNo; }
    public void setTableNo(String tableNo) { this.tableNo = tableNo; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getAddressSnapshot() { return addressSnapshot; }
    public void setAddressSnapshot(String addressSnapshot) { this.addressSnapshot = addressSnapshot; }

    public BigDecimal getGoodsAmount() { return goodsAmount; }
    public void setGoodsAmount(BigDecimal goodsAmount) { this.goodsAmount = goodsAmount; }

    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }

    public BigDecimal getPayAmount() { return payAmount; }
    public void setPayAmount(BigDecimal payAmount) { this.payAmount = payAmount; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getRefundStatus() { return refundStatus; }
    public void setRefundStatus(Integer refundStatus) { this.refundStatus = refundStatus; }

    public Integer getPayStatus() { return payStatus; }
    public void setPayStatus(Integer payStatus) { this.payStatus = payStatus; }

    public Date getPayTime() { return payTime; }
    public void setPayTime(Date payTime) { this.payTime = payTime; }

    public Long getRemainSeconds() { return remainSeconds; }
    public void setRemainSeconds(Long remainSeconds) { this.remainSeconds = remainSeconds; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public List<ItemVo> getItems() { return items; }
    public void setItems(List<ItemVo> items) { this.items = items; }

    public Boolean getHasCommented() { return hasCommented; }
    public void setHasCommented(Boolean hasCommented) { this.hasCommented = hasCommented; }
}
