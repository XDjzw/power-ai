package com.power.ai.mq;

import com.power.ai.biz.order.entity.FaultType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工单 MQ 消息体（通知消息和延时消息共用）。
 *
 * 字段取舍：
 * - 只带 stationId，不带电站名称/负责人：消费者收到后自己去查库，消费者侧有真实业务逻辑；
 * - 不带 status：消费者处理时以数据库里的实时状态为准（这正是"状态前置校验"幂等的依据），
 *   消息里带状态反而是过期的快照，不可信；
 * - JSON 序列化（RabbitMqConfig 里换了 Jackson 转换器），实体类无需 implements Serializable。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderMessage {

    /** 工单编号（幂等键，日志排查也靠它） */
    private String orderNo;

    private Long stationId;

    private String deviceName;

    private FaultType faultType;

    private String description;
}
