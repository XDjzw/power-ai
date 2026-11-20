package com.power.ai.biz.stats.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 月度考核聚合结果行（0-5 跑批专用，聚合 SQL 的结果载体）。
 *
 * 为什么单独建 DTO 而不是复用 GenDailyStats 实体：
 * 聚合结果一行代表"一个电站一个月"，字段含义与单行日统计完全不同，
 * 硬塞进实体会让字段语义混乱，后续维护的人会误以为它是可持久化的行。
 */
@Data
public class StationMonthAgg {

    /** 电站ID */
    private Long stationId;

    /** 本月累计实际发电量(千瓦时) */
    private BigDecimal actualGenPower;

    /** 月计划电量(千瓦时) */
    private BigDecimal monthPlanPower;
}
