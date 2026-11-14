package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报。
 */
@Data
@TableName("report")
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** ILLEGAL_CONTENT / NO_SHOW / FAKE_DELIVERY / HARASS / OTHER */
    private String type;

    private Long taskId;

    private Long postId;

    /** 冗余标题，方便管理端列表直接展示 */
    private String targetTitle;

    private Long reporterId;

    private String reason;

    /** JSON 数组字符串 */
    private String evidence;

    /** PENDING / HANDLED / REJECTED */
    private String status;

    private String result;

    private LocalDateTime createdAt;

    private LocalDateTime handledAt;
}
