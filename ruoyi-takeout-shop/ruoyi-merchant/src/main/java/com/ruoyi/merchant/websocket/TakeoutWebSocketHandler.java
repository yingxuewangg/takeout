package com.ruoyi.merchant.websocket;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 新订单实时提醒 WebSocket 处理器（T15）。
 *
 * 单店场景：所有通过握手鉴权的管理端连接保存在内存集合中，推送时广播给全部在线连接；
 * 不按用户/角色过滤（二期如需再扩展）。连接只收不发业务消息，客户端发来的文本仅记 debug 日志。
 *
 * @author 阿婆干饭社
 */
@Component
public class TakeoutWebSocketHandler extends TextWebSocketHandler
{
    private static final Logger log = LoggerFactory.getLogger(TakeoutWebSocketHandler.class);

    /** 在线管理端连接集合（线程安全；握手成功才进入） */
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session)
    {
        sessions.add(session);
        log.info("[新订单提醒] 管理端连接建立：sessionId={} 当前在线={}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
    {
        sessions.remove(session);
        log.info("[新订单提醒] 管理端连接关闭：sessionId={} status={} 当前在线={}", session.getId(), status, sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
    {
        // 客户端不需要上行通道，收到消息（心跳等）仅记日志
        log.debug("[新订单提醒] 收到客户端消息：sessionId={} payload={}", session.getId(), message.getPayload());
    }

    /**
     * 广播文本消息给全部在线连接（单条失败只剔除该连接，不影响其他连接）
     *
     * @return 实际投递成功的连接数
     */
    public int broadcast(String message)
    {
        int sent = 0;
        for (WebSocketSession session : sessions)
        {
            try
            {
                synchronized (session)
                {
                    if (session.isOpen())
                    {
                        session.sendMessage(new TextMessage(message));
                        sent++;
                    }
                }
            }
            catch (IOException e)
            {
                log.warn("[新订单提醒] 推送失败，剔除连接：sessionId={}", session.getId(), e);
                sessions.remove(session);
                try
                {
                    session.close();
                }
                catch (IOException ignore)
                {
                }
            }
        }
        return sent;
    }

    /** 当前在线连接数（自测用） */
    public int onlineCount()
    {
        return sessions.size();
    }
}
