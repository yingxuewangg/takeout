/**
 * RocketMQ 消费者包：order-timeout-close（10 分钟延迟消息超时关单，条件更新天然幂等）、
 * order-paid-success（一期消费仅记日志）。消费端一律幂等；
 * rocketmq.enabled=false 时本包不消费，超时关单仅靠定时任务兜底。
 */
package com.ruoyi.userapi.mq;
