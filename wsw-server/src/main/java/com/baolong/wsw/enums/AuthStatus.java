package com.baolong.wsw.enums;

/**
 * 学号实名认证状态。
 */
public enum AuthStatus {

    /** 未提交 */
    NONE,
    /** 审核中 */
    PENDING,
    /** 已通过 */
    APPROVED,
    /** 已驳回 */
    REJECTED
}
