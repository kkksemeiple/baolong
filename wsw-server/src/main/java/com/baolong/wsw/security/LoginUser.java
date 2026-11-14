package com.baolong.wsw.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户（请求级上下文）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    private Long id;
    private String username;
    private String role;

    /** 是否管理员（ADMIN / SADMIN） */
    public boolean isAdmin() {
        return "ADMIN".equals(role) || "SADMIN".equals(role);
    }

    /** 是否超级管理员 */
    public boolean isSuperAdmin() {
        return "SADMIN".equals(role);
    }
}
