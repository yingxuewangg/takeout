package com.ruoyi.userapi.domain.vo;

import java.math.BigDecimal;
import java.util.List;
import com.ruoyi.userapi.domain.BizAddress;

/**
 * 结算预览 VO（T4 展示与校验，不落订单；下单在 T5）
 *
 * @author 阿婆干饭社
 */
public class CheckoutVo
{
    /** 结算明细（含失效项标记） */
    private List<CartItemVo> items;

    /** 是否存在失效项（售罄/下架，存在则不可提交） */
    private Boolean hasInvalid;

    /** 履约方式（1堂食 2外卖） */
    private Integer deliveryType;

    /** 桌号（堂食） */
    private String tableNo;

    /** 菜品合计 */
    private BigDecimal goodsAmount;

    /** 配送费（外卖=店铺当前值，堂食为0；下单时才快照） */
    private BigDecimal deliveryFee;

    /** 应付合计（堂食=菜品合计；外卖=菜品合计+配送费） */
    private BigDecimal totalAmount;

    /** 起送价（外卖） */
    private BigDecimal minDeliveryAmount;

    /** 未达起送价差额（外卖且未达时 >0，否则为0） */
    private BigDecimal amountShort;

    /** 是否可提交下单（无失效项 + 达起送价 + 未打烊） */
    private Boolean canSubmit;

    /** 店铺是否打烊（打烊可浏览不可下单） */
    private Boolean businessClosed;

    /** 打烊提示语 */
    private String closedTip;

    /** 默认收货地址（外卖用，可空） */
    private BizAddress defaultAddress;

    public List<CartItemVo> getItems() { return items; }
    public void setItems(List<CartItemVo> items) { this.items = items; }

    public Boolean getHasInvalid() { return hasInvalid; }
    public void setHasInvalid(Boolean hasInvalid) { this.hasInvalid = hasInvalid; }

    public Integer getDeliveryType() { return deliveryType; }
    public void setDeliveryType(Integer deliveryType) { this.deliveryType = deliveryType; }

    public String getTableNo() { return tableNo; }
    public void setTableNo(String tableNo) { this.tableNo = tableNo; }

    public BigDecimal getGoodsAmount() { return goodsAmount; }
    public void setGoodsAmount(BigDecimal goodsAmount) { this.goodsAmount = goodsAmount; }

    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getMinDeliveryAmount() { return minDeliveryAmount; }
    public void setMinDeliveryAmount(BigDecimal minDeliveryAmount) { this.minDeliveryAmount = minDeliveryAmount; }

    public BigDecimal getAmountShort() { return amountShort; }
    public void setAmountShort(BigDecimal amountShort) { this.amountShort = amountShort; }

    public Boolean getCanSubmit() { return canSubmit; }
    public void setCanSubmit(Boolean canSubmit) { this.canSubmit = canSubmit; }

    public Boolean getBusinessClosed() { return businessClosed; }
    public void setBusinessClosed(Boolean businessClosed) { this.businessClosed = businessClosed; }

    public String getClosedTip() { return closedTip; }
    public void setClosedTip(String closedTip) { this.closedTip = closedTip; }

    public BizAddress getDefaultAddress() { return defaultAddress; }
    public void setDefaultAddress(BizAddress defaultAddress) { this.defaultAddress = defaultAddress; }
}
