package com.power.ai.biz.assessment.entity;

/**
 * 月度考核状态。
 *
 * MyBatis-Plus 默认按枚举名（name()）与数据库字符串互相映射，
 * 与 OrderStatus 的用法一致，DB 里存的就是 "NORMAL" / "BEHIND"。
 */
public enum MonthAssessmentStatus {
    NORMAL, BEHIND
}
