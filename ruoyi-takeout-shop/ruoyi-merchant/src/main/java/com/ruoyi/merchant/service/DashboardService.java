package com.ruoyi.merchant.service;

import java.util.Date;
import java.util.List;
import com.ruoyi.merchant.domain.vo.DashboardCategoryVo;
import com.ruoyi.merchant.domain.vo.DashboardEfficiencyVo;
import com.ruoyi.merchant.domain.vo.DashboardPeakHourVo;
import com.ruoyi.merchant.domain.vo.DashboardGoodsRankVo;
import com.ruoyi.merchant.domain.vo.DashboardOverviewVo;
import com.ruoyi.merchant.domain.vo.DashboardTrendVo;

/**
 * 数据看板服务（T14）。
 *
 * 只读统计，不缓存（理由：管理端低频访问 + 一期约定"订单数据不缓存"，聚合查询有索引支撑）。
 *
 * @author 阿婆干饭社
 */
public interface DashboardService
{
    /** 概览指标（默认区间：近 7 天） */
    DashboardOverviewVo overview(Date start, Date end, Integer deliveryType);

    /**
     * 趋势序列（空区间补零，保证折线图连续）。
     *
     * @param granularity day / hour；传 auto 时区间 ≤ 2 天按小时，否则按天
     */
    List<DashboardTrendVo> trend(Date start, Date end, Integer deliveryType, String granularity);

    /** 菜品销量排行（sortBy: quantity/amount） */
    List<DashboardGoodsRankVo> goodsRank(Date start, Date end, Integer deliveryType, String sortBy, int topN);

    /** 分类营业额占比（含百分比计算） */
    List<DashboardCategoryVo> categoryStat(Date start, Date end, Integer deliveryType);

    /** 高峰时段（T19）：区间内跨天按小时聚合有效订单与营业额，补齐 0-23 点并标记峰值 */
    List<DashboardPeakHourVo> peakHours(Date start, Date end, Integer deliveryType);

    /** 出餐效率（T19）：接单/制作/取餐等待/配送四段平均耗时（秒）与样本量 */
    DashboardEfficiencyVo efficiency(Date start, Date end, Integer deliveryType);
}
