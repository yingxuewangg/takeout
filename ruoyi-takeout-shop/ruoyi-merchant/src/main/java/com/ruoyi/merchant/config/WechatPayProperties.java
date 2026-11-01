package com.ruoyi.merchant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 微信支付 APIv3 商户配置（三期，前缀 takeout.wechat-pay）。
 *
 * 所有取值通过 ${TAKEOUT_WXPAY_*:} 环境变量占位注入 application.yml，密钥不入仓；
 * 本地调试在 application-local.yml（已在 .gitignore）或环境变量中提供。
 * 未配置完整时支付模块不初始化微信 SDK（clone 即跑），模拟支付不受影响。
 *
 * 参数申请指引（商户平台）：https://pay.weixin.qq.com/docs/merchant/development-interface-rules/mch-open.html
 *
 * @author 阿婆干饭社
 */
@Component
@ConfigurationProperties(prefix = "takeout.wechat-pay")
public class WechatPayProperties
{
    /** 小程序 AppID（与登录 takeout.wx.appid 同一小程序） */
    private String appId;

    /** 商户号 mchId */
    private String mchId;

    /** 商户 API 证书序列号（商户平台 API 安全中心查看） */
    private String mchSerialNo;

    /** 商户 APIv3 密钥（商户平台 API 安全中心设置，32 位） */
    private String apiV3Key;

    /** 商户私钥文件路径（apiclient_key.pem，本地绝对路径，文件不入仓） */
    private String privateKeyPath;

    /** 支付结果回调地址（必须是 https 公网地址，格式 https://host/api/pay/wechat/notify） */
    private String notifyUrl;

    public String getAppId()
    {
        return appId;
    }

    public void setAppId(String appId)
    {
        this.appId = appId;
    }

    public String getMchId()
    {
        return mchId;
    }

    public void setMchId(String mchId)
    {
        this.mchId = mchId;
    }

    public String getMchSerialNo()
    {
        return mchSerialNo;
    }

    public void setMchSerialNo(String mchSerialNo)
    {
        this.mchSerialNo = mchSerialNo;
    }

    public String getApiV3Key()
    {
        return apiV3Key;
    }

    public void setApiV3Key(String apiV3Key)
    {
        this.apiV3Key = apiV3Key;
    }

    public String getPrivateKeyPath()
    {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath)
    {
        this.privateKeyPath = privateKeyPath;
    }

    public String getNotifyUrl()
    {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl)
    {
        this.notifyUrl = notifyUrl;
    }

    /**
     * 六项配置是否全部就绪（就绪才允许初始化微信 SDK 与发起真实支付）
     */
    public boolean isConfigured()
    {
        return StringUtils.hasText(appId) && StringUtils.hasText(mchId)
                && StringUtils.hasText(mchSerialNo) && StringUtils.hasText(apiV3Key)
                && StringUtils.hasText(privateKeyPath) && StringUtils.hasText(notifyUrl);
    }
}
