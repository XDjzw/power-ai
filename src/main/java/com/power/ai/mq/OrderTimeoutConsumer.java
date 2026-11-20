package com.power.ai.mq;

import com.power.ai.biz.order.entity.MaintenanceOrder;
import com.power.ai.biz.order.entity.OrderStatus;
import com.power.ai.biz.order.service.OrderService;
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
 * 超时升级消费者：监听死信队列（延时队列里 TTL 到期的消息，2 小时后 / dev 60 秒后到达）。
 *
 * 可靠性第三件：幂等——"工单号 + 状态前置校验"。
 * 消息可能被重复投递（网络抖动、消费者崩溃后 requeue），但每次处理都以数据库里的
 * 实时状态为准：只有 WAITING/TIMEOUT 才继续流转，其余状态一律跳过。
 * 重复消费 N 次，最终业务效果只有一次（工单最终停在 ESCALATED）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutConsumer {

    private final OrderService orderService;
    private final StationService stationService;

    @RabbitListener(queues = RabbitMqConfig.TIMEOUT_QUEUE)
    public void onOrderTimeout(OrderMessage message, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            MaintenanceOrder order = orderService.lambdaQuery()
                    .eq(MaintenanceOrder::getOrderNo, message.getOrderNo())
                    .one();
            if (order == null) {
                // 查不到工单（可能被删了）：无法处理，直接确认，避免无效重投
                log.warn("超时消息对应的工单不存在，直接确认: orderNo={}", message.getOrderNo());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 可重入的状态机驱动：WAITING 先标记超时，TIMEOUT 再升级，其他状态跳过。
            // 为什么分成两步而不是一步到位：
            // 如果"标记超时"成功后、"升级"前崩溃，消息 requeue 重投后看到 TIMEOUT，
            // 会从第二步接着走，而不是全部重来——这就是状态机 + 幂等配合的价值
            OrderStatus status = order.getStatus();
            if (status == OrderStatus.WAITING) {
                orderService.markTimeout(order.getId());
                status = OrderStatus.TIMEOUT;
            }
            if (status == OrderStatus.TIMEOUT) {
                orderService.escalate(order.getId());
                PowerStation station = stationService.getById(order.getStationId());
                log.info("【超时升级】工单 {} 超过接单时限无人接单，已升级 ESCALATED，"
                        + "已通知运营中心处理：电站={}，设备={}",
                        order.getOrderNo(),
                        station == null ? "未知" : station.getName(),
                        order.getDeviceName());
            } else {
                // 幂等跳过：已被接单/办结/升级过，超时消息的业务目标已达成，确认即可
                log.info("工单已非待处理状态({})，跳过超时升级: orderNo={}",
                        status, message.getOrderNo());
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("超时升级消费失败, orderNo={}", message.getOrderNo(), e);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception nackEx) {
                log.error("basicNack 失败", nackEx);
            }
        }
    }
}
