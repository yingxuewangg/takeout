package com.ruoyi.merchant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.merchant.config.WechatPayProperties;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.jsapi.model.QueryOrderByOutTradeNoRequest;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest;

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
     * 按商户订单号查询微信侧支付订单状态（T24 查单兜底/关单守卫用）。
     */
    public com.wechat.pay.java.service.payments.model.Transaction queryOrderByOutTradeNo(String outTradeNo)
    {
        QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
        request.setMchid(props.getMchId());
        request.setOutTradeNo(outTradeNo);
        return new JsapiServiceExtension.Builder().config(config()).build().queryOrderByOutTradeNo(request);
    }

    /**
     * 关闭微信侧订单（T24 关单守卫：本地关单前先关微信侧，防"本地已关、用户却支付成功"）。
     * 微信侧已支付时 SDK 抛异常（微信侧订单不可关），调用方应重新查单确认真实状态。
     */
    public void closeOrder(String outTradeNo)
    {
        CloseOrderRequest request = new CloseOrderRequest();
        request.setMchid(props.getMchId());
        request.setOutTradeNo(outTradeNo);
        new JsapiServiceExtension.Builder().config(config()).build().closeOrder(request);
    }

    /**
     * 解析并验签微信支付结果通知（T23）：验签通过返回 AES-GCM 解密后的支付订单 Transaction，
     * 验签/解密失败抛 SDK 异常（调用方按 FAIL 应答，微信将重试通知）。
     * 文档：https://pay.weixin.qq.com/docs/merchant/development/interface-rules/payment-notification.html
     */
    public com.wechat.pay.java.service.payments.model.Transaction parsePaymentNotify(RequestParam requestParam)
    {
        NotificationParser parser = new NotificationParser(config());
        return parser.parse(requestParam, com.wechat.pay.java.service.payments.model.Transaction.class);
    }

    /**
     * 发起微信原路退款（三期 T25 商家审核通过时调用）。
     * 受理成功不代表退款到账（微信异步处理），到账以退款回调/查单兜底为准。
     * 文档：https://pay.weixin.qq.com/docs/merchant/apis/jsapi-payment/refund.html
     *
     * @param outTradeNo 商户订单号（= 现有订单号）
     * @param outRefundNo 商户退款单号（= biz_order_refund.id，全局唯一）
     * @param reason 退款原因（UTF-8 最长 80 字节，调用方截断）
     * @param totalCents 原订单金额（分）
     * @param refundCents 退款金额（分）
     */
    public com.wechat.pay.java.service.refund.model.Refund createRefund(
            String outTradeNo, String outRefundNo, String reason, long totalCents, long refundCents)
    {
        CreateRequest request = new CreateRequest();
        request.setOutTradeNo(outTradeNo);
        request.setOutRefundNo(outRefundNo);
        request.setReason(reason);
        if (props.getRefundNotifyUrl() != null && props.getRefundNotifyUrl().length() > 0)
        {
            request.setNotifyUrl(props.getRefundNotifyUrl());
        }
        AmountReq amount = new AmountReq();
        amount.setRefund(refundCents);
        amount.setTotal(totalCents);
        amount.setCurrency("CNY");
        request.setAmount(amount);
        try
        {
            return new RefundService.Builder().config(config()).build().create(request);
        }
        catch (com.wechat.pay.java.core.exception.ServiceException e)
        {
            // 微信明确拒绝（余额不足/单号重复/金额超限等）：带错误码抛出，调用方回滚审核
            log.error("[微信支付] 退款受理被拒：outTradeNo={} outRefundNo={} code={} body={}",
                    outTradeNo, outRefundNo, e.getErrorCode(), e.getResponseBody(), e);
            throw new ServiceException("微信退款受理失败：" + e.getErrorCode());
        }
        catch (Exception e)
        {
            log.error("[微信支付] 退款受理失败：outTradeNo={} outRefundNo={}", outTradeNo, outRefundNo, e);
            throw new ServiceException("微信退款受理失败：" + e.getMessage());
        }
    }

    /**
     * 按商户退款单号查询微信侧退款单（T25 查单兜底/回调对账用）。
     */
    public com.wechat.pay.java.service.refund.model.Refund queryRefundByOutRefundNo(String outRefundNo)
    {
        QueryByOutRefundNoRequest request = new QueryByOutRefundNoRequest();
        request.setOutRefundNo(outRefundNo);
        return new RefundService.Builder().config(config()).build().queryByOutRefundNo(request);
    }

    /**
     * 解析并验签微信退款结果通知（T25）：验签通过返回 AES-GCM 解密后的退款单 RefundNotification，
     * 验签/解密失败抛 SDK 异常（调用方按 FAIL 应答，微信将重试通知）。
     * 文档：https://pay.weixin.qq.com/docs/merchant/apis/jsapi-payment/refund-notification.html
     */
    public com.wechat.pay.java.service.refund.model.RefundNotification parseRefundNotify(RequestParam requestParam)
    {
        NotificationParser parser = new NotificationParser(config());
        return parser.parse(requestParam, com.wechat.pay.java.service.refund.model.RefundNotification.class);
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
