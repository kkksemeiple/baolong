package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 议价记录（BargainRecord），与 Task 是 1 : 0..* 的关系。
 * <p>规则（设计文档 9.1 第 3 条）：
 * <ul>
 *   <li>只有 WAITING 的任务可以议价</li>
 *   <li>同一任务同一时刻至多一条 PENDING，用 {@code idx_task_status} 支撑判断</li>
 *   <li>成交价必须在 {@code [0, reward × 1.5]} 区间内，上限可配置</li>
 *   <li>任务被接单、取消或过期后，所有 PENDING 一并置 INVALID</li>
 * </ul>
 */
@Data
@TableName("bargain_record")
public class BargainRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    /** 报价方 */
    private Long fromUserId;

    /** 被报价方 */
    private Long toUserId;

    private Long amount;

    /** PENDING / ACCEPTED / REJECTED / INVALID */
    private String status;

    /** 上一轮报价，串成议价链 */
    private Long parentId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
