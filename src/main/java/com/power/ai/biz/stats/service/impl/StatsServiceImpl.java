package com.power.ai.biz.stats.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.power.ai.biz.stats.entity.GenDailyStats;
import com.power.ai.biz.stats.mapper.GenDailyStatsMapper;
import com.power.ai.biz.stats.service.StatsService;
import org.springframework.stereotype.Service;

@Service
public class StatsServiceImpl extends ServiceImpl<GenDailyStatsMapper, GenDailyStats>
        implements StatsService {
}
