package com.power.ai.mq;

import com.power.ai.biz.station.entity.PowerStation;
import com.power.ai.biz.station.service.StationService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 通知消费者：监听"工单已创建"消息，模拟通知电站负责人。
 *
 * 可靠性第二件：手动 ack。
 * - 业务处理成功 → basicAck：告诉 broker 消息处理完毕，可以删了；
 * - 业务处理失败 → basicNack(requeue=true)：消息退回队列，稍后重新投递，不丢消息。
 *   （注意：如果消息本身有毒，requeue 会无限重试，生产上要配重试上限或转死信，
 *    这个点面试可以展开，教学项目先掌握 ack/nack 的语义）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotifyConsumer {

    private final StationService stationService;

    @RabbitListener(queues = RabbitMqConfig.NOTIFY_QUEUE)
    public void onOrderCreated(OrderMessage message, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            // 消息只带 stationId，负责人信息以数据库为准，消费者自己查
            PowerStation station = stationService.getById(message.getStationId());
            String stationName = station == null ? "未知电站" : station.getName();
            String manager = station == null ? "未知负责人" : station.getManagerName();

            // 真实项目这里发短信 / 企业微信 / 站内信，教学项目用日志模拟
            log.info("【工单通知】工单 {} 已创建：{}-{} {}，已通知电站负责人 {}",
                    message.getOrderNo(), stationName, message.getDeviceName(),
                    message.getFaultType().getDesc(), manager);

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("通知消费失败, orderNo={}", message.getOrderNo(), e);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception nackEx) {
                log.error("basicNack 失败", nackEx);
            }
        }
    }
}
