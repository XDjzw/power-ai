package com.power.ai.biz.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.power.ai.biz.assessment.dto.AssessmentVO;
import com.power.ai.biz.assessment.entity.MonthAssessment;
import com.power.ai.biz.assessment.entity.MonthAssessmentStatus;
import com.power.ai.biz.assessment.mapper.MonthAssessmentMapper;
import com.power.ai.biz.assessment.service.MonthAssessmentService;
import com.power.ai.biz.station.entity.PowerStation;
import com.power.ai.biz.station.service.StationService;
import com.power.ai.biz.stats.dto.StationMonthAgg;
import com.power.ai.biz.stats.mapper.GenDailyStatsMapper;
import com.power.ai.common.exception.BizException;
import com.power.ai.common.exception.ErrorCode;
import com.power.ai.mq.AssessmentAlertMessage;
import com.power.ai.mq.AssessmentAlertProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 月度考核跑批业务实现（0-5 核心逻辑）。
 *
 * 数据流：聚合本月发电 → 逐站判定 → UPSERT → 新欠进度发告警 → 汇总日志。
 * 调度（几点跑、分布式锁）在 MonthAssessmentJob，这里只关心"怎么算"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthAssessmentServiceImpl implements MonthAssessmentService {

    private final GenDailyStatsMapper genDailyStatsMapper;
    private final MonthAssessmentMapper monthAssessmentMapper;
    private final AssessmentAlertProducer alertProducer;
    private final StationService stationService;

    /** 欠进度判定系数：发电进度 < 时间进度 × behindFactor → BEHIND */
    @Value("${app.assessment.behind-factor:0.9}")
    private BigDecimal behindFactor;

    /** 每月前 N 天不考核（月初时间进度≈0 会全站误判） */
    @Value("${app.assessment.min-day:3}")
    private int minDay;

    @Override
    public void runMonthlyAssessment() {
        long start = System.currentTimeMillis();

        // ---- 1. 评估窗口：本月月初 ~ 昨天。月初前 minDay 天不跑（时间进度≈0 会全站误判）----
        LocalDate today = LocalDate.now();
        if (today.getDayOfMonth() <= minDay) {
            log.info("本月第 {} 天，未到考核起始日（第 {} 天），本次跳过", today.getDayOfMonth(), minDay);
            return;
        }
        LocalDate yesterday = today.minusDays(1);
        LocalDate monthStart = today.withDayOfMonth(1);
        String assessMonth = YearMonth.from(today).toString(); // "2026-11"，与表里 CHAR(7) 对应

        // ---- 2. 聚合：一条 GROUP BY 拿到所有电站的本月累计发电量和月计划电量 ----
        List<StationMonthAgg> rows = genDailyStatsMapper.selectStationMonthAgg(monthStart, yesterday);

        // ---- 3. 时间进度：分母是本月总天数，分子是"昨天"（评估窗口止于昨天，今天的数据还没生成）----
        BigDecimal timeProgress = BigDecimal.valueOf(yesterday.getDayOfMonth())
                .divide(BigDecimal.valueOf(today.lengthOfMonth()), 5, RoundingMode.HALF_UP);

        int normalCount = 0;
        int behindCount = 0;
        int alertCount = 0;

        for (StationMonthAgg row : rows) {
            // ---- 4. 发电进度 + 判定 ----
            // BigDecimal 除法必须指定精度和舍入模式，否则除不尽时直接抛 ArithmeticException
            BigDecimal genProgress = row.getActualGenPower()
                    .divide(row.getMonthPlanPower(), 5, RoundingMode.HALF_UP);
            boolean behind = genProgress.compareTo(timeProgress.multiply(behindFactor)) < 0;
            MonthAssessmentStatus newStatus = behind ? MonthAssessmentStatus.BEHIND : MonthAssessmentStatus.NORMAL;

            // ---- 5. UPSERT：先查旧记录，有则更新、无则插入 ----
            // 为什么不用 saveOrUpdate：它按"主键 id 是否为 null"决定 insert/update，
            // 而这里的幂等键是唯一键 (station_id, assess_month)，不是主键；
            // 而且我们必须拿到"旧状态"才能做第 6 步的告警幂等，selectOne 一举两得
            MonthAssessment old = monthAssessmentMapper.selectOne(new LambdaQueryWrapper<MonthAssessment>()
                    .eq(MonthAssessment::getStationId, row.getStationId())
                    .eq(MonthAssessment::getAssessMonth, assessMonth));

            if (old == null) {
                MonthAssessment record = new MonthAssessment();
                record.setStationId(row.getStationId());
                record.setAssessMonth(assessMonth);
                record.setMonthPlanPower(row.getMonthPlanPower());
                record.setActualGenPower(row.getActualGenPower());
                record.setTimeProgress(timeProgress);
                record.setGenProgress(genProgress);
                record.setAssessStatus(newStatus);
                monthAssessmentMapper.insert(record); // assessed_at/created_at 留空，走数据库默认值
            } else {
                old.setMonthPlanPower(row.getMonthPlanPower());
                old.setActualGenPower(row.getActualGenPower());
                old.setTimeProgress(timeProgress);
                old.setGenProgress(genProgress);
                old.setAssessStatus(newStatus);
                monthAssessmentMapper.updateById(old); // assessedAt 为 null 不拼进 UPDATE，DB 的 ON UPDATE 自动刷新
            }

            // ---- 6. 告警幂等（状态迁移判定）：只有"新变欠进度"才发 ----
            // 旧状态不是 BEHIND（含首次出现）且新状态是 BEHIND → 发告警；
            // 重复跑批时 WF002 一直是 BEHIND，不会再发，否则运营每天凌晨都被同一电站刷屏
            boolean newlyBehind = behind && (old == null || old.getAssessStatus() != MonthAssessmentStatus.BEHIND);
            if (newlyBehind) {
                alertProducer.sendAlert(new AssessmentAlertMessage(row.getStationId(), assessMonth));
                alertCount++;
            }

            if (behind) {
                behindCount++;
            } else {
                normalCount++;
            }
        }

        // ---- 7. 汇总日志：跑批没有界面，这条"战报"就是唯一现场 ----
        log.info("===== 月度考核跑批完成: 评估 {} 站, 正常 {}, 欠进度 {}, 发送告警 {}, 耗时 {}ms =====",
                rows.size(), normalCount, behindCount, alertCount, System.currentTimeMillis() - start);
    }

    @Override
    public List<AssessmentVO> listByMonth(String assessMonth) {
        if (!assessMonth.matches("\\d{4}-\\d{2}")) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "月份格式应为 YYYY-MM");
        }
        List<MonthAssessment> list = monthAssessmentMapper.selectList(
                new LambdaQueryWrapper<MonthAssessment>()
                        .eq(MonthAssessment::getAssessMonth, assessMonth)
                        .orderByDesc(MonthAssessment::getGenProgress));

        // MyBatis-Plus 不支持多表联查（它的定位是单表 CRUD），
        // 拼装 VO 用"两次查询 + 内存关联"：8 个站的数据量，一条 IN 查询搞定电站信息
        Map<Long, PowerStation> stationMap = list.isEmpty() ? Map.of() : stationService.listByIds(
                        list.stream().map(MonthAssessment::getStationId).toList())
                .stream().collect(Collectors.toMap(PowerStation::getId, Function.identity()));

        return list.stream().map(a -> {
            AssessmentVO vo = new AssessmentVO();
            PowerStation station = stationMap.get(a.getStationId());
            vo.setStationNo(station == null ? "?" : station.getStationNo());
            vo.setStationName(station == null ? "未知电站" : station.getName());
            vo.setAssessMonth(a.getAssessMonth());
            vo.setMonthPlanPower(a.getMonthPlanPower());
            vo.setActualGenPower(a.getActualGenPower());
            vo.setTimeProgress(a.getTimeProgress());
            vo.setGenProgress(a.getGenProgress());
            vo.setAssessStatus(a.getAssessStatus());
            vo.setAssessedAt(a.getAssessedAt());
            return vo;
        }).toList();
    }
}
