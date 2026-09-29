package com.ruoyi.userapi.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.merchant.domain.BizGoods;
import com.ruoyi.merchant.domain.BizOrder;
import com.ruoyi.merchant.domain.BizOrderItem;
import com.ruoyi.merchant.domain.BizPaymentRecord;
import com.ruoyi.merchant.mapper.BizGoodsMapper;
import com.ruoyi.merchant.mapper.BizOrderItemMapper;
import com.ruoyi.merchant.mapper.BizOrderMapper;
import com.ruoyi.merchant.mapper.BizPaymentRecordMapper;
import com.ruoyi.merchant.service.OrderPushService;
import com.ruoyi.userapi.mq.TakeoutMqProducer;
import com.ruoyi.userapi.service.PaymentService;

/**
 * 模拟支付实现（一期假支付：不扣钱、不调微信 SDK、不生成真实支付参数）。
 *
 * 事务内：订单条件更新 0->1 + 支付流水插入（订单号唯一约束幂等）+ 菜品销量同步累加；
 * 事务提交后：发 order-paid-success 事件消息（一期消费仅日志）+ 向管理端推送新订单实时提醒（T15）。
 * payment.mock.enabled=false 时拒绝支付（上线真实支付前必须关闭模拟开关）。
 *
 * @author 阿婆干饭社
 */
@Service
public class MockPaymentServiceImpl implements PaymentService
{
    private static final Logger log = LoggerFactory.getLogger(MockPaymentServiceImpl.class);

    /** 订单主状态：0待支付 1待接单 6已取消 */
    private static final int STATUS_UNPAID = 0;
    private static final int STATUS_WAIT_ACCEPT = 1;
    private static final int STATUS_CANCELLED = 6;

    @Value("${payment.mock.enabled:true}")
    private boolean mockEnabled;

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
    private TakeoutMqProducer mqProducer;

    /** 管理端实时推送（新订单提醒，T15）：推送失败内部吞异常，不影响支付 */
    @Autowired
    private OrderPushService orderPushService;

    @Override
    @Transactional
    public String pay(Long memberId, Long orderId, BigDecimal clientAmount)
    {
        if (!mockEnabled)
        {
            throw new ServiceException("模拟支付已关闭（payment.mock.enabled=false），请接入真实支付");
        }
        // ① 订单存在 + ② 归属校验
        BizOrder order = orderMapper.selectById(orderId);
        if (order == null)
        {
            throw new ServiceException("订单不存在");
        }
        if (!order.getMemberId().equals(memberId))
        {
            throw new ServiceException("无权支付该订单");
        }
        // ③ 主状态待支付（幂等提示）
        if (order.getStatus() != STATUS_UNPAID)
        {
            if (order.getStatus() == STATUS_WAIT_ACCEPT)
            {
                throw new ServiceException("订单已支付，请勿重复支付");
            }
            if (order.getStatus() == STATUS_CANCELLED)
            {
                throw new ServiceException("订单已超时关闭或已取消");
            }
            throw new ServiceException("订单当前状态不可支付");
        }
        // ④ 实付金额 > 0
        BigDecimal payAmount = order.getPayAmount();
        if (payAmount == null || payAmount.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("订单金额异常");
        }
        // ⑤ 实付金额 = 明细合计 + 配送费快照（金额以库内为准）
        List<BizOrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<BizOrderItem>()
                .eq(BizOrderItem::getOrderId, orderId));
        BigDecimal itemTotal = items.stream()
                .map(BizOrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expect = itemTotal.add(order.getDeliveryFee() == null ? BigDecimal.ZERO : order.getDeliveryFee());
        if (expect.compareTo(payAmount) != 0)
        {
            log.error("[模拟支付] 金额校验失败：orderId={} 明细合计+配送费={} != 实付={}", orderId, expect, payAmount);
            throw new ServiceException("订单金额校验失败");
        }
        // 前端若额外传金额，必须与库内一致（不信任前端金额）
        if (clientAmount != null && clientAmount.compareTo(payAmount) != 0)
        {
            throw new ServiceException("支付金额与订单实付金额不一致");
        }

        // 条件更新（where status=0）：影响行数=0 说明并发下已被支付/关闭，幂等拦截
        Date now = DateUtils.getNowDate();
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<BizOrder>()
                .eq(BizOrder::getId, orderId)
                .eq(BizOrder::getStatus, STATUS_UNPAID)
                .set(BizOrder::getStatus, STATUS_WAIT_ACCEPT)
                .set(BizOrder::getPayStatus, 1)
                .set(BizOrder::getPayType, 1)
                .set(BizOrder::getPayTime, now)
                .set(BizOrder::getUpdateTime, now));
        if (rows == 0)
        {
            throw new ServiceException("订单已支付，请勿重复支付");
        }

        // 支付流水（order_no 唯一约束兜底幂等）
        BizPaymentRecord record = new BizPaymentRecord();
        record.setOrderNo(order.getOrderNo());
        record.setMemberId(memberId);
        record.setAmount(payAmount);
        record.setPayType(1);
        record.setPayStatus(1);
        record.setIsMock(1);
        record.setMockTradeNo("MOCK" + IdUtils.fastSimpleUUID().substring(0, 20).toUpperCase());
        record.setPayTime(now);
        record.setCreateTime(now);
        try
        {
            paymentRecordMapper.insert(record);
        }
        catch (DuplicateKeyException e)
        {
            throw new ServiceException("请勿重复支付");
        }

        // 销量同步累加（支付事务内，强一致；MQ 事件不承担销量职责）
        for (BizOrderItem item : items)
        {
            goodsMapper.update(null, new LambdaUpdateWrapper<BizGoods>()
                    .eq(BizGoods::getId, item.getGoodsId())
                    .setSql("sales = sales + " + (item.getQuantity() == null ? 0 : item.getQuantity())));
        }

        // 事务提交后发支付成功事件（一期消费仅日志；MQ 关闭时降级跳过）
        if (mqProducer != null)
        {
            final String orderNo = order.getOrderNo();
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
            log.info("[模拟支付] rocketmq.enabled=false，跳过支付成功事件发送：orderNo={}", order.getOrderNo());
        }

        // 事务提交后向管理端 WebSocket 推送新订单实时提醒（T15；与 MQ 事件并列、互不依赖，推送失败仅记日志）
        final Long pushOrderId = orderId;
        final String pushOrderNo = order.getOrderNo();
        final Integer pushDeliveryType = order.getDeliveryType();
        final BigDecimal pushAmount = payAmount;
        final Date pushTime = now;
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit()
                    {
                        orderPushService.pushNewOrder(pushOrderId, pushOrderNo, pushDeliveryType, pushAmount, pushTime);
                    }
                });

        log.info("[模拟支付] 支付成功：orderNo={} amount={} member={} mockTradeNo={}",
                order.getOrderNo(), payAmount, memberId, record.getMockTradeNo());
        return order.getOrderNo();
    }
}
