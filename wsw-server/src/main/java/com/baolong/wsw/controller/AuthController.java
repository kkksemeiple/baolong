package com.baolong.wsw.controller;

import com.baolong.wsw.common.Result;
import com.baolong.wsw.dto.AuthDto;
import com.baolong.wsw.security.AuthContext;
import com.baolong.wsw.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 账号相关接口（对应前端 {@code src/api/auth.ts} 的 10 个方法）。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册 */
    @PostMapping("/register")
    public Result<AuthDto.LoginVO> register(@Valid @RequestBody AuthDto.RegisterReq req) {
        return Result.ok(authService.register(req));
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<AuthDto.LoginVO> login(@Valid @RequestBody AuthDto.LoginReq req) {
        String[] info = AuthService.clientInfo();
        return Result.ok(authService.login(req, info[0], info[1]));
    }

    /**
     * 退出登录。
     * <p>JWT 是无状态的，服务端不维护会话表，前端把 token 丢掉即可；
     * 这里保留接口是为了前端调用链统一，也方便以后换成 Redis 黑名单。
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok(null);
    }

    /** 当前登录用户 */
    @GetMapping("/me")
    public Result<AuthDto.UserVO> me() {
        return Result.ok(authService.me(AuthContext.requireUserId()));
    }

    /** 忘记密码：核对手机号 */
    @PostMapping("/password/forgot")
    public Result<Map<String, String>> forgot(@Valid @RequestBody AuthDto.ForgotReq req) {
        String masked = authService.forgotPassword(req);
        return Result.ok(Map.of("phone", masked, "message", "校验通过，请设置新密码"));
    }

    /** 重置密码 */
    @PostMapping("/password/reset")
    public Result<Void> reset(@Valid @RequestBody AuthDto.ResetReq req) {
        authService.resetPassword(req);
        return Result.ok(null);
    }

    /** 修改密码（已登录） */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody AuthDto.ChangePasswordReq req) {
        authService.changePassword(AuthContext.requireUserId(), req);
        return Result.ok(null);
    }

    /** 实名认证状态 */
    @GetMapping("/student-cert")
    public Result<AuthDto.CertStatusVO> certStatus() {
        return Result.ok(authService.studentCertStatus(AuthContext.requireUserId()));
    }

    /** 提交实名认证 */
    @PostMapping("/student-cert")
    public Result<Void> applyCert(@Valid @RequestBody AuthDto.StudentCertReq req) {
        authService.applyStudentCert(AuthContext.requireUserId(), req);
        return Result.ok(null);
    }

    /** 最近 20 条登录记录 */
    @GetMapping("/login-logs")
    public Result<List<AuthDto.LoginLogVO>> loginLogs() {
        return Result.ok(authService.loginLogs(AuthContext.requireUserId()));
    }
}
