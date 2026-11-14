package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户。
 * <p>注册时同步开两个东西：钱包账户（{@code wallet_account}）和信用档案（{@code user_credit}）。
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名 */
    private String username;

    /** BCrypt 哈希 */
    private String passwordHash;

    private String nickname;

    private String avatar;

    private String phone;

    /** USER / ADMIN / SADMIN */
    private String role;

    /** NORMAL / LIMITED / BANNED */
    private String status;

    /** NONE / PENDING / APPROVED / REJECTED */
    private String authStatus;

    private String studentNo;

    private String realName;

    private String certRejectReason;

    /** 学生证照片 URL，管理员审核实名时查看 */
    private String certImage;

    /** JSON 数组字符串，如 ["代取","家教"] */
    private String tags;

    private String intro;

    /** 个性化推荐开关 */
    private Boolean recommendEnabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastActiveAt;
}
