package com.ruoyi.userapi.service.impl;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.merchant.config.WechatPayProperties;
import com.ruoyi.merchant.domain.BizOrder;
import com.ruoyi.merchant.domain.BizOrderItem;
import com.ruoyi.merchant.mapper.BizOrderItemMapper;
import com.ruoyi.merchant.mapper.BizOrderMapper;
import com.ruoyi.merchant.service.WechatPayClient;
import com.ruoyi.merchant.util.PayAmountUtils;
import com.ruoyi.userapi.domain.BizMember;
import com.ruoyi.userapi.domain.vo.PayResultVo;
import com.ruoyi.userapi.mapper.BizMemberMapper;
import com.ruoyi.userapi.service.PaymentService;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;

/**
 * 微信支付 JSAPI 实现（三期）。
 *
 * 职责仅限"统一下单"：校验（与模拟支付同链）→ 条件更新打 pay_type=2 微信下单标记 →
 * 调 SDK JSAPI 下单拿调起参数返回小程序。不扣款、不落支付流水、不改订单主状态——
 * 支付是否成功一律以回调/查单兜底落账为准（见 merchant 侧 OrderPaymentLedgerService）。
 *
 * pay_type=2 标记用途：订单仍处于待支付（status=0）时标识"已发起微信下单"，
 * 供超时关单前的微信侧查单/关单联动使用；重复下单会再次覆盖为 2，无副作用。
 *
 * @author 阿婆干饭社
 */
@Service
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "false")
public class WechatPaymentServiceImpl implements PaymentService
{
    private static final Logger log = LoggerFactory.getLogger(WechatPaymentServiceImpl.class);

    /** 订单主状态：0待支付 1待接单 6已取消 */
    private static final int STATUS_UNPAID = 0;
    private static final int STATUS_WAIT_ACCEPT = 1;
    private static final int STATUS_CANCELLED = 6;

    /** 支付方式：1微信支付-模拟 2微信支付-真实（biz_order.pay_type） */
    private static final int PAY_TYPE_WECHAT = 2;

    @Autowired
    private BizOrderMapper orderMapper;

    @Autowired
    private BizOrderItemMapper orderItemMapper;

    @Autowired
    private BizMemberMapper memberMapper;

    @Autowired
    private WechatPayProperties wechatPayProperties;

    @Autowired
    private WechatPayClient wechatPayClient;

    @Override
    @Transactional
    public PayResultVo pay(Long memberId, Long orderId, BigDecimal clientAmount)
    {
        if (!wechatPayProperties.isConfigured())
        {
            throw new ServiceException("微信支付未配置（takeout.wechat-pay.*），请检查环境变量或 application-local.yml");
        }
        // ① 订单存在 + ② 归属校验（与模拟支付同链）
        BizOrder order = orderMapper.selectById(orderId);
        if (order == null)
        {
            throw new ServiceException("订单不存在");
        }
        if (!order.getMemberId().equals(memberId))
        {
            throw new ServiceException("无权支付该订单");
        }
        // ③ 主状态待支付（幂等提示）
        if (order.getStatus() != STATUS_UNPAID)
        {
            if (order.getStatus() == STATUS_WAIT_ACCEPT)
            {
                throw new ServiceException("订单已支付，请勿重复支付");
            }
            if (order.getStatus() == STATUS_CANCELLED)
            {
                throw new ServiceException("订单已超时关闭或已取消");
            }
            throw new ServiceException("订单当前状态不可支付");
        }
        // ④ 实付金额 > 0
        BigDecimal payAmount = order.getPayAmount();
        if (payAmount == null || payAmount.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new ServiceException("订单金额异常");
        }
        // ⑤ 实付金额 = 明细合计 + 配送费快照（金额以库内为准）
        List<BizOrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<BizOrderItem>()
                .eq(BizOrderItem::getOrderId, orderId));
        BigDecimal itemTotal = items.stream()
                .map(BizOrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expect = itemTotal.add(order.getDeliveryFee() == null ? BigDecimal.ZERO : order.getDeliveryFee());
        if (expect.compareTo(payAmount) != 0)
        {
            log.error("[微信支付] 金额校验失败：orderId={} 明细合计+配送费={} != 实付={}", orderId, expect, payAmount);
            throw new ServiceException("订单金额校验失败");
        }
        if (clientAmount != null && clientAmount.compareTo(payAmount) != 0)
        {
            throw new ServiceException("支付金额与订单实付金额不一致");
        }
        // ⑥ 支付用户必须有 openid（JSAPI 必填）
        BizMember member = memberMapper.selectById(memberId);
        if (member == null || member.getOpenid() == null || member.getOpenid().isEmpty())
        {
            throw new ServiceException("缺少支付用户 openid，无法发起微信支付");
        }

        // 条件更新打"已发起微信下单"标记（where status=0）：rows=0 说明并发下已被支付/关闭，幂等拦截。
        // 不改主状态/支付状态——真实支付成功与否由回调落账决定；下单后用户放弃支付，订单走原超时关单流程。
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<BizOrder>()
                .eq(BizOrder::getId, orderId)
                .eq(BizOrder::getStatus, STATUS_UNPAID)
                .set(BizOrder::getPayType, PAY_TYPE_WECHAT)
                .set(BizOrder::getUpdateTime, DateUtils.getNowDate()));
        if (rows == 0)
        {
            throw new ServiceException("订单已支付，请勿重复支付");
        }

        // JSAPI 下单（out_trade_no = 订单号；金额元转分单点换算）
        PrepayWithRequestPaymentResponse prepay = wechatPayClient.jsapiPrepay(
                order.getOrderNo(), PayAmountUtils.toCents(payAmount), "阿婆干饭社-订单" + order.getOrderNo(), member.getOpenid());

        // 组装 wx.requestPayment 调起参数（SDK 已签名；appId 取配置值）
        Map<String, String> payParams = new LinkedHashMap<>();
        payParams.put("appId", wechatPayProperties.getAppId());
        payParams.put("timeStamp", prepay.getTimeStamp());
        payParams.put("nonceStr", prepay.getNonceStr());
        payParams.put("package", prepay.getPackageVal());
        payParams.put("signType", prepay.getSignType());
        payParams.put("paySign", prepay.getPaySign());

        log.info("[微信支付] 下单成功：orderNo={} amount={} member={} mch={}",
                order.getOrderNo(), payAmount, memberId, wechatPayProperties.getMchId());
        return PayResultVo.wechat(order.getOrderNo(), payParams);
    }
}
