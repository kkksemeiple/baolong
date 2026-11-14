package com.baolong.wsw.enums;

/**
 * 账号状态。
 */
public enum UserStatus {

    /** 正常 */
    NORMAL,
    /** 受限：信用分过低，只能接小额单 */
    LIMITED,
    /** 封禁 */
    BANNED
}
