package com.ruoyi.merchant.domain;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 每日库存对象 biz_goods_stock_daily（T12 每日限量 + 自动售罄）
 *
 * 说明：
 *  1) spec_id 用 0 表示"菜品级"（不用 NULL：MySQL 唯一索引对 NULL 不去重）；
 *  2) 扣减用条件更新（where sold_qty + N <= limit_qty）防超卖；
 *  3) 库存不缓存，直接查库。
 *
 * @author 阿婆干饭社
 */
@TableName("biz_goods_stock_daily")
public class BizGoodsStockDaily implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 菜品级库存的 spec_id 占位值（0，非 NULL） */
    public static final long SPEC_ID_GOODS_LEVEL = 0L;

    /** 库存行ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 菜品ID */
    private Long goodsId;

    /** 规格ID（0=菜品级） */
    private Long specId;

    /** 库存日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date stockDate;

    /** 当日限量值 */
    private Integer limitQty;

    /** 当日已售数量 */
    private Integer soldQty;

    private Date createTime;

    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

    public Long getSpecId() { return specId; }
    public void setSpecId(Long specId) { this.specId = specId; }

    public Date getStockDate() { return stockDate; }
    public void setStockDate(Date stockDate) { this.stockDate = stockDate; }

    public Integer getLimitQty() { return limitQty; }
    public void setLimitQty(Integer limitQty) { this.limitQty = limitQty; }

    public Integer getSoldQty() { return soldQty; }
    public void setSoldQty(Integer soldQty) { this.soldQty = soldQty; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    /** 剩余库存（不为负） */
    public int remainQty()
    {
        int limit = limitQty == null ? 0 : limitQty;
        int sold = soldQty == null ? 0 : soldQty;
        return Math.max(limit - sold, 0);
    }
}
