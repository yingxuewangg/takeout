package com.ruoyi.userapi.domain.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 小程序端商品详情 VO（含规格/口味，售罄也可查看）
 *
 * @author 阿婆干饭社
 */
public class GoodsDetailVo
{
    private Long id;

    private Long categoryId;

    private String categoryName;

    private String name;

    /** 图片完整 URL（后端拼接） */
    private String image;

    private BigDecimal price;

    private String description;

    /** 1在售 0下架 */
    private String status;

    /** 售罄标记（0否 1是；有效售罄=手动 或 自动/库存耗尽） */
    private String soldOut;

    /** 是否启用每日限量（T12） */
    private Boolean dailyLimitEnabled;

    /** 菜品级今日剩余库存（未启用限量时为 null） */
    private Integer remainQty;

    /** 菜品级今日限量（未启用限量时为 null） */
    private Integer limitQty;

    private Integer sales;

    private List<SpecVo> specs;

    private List<FlavorVo> flavors;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSoldOut() { return soldOut; }
    public void setSoldOut(String soldOut) { this.soldOut = soldOut; }

    public Boolean getDailyLimitEnabled() { return dailyLimitEnabled; }
    public void setDailyLimitEnabled(Boolean dailyLimitEnabled) { this.dailyLimitEnabled = dailyLimitEnabled; }

    public Integer getRemainQty() { return remainQty; }
    public void setRemainQty(Integer remainQty) { this.remainQty = remainQty; }

    public Integer getLimitQty() { return limitQty; }
    public void setLimitQty(Integer limitQty) { this.limitQty = limitQty; }

    public Integer getSales() { return sales; }
    public void setSales(Integer sales) { this.sales = sales; }

    public List<SpecVo> getSpecs() { return specs; }
    public void setSpecs(List<SpecVo> specs) { this.specs = specs; }

    public List<FlavorVo> getFlavors() { return flavors; }
    public void setFlavors(List<FlavorVo> flavors) { this.flavors = flavors; }

    /**
     * 规格 VO
     */
    public static class SpecVo
    {
        private Long id;

        private String name;

        private BigDecimal priceDelta;

        /** 规格级售罄标记（0未售罄 1已售罄；有效售罄=手动 或 自动/库存耗尽） */
        private String soldOut;

        /** 该规格今日剩余库存（T12；未启用限量时为 null） */
        private Integer remainQty;

        /** 该规格今日限量（T12；未启用限量时为 null） */
        private Integer limitQty;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public BigDecimal getPriceDelta() { return priceDelta; }
        public void setPriceDelta(BigDecimal priceDelta) { this.priceDelta = priceDelta; }

        public String getSoldOut() { return soldOut; }
        public void setSoldOut(String soldOut) { this.soldOut = soldOut; }

        public Integer getRemainQty() { return remainQty; }
        public void setRemainQty(Integer remainQty) { this.remainQty = remainQty; }

        public Integer getLimitQty() { return limitQty; }
        public void setLimitQty(Integer limitQty) { this.limitQty = limitQty; }
    }

    /**
     * 口味组 VO（options 为字符串数组，后端解析 JSON 列）
     */
    public static class FlavorVo
    {
        private Long id;

        private String name;

        private List<String> options;

        /** 0单选 1多选 */
        private String selectType;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<String> getOptions() { return options; }
        public void setOptions(List<String> options) { this.options = options; }

        public String getSelectType() { return selectType; }
        public void setSelectType(String selectType) { this.selectType = selectType; }
    }
}
