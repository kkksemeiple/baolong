package com.baolong.wsw.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 鉴权拦截器。
 * <ul>
 *   <li>带 token：解析后写入 {@link AuthContext}</li>
 *   <li>不带 token：只放行若干「游客可看」的 GET 接口，其余一律 401</li>
 *   <li>{@code /admin/**}：必须是 ADMIN 或 SADMIN</li>
 * </ul>
 * <p>401 返回真正的 HTTP 401，前端 axios 拦截器据此清理凭证并跳登录页。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    /** 游客可访问的 GET 接口（任务大厅、帖子列表、他人主页等） */
    private static final Pattern[] ANONYMOUS_GET = {
            Pattern.compile("^/tasks$"),
            Pattern.compile("^/tasks/search$"),
            Pattern.compile("^/tasks/categories$"),
            Pattern.compile("^/tasks/\\d+$"),
            Pattern.compile("^/posts$"),
            Pattern.compile("^/posts/\\d+$"),
            Pattern.compile("^/posts/\\d+/replies$"),
            Pattern.compile("^/users/\\d+$")
    };

    /**
     * 完全公开的接口：不登录也必须能访问，否则会死锁 ——
     * 登录接口本身被拦下来，用户永远拿不到 token。
     * 这里用「方法和路径都要匹配」，避免误放行同路径的其他方法。
     */
    private static final Pattern[] PUBLIC = {
            Pattern.compile("^/auth/login$"),
            Pattern.compile("^/auth/register$"),
            Pattern.compile("^/auth/password/forgot$"),
            Pattern.compile("^/auth/password/reset$")
    };

    /** 上传后的静态文件要能直接打开（图片拿不到就没法展示） */
    private static final Pattern PUBLIC_GET = Pattern.compile("^/files/upload/.+$");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ")
                ? header.substring(7).trim()
                : null;

        LoginUser user = jwtUtil.parse(token);
        if (user != null) {
            AuthContext.set(user);
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        if (user == null) {
            if (isPublic(path)) {
                return true;
            }
            if ("GET".equalsIgnoreCase(method)
                    && (matchAnonymous(path) || PUBLIC_GET.matcher(path).matches())) {
                return true;
            }
            return reject(response, 401, 40100, "请先登录");
        }

        if (path.startsWith("/admin") && !user.isAdmin()) {
            return reject(response, 403, 40300, "没有权限访问管理后台");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    private boolean matchAnonymous(String path) {
        for (Pattern p : ANONYMOUS_GET) {
            if (p.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    private boolean isPublic(String path) {
        for (Pattern p : PUBLIC) {
            if (p.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    private boolean reject(HttpServletResponse response, int httpStatus, int code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}");
        return false;
    }
}
