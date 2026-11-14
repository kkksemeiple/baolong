package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 争议单。
 * <p>任务进入 {@code DISPUTED} 时创建，冻结金额保持不变，等管理员裁决后按结果拆账。
 */
@Data
@TableName("dispute")
public class Dispute {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long publisherId;

    private Long takerId;

    /** 争议中的冻结金额快照 */
    private Long frozenAmount;

    /** 谁发起的争议 */
    private Long initiatorId;

    private String reason;

    /** JSON 数组字符串 */
    private String evidence;

    /** PENDING / JUDGED */
    private String status;

    /** PUBLISHER / TAKER / PARTIAL / INSUFFICIENT */
    private String result;

    /** 裁决说明 */
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime judgedAt;
}
