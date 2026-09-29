package com.ruoyi.userapi.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.merchant.config.TakeoutCacheKeys;

/**
 * 小程序端 /api 登录态拦截器（独立于若依后台权限体系）。
 *
 * 规则：
 * - 放行接口（游客可浏览）：/api/login、/api/shop/info、/api/menu/list、/api/goods/**（在 WebConfig 中 exclude）；
 * - 其余 /api/** 必须携带有效 token（Authorization: Bearer {token}），
 *   token -> Redis takeout:token:{token} -> memberId 注入 ThreadLocal；
 * - 未登录/登录过期统一返回 {code:401, msg:请先登录}，由小程序端触发重新登录。
 *
 * @author 阿婆干饭社
 */
@Component
public class ApiLoginInterceptor implements HandlerInterceptor
{
    @Autowired
    private RedisCache redisCache;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        String token = resolveToken(request);
        if (StringUtils.isNotEmpty(token))
        {
            Long memberId = redisCache.getCacheObject(TakeoutCacheKeys.MEMBER_TOKEN_KEY + token);
            if (memberId != null)
            {
                ApiMemberContext.setMemberId(memberId);
                return true;
            }
        }
        // 统一 401 语义：HTTP 200 + body code=401，便于小程序端统一拦截处理
        ServletUtils.renderString(response, "{\"msg\":\"请先登录\",\"code\":401}");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
    {
        ApiMemberContext.clear();
    }

    /** 从 Authorization: Bearer {token} 中解析 token */
    private String resolveToken(HttpServletRequest request)
    {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.isNotEmpty(authorization) && authorization.startsWith("Bearer "))
        {
            return authorization.substring(7);
        }
        return null;
    }
}
