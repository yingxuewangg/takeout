package com.ruoyi.userapi.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;

/**
 * 订单状态变更事件消费者（一期仅记录日志，为二期微信订阅消息/数据统计解耦预留，方案 3.5）。
 * 生产者：管理端流转（merchant/OrderMqProducer）。rocketmq.enabled=false 时本消费者不注册。
 *
 * @author 阿婆干饭社
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@RocketMQMessageListener(topic = "order-status-changed", consumerGroup = "takeout-status-changed-consumer")
public class OrderStatusChangedConsumer implements RocketMQListener<String>
{
    private static final Logger log = LoggerFactory.getLogger(OrderStatusChangedConsumer.class);

    @Override
    public void onMessage(String payload)
    {
        log.info("[MQ] 订单状态变更事件（一期仅日志）：{}", payload);
    }
}
