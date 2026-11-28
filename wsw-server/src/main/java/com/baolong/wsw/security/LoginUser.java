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
    /**
     * 密码指纹（签发 token 时密码哈希的摘要前 12 位）。
     * 用户改密码后这个值必然变化 —— 拦截器据此让所有旧 token 立刻失效，
     * 兑现页面上那句「修改密码后，所有设备上的登录状态都会失效」。
     */
    private String pv;

    /** 只有 id/username/role 时用（例如拦截器回查数据库后重建上下文） */
    public LoginUser(Long id, String username, String role) {
        this(id, username, role, null);
    }

    /** 是否管理员（ADMIN / SADMIN） */
    public boolean isAdmin() {
        return "ADMIN".equals(role) || "SADMIN".equals(role);
    }

    /** 是否超级管理员 */
    public boolean isSuperAdmin() {
        return "SADMIN".equals(role);
    }
}
