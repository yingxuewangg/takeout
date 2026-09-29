package com.ruoyi.merchant.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 菜品规格对象 biz_goods_spec（含差价）
 *
 * @author 阿婆干饭社
 */
@TableName("biz_goods_spec")
public class BizGoodsSpec implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 规格ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 菜品ID */
    private Long goodsId;

    /** 规格名（如大份/小份） */
    private String name;

    /** 规格差价（在基础价上加减） */
    private BigDecimal priceDelta;

	/** 显示顺序 */
	private Integer sort;

	/** 规格级售罄标记（0未售罄 1已售罄；手动，优先级高于自动售罄） */
	private String soldOut;

	/** 自动售罄标记（T12 每日限量：0否 1是；库存耗尽置1、释放后置0，不覆盖手动售罄） */
	private String autoSoldOut;

	/** 规格级每日限量值（T12：留空则回落菜品级 biz_goods.daily_limit_qty） */
	private Integer dailyLimitQty;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public Long getGoodsId() { return goodsId; }
	public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public BigDecimal getPriceDelta() { return priceDelta; }
	public void setPriceDelta(BigDecimal priceDelta) { this.priceDelta = priceDelta; }

	public Integer getSort() { return sort; }
	public void setSort(Integer sort) { this.sort = sort; }

	public String getSoldOut() { return soldOut; }
	public void setSoldOut(String soldOut) { this.soldOut = soldOut; }

	public String getAutoSoldOut() { return autoSoldOut; }
	public void setAutoSoldOut(String autoSoldOut) { this.autoSoldOut = autoSoldOut; }

	public Integer getDailyLimitQty() { return dailyLimitQty; }
	public void setDailyLimitQty(Integer dailyLimitQty) { this.dailyLimitQty = dailyLimitQty; }
}
