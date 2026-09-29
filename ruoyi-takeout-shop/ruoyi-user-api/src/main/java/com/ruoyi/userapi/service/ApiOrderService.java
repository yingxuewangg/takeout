package com.ruoyi.userapi.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.userapi.domain.vo.OrderCreateVo;
import com.ruoyi.userapi.domain.vo.OrderDetailVo;
import com.ruoyi.userapi.domain.vo.OrderListVo;
import com.ruoyi.userapi.domain.vo.RepurchaseResultVo;

/**
 * 小程序端订单服务接口
 *
 * @author 阿婆干饭社
 */
public interface ApiOrderService
{
    /**
     * 从购物车创建订单（整单提交）：
     * Redis 防重锁（takeout:order:lock:{memberId}，5 秒）+ 打烊/失效项/起送价/桌号地址校验 +
     * 配送费快照 + 地址快照 + 明细快照 + 超时关闭时间（10 分钟）；
     * 事务提交后发 RocketMQ 10 分钟延迟消息（rocketmq.enabled=false 时降级仅靠定时任务）
     */
    OrderCreateVo createOrder(Long memberId, OrderCreateBody body);

    /**
     * 订单详情（归属校验；收银台/支付成功页/订单详情页展示）
     */
    OrderDetailVo getOrderDetail(Long memberId, Long orderId);

    /**
     * 用户订单列表（分页，按下单时间倒序；status 可选筛选；退款状态随单下发供"退款优先"展示）
     */
    IPage<OrderListVo> listOrders(Long memberId, Integer status, long pageNum, long pageSize);

    /**
     * 取消订单（仅待支付：条件更新 where status=0，幂等；取消后超时关单消息触达时自然跳过）
     */
    void cancelOrder(Long memberId, Long orderId);

    /**
     * 再次购买（T13）：把该订单明细（菜品+规格+口味+数量）逐项加入购物车。
     * 校验链：下架 → 菜品级售罄 → 规格是否仍存在 → 规格级售罄 → 口味按名称比对（缺失剔除）
     * → 每日限量库存（不足则按剩余量加购，剩余 0 跳过）。
     * 可再次购买的展示条件：主状态 ∈ {1,2,3,4,5} 且 refund_status ≠ 2。
     * 只读订单明细 + 复用既有加购（含防超卖二次校验），不改动订单/退款/支付逻辑。
     *
     * @return 成功加购条目 + 跳过条目及原因
     */
    RepurchaseResultVo repurchase(Long memberId, Long orderId);

    /** 下单请求体 */
    class OrderCreateBody
    {
        /** 履约方式（1堂食 2外卖） */
        private Integer deliveryType;

        /** 桌号（堂食必填） */
        private String tableNo;

        /** 收货地址ID（外卖必填） */
        private Long addressId;

        /** 联系人（可空；外卖默认取地址联系人） */
        private String contactName;

        /** 联系电话（可空；外卖默认取地址电话） */
        private String contactPhone;

        /** 订单备注（可空） */
        private String remark;

        public Integer getDeliveryType() { return deliveryType; }
        public void setDeliveryType(Integer deliveryType) { this.deliveryType = deliveryType; }

        public String getTableNo() { return tableNo; }
        public void setTableNo(String tableNo) { this.tableNo = tableNo; }

        public Long getAddressId() { return addressId; }
        public void setAddressId(Long addressId) { this.addressId = addressId; }

        public String getContactName() { return contactName; }
        public void setContactName(String contactName) { this.contactName = contactName; }

        public String getContactPhone() { return contactPhone; }
        public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }
}
