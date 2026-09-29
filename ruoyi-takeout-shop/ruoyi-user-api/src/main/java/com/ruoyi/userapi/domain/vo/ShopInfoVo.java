package com.ruoyi.userapi.domain.vo;

import java.math.BigDecimal;

/**
 * 小程序端店铺信息 VO（含营业状态/配送费/起送价，供首页与 T4 下单校验展示）
 *
 * @author 阿婆干饭社
 */
public class ShopInfoVo
{
    private Long shopId;

    private String shopName;

    private String address;

    private String businessHours;

    /** 1营业中 0已打烊 */
    private Integer businessStatus;

    private String phone;

    private String notice;

    /** 外卖配送费 */
    private BigDecimal deliveryFee;

    /** 外卖起送价 */
    private BigDecimal minDeliveryAmount;

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

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
}
