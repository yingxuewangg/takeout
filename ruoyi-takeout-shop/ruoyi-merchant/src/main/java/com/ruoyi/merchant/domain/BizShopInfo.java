package com.ruoyi.merchant.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 店铺信息对象 biz_shop_info（单店，shop_id 固定 1）
 *
 * @author 阿婆干饭社
 */
@TableName("biz_shop_info")
public class BizShopInfo implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 店铺ID（固定写1，不做多门店） */
    @TableId(value = "shop_id", type = IdType.INPUT)
    private Long shopId;

    /** 店铺名称 */
    private String shopName;

    /** 店铺位置（文字地址） */
    private String address;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 营业时间（如 09:00-21:00） */
    private String businessHours;

    /** 营业状态（1营业中 0已打烊；打烊后用户可浏览不可下单） */
    private Integer businessStatus;

    /** 商家电话（退款等场景展示给用户） */
    private String phone;

    /** 店铺公告 */
    private String notice;

    /** 外卖配送费（下单时快照到订单） */
    private BigDecimal deliveryFee;

    /** 外卖起送价（菜品合计未达不可下单） */
    private BigDecimal minDeliveryAmount;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public String getBusinessHours() { return businessHours; }
    public void setBusinessHours(String businessHours) { this.businessHours = businessHours; }

    public Integer getBusinessStatus() { return businessStatus; }
    public void setBusinessStatus(Integer businessStatus) { this.businessStatus = businessStatus; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getNotice() { return notice; }
    public void setNotice(String notice) { this.notice = notice; }

    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }

    public BigDecimal getMinDeliveryAmount() { return minDeliveryAmount; }
    public void setMinDeliveryAmount(BigDecimal minDeliveryAmount) { this.minDeliveryAmount = minDeliveryAmount; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
