package com.ruoyi.merchant.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置（T15）。
 *
 * 端点路径走配置项 takeout.websocket.path（默认 /websocket/order）；
 * 允许任意来源（管理端与后端同域或开发环境跨域，握手已做 token 鉴权）。
 *
 * @author 阿婆干饭社
 */
@Configuration
@EnableWebSocket
public class TakeoutWebSocketConfig implements WebSocketConfigurer
{
    /** WebSocket 端点路径（application.yml 可覆盖） */
    @Value("${takeout.websocket.path:/websocket/order}")
    private String websocketPath;

    private final TakeoutWebSocketHandler takeoutWebSocketHandler;

    private final TakeoutHandshakeInterceptor takeoutHandshakeInterceptor;

    public TakeoutWebSocketConfig(TakeoutWebSocketHandler takeoutWebSocketHandler,
                                  TakeoutHandshakeInterceptor takeoutHandshakeInterceptor)
    {
        this.takeoutWebSocketHandler = takeoutWebSocketHandler;
        this.takeoutHandshakeInterceptor = takeoutHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry)
    {
        registry.addHandler(takeoutWebSocketHandler, websocketPath)
                .addInterceptors(takeoutHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
