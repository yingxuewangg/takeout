package com.ruoyi.userapi.domain.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 再次购买结果 VO（T13）。
 *
 * 语义：
 *  - added：成功加入购物车的条目（含实际加购数量，库存不足时可能小于原数量）
 *  - skipped：被跳过的条目及原因（下架/售罄/规格已变更/库存为 0 等）
 *  - allFailed：是否全部失败（前端据此决定"不跳转购物车"，仅提示）
 *  - flavorAdjusted：是否存在口味被自动调整的条目（提示用，不影响成功判定）
 *
 * @author 阿婆干饭社
 */
public class RepurchaseResultVo
{
    /** 成功加购条目数 */
    private int successCount;

    /** 跳过条目数 */
    private int skipCount;

    /** 是否全部失败（无一条成功） */
    private boolean allFailed;

    /** 是否存在口味被调整（组/选项已变更，已剔除；条目仍视为加购成功） */
    private boolean flavorAdjusted;

    private List<AddedItem> added = new ArrayList<>();

    private List<SkippedItem> skipped = new ArrayList<>();

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public int getSkipCount() { return skipCount; }
    public void setSkipCount(int skipCount) { this.skipCount = skipCount; }

    public boolean isAllFailed() { return allFailed; }
    public void setAllFailed(boolean allFailed) { this.allFailed = allFailed; }

    public boolean isFlavorAdjusted() { return flavorAdjusted; }
    public void setFlavorAdjusted(boolean flavorAdjusted) { this.flavorAdjusted = flavorAdjusted; }

    public List<AddedItem> getAdded() { return added; }
    public void setAdded(List<AddedItem> added) { this.added = added; }

    public List<SkippedItem> getSkipped() { return skipped; }
    public void setSkipped(List<SkippedItem> skipped) { this.skipped = skipped; }

    /** 成功加购条目 */
    public static class AddedItem
    {
        private Long goodsId;
        private String goodsName;
        private String specName;
        /** 原订单数量 */
        private int originQuantity;
        /** 实际加购数量（库存不足时按剩余量加购，可能小于原数量） */
        private int addedQuantity;
        /** 是否因库存不足而减量加购 */
        private boolean quantityReduced;
        /** 减量原因说明（quantityReduced=true 时非空） */
        private String adjustTip;

        public Long getGoodsId() { return goodsId; }
        public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

        public String getGoodsName() { return goodsName; }
        public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

        public String getSpecName() { return specName; }
        public void setSpecName(String specName) { this.specName = specName; }

        public int getOriginQuantity() { return originQuantity; }
        public void setOriginQuantity(int originQuantity) { this.originQuantity = originQuantity; }

        public int getAddedQuantity() { return addedQuantity; }
        public void setAddedQuantity(int addedQuantity) { this.addedQuantity = addedQuantity; }

        public boolean isQuantityReduced() { return quantityReduced; }
        public void setQuantityReduced(boolean quantityReduced) { this.quantityReduced = quantityReduced; }

        public String getAdjustTip() { return adjustTip; }
        public void setAdjustTip(String adjustTip) { this.adjustTip = adjustTip; }
    }

    /** 跳过条目 */
    public static class SkippedItem
    {
        private Long goodsId;
        private String goodsName;
        private String specName;
        /** 跳过原因（已下架 / 已售罄 / 规格已下架或变更 / 今日已售罄 等） */
        private String reason;

        public SkippedItem() { }

        public SkippedItem(Long goodsId, String goodsName, String specName, String reason)
        {
            this.goodsId = goodsId;
            this.goodsName = goodsName;
            this.specName = specName;
            this.reason = reason;
        }

        public Long getGoodsId() { return goodsId; }
        public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

        public String getGoodsName() { return goodsName; }
        public void setGoodsName(String goodsName) { this.goodsName = goodsName; }

        public String getSpecName() { return specName; }
        public void setSpecName(String specName) { this.specName = specName; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
