package com.ruoyi.userapi.service;

import java.math.BigDecimal;

/**
 * 支付服务接口（策略模式，方案 3.3）。
 * 一期实现 MockPaymentServiceImpl（假支付）；二期替换为 WechatPaymentServiceImpl，
 * 业务代码不感知具体实现。
 *
 * @author 阿婆干饭社
 */
public interface PaymentService
{
    /**
     * 支付订单（模拟支付）。
     * 校验（金额一律以数据库为准，不信任前端）：订单存在、属于当前用户、主状态待支付、
     * 实付金额>0、实付金额与明细合计+配送费快照一致；前端若额外传金额必须与库内一致否则拒绝。
     * 幂等：支付流水订单号唯一约束 + 订单条件更新（where status=0），重复支付不会重复成功。
     *
     * @param memberId 当前登录用户
     * @param orderId 订单ID（支付请求只传订单ID，不传金额）
     * @param clientAmount 前端额外传入的金额（可空；非空时必须与库内实付一致）
     * @return 支付成功的订单号
     */
    String pay(Long memberId, Long orderId, BigDecimal clientAmount);
}
