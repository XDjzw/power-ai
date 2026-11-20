package com.power.ai.biz.assessment.dto;

import com.power.ai.biz.assessment.entity.MonthAssessmentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考核结果展示对象（管理台"考核结果"页使用）。
 *
 * 为什么拼 VO 而不是直接把 MonthAssessment 实体吐给前端：
 * 实体只有 stationId 一个数字，前端要显示"华锐风电场二期(WF002)"就得自己再查一遍电站；
 * VO 把电站编号/名称一起拼好，页面拿到的就是能直接展示的完整行——API 按"页面需要什么"设计。
 */
@Data
public class AssessmentVO {

    /** 电站编号 */
    private String stationNo;

    /** 电站名称 */
    private String stationName;

    /** 考核月份 YYYY-MM */
    private String assessMonth;

    /** 月计划电量(千瓦时) */
    private BigDecimal monthPlanPower;

    /** 本月累计实际发电量(千瓦时) */
    private BigDecimal actualGenPower;

    /** 时间进度（0~1） */
    private BigDecimal timeProgress;

    /** 发电进度（0~1） */
    private BigDecimal genProgress;

    /** 考核状态 */
    private MonthAssessmentStatus assessStatus;

    /** 最近考核时间 */
    private LocalDateTime assessedAt;
}
