package com.baolong.wsw.common;

/**
 * 业务异常。抛出后由 {@link GlobalExceptionHandler} 转成统一响应。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode rc) {
        super(rc.getMessage());
        this.code = rc.getCode();
    }

    public BizException(ResultCode rc, String message) {
        super(message);
        this.code = rc.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /* ---------- 常用快捷方法 ---------- */

    public static BizException badRequest(String message) {
        return new BizException(ResultCode.BAD_REQUEST, message);
    }

    public static BizException notFound(String message) {
        return new BizException(ResultCode.NOT_FOUND, message);
    }

    public static BizException conflict(String message) {
        return new BizException(ResultCode.TASK_STATUS_INVALID, message);
    }

    /** 按设计文档的错误码抛异常，例如 {@code BizException.of(ResultCode.TASK_ALREADY_TAKEN)} */
    public static BizException of(ResultCode rc) {
        return new BizException(rc);
    }

    public static BizException of(ResultCode rc, String message) {
        return new BizException(rc, message);
    }

    public static BizException forbidden(String message) {
        return new BizException(ResultCode.FORBIDDEN, message);
    }

    public static BizException unauthorized() {
        return new BizException(ResultCode.UNAUTHORIZED);
    }
}
