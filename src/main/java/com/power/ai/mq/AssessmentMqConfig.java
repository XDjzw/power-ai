package com.power.ai.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 月度考核告警 MQ 拓扑（0-5）。
 *
 * 消息流：考核跑批发现新欠进度 → assessment.alert.exchange
 *        --assessment.alert--> assessment.alert.queue → 运营收到告警（消费者打日志模拟）
 *
 * 为什么不复用 0-4 的 order.exchange，而是新开交换机：
 * 1. 业务域不同：工单通知是"电站负责人"，考核告警是"运营"，消费方完全不同；
 * 2. 流量隔离：考核跑批只在凌晨 1 点有量，工单通知全天都有，
 *    共用交换机时任一方的消费堆积会互相拖累（同交换机不同队列互不影响投递，但排查日志、权限、限流会混在一起）；
 * 3. 演进方向：生产上告警可能要接短信网关/监控系统，独立拓扑改起来不动工单链路。
 *
 * 生产者 confirm / JSON 转换器 / 手动 ack 已在 RabbitMqConfig 与 application.yml 全局配置，
 * 这里只声明拓扑即可。
 */
@Configuration
public class AssessmentMqConfig {

    /** 拓扑命名常量集中放这里：生产者、消费者两边都引用，避免字符串写错对不上 */
    public static final String ASSESSMENT_ALERT_EXCHANGE = "assessment.alert.exchange";
    public static final String ALERT_QUEUE = "assessment.alert.queue";
    public static final String ALERT_KEY = "assessment.alert";

    @Bean
    public DirectExchange assessmentAlertExchange() {
        return new DirectExchange(ASSESSMENT_ALERT_EXCHANGE, true, false);
    }

    @Bean
    public Queue assessmentAlertQueue() {
        return QueueBuilder.durable(ALERT_QUEUE).build();
    }

    @Bean
    public Binding assessmentAlertBinding() {
        return BindingBuilder.bind(assessmentAlertQueue()).to(assessmentAlertExchange()).with(ALERT_KEY);
    }
}
