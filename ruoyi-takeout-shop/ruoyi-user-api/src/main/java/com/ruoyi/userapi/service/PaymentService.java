package com.ruoyi.userapi.service;

import java.math.BigDecimal;
import com.ruoyi.userapi.domain.vo.PayResultVo;

/**
 * 支付服务接口（策略模式，方案 3.3；三期按 payment.mock.enabled 装配实现）。
 * 一期实现 MockPaymentServiceImpl（假支付，matchIfMissing=true 默认启用）；
 * 三期新增 WechatPaymentServiceImpl（微信 JSAPI 支付，payment.mock.enabled=false 时启用）。
 *
 * @author 阿婆干饭社
 */
public interface PaymentService
{
    /**
     * 统一下单/支付订单。
     * 校验（金额一律以数据库为准，不信任前端）：订单存在、属于当前用户、主状态待支付、
     * 实付金额>0、实付金额与明细合计+配送费快照一致；前端若额外传金额必须与库内一致否则拒绝。
     * 幂等：模拟支付=流水订单号唯一约束+订单条件更新（where status=0）；
     *       微信支付=同条件更新仅打 pay_type=2 下单标记，支付成功以回调/查单落账为准。
     *
     * @param memberId 当前登录用户
     * @param orderId 订单ID（支付请求只传订单ID，不传金额）
     * @param clientAmount 前端额外传入的金额（可空；非空时必须与库内实付一致）
     * @return PayResultVo：channel=mock（支付已成功，直接跳结果页）或 wechat（携带 wx.requestPayment 调起参数）
     */
    PayResultVo pay(Long memberId, Long orderId, BigDecimal clientAmount);
}
