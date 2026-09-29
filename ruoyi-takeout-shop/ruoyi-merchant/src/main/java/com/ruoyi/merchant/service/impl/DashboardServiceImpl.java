package com.ruoyi.merchant.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.merchant.domain.vo.DashboardCategoryVo;
import com.ruoyi.merchant.domain.vo.DashboardEfficiencyVo;
import com.ruoyi.merchant.domain.vo.DashboardGoodsRankVo;
import com.ruoyi.merchant.domain.vo.DashboardOverviewVo;
import com.ruoyi.merchant.domain.vo.DashboardPeakHourVo;
import com.ruoyi.merchant.domain.vo.DashboardTrendVo;
import com.ruoyi.merchant.mapper.DashboardMapper;
import com.ruoyi.merchant.service.DashboardService;

/**
 * 数据看板服务实现（T14）。
 *
 * 职责：口径计算（净营业额/客单价/占比）、趋势补零、参数白名单校验；
 * 统计 SQL 全部在 DashboardMapper.xml 中完成（只读聚合）。
 *
 * @author 阿婆干饭社
 */
@Service
public class DashboardServiceImpl implements DashboardService
{
    /** 趋势颗粒度切换阈值：区间不超过 2 天时按小时展示（否则点太密/太稀都不合适） */
    private static final long HOUR_MODE_MAX_DAYS = 2L;

    /** 排行默认 Top N 上限（防前端传超大值拖垮查询） */
    private static final int MAX_TOP_N = 100;

    @Autowired
    private DashboardMapper dashboardMapper;

    @Override
    public DashboardOverviewVo overview(Date start, Date end, Integer deliveryType)
    {
        DashboardOverviewVo vo = dashboardMapper.selectOverview(start, end, deliveryType);
        if (vo == null)
        {
            vo = new DashboardOverviewVo();
        }
        // 净营业额 = 营业额 - 退款扣减额（仅"已完成且已退款"的部分；退款审核中的不扣减）
        BigDecimal revenue = nvl(vo.getRevenue());
        BigDecimal refundedInRevenue = nvl(vo.getRefundedInRevenue());
        BigDecimal net = revenue.subtract(refundedInRevenue).setScale(2, RoundingMode.HALF_UP);
        vo.setNetRevenue(net);
        // 客单价 = 净营业额 / 有效订单数（无订单时为 0）
        if (vo.getOrderCount() > 0)
        {
            vo.setAvgOrderAmount(net.divide(BigDecimal.valueOf(vo.getOrderCount()), 2, RoundingMode.HALF_UP));
        }
        else
        {
            vo.setAvgOrderAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        // T19 退款率 = 退款成功订单数 /（有效订单数 + 退款成功订单数）——分母含退款单，避免"退得多显得退款率反而低"
        vo.setRefundRate(refundRate(vo.getOrderCount(), vo.getRefundedCount()));
        return vo;
    }

    @Override
    public List<DashboardTrendVo> trend(Date start, Date end, Integer deliveryType, String granularity)
    {
        boolean byHour = resolveByHour(start, end, granularity);
        List<DashboardTrendVo> rows = byHour
                ? dashboardMapper.selectTrendByHour(start, end, deliveryType)
                : dashboardMapper.selectTrendByDay(start, end, deliveryType);

        // 补零：把区间内每个时间点都补齐（数据库只返回有数据的点，否则折线图会断）
        Map<String, DashboardTrendVo> rowMap = new LinkedHashMap<>();
        for (DashboardTrendVo row : rows)
        {
            rowMap.put(row.getTimeLabel(), row);
        }
        List<DashboardTrendVo> result = new ArrayList<>();
        Calendar cursor = Calendar.getInstance();
        cursor.setTime(start);
        truncate(cursor, byHour);
        Calendar limit = Calendar.getInstance();
        limit.setTime(end);
        String pattern = byHour ? "yyyy-MM-dd HH:mm" : "yyyy-MM-dd";
        while (cursor.getTime().before(end))
        {
            String label = format(cursor.getTime(), pattern);
            DashboardTrendVo point = rowMap.get(label);
            if (point == null)
            {
                // 空点补零；按小时展示时标签截到整点（与 SQL 的 %H:00 一致）
                point = new DashboardTrendVo(byHour ? label.substring(0, 13) + ":00" : label);
            }
            else if (byHour)
            {
                point.setTimeLabel(point.getTimeLabel().substring(0, 13) + ":00");
            }
            // T19 每点客单价与退款率（毛口径：客单价=该点营业额/订单量；退款率分母含退款单）
            point.setAvgOrderAmount(point.getOrderCount() > 0
                    ? point.getRevenue().divide(BigDecimal.valueOf(point.getOrderCount()), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            point.setRefundRate(refundRate(point.getOrderCount(), point.getRefundedCount()));
            result.add(point);
            if (byHour)
            {
                cursor.add(Calendar.HOUR_OF_DAY, 1);
            }
            else
            {
                cursor.add(Calendar.DAY_OF_MONTH, 1);
            }
            // 防御：极端区间下避免无限循环（最多 24*366 个点）
            if (result.size() > 24 * 366)
            {
                break;
            }
        }
        return result;
    }

    @Override
    public List<DashboardGoodsRankVo> goodsRank(Date start, Date end, Integer deliveryType, String sortBy, int topN)
    {
        // 排序字段白名单（防列名拼接注入）
        String safeSortBy = "amount".equalsIgnoreCase(sortBy) ? "amount" : "quantity";
        int safeTopN = topN <= 0 ? 10 : Math.min(topN, MAX_TOP_N);
        List<DashboardGoodsRankVo> rows = dashboardMapper.selectGoodsRank(start, end, deliveryType, safeSortBy, safeTopN);
        // T19 合并退款关联数（已退款订单明细件数；单独查询——主排行 where 排除了已退款订单）
        List<DashboardGoodsRankVo> refunds = dashboardMapper.selectGoodsRefundCount(start, end, deliveryType);
        if (refunds != null && !refunds.isEmpty())
        {
            Map<Long, Long> refundMap = new LinkedHashMap<>();
            for (DashboardGoodsRankVo r : refunds)
            {
                refundMap.put(r.getGoodsId(), r.getRefundCount());
            }
            for (DashboardGoodsRankVo row : rows)
            {
                Long cnt = refundMap.get(row.getGoodsId());
                row.setRefundCount(cnt == null ? 0L : cnt);
            }
        }
        return rows;
    }

    @Override
    public List<DashboardCategoryVo> categoryStat(Date start, Date end, Integer deliveryType)
    {
        List<DashboardCategoryVo> rows = dashboardMapper.selectCategoryStat(start, end, deliveryType);
        BigDecimal total = BigDecimal.ZERO;
        for (DashboardCategoryVo row : rows)
        {
            total = total.add(nvl(row.getAmount()));
        }
        if (total.compareTo(BigDecimal.ZERO) > 0)
        {
            for (DashboardCategoryVo row : rows)
            {
                row.setPercent(nvl(row.getAmount())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(total, 2, RoundingMode.HALF_UP));
            }
        }
        return rows;
    }

    /** 是否按小时：显式指定优先，auto 时区间 ≤ 2 天按小时 */
    private boolean resolveByHour(Date start, Date end, String granularity)
    {
        if ("hour".equalsIgnoreCase(granularity))
        {
            return true;
        }
        if ("day".equalsIgnoreCase(granularity))
        {
            return false;
        }
        long diffMillis = end.getTime() - start.getTime();
        return diffMillis <= HOUR_MODE_MAX_DAYS * 24 * 3600_000L;
    }

    /** T19：高峰时段——0-23 补齐 + 峰值标记（订单数最高的小时，并列都标） */
    @Override
    public List<DashboardPeakHourVo> peakHours(Date start, Date end, Integer deliveryType)
    {
        List<DashboardPeakHourVo> rows = dashboardMapper.selectPeakHours(start, end, deliveryType);
        Map<Integer, DashboardPeakHourVo> rowMap = new LinkedHashMap<>();
        for (DashboardPeakHourVo row : rows)
        {
            rowMap.put(row.getHour(), row);
        }
        // 补齐 0-23 全部小时（数据库只返回有数据的小时，柱状图需要连续横轴）
        List<DashboardPeakHourVo> result = new ArrayList<>();
        long maxCount = 0;
        for (int h = 0; h < 24; h++)
        {
            DashboardPeakHourVo point = rowMap.get(h);
            if (point == null)
            {
                point = new DashboardPeakHourVo();
                point.setHour(h);
            }
            if (point.getOrderCount() > maxCount)
            {
                maxCount = point.getOrderCount();
            }
            result.add(point);
        }
        if (maxCount > 0)
        {
            for (DashboardPeakHourVo point : result)
            {
                point.setPeak(point.getOrderCount() == maxCount);
            }
        }
        return result;
    }

    /** T19：出餐效率——SQL 已算好平均值（秒）与样本量，服务层只做非空兜底 */
    @Override
    public DashboardEfficiencyVo efficiency(Date start, Date end, Integer deliveryType)
    {
        DashboardEfficiencyVo vo = dashboardMapper.selectEfficiency(start, end, deliveryType);
        return vo == null ? new DashboardEfficiencyVo() : vo;
    }

    /** T19：退款率 = 退款成功订单数 /（有效订单数 + 退款成功订单数），百分数保留 2 位 */
    private BigDecimal refundRate(long orderCount, long refundedCount)
    {
        long total = orderCount + refundedCount;
        if (total <= 0)
        {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(refundedCount * 100)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /** 把日历游标归零到整点/整天（与 SQL 的 date_format 分组口径对齐） */
    private void truncate(Calendar calendar, boolean byHour)
    {
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (!byHour)
        {
            calendar.set(Calendar.HOUR_OF_DAY, 0);
        }
    }

    private String format(Date date, String pattern)
    {
        return com.ruoyi.common.utils.DateUtils.parseDateToStr(pattern, date);
    }

    private BigDecimal nvl(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }
}
