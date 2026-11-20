package com.power.ai.biz.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.power.ai.common.security.UserRole;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体。
 *
 * 安全约定：
 * 1. password 只存 BCrypt 哈希，永不存明文、永不把哈希值返回给前端（登录响应里没有密码字段）；
 * 2. station_id 是电站负责人的绑定关系——阶段 3 Agent 的"我的工单"
 *    就靠它过滤数据，这是 0-6 提前埋下的身份钩子。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录账号 */
    private String username;

    /** 密码（BCrypt 哈希） */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 角色 */
    private UserRole role;

    /** 绑定电站ID（电站负责人专用，其余角色为空） */
    private Long stationId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
