package com.ruoyi.merchant.service.impl;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.merchant.service.OrderPushService;
import com.ruoyi.merchant.websocket.TakeoutWebSocketHandler;

/**
 * 管理端实时推送服务实现（T15）。
 *
 * 消息统一为 JSON：{"type":"NEW_ORDER","orderId":..,"orderNo":..,"deliveryType":..,
 * "amount":..,"createTime":..,"content":"您有新的订单，请及时处理"}
 * 推送失败只记日志（无在线连接视为正常降级），绝不向上抛异常。
 *
 * @author 阿婆干饭社
 */
@Service
public class OrderPushServiceImpl implements OrderPushService
{
    private static final Logger log = LoggerFactory.getLogger(OrderPushServiceImpl.class);

    /** 新订单消息类型（前端按此分发） */
    public static final String TYPE_NEW_ORDER = "NEW_ORDER";

    /** 语音播报文案 */
    public static final String NEW_ORDER_CONTENT = "您有新的订单，请及时处理";

    private final TakeoutWebSocketHandler webSocketHandler;

    public OrderPushServiceImpl(TakeoutWebSocketHandler webSocketHandler)
    {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void pushNewOrder(Long orderId, String orderNo, Integer deliveryType, BigDecimal amount, Date createTime)
    {
        try
        {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("type", TYPE_NEW_ORDER);
            message.put("orderId", orderId);
            message.put("orderNo", orderNo);
            message.put("deliveryType", deliveryType);
            message.put("amount", amount);
            message.put("createTime", createTime);
            message.put("content", NEW_ORDER_CONTENT);
            String json = JSON.toJSONString(message);
            int sent = webSocketHandler.broadcast(json);
            log.info("[新订单提醒] 已推送：orderNo={} amount={} 在线={} 成功投递={}", orderNo, amount, webSocketHandler.onlineCount(), sent);
        }
        catch (Exception e)
        {
            // 推送失败只记日志，不影响支付主流程
            log.warn("[新订单提醒] 推送异常（已忽略）：orderNo={}", orderNo, e);
        }
    }
}
