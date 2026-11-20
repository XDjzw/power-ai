package com.power.ai.biz.stats.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 日发电统计实体（核心事实表）
 *
 * 与 PowerStation 相同的模式，注意两点：
 * 1. 指标字段全是 BigDecimal——电量/比例来自企业真实表，精度规格照搬
 *    （发电量 5 位小数、损失电量 2 位、比例 5 位）
 * 2. 字段名的业务含义注释与 schema.sql 保持一致，
 *    阶段 2 列召回会用列 comment 做向量检索，这里和数据库必须对得上
 */
@Data
@TableName("gen_daily_stats")
public class GenDailyStats {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 电站ID */
    private Long stationId;

    /** 统计日期 */
    private LocalDate statDate;

    /** 日发电量(千瓦时) */
    private BigDecimal dayGenPower;

    /** 日上网电量(千瓦时) */
    private BigDecimal dayNetPower;

    /** 日厂供电量(千瓦时) */
    private BigDecimal dayOutputPower;

    /** 日厂用电量(千瓦时) */
    private BigDecimal plantConsumption;

    /** 综合厂用电率 */
    private BigDecimal auxiliaryPowerRate;

    /** 限电损失电量(千瓦时) */
    private BigDecimal curtailmentLoss;

    /** 限电比例 */
    private BigDecimal curtailmentRatio;

    /** 故障损失电量(千瓦时) */
    private BigDecimal faultLoss;

    /** 月计划电量(千瓦时) */
    private BigDecimal monthPlanPower;

    /** 预测发电量(千瓦时) */
    private BigDecimal productionFc;

    /** 预测风速(米/秒，仅风电场有值) */
    private BigDecimal windSpeedFc;

    /** 平均利用小时 */
    private BigDecimal avgUtilHours;

    /** 创建时间 */
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
