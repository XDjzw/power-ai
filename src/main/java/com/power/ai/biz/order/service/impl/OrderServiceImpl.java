package com.power.ai.biz.order.service.impl;


import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.power.ai.biz.order.entity.MaintenanceOrder;
import com.power.ai.biz.order.entity.OrderStatus;
import com.power.ai.biz.order.mapper.MaintenanceOrderMapper;
import com.power.ai.biz.order.service.OrderService;
import com.power.ai.common.exception.BizException;
import com.power.ai.common.exception.ErrorCode;
import com.power.ai.mq.OrderMessage;
import com.power.ai.mq.OrderMqProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<MaintenanceOrderMapper,MaintenanceOrder>
        implements OrderService {

    private final OrderMqProducer orderMqProducer;

    @Override
    public MaintenanceOrder createOrder(MaintenanceOrder order){
           order.setId(null);
           order.setOrderNo("MO"+ IdWorker.getIdStr());
           order.setStatus(OrderStatus.WAITING);
           order.setHandlerName(null);
           order.setFinishedAt(null);
           order.setCreatedAt(LocalDateTime.now());
           save(order);

           // 先落库再发消息：DB 是主数据，MQ 只是通知手段。
           // 发送失败只打日志不回滚——事务里发消息做不到 100% 一致性，业界标准解法是
           // "本地消息表 + 定时补偿"（面试可展开），教学项目做到"落库成功 + 失败可追溯"即可
           try {
               OrderMessage message = new OrderMessage(order.getOrderNo(), order.getStationId(),
                       order.getDeviceName(), order.getFaultType(), order.getDescription());
               // 两条消息：一条立即通知电站负责人，一条 2 小时后（dev 60s）触发超时检查
               orderMqProducer.sendNotify(message);
               orderMqProducer.sendTimeoutDelay(message);
           } catch (Exception e) {
               log.error("工单MQ消息发送失败, orderNo={}", order.getOrderNo(), e);
           }
           return order;
    }

    @Override
    public void accept(Long orderId,String handlerName){
          MaintenanceOrder order=getById(orderId);
          if(order == null){
              throw new BizException(ErrorCode.NOT_FOUND);
          }
        if (order.getStatus() != OrderStatus.WAITING) {
            throw new BizException(ErrorCode.BIZ_ERROR.getCode(),
                    "只有待接单的工单才能接单，当前状态: " + order.getStatus());
        }
        order.setStatus(OrderStatus.PROCESSING);
        order.setHandlerName(handlerName);
        updateById(order);


    }

    @Override
    public void finish(Long orderId, String remark) {
        MaintenanceOrder order = getById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new BizException(ErrorCode.BIZ_ERROR.getCode(),
                    "只有处理中的工单才能完工，当前状态: " + order.getStatus());
        }
        order.setStatus(OrderStatus.DONE);
        order.setFinishedAt(LocalDateTime.now());
        order.setRemark(remark);
        updateById(order);
    }

    @Override
    public void markTimeout(Long orderId) {
        MaintenanceOrder order = getById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 状态前置校验：只有 WAITING 的工单才有"超时"这回事，
        // 已经被接单/办结/升级的工单不允许倒退回 TIMEOUT
        if (order.getStatus() != OrderStatus.WAITING) {
            throw new BizException(ErrorCode.BIZ_ERROR.getCode(),
                    "只有待接单的工单才能标记超时，当前状态: " + order.getStatus());
        }
        order.setStatus(OrderStatus.TIMEOUT);
        updateById(order);
    }

    @Override
    public void escalate(Long orderId) {
        MaintenanceOrder order = getById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (order.getStatus() != OrderStatus.TIMEOUT) {
            throw new BizException(ErrorCode.BIZ_ERROR.getCode(),
                    "只有超时的工单才能升级，当前状态: " + order.getStatus());
        }
        order.setStatus(OrderStatus.ESCALATED);
        updateById(order);
    }
}
