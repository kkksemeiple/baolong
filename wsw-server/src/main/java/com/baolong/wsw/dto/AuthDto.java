package com.baolong.wsw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账号模块的入参与出参。
 */
public final class AuthDto {

    private AuthDto() {
    }

    /** 注册 */
    public record RegisterReq(
            @NotBlank(message = "请填写登录名")
            @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "登录名 4~20 位字母数字下划线")
            String username,

            @NotBlank(message = "请填写密码")
            @Size(min = 8, max = 32, message = "密码 8~32 位")
            String password,

            @NotBlank(message = "请填写昵称")
            @Size(max = 20, message = "昵称最多 20 字")
            String nickname,

            @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
            String phone,

            String studentNo
    ) {
    }

    /** 登录 */
    public record LoginReq(
            @NotBlank(message = "请填写登录名") String username,
            @NotBlank(message = "请填写密码") String password
    ) {
    }

    /** 登录成功返回 */
    public record LoginVO(String token, long expiresIn, UserVO user) {
    }

    /** 用户信息（出参） */
    public record UserVO(
            Long id,
            String username,
            String role,
            String nickname,
            String avatar,
            String phone,
            Integer creditScore,
            String status,
            String authStatus,
            Integer finishedCount,
            Integer goodRate,
            List<String> tags,
            String intro,
            Boolean recommendEnabled,
            LocalDateTime createdAt
    ) {
    }

    /** 修改密码 */
    public record ChangePasswordReq(
            @NotBlank(message = "请填写原密码") String oldPassword,
            @NotBlank @Size(min = 8, max = 32, message = "新密码 8~32 位") String newPassword
    ) {
    }

    /** 忘记密码：先核对手机号，前端第二步再提交新密码 */
    public record ForgotReq(
            @NotBlank(message = "请填写登录名") String username,
            @NotBlank(message = "请填写手机号") String phone
    ) {
    }

    /** 重置密码 */
    public record ResetReq(
            @NotBlank(message = "请填写登录名") String username,
            @NotBlank(message = "请填写手机号") String phone,
            @NotBlank @Size(min = 8, max = 32, message = "新密码 8~32 位") String newPassword
    ) {
    }

    /** 学号实名认证 */
    public record StudentCertReq(
            @NotBlank(message = "请填写学号") String studentNo,
            @NotBlank(message = "请填写真实姓名") String realName,
            String certImage
    ) {
    }

    /**
     * 实名认证状态。
     * <p>前端要一次拿到状态、学号、姓名与驳回原因来渲染表单，
     * 只回一个状态字符串的话，表单回显永远是空的。
     */
    public record CertStatusVO(
            String authStatus,
            String studentNo,
            String realName,
            String certImage,
            String rejectReason
    ) {
    }

    /**
     * 登录记录。
     * <p>字段名对齐前端 {@code LoginLogItem}：{@code loginAt} 不是 createdAt、
     * {@code userAgent} 才是设备串（实体里叫 device），否则「最近登录记录」的时间列会显示成 —。
     */
    public record LoginLogVO(
            Long id,
            String ip,
            /** 粗略归属：本机 / 局域网 / 外网 */
            String location,
            String userAgent,
            Boolean success,
            String failReason,
            LocalDateTime loginAt
    ) {
    }
}
