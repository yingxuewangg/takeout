package com.ruoyi.merchant.service;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 管理端实时推送服务（T15）。
 *
 * 目前仅"新订单"一种事件；退款申请、催单等后续事件在服务上追加方法即可，
 * 触发点均放在对应事务提交后（afterCommit）调用。
 *
 * @author 阿婆干饭社
 */
public interface OrderPushService
{
    /**
     * 新订单实时提醒：向全部在线管理端连接广播 NEW_ORDER 消息。
     * 必须在支付事务提交后调用（afterCommit）；内部吞掉全部异常，推送失败仅记日志，不影响支付主流程。
     *
     * @param orderId      订单ID
     * @param orderNo      订单号
     * @param deliveryType 履约方式（1堂食 2外卖）
     * @param amount       实付金额
     * @param createTime   下单时间
     */
    void pushNewOrder(Long orderId, String orderNo, Integer deliveryType, BigDecimal amount, Date createTime);
}
