package com.power.ai.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 工单消息生产者（confirm/return 回调已在 RabbitMqConfig 里全局配置）。
 *
 * 为什么拆出独立的生产者类，而不是在 OrderServiceImpl 里直接调 RabbitTemplate：
 * 1. 消息组包（哪些字段进消息体）和发送细节（TTL、CorrelationData）与业务代码解耦，
 *    0-5 考核告警还要发消息，可以照这个模式再来一个
 * 2. 每条消息带 CorrelationData 作为发送标识，confirm 日志里能对上"哪条消息被确认了"
 */
@Component
@RequiredArgsConstructor
public class OrderMqProducer {

    private final RabbitTemplate rabbitTemplate;

    /** 工单超时时长(毫秒)。dev 环境配短值方便演示，生产环境 7200000 = 2 小时 */
    @Value("${app.mq.order-ttl:7200000}")
    private long orderTtl;

    /** 发送"工单已创建"通知 → 通知队列（消费者负责通知电站负责人） */
    public void sendNotify(OrderMessage message) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.ORDER_EXCHANGE, RabbitMqConfig.NOTIFY_KEY, message,
                new CorrelationData("notify:" + message.getOrderNo()));
    }

    /** 发送延时消息 → 延时队列。TTL 到期后 broker 自动把它变成死信，路由到超时队列 */
    public void sendTimeoutDelay(OrderMessage message) {
        // 消息级 TTL：在消息属性上设置 expiration，比队列级 TTL 灵活（改 TTL 不用删队列）
        MessagePostProcessor setTtl = m -> {
            m.getMessageProperties().setExpiration(String.valueOf(orderTtl));
            return m;
        };
        rabbitTemplate.convertAndSend(RabbitMqConfig.ORDER_EXCHANGE, RabbitMqConfig.DELAY_KEY, message, setTtl,
                new CorrelationData("timeout:" + message.getOrderNo()));
    }
}
