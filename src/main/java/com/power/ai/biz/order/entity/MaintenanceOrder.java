package com.power.ai.biz.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("maintenance_order")
public class MaintenanceOrder {

    @TableId(type=IdType.ASSIGN_ID)
    private Long id;

    private String orderNo;

    /** 电站ID */
    private Long stationId;

    /** 设备名称 */
    private String deviceName;

    /** 故障类型 */
    private FaultType faultType;

    /** 工单状态 */
    private OrderStatus status;

    /** 故障描述 */
    private String description;

    /** 处理人（接单时写入） */
    private String handlerName;

    /** 完工时间（完工时写入） */
    private LocalDateTime finishedAt;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;

}
