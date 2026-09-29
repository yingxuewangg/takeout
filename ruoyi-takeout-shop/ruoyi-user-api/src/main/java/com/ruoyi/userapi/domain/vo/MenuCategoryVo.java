package com.ruoyi.userapi.domain.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 小程序端菜单 VO：分类 + 在售菜品（含菜品级售罄标记，图片完整 URL 由后端拼接）
 *
 * @author 阿婆干饭社
 */
public class MenuCategoryVo
{
    /** 分类ID */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 在售菜品列表 */
    private List<MenuItemVo> goodsList;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public List<MenuItemVo> getGoodsList() { return goodsList; }
    public void setGoodsList(List<MenuItemVo> goodsList) { this.goodsList = goodsList; }

    /**
     * 菜单项 VO
     */
    public static class MenuItemVo
    {
        private Long id;

        private Long categoryId;

        private String name;

        /** 图片完整 URL（base-url + 相对路径，后端拼好） */
        private String image;

        private BigDecimal price;

        private String description;

        /** 售罄标记（0否 1是；售罄置灰不可加购可看详情） */
        private String soldOut;

        /** 是否有规格（有则点"+"需进详情选择，无则可直接加购） */
        private Boolean hasSpec;

        /** 是否有口味选项 */
        private Boolean hasFlavor;

        /** 是否启用每日限量（T12；列表缓存不含实时库存，剩余量由 /api/goods/stock 单独查询） */
        private Boolean dailyLimitEnabled;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getSoldOut() { return soldOut; }
        public void setSoldOut(String soldOut) { this.soldOut = soldOut; }

        public Boolean getHasSpec() { return hasSpec; }
        public void setHasSpec(Boolean hasSpec) { this.hasSpec = hasSpec; }

        public Boolean getHasFlavor() { return hasFlavor; }
        public void setHasFlavor(Boolean hasFlavor) { this.hasFlavor = hasFlavor; }

        public Boolean getDailyLimitEnabled() { return dailyLimitEnabled; }
        public void setDailyLimitEnabled(Boolean dailyLimitEnabled) { this.dailyLimitEnabled = dailyLimitEnabled; }
    }
}
