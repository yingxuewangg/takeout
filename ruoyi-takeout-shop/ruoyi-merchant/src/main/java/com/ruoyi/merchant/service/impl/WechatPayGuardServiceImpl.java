package com.ruoyi.merchant.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.merchant.domain.BizOrder;
import com.ruoyi.merchant.mapper.BizOrderMapper;
import com.ruoyi.merchant.service.OrderPaymentLedgerService;
import com.ruoyi.merchant.service.WechatPayClient;
import com.ruoyi.merchant.service.WechatPayGuardService;
import com.wechat.pay.java.service.payments.model.Transaction;

/**
 * 微信支付关单守卫实现（三期 T24）。
 *
 * 流程：查微信侧单 -> SUCCESS：落账并拦截本地关单；NOTPAY/USERPAYING：先关微信侧
 * （关单失败说明可能刚好支付成功，复查一次，已支付则落账拦截）；CLOSED/REVOKED 等
 * 其余状态直接放行本地关单。查单/关单异常不阻塞本地关单（放行，回调与查单兜底仍可纠正，
 * 极端场景由 T25 关单后收到支付成功的退款路径兜底）。
 *
 * @author 阿婆干饭社
 */
@Service
public class WechatPayGuardServiceImpl implements WechatPayGuardService
{
    private static final Logger log = LoggerFactory.getLogger(WechatPayGuardServiceImpl.class);

    /** 微信支付下单标记（T22 发起 JSAPI 下单时写入） */
    private static final int PAY_TYPE_WECHAT_PREPAY = 2;

    @Autowired
    private BizOrderMapper orderMapper;

    @Autowired
    private WechatPayClient wechatPayClient;

    @Autowired
    private OrderPaymentLedgerService paymentLedgerService;

    @Override
    public boolean beforeLocalClose(String orderNo)
    {
        BizOrder order = orderMapper.selectOne(new LambdaQueryWrapper<BizOrder>()
                .eq(BizOrder::getOrderNo, orderNo));
        if (order == null || !Integer.valueOf(PAY_TYPE_WECHAT_PREPAY).equals(order.getPayType()))
        {
            // 模拟支付/未发起微信下单的订单：维持原有关单路径，零变化
            return true;
        }
        Transaction transaction;
        try
        {
            transaction = wechatPayClient.queryOrderByOutTradeNo(orderNo);
        }
        catch (Exception e)
        {
            log.error("[微信关单守卫] 查单失败，放行本地关单（回调/查单兜底仍可纠正）：orderNo={} err={}",
                    orderNo, e.getMessage());
            return true;
        }
        if (transaction.getTradeState() == Transaction.TradeStateEnum.SUCCESS)
        {
            OrderPaymentLedgerService.LedgerResult result = markPaid(transaction);
            if (result == OrderPaymentLedgerService.LedgerResult.PAID
                    || result == OrderPaymentLedgerService.LedgerResult.ALREADY_PAID)
            {
                log.info("[微信关单守卫] 微信侧已支付，拦截本地关单：orderNo={} result={}", orderNo, result);
                return false;
            }
            log.error("[微信关单守卫] 微信侧已支付但落账异常（{}），放行本地关单待人工对账：orderNo={}",
                    result, orderNo);
            return true;
        }
        if (transaction.getTradeState() == Transaction.TradeStateEnum.NOTPAY
                || transaction.getTradeState() == Transaction.TradeStateEnum.USERPAYING)
        {
            // 未支付/支付中：先关微信侧，避免本地关闭后用户又支付成功
            try
            {
                wechatPayClient.closeOrder(orderNo);
                log.info("[微信关单守卫] 微信侧关单成功：orderNo={} tradeState={}", orderNo, transaction.getTradeState());
                return true;
            }
            catch (Exception e)
            {
                // 关单失败（可能刚好支付成功）：复查一次，已支付则落账拦截；仍非支付态放行本地关单
                log.warn("[微信关单守卫] 微信侧关单失败，复查订单状态：orderNo={} err={}", orderNo, e.getMessage());
                return recheckAndDecide(orderNo);
            }
        }
        // CLOSED/REVOKED/PAYERROR/REFUND：微信侧无有效待支付单，直接放行本地关单
        log.info("[微信关单守卫] 微信侧非支付中状态，放行本地关单：orderNo={} tradeState={}",
                orderNo, transaction.getTradeState());
        return true;
    }

    /**
     * 微信侧关单失败后的复查决策：SUCCESS 则落账并拦截，其余放行
     */
    private boolean recheckAndDecide(String orderNo)
    {
        try
        {
            Transaction re = wechatPayClient.queryOrderByOutTradeNo(orderNo);
            if (re.getTradeState() == Transaction.TradeStateEnum.SUCCESS)
            {
                OrderPaymentLedgerService.LedgerResult result = markPaid(re);
                if (result == OrderPaymentLedgerService.LedgerResult.PAID
                        || result == OrderPaymentLedgerService.LedgerResult.ALREADY_PAID)
                {
                    log.info("[微信关单守卫] 复查发现已支付，拦截本地关单：orderNo={} result={}", orderNo, result);
                    return false;
                }
            }
        }
        catch (Exception e)
        {
            log.error("[微信关单守卫] 复查失败，放行本地关单：orderNo={} err={}", orderNo, e.getMessage());
        }
        return true;
    }

    private OrderPaymentLedgerService.LedgerResult markPaid(Transaction transaction)
    {
        Integer payerTotal = transaction.getAmount() == null ? null : transaction.getAmount().getPayerTotal();
        return paymentLedgerService.markPaid(transaction.getOutTradeNo(), transaction.getTransactionId(), payerTotal);
    }
}
