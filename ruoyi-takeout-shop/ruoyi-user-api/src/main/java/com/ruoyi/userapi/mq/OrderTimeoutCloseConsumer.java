package com.ruoyi.userapi.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.merchant.domain.BizOrder;
import com.ruoyi.merchant.mapper.BizOrderMapper;
import com.ruoyi.merchant.service.StockService;

/**
 * 订单超时关闭消费者（RocketMQ 10 分钟延迟消息，方案 3.3/3.5）。
 * 消费逻辑（幂等）：仍为待支付(status=0)则关闭置 6，已支付/已关闭忽略——条件更新天然幂等，
 * 重复投递无副作用。rocketmq.enabled=false 时本消费者不注册，超时关单仅靠定时任务兜底。
 *
 * @author 阿婆干饭社
 */
@Component
@ConditionalOnProperty(name = "rocketmq.enabled", havingValue = "true")
@RocketMQMessageListener(topic = TakeoutMqProducer.TOPIC_ORDER_TIMEOUT_CLOSE, consumerGroup = "takeout-timeout-close-consumer")
public class OrderTimeoutCloseConsumer implements RocketMQListener<String>
{
    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutCloseConsumer.class);

    /** 订单主状态：0待支付 6已取消 */
    private static final int STATUS_UNPAID = 0;
    private static final int STATUS_CANCELLED = 6;

    @Autowired
    private BizOrderMapper orderMapper;

    /** 每日限量库存服务（T12）：超时关单后释放库存（以订单号幂等） */
    @Autowired
    private StockService stockService;

    @Override
    public void onMessage(String orderNo)
    {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<BizOrder>()
                .eq(BizOrder::getOrderNo, orderNo)
                .eq(BizOrder::getStatus, STATUS_UNPAID)
                .set(BizOrder::getStatus, STATUS_CANCELLED)
                .set(BizOrder::getUpdateTime, DateUtils.getNowDate()));
        if (rows > 0)
        {
            // T12：关单成功才释放库存（释放内部以 stock_released 幂等，重复投递无副作用）
            stockService.releaseByOrderNo(orderNo);
            log.info("[MQ] 延迟消息超时关单成功：orderNo={}（0 -> 6，已释放库存）", orderNo);
        }
        else
        {
            // 已支付/已取消等场景：幂等忽略
            log.info("[MQ] 延迟消息关单跳过（订单已非待支付）：orderNo={}", orderNo);
        }
    }
}
