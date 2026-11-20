package com.power.ai.biz.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.power.ai.biz.order.entity.MaintenanceOrder;
import com.power.ai.biz.order.entity.OrderStatus;
import com.power.ai.biz.order.service.OrderService;
import com.power.ai.common.exception.BizException;
import com.power.ai.common.exception.ErrorCode;
import com.power.ai.common.result.Result;
import com.power.ai.common.security.RequireRole;
import com.power.ai.common.security.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 工单接口。
 * 与 station/stats 的差别：
 * 1. 没有 PUT /{id} 通用修改——状态只能通过 accept/finish 两个动作流转
 * 2. 没有 DELETE——工单是审计数据，业务上不允许删除
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 创建工单（返回实体，因为单号是服务端生成的，必须带回去）。仅运营——工单由运营侧发起 */
    @PostMapping
    @RequireRole(UserRole.OPERATOR)
    public Result<MaintenanceOrder> create(@RequestBody MaintenanceOrder order) {
        return Result.ok(orderService.createOrder(order));
    }

    /** 接单（动作端点：PUT /{id}/accept，状态流转不进 body）。仅电站负责人 */
    @PutMapping("/{id}/accept")
    @RequireRole(UserRole.MANAGER)
    public Result<Void> accept(@PathVariable Long id, @RequestParam String handlerName) {
        orderService.accept(id, handlerName);
        return Result.ok();
    }

    /** 完工（动作端点）。仅电站负责人 */
    @PutMapping("/{id}/finish")
    @RequireRole(UserRole.MANAGER)
    public Result<Void> finish(@PathVariable Long id, @RequestParam String remark) {
        orderService.finish(id, remark);
        return Result.ok();
    }

    /** 分页，支持按电站/状态筛选 */
    @GetMapping("/page")
    public Result<Page<MaintenanceOrder>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) Long stationId,
                                               @RequestParam(required = false) OrderStatus status) {
        LambdaQueryWrapper<MaintenanceOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(stationId != null, MaintenanceOrder::getStationId, stationId)
                .eq(status != null, MaintenanceOrder::getStatus, status)
                .orderByDesc(MaintenanceOrder::getCreatedAt);
        return Result.ok(orderService.page(new Page<>(current, size), wrapper));
    }

    /** 按 ID 查询 */
    @GetMapping("/{id}")
    public Result<MaintenanceOrder> getById(@PathVariable Long id) {
        MaintenanceOrder order = orderService.getById(id);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return Result.ok(order);
    }
}
