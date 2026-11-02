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
import com.ruoyi.merchant.service.OrderPaymentLedgerService;
import com.ruoyi.merchant.service.StockService;
import com.ruoyi.merchant.service.WechatPayClient;
import com.ruoyi.merchant.service.WechatPayGuardService;
import com.ruoyi.merchant.service.WechatRefundService;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.model.RefundNotification;

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

    /** 订单主状态：0待支付 6已取消；退款审核状态：0待审核 1已通过 2已驳回；订单退款状态：0无 1审核中 2已退款 */
    private static final int STATUS_UNPAID = 0;
    private static final int STATUS_CANCELLED = 6;
    private static final int REFUND_AUDIT_PENDING = 0;
    private static final int REFUND_AUDIT_APPROVED = 1;
    private static final int REFUND_AUDIT_REJECTED = 2;
    private static final int REFUND_STATUS_AUDITING = 1;
    private static final int REFUND_STATUS_NONE = 0;

    /** 退款审核超时小时数（24 小时，附录 B 决策 14） */
    private static final long REFUND_TIMEOUT_HOURS = 24;

    /** 微信支付单查单兜底的宽限分钟数（下单超过该时长仍未回调才查单，避免与回调赛跑） */
    private static final long WECHAT_CHECK_GRACE_MINUTES = 2;

    /** 微信支付下单标记（T22 发起 JSAPI 下单时写入） */
    private static final int PAY_TYPE_WECHAT_PREPAY = 2;

    @Autowired
    private BizOrderMapper orderMapper;

    @Autowired
    private BizOrderRefundMapper refundMapper;

    /** 每日限量库存服务（T12）：超时关单兜底后释放库存 */
    @Autowired
    private StockService stockService;

    /** 微信支付关单守卫（T24）：真实支付单关单前先问微信侧 */
    @Autowired
    private WechatPayGuardService wechatPayGuardService;

    /** 微信支付客户端（T24 查单兜底） */
    @Autowired
    private WechatPayClient wechatPayClient;

    /** 支付成功落账服务（T23/T24 查单兜底落账） */
    @Autowired
    private OrderPaymentLedgerService paymentLedgerService;

    /** 微信退款结果落库服务（T25 查单兜底补写受理号） */
    @Autowired
    private WechatRefundService wechatRefundService;

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
            // T24 关单竞态守卫：微信支付单先对微信侧查单/关单，已支付则落账并跳过本地关单
            if (!wechatPayGuardService.beforeLocalClose(order.getOrderNo()))
            {
                continue;
            }
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
     * 微信支付查单兜底（三期 T24，cron：每分钟 0 * * * * ?，禁止并发）。
     *
     * 覆盖回调丢失场景：对已发起微信下单（pay_type=2）仍待支付且下单超过宽限窗的订单，
     * 逐单向微信查单，微信侧已支付则落账（与回调同语义，重复执行幂等）。
     * 关单仍由超时关单任务负责（含微信侧关单守卫），本任务不关单不释放库存。
     */
    public void checkWechatOrders()
    {
        Date deadline = new Date(System.currentTimeMillis() - WECHAT_CHECK_GRACE_MINUTES * 60_000L);
        List<BizOrder> pendings = orderMapper.selectList(new LambdaQueryWrapper<BizOrder>()
                .eq(BizOrder::getStatus, STATUS_UNPAID)
                .eq(BizOrder::getPayType, PAY_TYPE_WECHAT_PREPAY)
                .lt(BizOrder::getCreateTime, deadline));
        if (pendings.isEmpty())
        {
            return;
        }
        int paid = 0;
        for (BizOrder order : pendings)
        {
            Transaction transaction;
            try
            {
                transaction = wechatPayClient.queryOrderByOutTradeNo(order.getOrderNo());
            }
            catch (Exception e)
            {
                // 单笔查单失败不影响其余订单，下一轮重查
                log.warn("[定时任务] 微信查单兜底查单失败：orderNo={} err={}", order.getOrderNo(), e.getMessage());
                continue;
            }
            if (transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS)
            {
                continue;
            }
            Integer payerTotal = transaction.getAmount() == null ? null : transaction.getAmount().getPayerTotal();
            OrderPaymentLedgerService.LedgerResult result = paymentLedgerService.markPaid(
                    order.getOrderNo(), transaction.getTransactionId(), payerTotal);
            if (result == OrderPaymentLedgerService.LedgerResult.PAID
                    || result == OrderPaymentLedgerService.LedgerResult.ALREADY_PAID)
            {
                paid++;
            }
            else
            {
                log.error("[定时任务] 微信查单兜底落账异常（{}）：orderNo={} transactionId={}",
                        result, order.getOrderNo(), transaction.getTransactionId());
            }
        }
        if (paid > 0)
        {
            log.info("[定时任务] 微信查单兜底：本次补落账 {} 单", paid);
        }
    }

    /**
     * 微信退款查单兜底（三期 T25，cron：每分钟 0 * * * * ?，禁止并发）。
     *
     * 覆盖退款回调丢失场景：对已受理（审核状态=1、非模拟、微信受理号未写回）且受理超过宽限窗的
     * 退款单，逐单按商户退款单号向微信查单，微信侧 SUCCESS 则补写受理号（与回调同语义，幂等），
     * ABNORMAL 记 ERROR 人工介入；其余状态等下一轮。不重复发起退款、不改订单状态。
     */
    public void checkWechatRefunds()
    {
        Date deadline = new Date(System.currentTimeMillis() - WECHAT_CHECK_GRACE_MINUTES * 60_000L);
        List<BizOrderRefund> pendings = refundMapper.selectList(new LambdaQueryWrapper<BizOrderRefund>()
                .eq(BizOrderRefund::getAuditStatus, REFUND_AUDIT_APPROVED)
                .eq(BizOrderRefund::getIsMockRefund, 0)
                .isNull(BizOrderRefund::getRefundId)
                .lt(BizOrderRefund::getUpdateTime, deadline));
        if (pendings.isEmpty())
        {
            return;
        }
        int success = 0;
        for (BizOrderRefund refund : pendings)
        {
            com.wechat.pay.java.service.refund.model.Refund wxRefund;
            try
            {
                wxRefund = wechatPayClient.queryRefundByOutRefundNo(String.valueOf(refund.getId()));
            }
            catch (Exception e)
            {
                // 单笔查单失败不影响其余退款单，下一轮重查
                log.warn("[定时任务] 微信退款查单失败：refundId={} err={}", refund.getId(), e.getMessage());
                continue;
            }
            if (wxRefund.getStatus() == com.wechat.pay.java.service.refund.model.Status.SUCCESS)
            {
                RefundNotification notification = new RefundNotification();
                notification.setOutRefundNo(wxRefund.getOutRefundNo());
                notification.setRefundId(wxRefund.getRefundId());
                notification.setRefundStatus(com.wechat.pay.java.service.refund.model.Status.SUCCESS);
                wechatRefundService.onRefundNotification(notification, "查单兜底");
                success++;
            }
            else if (wxRefund.getStatus() == com.wechat.pay.java.service.refund.model.Status.ABNORMAL)
            {
                log.error("[定时任务] 微信退款异常，需人工处理：refundId={} orderNo={} wxRefundId={}",
                        refund.getId(), refund.getOrderNo(), wxRefund.getRefundId());
            }
        }
        if (success > 0)
        {
            log.info("[定时任务] 微信退款查单兜底：本次补写受理号 {} 单", success);
        }
    }

    /**
     * 每日库存初始化（cron：每天 00:00 0 0 0 * * ?，禁止并发）
     *
     * T12：按 biz_goods.daily_limit_enabled / daily_limit_qty（规格级以 biz_goods_spec.daily_limit_qty 优先）
     * 为当天建库存行；依赖唯一索引 (goods_id, spec_id, stock_date) 幂等，重复调度不会重复建行。
     * 下单/查询路径另有惰性初始化兜底（定时任务未跑或服务停机时保证库存语义正确）。
     */
    public void initDailyStock()    {
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
