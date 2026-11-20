package com.power.ai.biz.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.power.ai.biz.assessment.entity.MonthAssessment;

/**
 * 月度考核 Mapper。
 *
 * 只有单表 CRUD，继承 BaseMapper 就够；
 * 0-5 的聚合 SQL 查的是 gen_daily_stats 表，放在 GenDailyStatsMapper 里（谁的查询谁负责）。
 */
public interface MonthAssessmentMapper extends BaseMapper<MonthAssessment> {
}
