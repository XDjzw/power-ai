package com.power.ai.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 *
 * 设计要点：
 * 1. 用"分类码 + 业务码"的区间约定，而不是从 1 开始随便编：
 *    40xxx = 请求侧问题（参数/登录/权限/不存在），50xxx = 服务侧问题
 *    面试官问"错误码怎么设计"时，能讲出区间约定就是加分项
 * 2. 错误码是给程序判断用的，message 是给人看的，两者必须分开
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "成功"),

    PARAM_ERROR(40000, "参数错误"),
    NOT_LOGIN(40100, "未登录"),
    NO_AUTH(40300, "无权限"),
    NOT_FOUND(40400, "资源不存在"),

    SYSTEM_ERROR(50000, "系统繁忙，请稍后重试"),
    BIZ_ERROR(50001, "业务处理失败");

    private final int code;
    private final String message;
}
