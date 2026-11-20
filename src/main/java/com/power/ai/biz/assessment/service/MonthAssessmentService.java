package com.power.ai.biz.assessment.service;

import com.power.ai.biz.assessment.dto.AssessmentVO;

import java.util.List;

/**
 * 月度计划考核服务（0-5 跑批 + 查询）。
 *
 * 设计原则：调度与业务分离。
 * runMonthlyAssessment 不关心"几点跑、拿不拿得到锁"（那是 MonthAssessmentJob 的事），
 * 只关心"怎么算"；listByMonth 是跑批结果的读出口，供管理台考核结果页使用。
 */
public interface MonthAssessmentService {

    /**
     * 执行一次月度考核跑批：
     * 聚合本月发电 → 逐站判定 → UPSERT month_assessment → 新欠进度发告警 → 汇总日志
     */
    void runMonthlyAssessment();

    /**
     * 查询指定月份（YYYY-MM）的考核结果，带电站编号/名称。
     */
    List<AssessmentVO> listByMonth(String assessMonth);
}
