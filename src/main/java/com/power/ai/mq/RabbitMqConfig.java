package com.power.ai.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明（0-4 工单消息流）。
 *
 * 消息流向图：
 *   createOrder ──┬─> order.exchange --order.notify--> order.notify.queue   通知电站负责人
 *                 └─> order.exchange --order.delay--> order.delay.queue     延时队列（TTL 到期变死信）
 *                                                            │ (x-dead-letter-exchange)
 *                                                            v
 *                    order.dlx.exchange --order.timeout--> order.timeout.queue   超时升级消费者监听
 *
 * 为什么用 "TTL + 死信交换机" 而不是 rabbitmq_delayed_message_exchange 插件：
 *   TTL+DLX 是官方原生机制、零额外安装，且是面试高频考点；延迟插件要额外安装且有可靠性坑。
 *
 * 为什么 TTL 放在消息上而不是队列上：
 *   队列级 TTL 写死在队列声明里，改 TTL 必须删队列重建（队列持久化，生产上删队列是事故）；
 *   消息级 TTL 由生产者发送时逐条设置，改配置即可生效，不同消息还可以不同 TTL。
 */
@Slf4j
@Configuration
public class RabbitMqConfig {

    /** 拓扑命名常量集中放这里：生产者、消费者两边都引用，避免字符串写错对不上 */
    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String NOTIFY_QUEUE = "order.notify.queue";
    public static final String NOTIFY_KEY = "order.notify";
    public static final String DELAY_QUEUE = "order.delay.queue";
    public static final String DELAY_KEY = "order.delay";
    public static final String ORDER_DLX = "order.dlx.exchange";
    public static final String TIMEOUT_QUEUE = "order.timeout.queue";
    public static final String TIMEOUT_KEY = "order.timeout";

    /** 业务交换机：通知队列、延时队列都绑在它上面 */
    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    /** 死信交换机：延时队列里的消息过期后，由 broker 自动转发到这里 */
    @Bean
    public DirectExchange orderDlx() {
        return new DirectExchange(ORDER_DLX, true, false);
    }

    /** 通知队列：创建工单 → 通知电站负责人（消费者打日志模拟发送） */
    @Bean
    public Queue notifyQueue() {
        return QueueBuilder.durable(NOTIFY_QUEUE).build();
    }

    /**
     * 延时队列：只声明死信去向，不声明 TTL——TTL 由生产者在每条消息上设置（见 OrderMqProducer）。
     * 消息过期后按 deadLetterRoutingKey 转发到死信交换机，再由它路由到超时队列。
     */
    @Bean
    public Queue delayQueue() {
        return QueueBuilder.durable(DELAY_QUEUE)
                .deadLetterExchange(ORDER_DLX)
                .deadLetterRoutingKey(TIMEOUT_KEY)
                .build();
    }

    /** 超时队列（死信的落点）：超时升级消费者监听它 */
    @Bean
    public Queue timeoutQueue() {
        return QueueBuilder.durable(TIMEOUT_QUEUE).build();
    }

    @Bean
    public Binding notifyBinding() {
        return BindingBuilder.bind(notifyQueue()).to(orderExchange()).with(NOTIFY_KEY);
    }

    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(delayQueue()).to(orderExchange()).with(DELAY_KEY);
    }

    @Bean
    public Binding timeoutBinding() {
        return BindingBuilder.bind(timeoutQueue()).to(orderDlx()).with(TIMEOUT_KEY);
    }

    /**
     * 消息转换器：Spring 默认的 SimpleMessageConverter 用 Java 序列化（二进制、实体类要
     * implements Serializable、管理台里看不懂）。换成 Jackson JSON 后，管理台可以直接
     * 看到可读的消息内容，排查问题方便得多。
     */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 可靠性第一件：生产者 confirm + return。
     * - confirm：broker 收到消息后异步回 ack/nack，nack 说明投递失败；
     * - return：消息到了交换机但没有队列能路由（routing key 写错），mandatory=true 时
     *   退回触发 ReturnsCallback，否则消息会被 broker 静默丢弃。
     * 注：Spring Boot 自动配置的 RabbitTemplate 遇到我们自定义的 Bean 会退让，此配置全局生效。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            String id = correlationData == null ? "unknown" : correlationData.getId();
            if (ack) {
                log.info("MQ消息已确认: {}", id);
            } else {
                log.error("MQ消息发送失败(broker nack): id={}, cause={}", id, cause);
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "MQ消息不可路由: exchange={}, routingKey={}, replyText={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return template;
    }
}
