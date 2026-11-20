package com.power.ai.biz.station.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 电站实体类
 *
 * 学习要点（面试高频）：
 * 1. @TableName：类名 PowerStation 和表名 power_station 不一致，显式指定。
 *    字段名靠全局配置 map-underscore-to-camel-case 自动映射（station_no → stationNo），
 *    所以字段不用逐个写 @TableField
 * 2. @TableId(type = ASSIGN_ID)：MyBatis-Plus 雪花算法生成 ID。
 *    你启动日志里那行 "Initialization Sequence datacenterId:28 workerId:12"
 *    就是雪花算法的初始化输出——机器码 + 机房码 + 序列号拼出的全局唯一 ID。
 *    为什么不用数据库自增：分布式下自增 ID 会冲突、且暴露业务数据量；
 *    雪花 ID 全局唯一、趋势递增（对索引友好）、不依赖数据库
 * 3. @TableLogic：逻辑删除。全局配置已声明 logic-delete-field: deleted，
 *    这里再显式标注是为了可读性。效果：deleteById() 实际执行 UPDATE deleted=1，
 *    所有查询自动带上 deleted=0 条件
 * 4. 电量/容量用 BigDecimal 不用 double：double 是二进制浮点，0.1+0.2≠0.3，
 *    电量结算场景一分钱误差都是生产事故。金额类字段永远 BigDecimal
 * 5. 日期用 LocalDate/LocalDateTime 不用 java.util.Date：
 *    Date 可变、时区坑多，LocalDate 不可变且语义清晰（stat_date 是"日期"不是"时刻"）
 */
@Data
@TableName("power_station")
public class PowerStation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 电站编号 */
    private String stationNo;

    /** 电站名称 */
    private String name;

    /** 电站类型: WIND风电场 SOLAR光伏电站 */
    private String stationType;

    /** 装机容量(千瓦) */
    private BigDecimal capacityKw;

    /** 所属区域公司 */
    private String regionCompany;

    /** 电站负责人姓名 */
    private String managerName;

    /** 并网日期 */
    private LocalDate gridConnectDate;

    /** 电站状态: ONLINE并网运行 BUILDING在建 STOPPED停运 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
