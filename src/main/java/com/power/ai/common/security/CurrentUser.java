package com.power.ai.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户上下文对象（token 解析后的产物，贯穿整个请求）。
 *
 * 设计要点：
 * 1. 它是"身份快照"，只放鉴权必需的字段，不放密码、不放业务实体；
 * 2. stationId 是电站负责人的绑定关系，阶段 3 Agent 的"我的工单"按它过滤——
 *    身份信息从 token 里带出来，而不是每次现查数据库（省一次 SQL，也是无状态的标准做法）。
 */
@Data
@AllArgsConstructor
public class CurrentUser {

    /** 用户ID */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 角色 */
    private UserRole role;

    /** 绑定的电站ID（电站负责人专用，其余角色为 null） */
    private Long stationId;
}
