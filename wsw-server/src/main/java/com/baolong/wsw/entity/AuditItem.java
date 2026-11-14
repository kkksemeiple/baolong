package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容审核队列。任务/帖子被举报或命中敏感词后进入这里。
 */
@Data
@TableName("audit_item")
public class AuditItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** TASK / POST / REPLY */
    private String targetType;

    private Long targetId;

    private String title;

    private String content;

    /** 命中的规则说明，例如「敏感词：代考」 */
    private String hitReason;

    private Long authorId;

    /** PENDING / APPROVED / REJECTED */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime handledAt;
}
