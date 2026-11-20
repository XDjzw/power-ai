-- ============================================================
-- 电力智能服务平台 v2：新能源发电运营域
-- 参考真实企业新能源发电量统计表结构裁剪
--
-- 重要：注释保留业务同义词风格（"限电损失电量 限电 日限电量"），
-- 阶段2 NL2SQL 的列召回会把这些注释向量化做语义检索，
-- 用户口语（"这个月超了多少"）要能对上注释里的别名（"超欠进度"），
-- 注释质量直接决定召回质量，这里是给 AI 准备的，不是给人看的装饰。
-- ============================================================
CREATE DATABASE IF NOT EXISTS power_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE power_ai;

-- 1. 新能源电站
CREATE TABLE power_station (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '电站ID',
    station_no        VARCHAR(32) NOT NULL UNIQUE COMMENT '电站编号',
    name              VARCHAR(64) NOT NULL COMMENT '电站名称 电场 光伏电站',
    station_type      VARCHAR(16) NOT NULL COMMENT '电站类型: WIND风电场 SOLAR光伏电站',
    capacity_kw       DECIMAL(12,2) NOT NULL COMMENT '装机容量(千瓦) 装机 容量',
    region_company    VARCHAR(64) COMMENT '所属区域公司 区域',
    manager_name      VARCHAR(32) COMMENT '电站负责人姓名 站长',
    grid_connect_date DATE COMMENT '并网日期 投运日期',
    status            VARCHAR(16) DEFAULT 'ONLINE' COMMENT '电站状态: ONLINE并网运行 BUILDING在建 STOPPED停运',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted           TINYINT  DEFAULT 0 COMMENT '逻辑删除标志: 0未删除 1已删除'
) COMMENT='新能源电站信息表';

-- 2. 日发电统计（核心事实表，智能问数的主要查询对象）
CREATE TABLE gen_daily_stats (
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '统计ID',
    station_id           BIGINT NOT NULL COMMENT '电站ID',
    stat_date            DATE NOT NULL COMMENT '统计日期 日期 时间',
    day_gen_power        DECIMAL(20,5) COMMENT '日发电量 发电量',
    day_net_power        DECIMAL(20,5) COMMENT '日上网电量 上网电量',
    day_output_power     DECIMAL(20,5) COMMENT '日厂供电量 厂供电量',
    plant_consumption    DECIMAL(20,2) COMMENT '日厂用电量 厂用电',
    auxiliary_power_rate DECIMAL(10,5) COMMENT '综合厂用电率 厂用电率',
    curtailment_loss     DECIMAL(20,2) COMMENT '限电损失电量 限电 限电量 日限电量',
    curtailment_ratio    DECIMAL(10,5) COMMENT '限电比例 限电比',
    fault_loss           DECIMAL(20,2) COMMENT '故障损失电量 非停损失电量 损失电量',
    month_plan_power     DECIMAL(20,2) COMMENT '月计划电量 超欠进度',
    production_fc        DECIMAL(20,2) COMMENT '预测发电量 预测电量',
    wind_speed_fc        DECIMAL(5,2) COMMENT '预测风速 风速',
    avg_util_hours       DECIMAL(10,2) COMMENT '平均利用小时 利用小时',
    created_at           DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted              TINYINT  DEFAULT 0 COMMENT '逻辑删除标志: 0未删除 1已删除',
    UNIQUE KEY uk_station_date (station_id, stat_date),
    KEY idx_stat_date (stat_date)
) COMMENT='电站日发电量统计表';

-- 3. 检修工单（RabbitMQ 通知/超时升级的落点）
CREATE TABLE maintenance_order (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '工单ID',
    order_no     VARCHAR(32) NOT NULL UNIQUE COMMENT '工单编号',
    station_id   BIGINT NOT NULL COMMENT '电站ID',
    device_name  VARCHAR(64) COMMENT '故障设备名称 设备 风机 逆变器',
    fault_type   VARCHAR(16) NOT NULL COMMENT '缺陷类型: PITCH变桨故障 GEARBOX齿轮箱异常 INVERTER逆变器故障 LINE线路跳闸 OTHER其他',
    status       VARCHAR(16) DEFAULT 'WAITING' COMMENT '工单状态: WAITING待处理 PROCESSING处理中 DONE已办结 TIMEOUT已超时 ESCALATED已升级',
    description  VARCHAR(512) NOT NULL COMMENT '工单问题描述',
    handler_name VARCHAR(32) COMMENT '处理人姓名',
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    finished_at  DATETIME COMMENT '办结时间',
    remark       VARCHAR(256) COMMENT '处理结果备注',
    deleted      TINYINT  DEFAULT 0 COMMENT '逻辑删除标志: 0未删除 1已删除',
    KEY idx_status_created (status, created_at),
    KEY idx_station (station_id)
) COMMENT='电站检修工单表';

-- 4. 月度计划考核（0-5 跑批任务的结果表，每天凌晨 UPSERT）
CREATE TABLE month_assessment (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '考核记录ID',
    station_id       BIGINT NOT NULL COMMENT '电站ID',
    assess_month     CHAR(7) NOT NULL COMMENT '考核月份, 格式YYYY-MM',
    month_plan_power DECIMAL(20,2) COMMENT '月计划电量 计划电量',
    actual_gen_power DECIMAL(20,5) COMMENT '本月累计实际发电量 累计发电量',
    time_progress    DECIMAL(10,5) COMMENT '时间进度 本月已过天数占比',
    gen_progress     DECIMAL(10,5) COMMENT '发电进度 累计发电量/月计划电量 完成进度',
    assess_status    VARCHAR(16) COMMENT '考核状态: NORMAL正常 BEHIND欠进度',
    assessed_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近考核时间',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted          TINYINT  DEFAULT 0 COMMENT '逻辑删除标志: 0未删除 1已删除',
    UNIQUE KEY uk_station_month (station_id, assess_month)
) COMMENT='电站月度计划考核表';

-- 5. 系统用户（0-6 JWT 登录鉴权）
CREATE TABLE sys_user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username    VARCHAR(32) NOT NULL UNIQUE COMMENT '登录账号 用户名',
    password    VARCHAR(64) NOT NULL COMMENT '密码(BCrypt哈希)',
    real_name   VARCHAR(32) COMMENT '真实姓名 姓名',
    role        VARCHAR(16) NOT NULL COMMENT '角色: ADMIN管理员 MANAGER电站负责人 OPERATOR运营',
    station_id  BIGINT COMMENT '绑定电站ID(电站负责人绑定所属电站)',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted     TINYINT  DEFAULT 0 COMMENT '逻辑删除标志: 0未删除 1已删除'
) COMMENT='系统用户表';
