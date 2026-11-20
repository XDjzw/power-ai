package com.power.ai.biz.stats.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.power.ai.biz.stats.dto.StationMonthAgg;
import com.power.ai.biz.stats.entity.GenDailyStats;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 日发电统计 Mapper，继承 BaseMapper 即拥有单表全部 CRUD。
 */
public interface GenDailyStatsMapper extends BaseMapper<GenDailyStats> {

    /**
     * 月度考核聚合（0-5 跑批专用）：按电站汇总 [monthStart, yesterday] 窗口内的
     * 累计发电量与月计划电量。
     *
     * 设计说明：
     * 1. 聚合在数据库做，不在 Java 里逐行累加——两年 5840 行时无所谓，
     *    数据量上去了这条 GROUP BY 也是 O(n) 单次扫描，Java 逐行读是灾难；
     * 2. 月计划电量取 MAX：DataSeeder 约定同月每天存同一个值（见 DataSeeder 注释），
     *    MAX 只是保险写法——月中若调整计划，以最大值口径统计；
     * 3. 窗口上限是"昨天"而不是"今天"：跑批在凌晨 1 点执行，当天的日统计还没生成，
     *    统计进"今天"会把空行/半行数据算进去；
     * 4. 在建电站（WF006）没有日统计数据，GROUP BY 结果里天然不会出现，无需特殊过滤。
     */
    @Select("""
            SELECT station_id,
                   SUM(day_gen_power)    AS actual_gen_power,
                   MAX(month_plan_power) AS month_plan_power
            FROM gen_daily_stats
            WHERE stat_date BETWEEN #{monthStart} AND #{yesterday}
              AND deleted = 0
            GROUP BY station_id
            """)
    List<StationMonthAgg> selectStationMonthAgg(@Param("monthStart") LocalDate monthStart,
                                                @Param("yesterday") LocalDate yesterday);
}
