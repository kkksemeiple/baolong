package com.baolong.wsw.security;

import com.baolong.wsw.entity.User;
import com.baolong.wsw.enums.UserRole;
import com.baolong.wsw.enums.UserStatus;
import com.baolong.wsw.mapper.UserMapper;
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
    private final UserMapper userMapper;

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
        // 进任何请求之前先清空线程上下文。
        // ThreadLocal 是复用的：上一个请求如果在本拦截器里被拒（比如普通用户访问 /admin 返回 403），
        // Spring 不会为「返回 false 的那个拦截器」回调 afterCompletion，身份就残留在池化线程上。
        // 下一个落到同一线程的请求（比如游客看任务详情）会把这个残留身份当成自己 ——
        // 表现为「游客看到别人的 mine/favorited、浏览记录记到别人头上」这种诡异串号。
        AuthContext.clear();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ")
                ? header.substring(7).trim()
                : null;

        LoginUser user = jwtUtil.parse(token);
        String dbRole = null;
        if (user != null) {
            // JWT 是无状态的：token 一旦签发，在有效期内就一直能通过签名校验。
            // 所以「封禁 / 注销 / 撤管理员」都必须在这里回查一次数据库，
            // 否则被踢的人还能用旧 token 一直操作到 7 天后过期 —— 封禁、降权都形同虚设。
            User account = userMapper.selectById(user.getId());
            if (account == null) {
                return reject(response, 401, 40101, "账号不存在或已注销，请重新登录");
            }
            if (UserStatus.BANNED.name().equals(account.getStatus())) {
                return reject(response, 401, 40101, "账号已被封禁，请联系管理员");
            }
            // 注意：LIMITED（信誉不足限制接单）是正常可用账号，不能拦。
            dbRole = account.getRole();
            // 密码指纹比对：改过密码的旧 token 必须立刻失效。
            // （页面上明确写了「修改密码后，所有设备上的登录状态都会失效」，
            //   不做这一步就是纯文案骗人 —— JWT 本身不会因为改密码而失效。）
            String expectPv = JwtUtil.fingerprint(account.getPasswordHash());
            if (expectPv != null && !expectPv.equals(user.getPv())) {
                return reject(response, 401, 40101, "密码已修改，请重新登录");
            }
            // 写进上下文的角色必须是「库里此刻的角色」，不能沿用 token 里的旧值：
            // 否则被降权的管理员，在别的业务代码里仍是管理员。
            AuthContext.set(new LoginUser(user.getId(), account.getUsername(), dbRole));
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

        // 用库里的角色判权限，而不是 token 里那份快照：
        // 管理员被降权后，旧 token 里的 role 仍然是 ADMIN，只看 token 等于永不失效。
        boolean admin = UserRole.ADMIN.name().equals(dbRole) || UserRole.SADMIN.name().equals(dbRole);
        if (path.startsWith("/admin") && !admin) {
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
