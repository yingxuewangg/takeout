package com.ruoyi.merchant.service.impl;

import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.merchant.domain.BizOrderRefund;
import com.ruoyi.merchant.mapper.BizOrderRefundMapper;
import com.ruoyi.merchant.service.WechatRefundService;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import com.wechat.pay.java.service.refund.model.Status;

/**
 * 微信退款结果落库服务实现（三期 T25）。
 * 按"重复到达零副作用"设计：写回条件带 audit_status=1 且 refund_id 为空，重复通知/重复查单幂等。
 *
 * @author 阿婆干饭社
 */
@Service
public class WechatRefundServiceImpl implements WechatRefundService
{
    private static final Logger log = LoggerFactory.getLogger(WechatRefundServiceImpl.class);

    /** 退款审核状态：1已通过（受理后等待微信到账回调） */
    private static final int AUDIT_APPROVED = 1;

    @Autowired
    private BizOrderRefundMapper refundMapper;

    @Override
    public void onRefundNotification(RefundNotification notification, String source)
    {
        String outRefundNo = notification.getOutRefundNo();
        Status refundStatus = notification.getRefundStatus();
        if (refundStatus != Status.SUCCESS)
        {
            if (refundStatus == Status.ABNORMAL)
            {
                // 退款异常（余额不足退回/渠道异常等）：需人工介入，不自动改状态
                log.error("[微信退款-{}] 退款异常，需人工处理：outRefundNo={} wxRefundId={}",
                        source, outRefundNo, notification.getRefundId());
            }
            else
            {
                log.info("[微信退款-{}] 非终态通知：outRefundNo={} refundStatus={}",
                        source, outRefundNo, refundStatus);
            }
            return;
        }
        Long refundId = parseRefundId(outRefundNo);
        if (refundId == null)
        {
            log.error("[微信退款-{}] 无法解析商户退款单号：outRefundNo={}", source, outRefundNo);
            return;
        }
        // 条件写回（where audit_status=1 且 refund_id 为空）：重复通知/查单兜底幂等
        int rows = refundMapper.update(null, new LambdaUpdateWrapper<BizOrderRefund>()
                .eq(BizOrderRefund::getId, refundId)
                .eq(BizOrderRefund::getAuditStatus, AUDIT_APPROVED)
                .isNull(BizOrderRefund::getRefundId)
                .set(BizOrderRefund::getRefundId, notification.getRefundId())
                .set(BizOrderRefund::getUpdateTime, new Date()));
        if (rows > 0)
        {
            log.info("[微信退款-{}] 退款到账，受理号写回：refundId={} outRefundNo={} wxRefundId={} successTime={}",
                    source, refundId, outRefundNo, notification.getRefundId(), notification.getSuccessTime());
        }
        else
        {
            log.info("[微信退款-{}] 退款通知幂等跳过（重复通知或状态不符）：outRefundNo={} wxRefundId={}",
                    source, outRefundNo, notification.getRefundId());
        }
    }

    /** 商户退款单号 = biz_order_refund.id（审核通过时以 String.valueOf(id) 提交微信） */
    private Long parseRefundId(String outRefundNo)
    {
        if (StringUtils.isEmpty(outRefundNo))
        {
            return null;
        }
        try
        {
            return Long.valueOf(outRefundNo);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
