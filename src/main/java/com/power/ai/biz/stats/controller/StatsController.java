package com.power.ai.biz.stats.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.power.ai.biz.stats.entity.GenDailyStats;
import com.power.ai.biz.stats.service.StatsService;
import com.power.ai.common.exception.BizException;
import com.power.ai.common.exception.ErrorCode;
import com.power.ai.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 分页查询日统计 */
    @GetMapping("/page")
    public Result<Page<GenDailyStats>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(statsService.page(new Page<>(current, size)));
    }

    /**
     * 查某电站的全部日统计，按日期倒序（最新在前）
     *
     * 新知识点 LambdaQueryWrapper：
     * 用方法引用（GenDailyStats::getStationId）代替字符串列名，
     * 列名写错编译期直接报错；链式调用可读性好
     */
    @GetMapping("/station/{stationId}")
    public Result<List<GenDailyStats>> listByStation(@PathVariable Long stationId) {
        LambdaQueryWrapper<GenDailyStats> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenDailyStats::getStationId, stationId)
                .orderByDesc(GenDailyStats::getStatDate);
        return Result.ok(statsService.list(wrapper));
    }

    /** 按 ID 查询 */
    @GetMapping("/{id}")
    public Result<GenDailyStats> getById(@PathVariable Long id) {
        GenDailyStats stats = statsService.getById(id);
        if (stats == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return Result.ok(stats);
    }

    /** 新增日统计 */
    @PostMapping
    public Result<Void> create(@RequestBody GenDailyStats stats) {
        stats.setId(null);
        statsService.save(stats);
        return Result.ok();
    }

    /** 修改日统计 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody GenDailyStats stats) {
        stats.setId(id);
        if (!statsService.updateById(stats)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return Result.ok();
    }

    /** 删除日统计（逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (!statsService.removeById(id)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return Result.ok();
    }
}
