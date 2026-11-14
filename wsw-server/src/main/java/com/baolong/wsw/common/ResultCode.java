package com.baolong.wsw.common;

/**
 * 业务状态码。
 * <p>前三段是通用码；40xxx 段里 4 位尾数的任务相关码严格对齐
 * 《任务类设计说明》9.2 的错误码表，前端可以按码做差异化提示。
 * <p>约定：HTTP 状态码只有 200 / 401 / 403 / 404 / 500 这几种，
 * 业务失败一律 HTTP 200 + 非 0 code（唯一例外是未登录的 401，前端靠它跳登录页）。
 */
public enum ResultCode {

    OK(0, "ok"),

    /* ---------- 通用 ---------- */
    SELF_ACCEPT(40001, "不能接自己发布的任务"),
    PARAM_INVALID(40002, "参数不合法"),
    BAD_REQUEST(40002, "参数不合法"),
    UNAUTHORIZED(40100, "请先登录"),
    TOKEN_EXPIRED(40101, "登录已过期，请重新登录"),
    FORBIDDEN(40300, "没有权限执行该操作"),
    CREDIT_LIMIT(40301, "信用不足，暂时无法接单"),
    NOT_FOUND(40400, "资源不存在"),
    TASK_NOT_FOUND(40401, "任务不存在"),

    /* ---------- 任务状态机 ---------- */
    TASK_STATUS_INVALID(40901, "当前状态不允许该操作"),
    TASK_ALREADY_TAKEN(40902, "手慢了，任务已被他人接取"),
    CONFLICT_SCHEDULE(40903, "与已接任务时间冲突"),
    VERSION_CONFLICT(40904, "任务已被修改，请刷新后重试"),
    BARGAIN_NOT_ALLOWED(40905, "当前不可议价，或报价超出允许区间"),
    DEADLINE_EXPIRED(40906, "任务已过截止时间"),
    IDEMPOTENCY_CONFLICT(40907, "重复提交，请勿多次点击"),

    /* ---------- 钱包与账号 ---------- */
    INSUFFICIENT_BALANCE(41001, "屋币不足"),
    BALANCE_LIMIT(41002, "超出单人持有上限"),
    WALLET_FROZEN_RULE(41006, "余额涉及未结算任务，暂不可操作"),
    VIOLATION(41005, "内容包含违规词，已转入人工审核"),
    BANNED(41007, "账号已被封禁"),
    ACCOUNT_LIMITED(41008, "账号处于受限状态"),

    TOO_MANY(42900, "操作过于频繁，请稍后再试"),
    SERVER_ERROR(50000, "服务器内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
