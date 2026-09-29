package com.ruoyi.userapi.service;

import com.ruoyi.userapi.domain.vo.LoginVo;

/**
 * 小程序认证服务接口
 *
 * @author 阿婆干饭社
 */
public interface ApiAuthService
{
    /**
     * 微信登录：code 换 openid（或开发辅助 mock），查/建用户，签发 Redis token（7 天）
     *
     * @param code wx.login 获取的 code；mock 模式下直接作为 openid 使用
     */
    LoginVo login(String code);
}
