package com.ruoyi.merchant.websocket;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;

/**
 * WebSocket 握手鉴权拦截器（T15）。
 *
 * 管理端连接时 URL 带 ?token=<若依登录token>；此处在 Redis 校验 login_tokens:<token> 存在才放行，
 * 非法/过期 token 直接拒绝握手（HTTP 401）。不引入 framework 的 TokenService 依赖
 * （merchant 模块不依赖 framework），直接读同一份登录缓存即可完成校验。
 *
 * @author 阿婆干饭社
 */
@Component
public class TakeoutHandshakeInterceptor implements HandshakeInterceptor
{
    private static final Logger log = LoggerFactory.getLogger(TakeoutHandshakeInterceptor.class);

    private final RedisCache redisCache;

    public TakeoutHandshakeInterceptor(RedisCache redisCache)
    {
        this.redisCache = redisCache;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes)
    {
        if (!(request instanceof ServletServerHttpRequest))
        {
            return false;
        }
        String jwt = ((ServletServerHttpRequest) request).getServletRequest().getParameter("token");
        String tokenId = extractTokenId(jwt);
        if (tokenId == null || redisCache.getCacheObject(CacheConstants.LOGIN_TOKEN_KEY + tokenId) == null)
        {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            log.warn("[新订单提醒] WebSocket 握手拒绝：JWT 缺失、格式错误或登录已过期");
            return false;
        }
        log.info("[新订单提醒] WebSocket 握手通过：tokenId={}", tokenId);
        return true;
    }

    /**
     * 若依管理端 Cookie Admin-Token 存的是 JWT，Redis 使用 JWT claims.login_user_key 对应的 UUID 作为键后缀。
     * 这里只解析 payload，不负责验签；真正的有效性由 Redis token 存在性和过期时间校验。
     */
    private String extractTokenId(String jwt)
    {
        try
        {
            if (jwt == null || jwt.isEmpty())
            {
                return null;
            }
            String[] parts = jwt.split("\\.");
            if (parts.length != 3)
            {
                return null;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JSONObject claims = JSON.parseObject(payload);
            String tokenId = claims.getString("login_user_key");
            return tokenId == null || tokenId.isEmpty() ? null : tokenId;
        }
        catch (Exception e)
        {
            log.debug("[新订单提醒] JWT payload 解析失败", e);
            return null;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception)
    {
        // 无需处理
    }
}
