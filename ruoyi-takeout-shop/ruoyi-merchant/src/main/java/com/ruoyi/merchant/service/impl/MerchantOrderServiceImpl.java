package com.ruoyi.merchant.service.impl;

import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.merchant.domain.BizOrder;
import com.ruoyi.merchant.domain.BizOrderItem;
import com.ruoyi.merchant.mapper.BizOrderItemMapper;
import com.ruoyi.merchant.mapper.BizOrderMapper;
import com.ruoyi.merchant.mq.OrderMqProducer;
import com.ruoyi.merchant.service.MerchantOrderService;

/**
 * 管理端订单服务实现。
 * 状态流转编码依据：方案 3.4 状态流转操作表——一律条件更新（where 前置状态 + refund_status=0）
 * + 影响行数校验，重复点击幂等；退款审核中（refund_status=1）主状态冻结、流转暂停。
 *
 * @author 阿婆干饭社
 */
@Service
public class MerchantOrderServiceImpl implements MerchantOrderService
{
    private static final Logger log = LoggerFactory.getLogger(MerchantOrderServiceImpl.class);

    /** 主状态字典（与 SQL 注释/两端展示一致） */
    public static final int STATUS_UNPAID = 0;
    public static final int STATUS_WAIT_ACCEPT = 1;
    public static final int STATUS_MAKING = 2;
    public static final int STATUS_READY = 3;
    public static final int STATUS_DELIVERING = 4;
    public static final int STATUS_FINISHED = 5;
    public static final int STATUS_CANCELLED = 6;

    private static final int TYPE_DINE = 1;
    private static final int TYPE_TAKEOUT = 2;

    @Autowired
    private BizOrderMapper orderMapper;

    @Autowired
    private BizOrderItemMapper orderItemMapper;

    /** MQ 生产者：rocketmq.enabled=false 时不注入（降级运行） */
    @Autowired(required = false)
    private OrderMqProducer orderMqProducer;

    @Override
    public IPage<BizOrder> listOrders(String orderNo, Integer status, Integer deliveryType, long pageNum, long pageSize)
    {
        LambdaQueryWrapper<BizOrder> wrapper = new LambdaQueryWrapper<BizOrder>()
                .like(StringUtils.isNotEmpty(orderNo), BizOrder::getOrderNo, orderNo)
                .eq(status != null, BizOrder::getStatus, status)
                .eq(deliveryType != null, BizOrder::getDeliveryType, deliveryType)
                .orderByDesc(BizOrder::getId);
        return orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public OrderDetail getOrder(Long id)
    {
        BizOrder order = orderMapper.selectById(id);
        if (order == null)
        {
            throw new ServiceException("订单不存在");
        }
        OrderDetail detail = new OrderDetail();
        detail.setOrder(order);
        detail.setItems(orderItemMapper.selectList(new LambdaQueryWrapper<BizOrderItem>()
                .eq(BizOrderItem::getOrderId, id).orderByAsc(BizOrderItem::getId)));
        return detail;
    }

    @Override
    public void accept(Long id)
    {
        // T19：接单写 accept_time（平均接单耗时统计依据）
        transition(id, STATUS_WAIT_ACCEPT, STATUS_MAKING, null, "接单", "acceptTime");
    }

    @Override
    public void ready(Long id)
    {
        transition(id, STATUS_MAKING, STATUS_READY, null, "出餐", "readyTime");
    }

    @Override
    public void deliver(Long id)
    {
        // T18：外卖 3->4 语义明确为「骑手已取餐」，接口路径 /deliver 不变
        transition(id, STATUS_READY, STATUS_DELIVERING, TYPE_TAKEOUT, "骑手已取餐", "pickedUpTime");
    }

    @Override
    public void complete(Long id)
    {
        BizOrder order = orderMapper.selectById(id);
        if (order == null)
        {
            throw new ServiceException("订单不存在");
        }
        if (order.getDeliveryType() != null && order.getDeliveryType() == TYPE_TAKEOUT)
        {
            // 外卖：配送中(4) -> 已完成(5)，写送达时间
            transition(id, STATUS_DELIVERING, STATUS_FINISHED, TYPE_TAKEOUT, "确认送达", "deliveredTime");
        }
        else
        {
            // 堂食：已出餐待取餐(3) -> 已完成(5)
            transition(id, STATUS_READY, STATUS_FINISHED, TYPE_DINE, "确认完成", null);
        }
    }

    /**
     * 通用状态流转（条件更新 + 影响行数校验幂等）：
     * where id=? and status=from and refund_status=0（退款审核中主状态冻结、流转暂停）
     * [and delivery_type=?（可选约束）]；成功后发 order-status-changed 事件。
     * timeField（T18）：流转成功时同条件写入对应时间列（readyTime/pickedUpTime/deliveredTime），
     * 处于同一条件更新内——流转失败不写、重复点击因 where 前置状态不命中而不会覆盖时间。
     */
    private void transition(Long id, int from, int to, Integer requireDeliveryType, String actionName, String timeField)
    {
        BizOrder target = orderMapper.selectById(id);
        if (target == null)
        {
            throw new ServiceException("订单不存在");
        }
        LambdaUpdateWrapper<BizOrder> wrapper = new LambdaUpdateWrapper<BizOrder>()
                .eq(BizOrder::getId, id)
                .eq(BizOrder::getStatus, from)
                .eq(BizOrder::getRefundStatus, 0);
        if (requireDeliveryType != null)
        {
            wrapper.eq(BizOrder::getDeliveryType, requireDeliveryType);
        }
        wrapper.set(BizOrder::getStatus, to)
                .set(BizOrder::getUpdateTime, DateUtils.getNowDate());
        if ("acceptTime".equals(timeField))
        {
            wrapper.set(BizOrder::getAcceptTime, DateUtils.getNowDate());
        }
        else if ("readyTime".equals(timeField))
        {
            wrapper.set(BizOrder::getReadyTime, DateUtils.getNowDate());
        }
        else if ("pickedUpTime".equals(timeField))
        {
            wrapper.set(BizOrder::getPickedUpTime, DateUtils.getNowDate());
        }
        else if ("deliveredTime".equals(timeField))
        {
            wrapper.set(BizOrder::getDeliveredTime, DateUtils.getNowDate());
        }
        int rows = orderMapper.update(null, wrapper);
        if (rows == 0)
        {
            // 幂等/非法流转的精确提示
            if (target.getRefundStatus() != null && target.getRefundStatus() == 1)
            {
                throw new ServiceException("订单退款审核中，流转已暂停");
            }
            if (target.getRefundStatus() != null && target.getRefundStatus() == 2)
            {
                throw new ServiceException("订单已退款（终态），禁止流转");
            }
            throw new ServiceException("操作失败：订单当前为【" + statusText(target.getStatus()) + "】，无法" + actionName);
        }
        log.info("[订单流转] {}：orderNo={} {} -> {}", actionName, target.getOrderNo(), from, to);
        // 每次流转发送状态变更消息（MQ 关闭时降级跳过）
        if (orderMqProducer != null)
        {
            orderMqProducer.sendOrderStatusChanged(target.getOrderNo(), from, to);
        }
    }

    /** 主状态文案（与三端字典一致） */
    private String statusText(Integer status)
    {
        if (status == null)
        {
            return "未知";
        }
        switch (status)
        {
            case STATUS_UNPAID: return "待支付";
            case STATUS_WAIT_ACCEPT: return "待接单";
            case STATUS_MAKING: return "已接单制作中";
            case STATUS_READY: return "已出餐待取餐";
            case STATUS_DELIVERING: return "配送中";
            case STATUS_FINISHED: return "已完成";
            case STATUS_CANCELLED: return "已取消";
            default: return "未知(" + status + ")";
        }
    }
}
