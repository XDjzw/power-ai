package com.power.ai.biz.stats.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.power.ai.biz.stats.entity.GenDailyStats;

/**
 * 日发电统计服务接口。
 * 复杂业务方法（如月度聚合统计）后续加在这里，不在 Controller 里堆逻辑。
 */
public interface StatsService extends IService<GenDailyStats> {
}
