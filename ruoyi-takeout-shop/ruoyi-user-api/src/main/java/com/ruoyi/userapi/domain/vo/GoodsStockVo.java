package com.ruoyi.userapi.domain.vo;

import java.util.List;

/**
 * 小程序端库存查询 VO（T12）。
 * 列表页批量查询/详情页单独查询共用；未启用每日限量的菜品不返回。
 *
 * @author 阿婆干饭社
 */
public class GoodsStockVo
{
    /** 菜品ID */
    private Long goodsId;

    /** 是否启用每日限量 */
    private Boolean dailyLimitEnabled;

    /** 菜品级剩余库存（未启用/无菜品级库存行为 null） */
    private Integer remainQty;

    /** 菜品级限量（未启用/无菜品级库存行为 null） */
    private Integer limitQty;

    /** 各规格库存（无规格时为空列表） */
    private List<SpecStockVo> specs;

    public Long getGoodsId() { return goodsId; }
    public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

    public Boolean getDailyLimitEnabled() { return dailyLimitEnabled; }
    public void setDailyLimitEnabled(Boolean dailyLimitEnabled) { this.dailyLimitEnabled = dailyLimitEnabled; }

    public Integer getRemainQty() { return remainQty; }
    public void setRemainQty(Integer remainQty) { this.remainQty = remainQty; }

    public Integer getLimitQty() { return limitQty; }
    public void setLimitQty(Integer limitQty) { this.limitQty = limitQty; }

    public List<SpecStockVo> getSpecs() { return specs; }
    public void setSpecs(List<SpecStockVo> specs) { this.specs = specs; }

    /** 规格库存项 */
    public static class SpecStockVo
    {
        private Long specId;
        private Integer remainQty;
        private Integer limitQty;

        public Long getSpecId() { return specId; }
        public void setSpecId(Long specId) { this.specId = specId; }

        public Integer getRemainQty() { return remainQty; }
        public void setRemainQty(Integer remainQty) { this.remainQty = remainQty; }

        public Integer getLimitQty() { return limitQty; }
        public void setLimitQty(Integer limitQty) { this.limitQty = limitQty; }
    }
}
