package com.ruoyi.userapi.domain.vo;

import java.math.BigDecimal;

/**
 * 收藏列表项 VO（T20 子项④）。
 * 联查 biz_goods 当前状态：下架/售罄商品仍展示但置灰（历史收藏不因商品状态丢失）。
 *
 * @author 阿婆干饭社
 */
public class FavoriteItemVo
{
    /** 收藏ID */
    private Long id;

    /** 菜品ID */
    private Long goodsId;

    /** 菜品名称 */
    private String goodsName;

    /** 图片完整 URL（前缀拼接后的） */
    private String image;

    /** 当前售价（基础价） */
    private BigDecimal price;

    /** 描述 */
    private String description;

    /** 是否在售（biz_goods.status='1'） */
    private Boolean onSale;

    /** 菜品级售罄标记（'1'售罄） */
    private String soldOut;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getOnSale() { return onSale; }
    public void setOnSale(Boolean onSale) { this.onSale = onSale; }

    public String getSoldOut() { return soldOut; }
    public void setSoldOut(String soldOut) { this.soldOut = soldOut; }
}
