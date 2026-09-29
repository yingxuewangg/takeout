package com.ruoyi.userapi.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.userapi.domain.vo.LoginVo;
import com.ruoyi.userapi.service.ApiAuthService;

/**
 * 小程序端登录接口（/api 前缀，独立登录态，不走若依权限体系）
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/api")
public class ApiAuthController extends BaseController
{
    @Autowired
    private ApiAuthService apiAuthService;

    /**
     * 微信登录
     *
     * @param body {code: wx.login 获取的 code}；开发辅助 mock 开关开启时 code 即 openid
     * @return {token, memberId, nickname, avatar, phone, newUser}
     */
    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody body)
    {
        LoginVo vo = apiAuthService.login(body.getCode());
        return success(vo);
    }

    /** 登录请求体 */
    public static class LoginBody
    {
        private String code;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }
}
