package com.ruoyi.userapi.controller;

import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.merchant.service.OrderPaymentLedgerService;
import com.ruoyi.merchant.service.WechatPayClient;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;

/**
 * 微信支付回调接口（三期 T23，公开接口：微信服务器调用，已加登录拦截器放行）。
 *
 * 安全做法：不信任任何未验签内容——用官方 SDK NotificationParser 对
 * Wechatpay-* 请求头 + 原始报文整体验签，再 AES-GCM 解密出支付结果；
 * 业务落账委托管理侧共享服务（回调/查单兜底同语义），按"重复到达零副作用"设计：
 * 已落账订单重复通知幂等返回 SUCCESS；落账结果只决定日志与应答，不抛业务异常。
 *
 * 应答约定（微信支付 APIv3）：
 * - 成功：HTTP 200 + {"code":"SUCCESS","message":"成功"}
 * - 失败：HTTP 500 + {"code":"FAIL","message":"..."}（微信将按衰减节奏重试）
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/api/pay/wechat")
public class ApiWechatPayNotifyController
{
    private static final Logger log = LoggerFactory.getLogger(ApiWechatPayNotifyController.class);

    @Autowired
    private WechatPayClient wechatPayClient;

    @Autowired
    private OrderPaymentLedgerService paymentLedgerService;

    /**
     * 支付结果通知（takeout.wechat-pay.notify-url 指向本接口）
     */
    @PostMapping("/notify")
    public ResponseEntity<Map<String, String>> payNotify(HttpServletRequest request, @RequestBody String body)
    {
        try
        {
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(request.getHeader("Wechatpay-Serial"))
                    .nonce(request.getHeader("Wechatpay-Nonce"))
                    .signature(request.getHeader("Wechatpay-Signature"))
                    .timestamp(request.getHeader("Wechatpay-Timestamp"))
                    .body(body)
                    .build();
            Transaction transaction = wechatPayClient.parsePaymentNotify(requestParam);
            if (transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS)
            {
                // 非成功状态（NOTPAY/USERPAYING/CLOSED 等）不落账，直接应答成功结束本次通知
                log.info("[微信支付回调] 非成功状态通知：outTradeNo={} tradeState={}",
                        transaction.getOutTradeNo(), transaction.getTradeState());
                return success();
            }
            Integer payerTotal = transaction.getAmount() == null ? null : transaction.getAmount().getPayerTotal();
            OrderPaymentLedgerService.LedgerResult result = paymentLedgerService.markPaid(
                    transaction.getOutTradeNo(), transaction.getTransactionId(), payerTotal);
            switch (result)
            {
                case PAID:
                case ALREADY_PAID:
                    return success();
                case CLOSED:
                    // 已关闭订单收到支付成功：记录待退款（T25 自动原路退款），应答成功避免重试风暴
                    log.error("[微信支付回调] 订单已关闭，待原路退款：outTradeNo={} transactionId={}",
                            transaction.getOutTradeNo(), transaction.getTransactionId());
                    return success();
                case NOT_FOUND:
                case AMOUNT_MISMATCH:
                    // 数据异常不落账：应答成功终止重试，依赖查单兜底与人工对账
                    log.error("[微信支付回调] 落账未完成（{}）：outTradeNo={} transactionId={} payerTotal={}",
                            result, transaction.getOutTradeNo(), transaction.getTransactionId(), payerTotal);
                    return success();
                default:
                    return fail("未知落账结果");
            }
        }
        catch (Exception e)
        {
            // 验签失败/解密失败/系统异常：应答 FAIL 让微信重试
            log.error("[微信支付回调] 处理失败（验签或落账异常）", e);
            return fail("处理失败");
        }
    }

    private ResponseEntity<Map<String, String>> success()
    {
        Map<String, String> body = new HashMap<>();
        body.put("code", "SUCCESS");
        body.put("message", "成功");
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Map<String, String>> fail(String message)
    {
        Map<String, String> body = new HashMap<>();
        body.put("code", "FAIL");
        body.put("message", message);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
