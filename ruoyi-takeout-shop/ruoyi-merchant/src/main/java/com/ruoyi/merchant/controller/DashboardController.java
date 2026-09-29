package com.ruoyi.merchant.controller;

import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.merchant.service.DashboardService;

/**
 * 管理端数据看板（T14）—— 只读统计接口，走若依权限体系（不加 /api 前缀）。
 *
 * 统计口径（已与用户确认）：营业额/订单量取主状态 5（已完成）按 create_time 统计；
 * 已退款（refund_status=2）从净营业额扣减，审核中单独展示；菜品排行与分类占比
 * 取 biz_order_item 聚合且排除已退款订单。所有查询只读、不缓存。
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/merchant/dashboard")
public class DashboardController extends BaseController
{
    /** 时间范围预设 */
    private static final String RANGE_TODAY = "today";
    private static final String RANGE_YESTERDAY = "yesterday";
    private static final String RANGE_LAST7 = "last7";
    private static final String RANGE_LAST30 = "last30";
    private static final String RANGE_MONTH = "month";
    private static final String RANGE_CUSTOM = "custom";

    @Autowired
    private DashboardService dashboardService;

    /** 概览卡片：营业额/菜品收入/配送费收入/退款金额/退款中金额/净营业额/订单量/客单价/已取消/退款成功 */
    @PreAuthorize("@ss.hasPermi('merchant:dashboard:view')")
    @GetMapping("/overview")
    public AjaxResult overview(@RequestParam(defaultValue = "last7") String range,
                               @RequestParam(required = false) String startDate,
                               @RequestParam(required = false) String endDate,
                               @RequestParam(required = false) Integer deliveryType)
    {
        Date[] interval = resolveInterval(range, startDate, endDate);
        return success(dashboardService.overview(interval[0], interval[1], deliveryType));
    }

    /** 趋势：按天或按小时（granularity=auto/hour/day），空区间补零 */
    @PreAuthorize("@ss.hasPermi('merchant:dashboard:view')")
    @GetMapping("/trend")
    public AjaxResult trend(@RequestParam(defaultValue = "last7") String range,
                            @RequestParam(required = false) String startDate,
                            @RequestParam(required = false) String endDate,
                            @RequestParam(required = false) Integer deliveryType,
                            @RequestParam(defaultValue = "auto") String granularity)
    {
        Date[] interval = resolveInterval(range, startDate, endDate);
        return success(dashboardService.trend(interval[0], interval[1], deliveryType, granularity));
    }

    /** 菜品销量排行：sortBy=quantity/amount，topN 默认 10（上限 100） */
    @PreAuthorize("@ss.hasPermi('merchant:dashboard:view')")
    @GetMapping("/goodsRank")
    public AjaxResult goodsRank(@RequestParam(defaultValue = "last7") String range,
                                @RequestParam(required = false) String startDate,
                                @RequestParam(required = false) String endDate,
                                @RequestParam(required = false) Integer deliveryType,
                                @RequestParam(defaultValue = "quantity") String sortBy,
                                @RequestParam(defaultValue = "10") int topN)
    {
        Date[] interval = resolveInterval(range, startDate, endDate);
        return success(dashboardService.goodsRank(interval[0], interval[1], deliveryType, sortBy, topN));
    }

    /** 分类营业额占比 */
    @PreAuthorize("@ss.hasPermi('merchant:dashboard:view')")
    @GetMapping("/categoryStat")
    public AjaxResult categoryStat(@RequestParam(defaultValue = "last7") String range,
                                   @RequestParam(required = false) String startDate,
                                   @RequestParam(required = false) String endDate,
                                   @RequestParam(required = false) Integer deliveryType)
    {
        Date[] interval = resolveInterval(range, startDate, endDate);
        return success(dashboardService.categoryStat(interval[0], interval[1], deliveryType));
    }

    /** T19 高峰时段：区间内跨天按小时聚合有效订单与营业额（0-23 补齐 + 峰值标记） */
    @PreAuthorize("@ss.hasPermi('merchant:dashboard:view')")
    @GetMapping("/peakHours")
    public AjaxResult peakHours(@RequestParam(defaultValue = "last7") String range,
                                @RequestParam(required = false) String startDate,
                                @RequestParam(required = false) String endDate,
                                @RequestParam(required = false) Integer deliveryType)
    {
        Date[] interval = resolveInterval(range, startDate, endDate);
        return success(dashboardService.peakHours(interval[0], interval[1], deliveryType));
    }

    /** T19 出餐效率：接单/制作/取餐等待/配送四段平均耗时（秒）与样本量 */
    @PreAuthorize("@ss.hasPermi('merchant:dashboard:view')")
    @GetMapping("/efficiency")
    public AjaxResult efficiency(@RequestParam(defaultValue = "last7") String range,
                                 @RequestParam(required = false) String startDate,
                                 @RequestParam(required = false) String endDate,
                                 @RequestParam(required = false) Integer deliveryType)
    {
        Date[] interval = resolveInterval(range, startDate, endDate);
        return success(dashboardService.efficiency(interval[0], interval[1], deliveryType));
    }

    /**
     * 时间范围解析：返回 [start, end) 左闭右开区间。
     * 预设：today 今日 / yesterday 昨日 / last7 近 7 天（含今日）/ last30 近 30 天 / month 本月 / custom 自定义。
     * 默认与兜底均为近 7 天。自定义缺参时同样回落近 7 天（不抛错，保证看板可用）。
     */
    private Date[] resolveInterval(String range, String startDate, String endDate)
    {
        java.util.Calendar todayStart = java.util.Calendar.getInstance();
        todayStart.set(java.util.Calendar.HOUR_OF_DAY, 0);
        todayStart.set(java.util.Calendar.MINUTE, 0);
        todayStart.set(java.util.Calendar.SECOND, 0);
        todayStart.set(java.util.Calendar.MILLISECOND, 0);

        java.util.Calendar start = (java.util.Calendar) todayStart.clone();
        java.util.Calendar end = (java.util.Calendar) todayStart.clone();
        end.add(java.util.Calendar.DAY_OF_MONTH, 1);   // 默认：今日 00:00 ~ 明日 00:00

        String r = range == null ? RANGE_LAST7 : range.trim().toLowerCase();
        switch (r)
        {
            case RANGE_TODAY:
                break;   // 已默认今天
            case RANGE_YESTERDAY:
                start.add(java.util.Calendar.DAY_OF_MONTH, -1);
                end.add(java.util.Calendar.DAY_OF_MONTH, -1);
                break;
            case RANGE_LAST30:
                start.add(java.util.Calendar.DAY_OF_MONTH, -29);   // 含今日共 30 天
                break;
            case RANGE_MONTH:
                start.set(java.util.Calendar.DAY_OF_MONTH, 1);
                break;
            case RANGE_CUSTOM:
                Date parsedStart = parseDate(startDate);
                Date parsedEnd = parseDate(endDate);
                if (parsedStart != null && parsedEnd != null)
                {
                    java.util.Calendar cs = java.util.Calendar.getInstance();
                    cs.setTime(parsedStart);
                    cs.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    cs.set(java.util.Calendar.MINUTE, 0);
                    cs.set(java.util.Calendar.SECOND, 0);
                    cs.set(java.util.Calendar.MILLISECOND, 0);
                    java.util.Calendar ce = java.util.Calendar.getInstance();
                    ce.setTime(parsedEnd);
                    ce.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    ce.set(java.util.Calendar.MINUTE, 0);
                    ce.set(java.util.Calendar.SECOND, 0);
                    ce.set(java.util.Calendar.MILLISECOND, 0);
                    ce.add(java.util.Calendar.DAY_OF_MONTH, 1);        // 结束日期当天包含在内
                    if (!ce.before(cs))
                    {
                        start = cs;
                        end = ce;
                    }
                    // 缺参/非法（含起止倒置）→ 保持默认近 7 天，不抛错
                    if (parsedStart == null || parsedEnd == null || ce.before(cs))
                    {
                        start = (java.util.Calendar) todayStart.clone();
                        start.add(java.util.Calendar.DAY_OF_MONTH, -6);
                        end = (java.util.Calendar) todayStart.clone();
                        end.add(java.util.Calendar.DAY_OF_MONTH, 1);
                    }
                }
                else
                {
                    start.add(java.util.Calendar.DAY_OF_MONTH, -6);
                }
                break;
            case RANGE_LAST7:
            default:
                start.add(java.util.Calendar.DAY_OF_MONTH, -6);        // 含今日共 7 天
                break;
        }
        return new Date[] { start.getTime(), end.getTime() };
    }

    /** 解析 yyyy-MM-dd（非法返回 null，由调用方兜底） */
    private Date parseDate(String text)
    {
        if (text == null || text.trim().isEmpty())
        {
            return null;
        }
        try
        {
            return com.ruoyi.common.utils.DateUtils.parseDate(text.trim());
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
