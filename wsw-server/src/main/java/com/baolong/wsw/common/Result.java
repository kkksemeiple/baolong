package com.baolong.wsw.common;

/**
 * 统一响应体。
 * <p>前端约定：{@code code == 0} 表示成功，其余按错误码处理。
 *
 * @param code    业务状态码
 * @param message 提示信息
 * @param data    业务数据
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.OK.getCode(), "ok", data);
    }

    public static Result<Void> ok() {
        return new Result<>(ResultCode.OK.getCode(), "ok", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(ResultCode rc) {
        return new Result<>(rc.getCode(), rc.getMessage(), null);
    }

    public static <T> Result<T> fail(ResultCode rc, String message) {
        return new Result<>(rc.getCode(), message, null);
    }
}
