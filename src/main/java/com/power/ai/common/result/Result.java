package com.power.ai.common.result;

import com.power.ai.common.exception.ErrorCode;
import lombok.Data;
import org.slf4j.MDC;

/**
 * 统一响应体
 *
 * 设计要点：
 * 1. code 用数字不用字符串：程序判断方便，且和 ErrorCode 枚举一一对应
 * 2. 每个响应都带 traceId（从 MDC 取，由 TraceIdFilter 写入）：
 *    用户报障时贴 traceId，就能直接 grep 出该请求的整条日志链，
 *    这是生产环境排障的标配做法，面试常问"线上出问题怎么定位"
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.code = ErrorCode.SUCCESS.getCode();
        result.message = "成功";
        result.data = data;
        result.traceId = MDC.get("traceId");
        return result;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        result.traceId = MDC.get("traceId");
        return result;
    }
}
