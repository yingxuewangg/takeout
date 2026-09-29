package com.ruoyi.userapi.controller;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.userapi.config.ApiMemberContext;
import com.ruoyi.userapi.service.PaymentService;

/**
 * 小程序端模拟支付接口（走登录态）。
 * 支付请求只传 orderId（金额以库内为准）；前端若额外传金额必须与库内一致否则拒绝。
 * 方案 3.3：假支付仅用于开发/演示；上线真实支付时替换 WechatPaymentService 并关闭 mock 开关。
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/api/pay")
public class ApiPayController extends BaseController
{
    @Autowired
    private PaymentService paymentService;

    /**
     * 模拟支付
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
        String orderNo = paymentService.pay(ApiMemberContext.requireMemberId(), body.getOrderId(), body.getAmount());
        AjaxResult ajax = success();
        ajax.put("orderNo", orderNo);
        return ajax;
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
