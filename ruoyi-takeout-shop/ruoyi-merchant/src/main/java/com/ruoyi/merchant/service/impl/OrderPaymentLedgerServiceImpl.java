package com.ruoyi.merchant.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.merchant.domain.BizGoods;
import com.ruoyi.merchant.domain.BizOrder;
import com.ruoyi.merchant.domain.BizOrderItem;
import com.ruoyi.merchant.domain.BizPaymentRecord;
import com.ruoyi.merchant.mapper.BizGoodsMapper;
import com.ruoyi.merchant.mapper.BizOrderItemMapper;
import com.ruoyi.merchant.mapper.BizOrderMapper;
import com.ruoyi.merchant.mapper.BizPaymentRecordMapper;
import com.ruoyi.merchant.mq.OrderMqProducer;
import com.ruoyi.merchant.service.OrderPaymentLedgerService;
import com.ruoyi.merchant.service.OrderPushService;
import com.ruoyi.merchant.util.PayAmountUtils;

/**
 * 支付成功落账服务实现（三期 T23）：支付成功回调与查单兜底共用。
 *
 * 事务内：订单条件更新 0->1（where status=0，影响行数=0 即并发已处理，重查判幂等）
 * + 支付流水插入（order_no 唯一约束二次幂等兜底）+ 菜品销量同步累加；
 * 事务提交后：发 order-paid-success 事件 + 管理端新订单实时推送。
 * 金额以库内订单实付为准，微信侧金额不一致只记 ERROR 不落账（AMOUNT_MISMATCH）。
 *
 * @author 阿婆干饭社
 */
@Service
public class OrderPaymentLedgerServiceImpl implements OrderPaymentLedgerService
{
    private static final Logger log = LoggerFactory.getLogger(OrderPaymentLedgerServiceImpl.class);

    /** 订单主状态：0待支付 1待接单 6已取消 */
    private static final int STATUS_UNPAID = 0;
    private static final int STATUS_WAIT_ACCEPT = 1;
    private static final int STATUS_CANCELLED = 6;

    @Autowired
    private BizOrderMapper orderMapper;

    @Autowired
    private BizOrderItemMapper orderItemMapper;

    @Autowired
    private BizPaymentRecordMapper paymentRecordMapper;

    @Autowired
    private BizGoodsMapper goodsMapper;

    /** MQ 生产者：rocketmq.enabled=false 时不注入（降级运行） */
    @Autowired(required = false)
    private OrderMqProducer mqProducer;

    /** 管理端实时推送（新订单提醒）：推送失败内部吞异常，不影响落账 */
    @Autowired
    private OrderPushService orderPushService;

    @Override
    @Transactional
    public LedgerResult markPaid(String orderNo, String transactionId, Integer payerTotalCents)
    {
        BizOrder order = orderMapper.selectOne(new LambdaQueryWrapper<BizOrder>()
                .eq(BizOrder::getOrderNo, orderNo));
        if (order == null)
        {
            log.error("[微信支付落账] 订单不存在：orderNo={} transactionId={}", orderNo, transactionId);
            return LedgerResult.NOT_FOUND;
        }
        // 已落账幂等命中（重复回调/查单）
        if (order.getStatus() == STATUS_WAIT_ACCEPT && Integer.valueOf(1).equals(order.getPayStatus()))
        {
            log.info("[微信支付落账] 订单已落账，幂等跳过：orderNo={}", orderNo);
            return LedgerResult.ALREADY_PAID;
        }
        // 已取消/超时关闭：款项需原路退回（T25 接入），本方法不做状态变更
        if (order.getStatus() == STATUS_CANCELLED)
        {
            log.error("[微信支付落账] 订单已关闭但收到支付成功，需原路退款：orderNo={} transactionId={}",
                    orderNo, transactionId);
            return LedgerResult.CLOSED;
        }
        if (order.getStatus() != STATUS_UNPAID)
        {
            log.error("[微信支付落账] 订单状态异常且未落账，暂不处理：orderNo={} status={} payStatus={}",
                    orderNo, order.getStatus(), order.getPayStatus());
            return LedgerResult.CLOSED;
        }

        // 金额核对（分对分，以库内实付为准）
        long expectCents = PayAmountUtils.toCents(order.getPayAmount());
        if (payerTotalCents == null || payerTotalCents != expectCents)
        {
            log.error("[微信支付落账] 金额不一致，不落账待人工对账：orderNo={} expectCents={} wechatPayerCents={}",
                    orderNo, expectCents, payerTotalCents);
            return LedgerResult.AMOUNT_MISMATCH;
        }

        // 条件更新（where status=0）：影响行数=0 说明并发下已被支付/关闭，重查判幂等
        Date now = DateUtils.getNowDate();
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<BizOrder>()
                .eq(BizOrder::getId, order.getId())
                .eq(BizOrder::getStatus, STATUS_UNPAID)
                .set(BizOrder::getStatus, STATUS_WAIT_ACCEPT)
                .set(BizOrder::getPayStatus, 1)
                .set(BizOrder::getPayType, 2)
                .set(BizOrder::getPayTime, now)
                .set(BizOrder::getUpdateTime, now));
        if (rows == 0)
        {
            BizOrder latest = orderMapper.selectById(order.getId());
            log.error("[微信支付落账] 条件更新未命中，并发竞态：orderNo={} latestStatus={}", orderNo, latest.getStatus());
            return latest.getStatus() == STATUS_CANCELLED ? LedgerResult.CLOSED : LedgerResult.ALREADY_PAID;
        }

        // 支付流水（order_no 唯一约束兜底幂等；真实支付 is_mock=0 + 微信支付订单号）
        BigDecimal payAmount = order.getPayAmount();
        BizPaymentRecord record = new BizPaymentRecord();
        record.setOrderNo(orderNo);
        record.setMemberId(order.getMemberId());
        record.setAmount(payAmount);
        record.setPayType(2);
        record.setPayStatus(1);
        record.setIsMock(0);
        record.setTransactionId(transactionId);
        record.setPayTime(now);
        record.setCreateTime(now);
        try
        {
            paymentRecordMapper.insert(record);
        }
        catch (DuplicateKeyException e)
        {
            // 并发下流水已存在（理论上条件更新互斥不会走到），按幂等处理
            log.error("[微信支付落账] 支付流水重复，按幂等处理：orderNo={} transactionId={}", orderNo, transactionId);
            return LedgerResult.ALREADY_PAID;
        }

        // 销量同步累加（与模拟支付一致，落账事务内强一致）
        List<BizOrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<BizOrderItem>()
                .eq(BizOrderItem::getOrderId, order.getId()));
        for (BizOrderItem item : items)
        {
            goodsMapper.update(null, new LambdaUpdateWrapper<BizGoods>()
                    .eq(BizGoods::getId, item.getGoodsId())
                    .setSql("sales = sales + " + (item.getQuantity() == null ? 0 : item.getQuantity())));
        }

        // 事务提交后发支付成功事件（MQ 关闭时降级跳过）
        if (mqProducer != null)
        {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit()
                        {
                            mqProducer.sendOrderPaidSuccess(orderNo);
                        }
                    });
        }
        else
        {
            log.info("[微信支付落账] rocketmq.enabled=false，跳过支付成功事件发送：orderNo={}", orderNo);
        }

        // 事务提交后向管理端 WebSocket 推送新订单实时提醒（推送失败仅记日志）
        final Long pushOrderId = order.getId();
        final Integer pushDeliveryType = order.getDeliveryType();
        final BigDecimal pushAmount = payAmount;
        final Date pushTime = now;
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit()
                    {
                        orderPushService.pushNewOrder(pushOrderId, orderNo, pushDeliveryType, pushAmount, pushTime);
                    }
                });

        log.info("[微信支付落账] 支付成功：orderNo={} amount={} transactionId={}", orderNo, payAmount, transactionId);
        return LedgerResult.PAID;
    }
}
