package com.baolong.wsw.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

/**
 * 幂等拦截器。
 * <p>前端每个写请求都会带一个 {@code Idempotency-Key} 头。
 * 这里把它插进 {@code idem_record} 表，靠主键唯一约束挡住重复提交——
 * 连点两下「接单」不会产生两条订单，也不会重复扣钱。
 * <p>记录由定时任务每天清理一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentInterceptor implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!WRITE_METHODS.contains(request.getMethod().toUpperCase())) {
            return true;
        }
        String key = request.getHeader("Idempotency-Key");
        LoginUser user = AuthContext.get();
        if (key == null || key.isBlank() || user == null) {
            return true;
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO idem_record(idem_key, user_id, api) VALUES (?, ?, ?)",
                    key.trim(), user.getId(), request.getRequestURI());
            return true;
        } catch (DuplicateKeyException e) {
            log.warn("重复提交被拦截: user={}, key={}, api={}", user.getId(), key, request.getRequestURI());
            response.setStatus(200);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":40907,\"message\":\"重复提交，请勿多次点击\",\"data\":null}");
            return false;
        }
    }
}
