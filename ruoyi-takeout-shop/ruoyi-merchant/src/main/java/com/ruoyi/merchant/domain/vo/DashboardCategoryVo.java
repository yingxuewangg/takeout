package com.ruoyi.merchant.domain.vo;

import java.math.BigDecimal;

/**
 * 数据看板 - 分类占比项 VO（T14）。
 *
 * 口径说明：分类按「菜品当前所属分类」聚合（biz_goods.category_id → biz_category.name），
 * 非下单时的历史分类快照（订单明细只存菜品名，未存分类）；菜品被删除或改分类会影响历史归类。
 *
 * @author 阿婆干饭社
 */
public class DashboardCategoryVo
{
    private Long categoryId;

    private String categoryName;

    /** 该分类营业额（明细 subtotal 合计） */
    private BigDecimal amount = BigDecimal.ZERO;

    /** 该分类销量（明细 quantity 合计） */
    private long quantity;

    /** 占比（%，保留两位；由服务层计算，便于前端直接展示） */
    private BigDecimal percent = BigDecimal.ZERO;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }

    public BigDecimal getPercent() { return percent; }
    public void setPercent(BigDecimal percent) { this.percent = percent; }
}
