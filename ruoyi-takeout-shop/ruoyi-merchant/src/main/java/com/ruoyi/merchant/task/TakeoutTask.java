package com.ruoyi.merchant.task;

import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.merchant.domain.BizOrder;
import com.ruoyi.merchant.domain.BizOrderRefund;
import com.ruoyi.merchant.mapper.BizOrderMapper;
import com.ruoyi.merchant.mapper.BizOrderRefundMapper;
import com.ruoyi.merchant.service.StockService;

/**
 * 阿婆干饭社定时任务（由若依 quartz 管理端配置调度，invokeTarget: takeoutTask.xxx）。
 *
 * 1) 超时关单兜底：RocketMQ 延迟消息为主，本任务低频扫描兜底防消息丢失（附录 B 决策 5）。
 * 2) 退款 24 小时未审核自动驳回（附录 B 决策 14）：效果与人工驳回一致——
 *    退款单置已驳回(is_auto_reject=1) + 订单 refund_status 回 0（主状态不变、流转恢复、可再次申请）。
 * 3) 每日库存初始化（T12）：每天 00:00 按限量配置初始化当天库存行。
 * 以上均条件更新幂等，重复调度无副作用。
 *
 * @author 阿婆干饭社
 */
@Component("takeoutTask")
public class TakeoutTask
{
    private static final Logger log = LoggerFactory.getLogger(TakeoutTask.class);

    /** 订单主状态：0待支付 6已取消；退款状态：0无 1审核中 2已退款 */
    private static final int STATUS_UNPAID = 0;
    private static final int STATUS_CANCELLED = 6;
    private static final int REFUND_AUDIT_PENDING = 0;
    private static final int REFUND_AUDIT_REJECTED = 2;
    private static final int REFUND_STATUS_AUDITING = 1;
    private static final int REFUND_STATUS_NONE = 0;

    /** 退款审核超时小时数（24 小时，附录 B 决策 14） */
    private static final long REFUND_TIMEOUT_HOURS = 24;

    @Autowired
    private BizOrderMapper orderMapper;

    @Autowired
    private BizOrderRefundMapper refundMapper;

    /** 每日限量库存服务（T12）：超时关单兜底后释放库存 */
    @Autowired
    private StockService stockService;

    /**
     * 待支付订单超时自动关闭（cron：每分钟 0 * * * * ?，禁止并发）
     *
     * T12：需按单逐个条件关单，以便对"本次真正关单成功"的订单释放每日限量库存
     * （批量 UPDATE 拿不到订单号；释放内部以 stock_released 幂等，重复扫描无副作用）。
     */
    public void closeTimeoutOrders()
    {
        List<BizOrder> timeouts = orderMapper.selectList(new LambdaQueryWrapper<BizOrder>()
                .eq(BizOrder::getStatus, STATUS_UNPAID)
                .lt(BizOrder::getTimeoutCloseTime, DateUtils.getNowDate()));
        int closed = 0;
        for (BizOrder order : timeouts)
        {
            // 条件更新（where status=0）幂等：并发下与 MQ 消费者互斥，只有一方关单成功
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<BizOrder>()
                    .eq(BizOrder::getId, order.getId())
                    .eq(BizOrder::getStatus, STATUS_UNPAID)
                    .set(BizOrder::getStatus, STATUS_CANCELLED)
                    .set(BizOrder::getUpdateTime, new Date()));
            if (rows > 0)
            {
                stockService.releaseByOrderNo(order.getOrderNo());
                closed++;
            }
        }
        if (closed > 0)
        {
            log.info("[定时任务] 超时关单兜底：本次关闭 {} 单（0 -> 6，已释放库存）", closed);
        }
    }

    /**
     * 每日库存初始化（cron：每天 00:00 0 0 0 * * ?，禁止并发）
     *
     * T12：按 biz_goods.daily_limit_enabled / daily_limit_qty（规格级以 biz_goods_spec.daily_limit_qty 优先）
     * 为当天建库存行；依赖唯一索引 (goods_id, spec_id, stock_date) 幂等，重复调度不会重复建行。
     * 下单/查询路径另有惰性初始化兜底（定时任务未跑或服务停机时保证库存语义正确）。
     */
    public void initDailyStock()
    {
        int created = stockService.initDailyStock(DateUtils.parseDate(DateUtils.getDate()));
        log.info("[定时任务] 每日库存初始化：日期={} 新增 {} 行", DateUtils.getDate(), created);
    }

    /**
     * 退款单 24 小时未审核自动驳回（cron：每小时 0 0 * * * ?，禁止并发）
     */
    public void rejectTimeoutRefunds()
    {
        Date deadline = new Date(System.currentTimeMillis() - REFUND_TIMEOUT_HOURS * 3600_000L);
        List<BizOrderRefund> refunds = refundMapper.selectList(new LambdaQueryWrapper<BizOrderRefund>()
                .eq(BizOrderRefund::getAuditStatus, REFUND_AUDIT_PENDING)
                .lt(BizOrderRefund::getCreateTime, deadline));
        int count = 0;
        for (BizOrderRefund refund : refunds)
        {
            // 顺序：先退订单退款状态（恢复流转），再置退款单已驳回；任一步失败下次重扫幂等补齐
            int r1 = orderMapper.update(null, new LambdaUpdateWrapper<BizOrder>()
                    .eq(BizOrder::getOrderNo, refund.getOrderNo())
                    .eq(BizOrder::getRefundStatus, REFUND_STATUS_AUDITING)
                    .set(BizOrder::getRefundStatus, REFUND_STATUS_NONE)
                    .set(BizOrder::getUpdateTime, new Date()));
            int r2 = refundMapper.update(null, new LambdaUpdateWrapper<BizOrderRefund>()
                    .eq(BizOrderRefund::getId, refund.getId())
                    .eq(BizOrderRefund::getAuditStatus, REFUND_AUDIT_PENDING)
                    .set(BizOrderRefund::getAuditStatus, REFUND_AUDIT_REJECTED)
                    .set(BizOrderRefund::getIsAutoReject, 1)
                    .set(BizOrderRefund::getRejectReason, "超时未审核，系统自动驳回")
                    .set(BizOrderRefund::getUpdateTime, new Date()));
            if (r2 > 0)
            {
                count++;
                log.info("[定时任务] 退款超时自动驳回：refundId={} orderNo={}（订单流转恢复 {}）",
                        refund.getId(), refund.getOrderNo(), r1 > 0 ? "成功" : "无需变更");
            }
        }
        if (count > 0)
        {
            log.info("[定时任务] 退款超时自动驳回：本次驳回 {} 单", count);
        }
    }
}
