package com.baolong.wsw.security;

import com.baolong.wsw.common.BizException;

/**
 * 当前请求的用户上下文，基于 ThreadLocal。
 * <p>由 {@link AuthInterceptor} 写入，请求结束时清理，避免线程复用串号。
 */
public final class AuthContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 取当前用户，未登录直接抛 401 */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw BizException.unauthorized();
        }
        return user;
    }

    /** 取当前用户 ID，未登录直接抛 401 */
    public static Long requireUserId() {
        return require().getId();
    }

    public static Long userIdOrNull() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
