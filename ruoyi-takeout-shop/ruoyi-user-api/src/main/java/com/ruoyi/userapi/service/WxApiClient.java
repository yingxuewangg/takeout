package com.ruoyi.userapi.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;

/**
 * 微信 code2session 调用封装。
 * 文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html
 *
 * @author 阿婆干饭社
 */
@Component
public class WxApiClient
{
    private static final String JSCODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code";

    @Value("${takeout.wx.appid:}")
    private String appid;

    @Value("${takeout.wx.secret:}")
    private String secret;

    private final RestClient restClient = RestClient.create();

    /**
     * code 换 openid（真实模式）
     *
     * @param code wx.login 获取的 code
     * @return openid
     */
    public String code2Session(String code)
    {
        if (!StringUtils.hasText(appid) || !StringUtils.hasText(secret))
        {
            throw new ServiceException("微信 appid/secret 未配置（takeout.wx.appid/secret），开发调试可开启 takeout.wx.mock-enabled 开关");
        }
        String body;
        try
        {
            body = restClient.get()
                    .uri(JSCODE2SESSION_URL, appid, secret, code)
                    .retrieve()
                    .body(String.class);
        }
        catch (Exception e)
        {
            throw new ServiceException("微信登录接口调用失败：" + e.getMessage());
        }
        JSONObject resp;
        try
        {
            resp = JSON.parseObject(body);
        }
        catch (Exception e)
        {
            throw new ServiceException("微信登录接口响应解析失败");
        }
        if (resp == null || !StringUtils.hasText(resp.getString("openid")))
        {
            throw new ServiceException("微信登录失败：" + resp.getString("errmsg") + "(errcode=" + resp.getIntValue("errcode") + ")");
        }
        return resp.getString("openid");
    }
}
