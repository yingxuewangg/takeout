package com.ruoyi.merchant.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 订单明细对象 biz_order_item（菜品快照：名称/图片相对路径/单价/数量/规格口味 JSON/小计）
 *
 * @author 阿婆干饭社
 */
@TableName("biz_order_item")
public class BizOrderItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 明细ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 菜品ID */
    private Long goodsId;

    /** 菜品名称快照 */
    private String goodsName;

    /** 菜品图片快照（只存相对路径） */
    private String goodsImage;

    /** 规格口味快照（JSON，如 {"spec":{"id":3,"name":"大份","priceDelta":3},"flavors":[{"name":"辣度","values":["微辣"]}]}） */
    private String specFlavorJson;

    /** 单价快照（基础价+规格差价，下单时刻） */
    private BigDecimal unitPrice;

    /** 数量 */
    private Integer quantity;

    /** 小计（单价x数量） */
    private BigDecimal subtotal;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

    public String getGoodsImage() { return goodsImage; }
    public void setGoodsImage(String goodsImage) { this.goodsImage = goodsImage; }

    public String getSpecFlavorJson() { return specFlavorJson; }
    public void setSpecFlavorJson(String specFlavorJson) { this.specFlavorJson = specFlavorJson; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
}
