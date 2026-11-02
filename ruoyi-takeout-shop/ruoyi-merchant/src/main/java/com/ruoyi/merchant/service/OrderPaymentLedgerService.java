package com.ruoyi.merchant.service;

/**
 * 支付成功落账服务（三期 T23）：真实微信支付的"支付成功"落账逻辑单点收敛，
 * 供支付成功回调（userapi 控制器委托）与查单兜底（管理侧定时任务）共用。
 *
 * 与模拟支付同一套落账语义：订单条件更新 0->1（where status=0 幂等）+ 支付流水
 * （is_mock=0，记录微信支付订单号）+ 菜品销量同步累加；事务提交后发支付成功事件
 * 与管理端新订单实时推送。金额以库内订单实付为准，微信侧金额不一致不落账。
 *
 * @author 阿婆干饭社
 */
public interface OrderPaymentLedgerService
{
    /** 落账结果 */
    enum LedgerResult
    {
        /** 本次调用完成落账 */
        PAID,

        /** 幂等命中：订单此前已落账（重复回调/查单兜底） */
        ALREADY_PAID,

        /** 订单已取消/超时关闭，款项需原路退回（退款接入见 T25） */
        CLOSED,

        /** 订单不存在 */
        NOT_FOUND,

        /** 微信侧实付金额与订单实付不一致（人工对账，不落账） */
        AMOUNT_MISMATCH
    }

    /**
     * 支付成功落账。
     *
     * @param orderNo 商户订单号（out_trade_no = 订单号）
     * @param transactionId 微信支付订单号
     * @param payerTotalCents 微信侧实付金额（分）
     * @return 落账结果（调用方据此记录日志/应答微信）
     */
    LedgerResult markPaid(String orderNo, String transactionId, Integer payerTotalCents);
}
