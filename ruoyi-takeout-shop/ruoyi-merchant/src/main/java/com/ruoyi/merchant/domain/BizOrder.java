package com.ruoyi.merchant.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 订单主表对象 biz_order。
 * 核心设计（方案 3.4）：主状态 status 只表达业务流转，与退款状态 refund_status 彻底分离。
 * 状态字典（三处一致：SQL 注释/用户端/管理端）：
 *   status        0待支付 1待接单 2已接单制作中 3已出餐待取餐 4配送中 5已完成 6已取消
 *   refund_status 0无退款 1退款审核中(主状态冻结/流转暂停) 2已退款(终态)
 *
 * @author 阿婆干饭社
 */
@TableName("biz_order")
public class BizOrder implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 订单ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单号（唯一） */
    private String orderNo;

    /** 下单用户ID */
    private Long memberId;

    /** 履约方式（1堂食 2外卖配送） */
    private Integer deliveryType;

    /** 桌号（堂食必绑；同桌多单各自独立） */
    private String tableNo;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 收货地址快照（JSON：联系人/电话/省市区/详址；不随后续地址修改变化，堂食为null） */
    private String addressSnapshot;

    /** 菜品合计 */
    private BigDecimal goodsAmount;

    /** 配送费快照（堂食为0；外卖下单时从店铺信息快照） */
    private BigDecimal deliveryFee;

    /** 实付金额（=菜品合计+配送费快照；退款金额以此为准） */
    private BigDecimal payAmount;

    /** 订单主状态（0待支付 1待接单 2已接单制作中 3已出餐待取餐 4配送中 5已完成 6已取消） */
    private Integer status;

    /** 退款状态（与主状态分离：0无退款 1退款审核中 2已退款终态） */
    private Integer refundStatus;

    /** 订单备注 */
    private String remark;

    /** 支付方式（1微信支付-模拟） */
    private Integer payType;

    /** 支付状态（0未支付 1已支付） */
    private Integer payStatus;

    /** 支付时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date payTime;

    /** 接单时间（T19：主状态1->2时写入，条件更新幂等不覆盖） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date acceptTime;

    /** 出餐时间（T18：主状态2->3时写入，条件更新幂等不覆盖） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date readyTime;

    /** 骑手取餐时间（T18：外卖主状态3->4时写入） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pickedUpTime;

    /** 送达时间（T18：外卖主状态4->5时写入） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date deliveredTime;

    /** 超时自动关闭时间（下单时=创建时间+10分钟） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date timeoutCloseTime;

    /** 库存是否已释放（T12 幂等键：0未释放 1已释放；超时关单/取消/退款成功任一入口释放，只释放一次） */
    private String stockReleased;

    /** 下单时间 */
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

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getPayType() { return payType; }
    public void setPayType(Integer payType) { this.payType = payType; }

    public Integer getPayStatus() { return payStatus; }
    public void setPayStatus(Integer payStatus) { this.payStatus = payStatus; }

    public Date getPayTime() { return payTime; }
    public void setPayTime(Date payTime) { this.payTime = payTime; }

    public Date getAcceptTime() { return acceptTime; }
    public void setAcceptTime(Date acceptTime) { this.acceptTime = acceptTime; }

    public Date getReadyTime() { return readyTime; }
    public void setReadyTime(Date readyTime) { this.readyTime = readyTime; }

    public Date getPickedUpTime() { return pickedUpTime; }
    public void setPickedUpTime(Date pickedUpTime) { this.pickedUpTime = pickedUpTime; }

    public Date getDeliveredTime() { return deliveredTime; }
    public void setDeliveredTime(Date deliveredTime) { this.deliveredTime = deliveredTime; }

    public Date getTimeoutCloseTime() { return timeoutCloseTime; }
    public void setTimeoutCloseTime(Date timeoutCloseTime) { this.timeoutCloseTime = timeoutCloseTime; }

    public String getStockReleased() { return stockReleased; }
    public void setStockReleased(String stockReleased) { this.stockReleased = stockReleased; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
