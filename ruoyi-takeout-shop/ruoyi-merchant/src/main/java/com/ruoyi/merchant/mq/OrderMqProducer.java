package com.ruoyi.merchant.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;

/**
 * 管理端 RocketMQ 生产者（rocketmq.enabled=false 时不注册，业务侧判空降级）。
 * 订单状态变更事件 order-status-changed：管理端每次流转操作后发送
 * （接单/出餐/开始配送/确认完成），一期消费仅记日志，为二期订阅消息/统计解耦预留。
 * 用户端下单/取消/支付等消息由 ruoyi-user-api 的 TakeoutMqProducer 负责（依赖方向不可复用）。
 *
 * @author 阿婆干饭社
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
public class OrderMqProducer
{
    private static final Logger log = LoggerFactory.getLogger(OrderMqProducer.class);

    public static final String TOPIC_ORDER_STATUS_CHANGED = "order-status-changed";

    @Autowired
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    /**
     * 订单状态变更事件（payload=JSON {orderNo, fromStatus, toStatus}）
     */
    public void sendOrderStatusChanged(String orderNo, int fromStatus, int toStatus)
    {
        try
        {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("orderNo", orderNo);
            payload.put("fromStatus", fromStatus);
            payload.put("toStatus", toStatus);
            org.springframework.messaging.Message<String> msg =
                    org.springframework.messaging.support.MessageBuilder
                            .withPayload(JSON.toJSONString(payload)).build();
            rocketMQTemplate.syncSend(TOPIC_ORDER_STATUS_CHANGED, msg, 3000);
            log.info("[MQ] 订单状态变更事件已发送：orderNo={} {} -> {}", orderNo, fromStatus, toStatus);
        }
        catch (Exception e)
        {
            // 发送失败不阻塞主流程
            log.error("[MQ] 订单状态变更事件发送失败：orderNo={}", orderNo, e);
        }
    }
}
