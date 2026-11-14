package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知。
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** TASK / FUND / FORUM / SYSTEM */
    private String type;

    private String title;

    private String content;

    private Long bizId;

    /** 前端跳转路径，例如 /tasks/12 */
    private String link;

    private Boolean readFlag;

    private LocalDateTime createdAt;
}
