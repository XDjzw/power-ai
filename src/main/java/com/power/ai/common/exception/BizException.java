package com.power.ai.common.exception;

import lombok.Getter;

/**
 * 业务异常：业务规则不满足时抛出（如"工单已办结不能再修改"），
 * 由 GlobalExceptionHandler 统一转成 Result 返回，业务代码里不用到处 try-catch。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(String message) {
        this(ErrorCode.BIZ_ERROR.getCode(), message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
