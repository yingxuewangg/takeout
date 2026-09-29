/**
 * RocketMQ 生产者与消息体包：order-status-changed（订单状态变更事件，一期消费仅记日志）。
 * 发送失败不阻塞主流程；rocketmq.enabled=false 时整体降级（消息不发，超时关单靠定时任务兜底）。
 */
package com.ruoyi.merchant.mq;
