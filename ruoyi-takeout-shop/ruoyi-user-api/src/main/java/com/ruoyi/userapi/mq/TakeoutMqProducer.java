package com.ruoyi.userapi.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 生产者封装（rocketmq.enabled=false 时本 Bean 不注册，业务侧判空降级）。
 * 消息主题（方案 3.5）：
 * - order-timeout-close：下单成功后发 10 分钟延迟消息（延迟级别 14），消费端条件更新幂等；
 * - order-paid-success：模拟支付成功后的事件消息（一期消费仅记日志）；
 * - order-status-changed：订单状态流转事件（T6 使用）。
 * 发送失败不阻塞主流程（记录日志）。
 *
 * @author 阿婆干饭社
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
public class TakeoutMqProducer
{
    private static final Logger log = LoggerFactory.getLogger(TakeoutMqProducer.class);

    /** 延迟级别 14 = 10 分钟（1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m...） */
    private static final int DELAY_LEVEL_10_MINUTES = 14;

    public static final String TOPIC_ORDER_TIMEOUT_CLOSE = "order-timeout-close";

    public static final String TOPIC_ORDER_PAID_SUCCESS = "order-paid-success";

    public static final String TOPIC_ORDER_STATUS_CHANGED = "order-status-changed";

    @Autowired
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    /**
     * 订单超时关闭延迟消息（payload=订单号）
     */
    public void sendOrderTimeoutClose(String orderNo)
    {
        try
        {
            org.springframework.messaging.Message<String> msg =
                    org.springframework.messaging.support.MessageBuilder.withPayload(orderNo).build();
            rocketMQTemplate.syncSend(TOPIC_ORDER_TIMEOUT_CLOSE, msg, 3000, DELAY_LEVEL_10_MINUTES);
            log.info("[MQ] 超时关单延迟消息已发送：orderNo={}，10 分钟后投递", orderNo);
        }
        catch (Exception e)
        {
            // 发送失败不阻塞主流程：定时任务兜底
            log.error("[MQ] 超时关单延迟消息发送失败（定时任务兜底）：orderNo={}", orderNo, e);
        }
    }

    /**
     * 支付成功事件消息（payload=订单号）
     */
    public void sendOrderPaidSuccess(String orderNo)
    {
        try
        {
            org.springframework.messaging.Message<String> msg =
                    org.springframework.messaging.support.MessageBuilder.withPayload(orderNo).build();
            rocketMQTemplate.syncSend(TOPIC_ORDER_PAID_SUCCESS, msg, 3000);
            log.info("[MQ] 支付成功事件已发送：orderNo={}", orderNo);
        }
        catch (Exception e)
        {
            log.error("[MQ] 支付成功事件发送失败（一期仅事件日志，不影响主流程）：orderNo={}", orderNo, e);
        }
    }
}
