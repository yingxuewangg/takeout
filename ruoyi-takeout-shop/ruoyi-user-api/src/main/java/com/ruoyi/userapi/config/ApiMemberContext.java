package com.ruoyi.userapi.config;

/**
 * 小程序端登录用户上下文（ThreadLocal）。
 * 由 ApiLoginInterceptor 在 /api 请求进入时解析 token 并注入，请求结束必须 clear（拦截器 afterCompletion）。
 *
 * @author 阿婆干饭社
 */
public class ApiMemberContext
{
    private static final ThreadLocal<Long> MEMBER_ID_HOLDER = new ThreadLocal<>();

    public static void setMemberId(Long memberId)
    {
        MEMBER_ID_HOLDER.set(memberId);
    }

    /**
     * @return 当前登录用户ID；未登录（游客浏览放行接口）返回 null
     */
    public static Long getMemberId()
    {
        return MEMBER_ID_HOLDER.get();
    }

    /**
     * 获取当前登录用户ID，未登录直接抛出异常（强制登录的接口使用）
     */
    public static Long requireMemberId()
    {
        Long memberId = MEMBER_ID_HOLDER.get();
        if (memberId == null)
        {
            throw new com.ruoyi.common.exception.ServiceException("请先登录", com.ruoyi.common.constant.HttpStatus.UNAUTHORIZED);
        }
        return memberId;
    }

    public static void clear()
    {
        MEMBER_ID_HOLDER.remove();
    }
}
