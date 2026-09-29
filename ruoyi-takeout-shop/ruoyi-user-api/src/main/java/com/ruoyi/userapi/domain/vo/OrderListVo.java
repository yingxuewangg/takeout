package com.ruoyi.userapi.domain.vo;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 用户订单列表 VO（轻量；退款状态与主状态同时下发，前端展示"退款状态优先"）
 *
 * @author 阿婆干饭社
 */
public class OrderListVo
{
    private Long id;

    private String orderNo;

    /** 主状态（0待支付 1待接单 2已接单制作中 3已出餐待取餐 4配送中 5已完成 6已取消） */
    private Integer status;

    /** 退款状态（0无 1退款审核中 2已退款；展示优先级高于主状态） */
    private Integer refundStatus;

    /** 履约方式（1堂食 2外卖） */
    private Integer deliveryType;

    private String tableNo;

    /** 地址摘要（外卖：详址截断；堂食为空） */
    private String addressBrief;

    /** 应付金额 */
    private BigDecimal payAmount;

    /** 商品总件数 */
    private Integer goodsCount;

    /** 待支付剩余秒数（status=0 时有效，其余为 0） */
    private Long remainSeconds;

    /** 是否已评价（T20 评价提醒：该订单存在关联留言即为 true） */
    private Boolean hasCommented;

    /** 是否可再次购买（T13：主状态 1-5 且 refund_status≠2） */
    private Boolean canRepurchase;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 明细摘要（图片完整 URL/名称/数量） */
    private List<ItemVo> items;

    public static class ItemVo
    {
        private Long goodsId;

        private String goodsName;

        private String image;

        private Integer quantity;

        public Long getGoodsId() { return goodsId; }
        public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

        public String getGoodsName() { return goodsName; }
        public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getRefundStatus() { return refundStatus; }
    public void setRefundStatus(Integer refundStatus) { this.refundStatus = refundStatus; }

    public Integer getDeliveryType() { return deliveryType; }
    public void setDeliveryType(Integer deliveryType) { this.deliveryType = deliveryType; }

    public String getTableNo() { return tableNo; }
    public void setTableNo(String tableNo) { this.tableNo = tableNo; }

    public String getAddressBrief() { return addressBrief; }
    public void setAddressBrief(String addressBrief) { this.addressBrief = addressBrief; }

    public BigDecimal getPayAmount() { return payAmount; }
    public void setPayAmount(BigDecimal payAmount) { this.payAmount = payAmount; }

    public Integer getGoodsCount() { return goodsCount; }
    public void setGoodsCount(Integer goodsCount) { this.goodsCount = goodsCount; }

    public Long getRemainSeconds() { return remainSeconds; }
    public void setRemainSeconds(Long remainSeconds) { this.remainSeconds = remainSeconds; }

    public Boolean getHasCommented() { return hasCommented; }
    public void setHasCommented(Boolean hasCommented) { this.hasCommented = hasCommented; }

    public Boolean getCanRepurchase() { return canRepurchase; }
    public void setCanRepurchase(Boolean canRepurchase) { this.canRepurchase = canRepurchase; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public List<ItemVo> getItems() { return items; }
    public void setItems(List<ItemVo> items) { this.items = items; }
}
