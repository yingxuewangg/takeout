package com.ruoyi.merchant.service;

import com.wechat.pay.java.service.refund.model.RefundNotification;

/**
 * 微信退款结果落库服务（三期 T25，管理侧共享：退款回调与查单兜底同语义复用）。
 *
 * @author 阿婆干饭社
 */
public interface WechatRefundService
{
    /**
     * 处理微信退款结果（回调报文或查单结果统一入口）：
     * SUCCESS 时把微信退款受理号按退款单号幂等写回（仅 audit_status=1 且受理号为空的记录）。
     * 任何情况都不抛业务异常、不改订单状态（订单 refund_status 在审核通过时已是终态 2），
     * 异常仅记日志，由查单兜底任务与人工对账兜底。
     *
     * @param notification 微信退款单（已验签解密的回调报文，或查单接口返回）
     * @param source 来源描述（回调/查单兜底），仅用于日志
     */
    void onRefundNotification(RefundNotification notification, String source);
}
