package com.ruoyi.merchant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.merchant.config.WechatPayProperties;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;

/**
 * 微信支付 APIv3 客户端封装（三期，官方 SDK wechatpay-java）。
 *
 * 懒加载：RSAAutoCertificateConfig 仅在首次真实调用时初始化（自动下载平台证书），
 * 未配置商户参数（takeout.wechat-pay.*）时保持 null，保证 clone 即跑、模拟支付不受影响。
 * 文档：https://pay.weixin.qq.com/docs/merchant/apis/jsapi-payment/jsapi-prepay.html
 *
 * @author 阿婆干饭社
 */
@Component
public class WechatPayClient
{
    private static final Logger log = LoggerFactory.getLogger(WechatPayClient.class);

    @Autowired
    private WechatPayProperties props;

    /** SDK 全局配置（自动更新平台证书），懒加载 + 双重检查锁 */
    private volatile RSAAutoCertificateConfig config;

    /**
     * JSAPI 下单并直接返回小程序调起支付参数（SDK 已完成签名）。
     * 注意：本方法只向微信下单、不扣款；支付是否成功以回调/查单为准。
     *
     * @param outTradeNo 商户订单号（= 现有订单号）
     * @param totalCents 订单金额（分）
     * @param description 商品描述
     * @param openid 支付用户 openid
     * @return 小程序 wx.requestPayment 所需参数（timeStamp/nonceStr/packageVal/signType/paySign）
     */
    public PrepayWithRequestPaymentResponse jsapiPrepay(String outTradeNo, long totalCents, String description, String openid)
    {
        JsapiServiceExtension service = new JsapiServiceExtension.Builder().config(config()).build();
        PrepayRequest request = new PrepayRequest();
        request.setAppid(props.getAppId());
        request.setMchid(props.getMchId());
        request.setDescription(description);
        request.setOutTradeNo(outTradeNo);
        request.setNotifyUrl(props.getNotifyUrl());
        Amount amount = new Amount();
        amount.setTotal((int) totalCents);
        request.setAmount(amount);
        Payer payer = new Payer();
        payer.setOpenid(openid);
        request.setPayer(payer);
        try
        {
            return service.prepayWithRequestPayment(request);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.error("[微信支付] JSAPI 下单失败：outTradeNo={} totalCents={}", outTradeNo, totalCents, e);
            throw new ServiceException("微信下单失败：" + e.getMessage());
        }
    }

    /**
     * 获取（并缓存）SDK 配置；未配置完整商户参数时抛出可读异常
     */
    private RSAAutoCertificateConfig config()
    {
        if (!props.isConfigured())
        {
            throw new ServiceException("微信支付未配置（takeout.wechat-pay.app-id/mch-id/mch-serial-no/api-v3-key/private-key-path/notify-url），无法发起真实支付");
        }
        RSAAutoCertificateConfig c = config;
        if (c == null)
        {
            synchronized (this)
            {
                c = config;
                if (c == null)
                {
                    c = new RSAAutoCertificateConfig.Builder()
                            .merchantId(props.getMchId())
                            .privateKeyFromPath(props.getPrivateKeyPath())
                            .merchantSerialNumber(props.getMchSerialNo())
                            .apiV3Key(props.getApiV3Key())
                            .build();
                    config = c;
                    log.info("[微信支付] SDK 配置初始化完成：mchId={} serialNo={}", props.getMchId(), props.getMchSerialNo());
                }
            }
        }
        return c;
    }
}
