package com.power.ai.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 考核告警消息生产者（照 OrderMqProducer 的模式）。
 *
 * CorrelationData 作为每条消息的发送标识：confirm 回调日志里
 * （RabbitMqConfig 全局配置）能对上"哪条告警被 broker 确认了"。
 */
@Component
@RequiredArgsConstructor
public class AssessmentAlertProducer {

    private final RabbitTemplate rabbitTemplate;

    /** 发送"欠进度"告警 → 运营（消费者负责打日志模拟通知） */
    public void sendAlert(AssessmentAlertMessage message) {
        rabbitTemplate.convertAndSend(
                AssessmentMqConfig.ASSESSMENT_ALERT_EXCHANGE,
                AssessmentMqConfig.ALERT_KEY,
                message,
                new CorrelationData("alert:" + message.getStationId() + ":" + message.getAssessMonth()));
    }
}
