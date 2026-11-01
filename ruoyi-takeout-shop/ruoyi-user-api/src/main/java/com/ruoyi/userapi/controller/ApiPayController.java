package com.ruoyi.userapi.controller;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.userapi.config.ApiMemberContext;
import com.ruoyi.userapi.domain.vo.PayResultVo;
import com.ruoyi.userapi.service.PaymentService;
import com.ruoyi.userapi.service.impl.MockPaymentServiceImpl;
import com.ruoyi.userapi.service.impl.WechatPaymentServiceImpl;

/**
 * 小程序端支付接口（走登录态）。
 * 支付请求只传 orderId（金额以库内为准）；前端若额外传金额必须与库内一致否则拒绝。
 * 三期统一入口 POST /submit：按 payment.mock.enabled 分发模拟/微信支付实现，
 * 返回 PayResultVo（channel=mock 直接成功；channel=wechat 带 wx.requestPayment 调起参数）。
 * GET /config：小程序收银台读取当前渠道与文案。POST /mock 为一期遗留入口，小程序切换 /submit 后移除。
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/api/pay")
public class ApiPayController extends BaseController
{
    /** 模拟支付实现（payment.mock.enabled=true 或缺省时装配） */
    @Autowired(required = false)
    private MockPaymentServiceImpl mockPaymentService;

    /** 微信支付实现（payment.mock.enabled=false 时装配） */
    @Autowired(required = false)
    private WechatPaymentServiceImpl wechatPaymentService;

    @Value("${payment.mock.enabled:true}")
    private boolean mockEnabled;

    /**
     * 统一下单（三期入口）
     *
     * @param body {orderId: 订单ID, amount: 金额（可选，传了必须与库内一致）}
     * @return PayResultVo：{channel, orderNo, payParams?}
     */
    @PostMapping("/submit")
    public AjaxResult submit(@RequestBody PayBody body)
    {
        if (body.getOrderId() == null)
        {
            return error("orderId 不能为空");
        }
        PayResultVo result = currentService().pay(ApiMemberContext.requireMemberId(), body.getOrderId(), body.getAmount());
        AjaxResult ajax = success();
        ajax.put("channel", result.getChannel());
        ajax.put("orderNo", result.getOrderNo());
        if (result.getPayParams() != null)
        {
            ajax.put("payParams", result.getPayParams());
        }
        return ajax;
    }

    /**
     * 当前支付渠道配置（小程序收银台文案用）
     *
     * @return {channel: mock|wechat, label: 文案}
     */
    @GetMapping("/config")
    public AjaxResult config()
    {
        AjaxResult ajax = success();
        ajax.put("channel", mockEnabled ? "mock" : "wechat");
        ajax.put("label", mockEnabled ? "微信支付（模拟）" : "微信支付");
        return ajax;
    }

    /**
     * 模拟支付（一期遗留入口，T26 小程序切换 /submit 后移除）
     *
     * @param body {orderId: 订单ID, amount: 金额（可选，传了必须与库内一致）}
     * @return {orderNo}
     */
    @PostMapping("/mock")
    public AjaxResult mockPay(@RequestBody PayBody body)
    {
        if (body.getOrderId() == null)
        {
            return error("orderId 不能为空");
        }
        PayResultVo result = mockPaymentService.pay(ApiMemberContext.requireMemberId(), body.getOrderId(), body.getAmount());
        AjaxResult ajax = success();
        ajax.put("orderNo", result.getOrderNo());
        return ajax;
    }

    /** 按开关取当前支付实现（配置为真实支付但未装配实现时给出可读错误） */
    private PaymentService currentService()
    {
        if (mockEnabled)
        {
            if (mockPaymentService == null)
            {
                throw new ServiceException("模拟支付未启用");
            }
            return mockPaymentService;
        }
        if (wechatPaymentService == null)
        {
            throw new ServiceException("微信支付未启用（payment.mock.enabled=false 但实现未装配）");
        }
        return wechatPaymentService;
    }

    /** 支付请求体（金额可选且不可信，仅作一致性校验） */
    public static class PayBody
    {
        private Long orderId;

        private BigDecimal amount;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }
}
