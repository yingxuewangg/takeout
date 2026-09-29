package com.ruoyi.merchant.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.merchant.domain.vo.DashboardCategoryVo;
import com.ruoyi.merchant.domain.vo.DashboardEfficiencyVo;
import com.ruoyi.merchant.domain.vo.DashboardPeakHourVo;
import com.ruoyi.merchant.domain.vo.DashboardGoodsRankVo;
import com.ruoyi.merchant.domain.vo.DashboardOverviewVo;
import com.ruoyi.merchant.domain.vo.DashboardTrendVo;

/**
 * 数据看板统计 Mapper（T14）——全部为只读聚合查询，不写任何数据。
 *
 * 统一口径（已与用户确认）：
 *  - 营业额/订单量：主状态 5（已完成）订单，按 create_time（下单时间）过滤；
 *  - 退款：refund_status=2 的金额从净营业额扣减，=1 的单独展示不扣减；
 *  - 菜品排行/分类占比：biz_order_item 聚合，仅主状态 5 且 refund_status≠2；
 *  - 时间区间一律参数化（>= start and < end），保证命中索引。
 *
 * @author 阿婆干饭社
 */
@Mapper
public interface DashboardMapper
{
    /**
     * 概览指标（单条 SQL 用 CASE WHEN 一次算出多指标，避免多次扫表）。
     *
     * @param start 区间起点（含）
     * @param end   区间终点（不含）
     * @param deliveryType 履约方式筛选（null=全部，1堂食，2外卖）
     */
    DashboardOverviewVo selectOverview(@Param("start") Date start,
                                       @Param("end") Date end,
                                       @Param("deliveryType") Integer deliveryType);

    /**
     * 趋势（按天）：返回区间内有数据的「天」聚合，空天由服务层补零。
     */
    List<DashboardTrendVo> selectTrendByDay(@Param("start") Date start,
                                            @Param("end") Date end,
                                            @Param("deliveryType") Integer deliveryType);

    /**
     * 趋势（按小时）：返回区间内有数据的「小时」聚合，空小时由服务层补零。
     */
    List<DashboardTrendVo> selectTrendByHour(@Param("start") Date start,
                                             @Param("end") Date end,
                                             @Param("deliveryType") Integer deliveryType);

    /**
     * 菜品销量排行（仅已完成且未退款成功的订单明细）。
     *
     * @param sortBy quantity=按销量 / amount=按销售额（服务层已做白名单校验，防注入）
     * @param topN   取前 N 条
     */
    List<DashboardGoodsRankVo> selectGoodsRank(@Param("start") Date start,
                                               @Param("end") Date end,
                                               @Param("deliveryType") Integer deliveryType,
                                               @Param("sortBy") String sortBy,
                                               @Param("topN") int topN);

    /**
     * 分类营业额占比（按菜品当前所属分类聚合；未匹配到分类的归入"未分类"）。
     */
    List<DashboardCategoryVo> selectCategoryStat(@Param("start") Date start,
                                                 @Param("end") Date end,
                                                 @Param("deliveryType") Integer deliveryType);

    /**
     * 菜品退款关联数（T19）：各菜品在已退款订单（refund_status=2）中的明细件数合计，服务层合并进排行。
     */
    List<DashboardGoodsRankVo> selectGoodsRefundCount(@Param("start") Date start,
                                                      @Param("end") Date end,
                                                      @Param("deliveryType") Integer deliveryType);

    /**
     * 高峰时段（T19）：区间内跨天按小时 of day 聚合有效订单与营业额，只返回有数据的小时。
     */
    List<DashboardPeakHourVo> selectPeakHours(@Param("start") Date start,
                                              @Param("end") Date end,
                                              @Param("deliveryType") Integer deliveryType);

    /**
     * 出餐效率（T19）：接单/制作/取餐等待/配送四段平均耗时与样本量（时间字段非 NULL 才计入）。
     */
    DashboardEfficiencyVo selectEfficiency(@Param("start") Date start,
                                           @Param("end") Date end,
                                           @Param("deliveryType") Integer deliveryType);
}
