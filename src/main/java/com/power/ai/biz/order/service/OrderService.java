package com.power.ai.biz.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.power.ai.biz.order.entity.MaintenanceOrder;

public interface OrderService extends IService<MaintenanceOrder> {

    MaintenanceOrder createOrder(MaintenanceOrder order);

    void accept(Long orderId,String handlerName);

    void finish(Long orderId,String remark);

    /** 超时标记：WAITING -> TIMEOUT（工单超过接单时限仍无人接单） */
    void markTimeout(Long orderId);

    /** 超时升级：TIMEOUT -> ESCALATED（升级给运营中心处理） */
    void escalate(Long orderId);
}
