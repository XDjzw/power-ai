package com.power.ai.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 月度考核欠进度告警消息体。
 *
 * 字段取舍（与 OrderMessage 同一哲学）：
 * - 只带 stationId + assessMonth 两个幂等键，不带计划电量/进度等指标：
 *   消费者收到后自己去查 month_assessment 表，拿到的是刚落库的实时数据，
 *   消息里带指标反而是发送瞬间的过期快照，不可信；
 * - 不带电站名称/负责人：消费者自己查库（消息走 JSON 序列化，无需 implements Serializable）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentAlertMessage {

    /** 电站ID（定位考核记录的键之一） */
    private Long stationId;

    /** 考核月份 YYYY-MM（与 month_assessment 的 assess_month 对应，日志排查靠它） */
    private String assessMonth;
}
