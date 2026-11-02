package com.ruoyi.merchant.service;

/**
 * 微信支付关单守卫服务（三期 T24）。
 *
 * 解决关单竞态：订单本地超时关闭/用户取消的同时，用户恰好在微信收银台支付成功——
 * 若不先问微信侧，会出现"本地已关单、微信已扣款"。三个本地关单入口
 * （MQ 超时消费者 / 超时关单定时任务 / 用户主动取消）在条件更新前置本守卫。
 *
 * 仅对已发起微信下单的订单生效（pay_type=2，T22 下单时打标记）；
 * 模拟支付/未下单订单直接放行，原有关单路径零变化。
 *
 * @author 阿婆干饭社
 */
public interface WechatPayGuardService
{
    /**
     * 本地关单前置守卫：微信侧查单，已支付则落账并拦截本地关单；
     * 未支付则先关微信侧订单，再放行本地关单（关单失败复查一次，已支付同样拦截）。
     *
     * @param orderNo 订单号
     * @return true=继续本地关单；false=拦截（微信侧已支付且已落账，订单应走已支付语义）
     */
    boolean beforeLocalClose(String orderNo);
}
