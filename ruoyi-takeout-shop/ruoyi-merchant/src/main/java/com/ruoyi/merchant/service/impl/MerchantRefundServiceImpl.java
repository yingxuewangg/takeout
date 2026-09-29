package com.ruoyi.merchant.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.merchant.domain.BizOrder;
import com.ruoyi.merchant.domain.BizOrderRefund;
import com.ruoyi.merchant.mapper.BizOrderMapper;
import com.ruoyi.merchant.mapper.BizOrderRefundMapper;
import com.ruoyi.merchant.service.MerchantRefundService;
import com.ruoyi.merchant.service.StockService;

/**
 * 管理端退款审核服务实现。
 * 通过（模拟退款成功）：退款单 0->1 + 订单 refund_status 1->2（终态）——两步同事务条件更新，
 * 行数不符即抛错回滚，防数据不一致；驳回：退款单 0->2 + 订单 1->0（主状态不变、流转恢复、可再次申请）。
 *
 * @author 阿婆干饭社
 */
@Service
public class MerchantRefundServiceImpl implements MerchantRefundService
{
    private static final Logger log = LoggerFactory.getLogger(MerchantRefundServiceImpl.class);

    /** 退款审核状态：0待审核 1已通过 2已驳回；订单退款状态：0无 1审核中 2已退款终态 */
    private static final int AUDIT_PENDING = 0;
    private static final int AUDIT_APPROVED = 1;
    private static final int AUDIT_REJECTED = 2;
    private static final int REFUND_STATUS_NONE = 0;
    private static final int REFUND_STATUS_AUDITING = 1;
    private static final int REFUND_STATUS_REFUNDED = 2;

    /** 图片 URL 前缀（与 user-api FileUrlBuilder 同一配置，同规则拼接） */
    @Value("${takeout.file.base-url:}")
    private String baseUrl;

    @Autowired
    private BizOrderRefundMapper refundMapper;

    @Autowired
    private BizOrderMapper orderMapper;

    /** 每日限量库存服务（T12）：退款成功（refund_status -> 2）后释放库存 */
    @Autowired
    private StockService stockService;

    /** 完整 URL = takeout.file.base-url + 相对路径（库内只存相对路径） */
    private String buildUrl(String relativePath)
    {
        if (StringUtils.isEmpty(relativePath))
        {
            return "";
        }
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://"))
        {
            return relativePath;
        }
        String prefix = baseUrl.endsWith("/") && baseUrl.length() > 0
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        return prefix + path;
    }

    @Override
    public IPage<RefundManageVo> listRefunds(Integer auditStatus, String orderNo, long pageNum, long pageSize)
    {
        LambdaQueryWrapper<BizOrderRefund> wrapper = new LambdaQueryWrapper<BizOrderRefund>()
                .eq(auditStatus != null, BizOrderRefund::getAuditStatus, auditStatus)
                .like(StringUtils.isNotEmpty(orderNo), BizOrderRefund::getOrderNo, orderNo)
                .orderByDesc(BizOrderRefund::getId);
        IPage<BizOrderRefund> page = refundMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<BizOrderRefund> refunds = page.getRecords();

        Map<String, BizOrder> orderByNo = refunds.isEmpty() ? Map.of()
                : orderMapper.selectList(new LambdaQueryWrapper<BizOrder>()
                        .in(BizOrder::getOrderNo, refunds.stream().map(BizOrderRefund::getOrderNo).collect(Collectors.toList())))
                        .stream().collect(Collectors.toMap(BizOrder::getOrderNo, Function.identity(), (a, b) -> a));

        List<RefundManageVo> vos = new ArrayList<>();
        for (BizOrderRefund refund : refunds)
        {
            vos.add(toVo(refund, orderByNo.get(refund.getOrderNo())));
        }
        Page<RefundManageVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(vos);
        return result;
    }

    @Override
    public long pendingCount()
    {
        return refundMapper.selectCount(new LambdaQueryWrapper<BizOrderRefund>()
                .eq(BizOrderRefund::getAuditStatus, AUDIT_PENDING));
    }

    @Override
    public RefundManageVo getRefund(Long id)
    {
        BizOrderRefund refund = refundMapper.selectById(id);
        if (refund == null)
        {
            throw new ServiceException("退款单不存在");
        }
        BizOrder order = orderMapper.selectOne(new LambdaQueryWrapper<BizOrder>()
                .eq(BizOrder::getOrderNo, refund.getOrderNo()).last("limit 1"));
        return toVo(refund, order);
    }

    @Override
    @Transactional
    public void approve(Long id)
    {
        Date now = DateUtils.getNowDate();
        // 退款单条件更新（where audit_status=0）：重复点击幂等
        int rows = refundMapper.update(null, new LambdaUpdateWrapper<BizOrderRefund>()
                .eq(BizOrderRefund::getId, id)
                .eq(BizOrderRefund::getAuditStatus, AUDIT_PENDING)
                .set(BizOrderRefund::getAuditStatus, AUDIT_APPROVED)
                .set(BizOrderRefund::getIsMockRefund, 1)
                .set(BizOrderRefund::getRefundTime, now)
                .set(BizOrderRefund::getUpdateTime, now));
        if (rows == 0)
        {
            throw new ServiceException("该退款单已处理，请勿重复操作");
        }
        BizOrderRefund refund = refundMapper.selectById(id);
        // 订单 refund_status 1->2（终态）；行数=0 属数据不一致，抛错回滚整个事务
        int orderRows = orderMapper.update(null, new LambdaUpdateWrapper<BizOrder>()
                .eq(BizOrder::getOrderNo, refund.getOrderNo())
                .eq(BizOrder::getRefundStatus, REFUND_STATUS_AUDITING)
                .set(BizOrder::getRefundStatus, REFUND_STATUS_REFUNDED)
                .set(BizOrder::getUpdateTime, now));
        if (orderRows == 0)
        {
            throw new ServiceException("订单退款状态异常，审核未生效");
        }
        // T12：退款成功（refund_status=2 终态）后释放每日限量库存（以订单号幂等，只释放一次）
        stockService.releaseByOrderNo(refund.getOrderNo());
        log.info("[退款审核] 通过（模拟退款成功）：refundId={} orderNo={} amount={}",
                id, refund.getOrderNo(), refund.getRefundAmount());
    }

    @Override
    @Transactional
    public void reject(Long id, String reason)
    {
        if (StringUtils.isEmpty(reason))
        {
            throw new ServiceException("请填写驳回理由");
        }
        Date now = DateUtils.getNowDate();
        int rows = refundMapper.update(null, new LambdaUpdateWrapper<BizOrderRefund>()
                .eq(BizOrderRefund::getId, id)
                .eq(BizOrderRefund::getAuditStatus, AUDIT_PENDING)
                .set(BizOrderRefund::getAuditStatus, AUDIT_REJECTED)
                .set(BizOrderRefund::getRejectReason, reason.trim())
                .set(BizOrderRefund::getIsAutoReject, 0)
                .set(BizOrderRefund::getUpdateTime, now));
        if (rows == 0)
        {
            throw new ServiceException("该退款单已处理，请勿重复操作");
        }
        BizOrderRefund refund = refundMapper.selectById(id);
        // 订单 refund_status 1->0：主状态不变、流转恢复、用户可再次申请
        int orderRows = orderMapper.update(null, new LambdaUpdateWrapper<BizOrder>()
                .eq(BizOrder::getOrderNo, refund.getOrderNo())
                .eq(BizOrder::getRefundStatus, REFUND_STATUS_AUDITING)
                .set(BizOrder::getRefundStatus, REFUND_STATUS_NONE)
                .set(BizOrder::getUpdateTime, now));
        if (orderRows == 0)
        {
            throw new ServiceException("订单退款状态异常，驳回未生效");
        }
        log.info("[退款审核] 驳回：refundId={} orderNo={} reason={}", id, refund.getOrderNo(), reason.trim());
    }

    /** 退款单 -> 管理端 VO（订单关联信息 + 凭证 URL 拼接） */
    private RefundManageVo toVo(BizOrderRefund refund, BizOrder order)
    {
        RefundManageVo vo = new RefundManageVo();
        vo.setId(refund.getId());
        vo.setOrderNo(refund.getOrderNo());
        vo.setRefundAmount(refund.getRefundAmount());
        vo.setReason(refund.getReason());
        try
        {
            List<String> images = StringUtils.isEmpty(refund.getEvidenceImages())
                    ? List.of() : JSON.parseArray(refund.getEvidenceImages(), String.class);
            vo.setEvidenceImages(images.stream().map(this::buildUrl).collect(Collectors.toList()));
        }
        catch (Exception e)
        {
            vo.setEvidenceImages(List.of());
        }
        vo.setAuditStatus(refund.getAuditStatus());
        vo.setRejectReason(refund.getRejectReason());
        vo.setIsAutoReject(refund.getIsAutoReject());
        vo.setRefundTime(refund.getRefundTime());
        vo.setCreateTime(refund.getCreateTime());
        if (order != null)
        {
            vo.setOrderId(order.getId());
            vo.setDeliveryType(order.getDeliveryType());
            vo.setOrderStatus(order.getStatus());
            vo.setContactPhone(order.getContactPhone());
            vo.setContactName(order.getContactName());
        }
        return vo;
    }
}
