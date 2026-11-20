package com.power.ai.biz.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 电站月度计划考核实体（0-5 跑批任务的结果表）。
 *
 * 与 GenDailyStats 相同的模式，注意两点：
 * 1. 唯一键 uk_station_month(station_id, assess_month) 是 UPSERT 幂等的根基——
 *    同一电站同一月份只有一行，重跑跑批是"更新"而不是"多插"；
 * 2. assessed_at 由数据库维护（DEFAULT CURRENT_TIMESTAMP ON UPDATE），
 *    MyBatis-Plus 默认更新策略下实体字段为 null 时不会拼进 UPDATE，
 *    因此这里不需要 @TableField(update = ...) 也能让数据库自动刷新时间。
 */
@Data
@TableName("month_assessment")
public class MonthAssessment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 电站ID */
    private Long stationId;

    /** 考核月份，格式 YYYY-MM（CHAR(7)） */
    private String assessMonth;

    /** 月计划电量(千瓦时) */
    private BigDecimal monthPlanPower;

    /** 本月累计实际发电量(千瓦时) */
    private BigDecimal actualGenPower;

    /** 时间进度 = 本月已过天数 / 本月总天数（0~1 比例） */
    private BigDecimal timeProgress;

    /** 发电进度 = 累计发电量 / 月计划电量（0~1 比例） */
    private BigDecimal genProgress;

    /** 考核状态 */
    private MonthAssessmentStatus assessStatus;

    /** 最近考核时间（数据库 ON UPDATE 自动维护） */
    private LocalDateTime assessedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
