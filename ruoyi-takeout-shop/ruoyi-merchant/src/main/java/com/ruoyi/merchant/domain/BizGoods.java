package com.ruoyi.merchant.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 菜品对象 biz_goods
 * 图片字段只存"名称+相对路径"，不存域名/完整 URL（v2.5 图片存储方案）
 *
 * @author 阿婆干饭社
 */
@TableName("biz_goods")
public class BizGoods implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 菜品ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 分类ID */
    private Long categoryId;

    /** 菜品名称 */
    private String name;

    /** 菜品图片（只存相对路径，不存域名） */
    private String image;

    /** 售价（基础价） */
    private BigDecimal price;

    /** 菜品描述 */
    private String description;

    /** 上架状态（1在售 0下架） */
    private String status;

    /** 手动售罄标记（菜品级：0否 1是；优先级高于自动售罄） */
    private String soldOut;

    /** 自动售罄标记（T12 每日限量：0否 1是；库存耗尽置1、释放后置0，不覆盖手动售罄） */
    private String autoSoldOut;

    /** 是否启用每日限量（T12：0否 1是） */
    private String dailyLimitEnabled;

    /** 每日限量值（菜品级模板；规格级以 biz_goods_spec.daily_limit_qty 优先） */
    private Integer dailyLimitQty;

    /** 销量（支付成功的数据库事务内同步累加） */
    private Integer sales;

    /** 显示顺序（越小越靠前） */
    private Integer sort;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /** 规格列表（非表字段，随菜品级联保存/查询） */
    @TableField(exist = false)
    private List<BizGoodsSpec> specs;

    /** 口味组列表（非表字段，随菜品级联保存/查询） */
    @TableField(exist = false)
    private List<BizGoodsFlavor> flavors;

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSoldOut() { return soldOut; }
    public void setSoldOut(String soldOut) { this.soldOut = soldOut; }

    public String getAutoSoldOut() { return autoSoldOut; }
    public void setAutoSoldOut(String autoSoldOut) { this.autoSoldOut = autoSoldOut; }

    public String getDailyLimitEnabled() { return dailyLimitEnabled; }
    public void setDailyLimitEnabled(String dailyLimitEnabled) { this.dailyLimitEnabled = dailyLimitEnabled; }

    public Integer getDailyLimitQty() { return dailyLimitQty; }
    public void setDailyLimitQty(Integer dailyLimitQty) { this.dailyLimitQty = dailyLimitQty; }

    public Integer getSales() { return sales; }
    public void setSales(Integer sales) { this.sales = sales; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public List<BizGoodsSpec> getSpecs() { return specs; }
    public void setSpecs(List<BizGoodsSpec> specs) { this.specs = specs; }

    public List<BizGoodsFlavor> getFlavors() { return flavors; }
    public void setFlavors(List<BizGoodsFlavor> flavors) { this.flavors = flavors; }
}
