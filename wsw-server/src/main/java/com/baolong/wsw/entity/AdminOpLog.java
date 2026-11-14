package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员操作留痕。封号、发币、冲正、裁决这些动作都要能在事后查到是谁做的。
 */
@Data
@TableName("admin_op_log")
public class AdminOpLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long adminId;

    /** 操作名，例如 BAN_USER / ISSUE_COINS / JUDGE_DISPUTE */
    private String action;

    private String target;

    private String detail;

    private LocalDateTime createdAt;
}
