package com.power.ai.biz.assessment.controller;

import com.power.ai.biz.assessment.dto.AssessmentVO;
import com.power.ai.biz.assessment.service.MonthAssessmentService;
import com.power.ai.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 考核结果查询接口（管理台"考核结果"页的数据源）。
 *
 * 0-5 的入口是凌晨 1 点的时钟（没有写接口），这里补的是读出口——
 * 跑批是"写"，运营看结果是"读"，读写分离的接口设计。
 */
@RestController
@RequestMapping("/api/assessment")
@RequiredArgsConstructor
public class AssessmentController {

    private final MonthAssessmentService monthAssessmentService;

    /** 查询指定月份（YYYY-MM）的考核结果，带电站编号/名称 */
    @GetMapping("/month/{month}")
    public Result<List<AssessmentVO>> listByMonth(@PathVariable String month) {
        return Result.ok(monthAssessmentService.listByMonth(month));
    }
}
