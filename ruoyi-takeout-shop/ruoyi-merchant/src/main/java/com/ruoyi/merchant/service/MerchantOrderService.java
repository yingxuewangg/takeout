package com.ruoyi.merchant.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.merchant.domain.BizOrder;
import com.ruoyi.merchant.domain.BizOrderItem;

/**
 * 管理端订单服务接口（订单流转严格按方案 3.4 状态流转操作表执行，条件更新+影响行数校验幂等）
 *
 * @author 阿婆干饭社
 */
public interface MerchantOrderService
{
    /**
     * 订单分页列表（订单号/状态/履约方式筛选）
     */
    IPage<BizOrder> listOrders(String orderNo, Integer status, Integer deliveryType, long pageNum, long pageSize);

    /**
     * 订单详情（含明细快照）
     */
    OrderDetail getOrder(Long id);

    /**
     * 接单（1 待接单 -> 2 已接单制作中；refund_status=0 才可流转）
     */
    void accept(Long id);

    /**
     * 出餐（2 -> 3 已出餐待取餐）
     */
    void ready(Long id);

    /**
     * 开始配送（3 -> 4 配送中，仅外卖）
     */
    void deliver(Long id);

    /**
     * 确认完成：堂食 3 -> 5；外卖 4 -> 5
     */
    void complete(Long id);

    /** 管理端订单详情结构（订单 + 明细） */
    class OrderDetail
    {
        private BizOrder order;

        private java.util.List<BizOrderItem> items;

        public BizOrder getOrder() { return order; }
        public void setOrder(BizOrder order) { this.order = order; }

        public java.util.List<BizOrderItem> getItems() { return items; }
        public void setItems(java.util.List<BizOrderItem> items) { this.items = items; }
    }
}
