package com.ruoyi.userapi.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.merchant.config.TakeoutCacheKeys;
import com.ruoyi.merchant.domain.BizOrder;
import com.ruoyi.merchant.domain.BizOrderRefund;
import com.ruoyi.merchant.mapper.BizOrderMapper;
import com.ruoyi.merchant.mapper.BizOrderRefundMapper;
import com.ruoyi.userapi.domain.vo.RefundVo;
import com.ruoyi.userapi.service.ApiRefundService;
import com.ruoyi.userapi.util.FileUrlBuilder;

/**
 * 小程序端退款服务实现（方案 3.3）。
 * 申请流程：退款防重锁 -> 状态链校验（归属/主状态 1-3/无进行中退款）->
 * 事务内：生成退款单（refund_amount=实付含配送费快照）+ 订单 refund_status 0->1。
 *
 * @author 阿婆干饭社
 */
@Service
public class ApiRefundServiceImpl implements ApiRefundService
{
    private static final Logger log = LoggerFactory.getLogger(ApiRefundServiceImpl.class);

    /** 订单主状态字典 */
    private static final int STATUS_UNPAID = 0;
    private static final int STATUS_CANCELLED = 6;
    /** 退款状态：0无 1审核中 2已退款终态 */
    private static final int REFUND_STATUS_NONE = 0;
    private static final int REFUND_STATUS_AUDITING = 1;

    private static final int MAX_EVIDENCE_COUNT = 5;

    @Autowired
    private BizOrderMapper orderMapper;

    @Autowired
    private BizOrderRefundMapper refundMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private FileUrlBuilder fileUrlBuilder;

    @Override
    public void applyRefund(Long memberId, Long orderId, String reason, List<String> evidenceImages)
    {
        // 0. 参数校验（不占防重锁，失败可立即重试）
        if (orderId == null)
        {
            throw new ServiceException("订单ID不能为空");
        }
        if (StringUtils.isEmpty(reason))
        {
            throw new ServiceException("请填写退款原因");
        }
        if (reason.length() > 200)
        {
            throw new ServiceException("退款原因不能超过 200 字");
        }
        if (evidenceImages != null && evidenceImages.size() > MAX_EVIDENCE_COUNT)
        {
            throw new ServiceException("图片凭证最多 " + MAX_EVIDENCE_COUNT + " 张");
        }
        // 1. 退款防重锁：takeout:refund:lock:{memberId}:{orderId}（setnx + 5 秒，附录 B 决策 17）
        String lockKey = TakeoutCacheKeys.REFUND_LOCK_KEY + memberId + ":" + orderId;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(5));
        if (!Boolean.TRUE.equals(locked))
        {
            throw new ServiceException("操作太快啦，请稍候几秒再提交");
        }
        try
        {
            // 2. 订单归属校验
            BizOrder order = orderMapper.selectById(orderId);
            if (order == null || !order.getMemberId().equals(memberId))
            {
                throw new ServiceException("订单不存在");
            }
            // 3. 主状态 1-3 才可自助申请（配送中 4 联系商家协商；0/5/6 均不可）
            Integer status = order.getStatus();
            if (status == null || status < 1 || status > 3)
            {
                if (status == STATUS_UNPAID)
                {
                    throw new ServiceException("订单未支付，无需退款");
                }
                if (status == STATUS_CANCELLED)
                {
                    throw new ServiceException("订单已取消，无法申请退款");
                }
                throw new ServiceException("订单当前状态不可申请退款");
            }
            // 4. 无进行中退款（refund_status=0；1=审核中勿重复申请；2=已退款终态）
            Integer refundStatus = order.getRefundStatus();
            if (refundStatus != null && refundStatus == 1)
            {
                throw new ServiceException("退款审核中，请勿重复申请");
            }
            if (refundStatus != null && refundStatus == 2)
            {
                throw new ServiceException("订单已退款，不可再次申请");
            }

            // 5. 事务：生成退款单 + 订单 refund_status 0->1（条件更新，行数=0 说明并发状态变化）
            Date now = DateUtils.getNowDate();
            BigDecimal refundAmount = order.getPayAmount();
            transactionTemplate.executeWithoutResult(tx -> {
                BizOrderRefund refund = new BizOrderRefund();
                refund.setOrderNo(order.getOrderNo());
                refund.setMemberId(memberId);
                refund.setRefundAmount(refundAmount);
                refund.setReason(reason.trim());
                refund.setEvidenceImages(evidenceImages == null || evidenceImages.isEmpty()
                        ? null : JSON.toJSONString(evidenceImages));
                refund.setAuditStatus(0);
                refund.setIsAutoReject(0);
                refund.setIsMockRefund(1);
                refund.setCreateTime(now);
                refund.setUpdateTime(now);
                refundMapper.insert(refund);

                int rows = orderMapper.update(null, new LambdaUpdateWrapper<BizOrder>()
                        .eq(BizOrder::getId, orderId)
                        .eq(BizOrder::getRefundStatus, REFUND_STATUS_NONE)
                        .set(BizOrder::getRefundStatus, REFUND_STATUS_AUDITING)
                        .set(BizOrder::getUpdateTime, now));
                if (rows == 0)
                {
                    // 抛出触发回滚（退款单一并回滚）
                    throw new ServiceException("订单状态已变化，请刷新后重试");
                }
            });
            // 主状态不变、流转暂停（商家端流转条件已含 refund_status=0 拦截）
            log.info("[申请退款] 退款单生成：orderNo={} member={} amount={}",
                    order.getOrderNo(), memberId, refundAmount);
        }
        catch (RuntimeException e)
        {
            // 业务失败立即释放防重锁（成功保留至自然过期，防连点重复提交）
            stringRedisTemplate.delete(lockKey);
            throw e;
        }
    }

    @Override
    public List<RefundVo> listByOrder(Long memberId, Long orderId)
    {
        BizOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getMemberId().equals(memberId))
        {
            throw new ServiceException("订单不存在");
        }
        List<BizOrderRefund> refunds = refundMapper.selectList(new LambdaQueryWrapper<BizOrderRefund>()
                .eq(BizOrderRefund::getOrderNo, order.getOrderNo())
                .orderByDesc(BizOrderRefund::getId));
        return refunds.stream().map(r -> {
            RefundVo vo = new RefundVo();
            vo.setId(r.getId());
            vo.setOrderNo(r.getOrderNo());
            vo.setRefundAmount(r.getRefundAmount());
            vo.setReason(r.getReason());
            try
            {
                List<String> images = StringUtils.isEmpty(r.getEvidenceImages())
                        ? List.of() : JSON.parseArray(r.getEvidenceImages(), String.class);
                vo.setEvidenceImages(images.stream().map(fileUrlBuilder::build).collect(Collectors.toList()));
            }
            catch (Exception e)
            {
                vo.setEvidenceImages(List.of());
            }
            vo.setAuditStatus(r.getAuditStatus());
            vo.setRejectReason(r.getRejectReason());
            vo.setIsAutoReject(r.getIsAutoReject());
            vo.setRefundTime(r.getRefundTime());
            vo.setCreateTime(r.getCreateTime());
            return vo;
        }).collect(java.util.stream.Collectors.toList());
    }
}
