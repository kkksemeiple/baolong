package com.baolong.wsw.enums;

/**
 * 用户角色。
 */
public enum UserRole {

    /** 普通用户 */
    USER,
    /** 管理员 */
    ADMIN,
    /** 超级管理员：可做资金冲正与系统配置 */
    SADMIN;

    public boolean isAdmin() {
        return this == ADMIN || this == SADMIN;
    }
}
