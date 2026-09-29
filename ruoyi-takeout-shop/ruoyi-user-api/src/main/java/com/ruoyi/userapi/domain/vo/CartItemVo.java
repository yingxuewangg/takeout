package com.ruoyi.userapi.domain.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车明细 VO（含菜品实时信息与有效性标记）
 *
 * @author 阿婆干饭社
 */
public class CartItemVo
{
    /** 购物车记录ID */
    private Long id;

    private Long goodsId;

    private String goodsName;

    /** 图片完整 URL（后端拼接） */
    private String image;

    /** 图片相对路径（数据库原始值，下单时写入订单明细快照用） */
    private String imagePath;

    /** 规格ID（可空） */
    private Long specId;

    /** 规格名快照 */
    private String specName;

    /** 口味快照（已解析：[{name,values[]}]） */
    private List<FlavorItem> flavors;

    /** 当前单价（基础价+规格差价，菜品改价后以此为准展示） */
    private BigDecimal price;

    /** 数量 */
    private Integer quantity;

    /** 小计 */
    private BigDecimal subtotal;

    /**
     * 有效性：normal 正常 / soldout 已售罄 / offshelf 已下架（置灰且结算拦截）
     */
    private String status;

    public static class FlavorItem
    {
        private String name;

        private List<String> values;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<String> getValues() { return values; }
        public void setValues(List<String> values) { this.values = values; }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

    public String getGoodsName() { return goodsName; }
    public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public Long getSpecId() { return specId; }
    public void setSpecId(Long specId) { this.specId = specId; }

    public String getSpecName() { return specName; }
    public void setSpecName(String specName) { this.specName = specName; }

    public List<FlavorItem> getFlavors() { return flavors; }
    public void setFlavors(List<FlavorItem> flavors) { this.flavors = flavors; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
