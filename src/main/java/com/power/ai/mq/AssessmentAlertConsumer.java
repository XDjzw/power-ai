package com.power.ai.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.power.ai.biz.assessment.entity.MonthAssessment;
import com.power.ai.biz.assessment.mapper.MonthAssessmentMapper;
import com.power.ai.biz.station.entity.PowerStation;
import com.power.ai.biz.station.service.StationService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 考核告警消费者：监听欠进度告警，模拟通知运营。
 *
 * 与 OrderNotifyConsumer 同一套可靠性写法（手动 ack / 失败 nack 退回队列），
 * 区别在于：消息只带幂等键，告警详情以数据库里的考核记录为准——消息是"事件通知"，
 * 数据以落库结果为准，两边永远不会出现口径不一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssessmentAlertConsumer {

    private final StationService stationService;
    private final MonthAssessmentMapper monthAssessmentMapper;

    @RabbitListener(queues = AssessmentMqConfig.ALERT_QUEUE)
    public void onAssessmentAlert(AssessmentAlertMessage message, Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            PowerStation station = stationService.getById(message.getStationId());
            String stationName = station == null ? "未知电站" : station.getName();

            MonthAssessment record = monthAssessmentMapper.selectOne(
                    new LambdaQueryWrapper<MonthAssessment>()
                            .eq(MonthAssessment::getStationId, message.getStationId())
                            .eq(MonthAssessment::getAssessMonth, message.getAssessMonth()));

            if (record == null) {
                log.warn("【考核告警】{} {} 月欠进度，但考核记录不存在（可能被删除），仅记录事件",
                        stationName, message.getAssessMonth());
            } else {
                // 比例按百分比展示，运营看日志更直观
                BigDecimal genPct = record.getGenProgress().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal timePct = record.getTimeProgress().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                log.warn("【考核告警】{}({}) {} 月欠进度：计划电量 {} 实际发电 {}，发电进度 {}% < 时间进度 {}%，已通知运营关注",
                        stationName, station == null ? "?" : station.getStationNo(),
                        message.getAssessMonth(), record.getMonthPlanPower(),
                        record.getActualGenPower(), genPct, timePct);
            }

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("告警消费失败, stationId={}, assessMonth={}", message.getStationId(), message.getAssessMonth(), e);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception nackEx) {
                log.error("basicNack 失败", nackEx);
            }
        }
    }
}
