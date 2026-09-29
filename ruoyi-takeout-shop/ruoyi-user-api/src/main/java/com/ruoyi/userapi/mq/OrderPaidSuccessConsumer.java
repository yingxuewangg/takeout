package com.ruoyi.userapi.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;

/**
 * 支付成功事件消费者（一期仅记录日志，为二期订阅消息/数据统计解耦预留，方案 3.5）。
 * 销量不在此处理：销量已在支付成功的数据库事务内同步累加（防重复累加，附录 B 决策 15）。
 * rocketmq.enabled=false 时本消费者不注册。
 *
 * @author 阿婆干饭社
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@RocketMQMessageListener(topic = TakeoutMqProducer.TOPIC_ORDER_PAID_SUCCESS, consumerGroup = "takeout-paid-success-consumer")
public class OrderPaidSuccessConsumer implements RocketMQListener<String>
{
    private static final Logger log = LoggerFactory.getLogger(OrderPaidSuccessConsumer.class);

    @Override
    public void onMessage(String orderNo)
    {
        log.info("[MQ] 支付成功事件（一期仅日志）：orderNo={}", orderNo);
    }
}
