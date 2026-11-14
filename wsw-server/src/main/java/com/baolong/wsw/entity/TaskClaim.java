package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 承接记录（TaskClaim），与 Task 是 1 : 0..1 的关系。
 *
 * <p>为什么不把承接信息塞进 task 表：
 * <ol>
 *   <li>「一单一人」有了可落地的唯一约束：{@code uk_task_id(task_id)}</li>
 *   <li>交付凭证、评价这类承接方侧的数据有地方放，不污染任务主表</li>
 *   <li>信誉评分要按承接记录聚合（是否准时、有没有被差评），查一张表就够</li>
 * </ol>
 * 记录状态与任务状态联动，取值见 {@code ClaimStatus}；
 * {@code cancel_by} 用于区分取消责任，信誉评分时「发布人责任」不进分母。
 */
@Data
@TableName("task_claim")
public class TaskClaim {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long claimerId;

    /** IN_PROGRESS / SUBMITTED / COMPLETED / CANCELLED */
    private String status;

    private LocalDateTime claimedAt;

    private LocalDateTime submittedAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime finishedAt;

    private String submitContent;

    /** JSON 数组字符串 */
    private String submitProof;

    private String cancelReason;

    /** PUBLISHER / CLAIMER / SYSTEM —— 信誉评分时用于区分谁的责任 */
    private String cancelBy;

    /** 发布方评价 1~5 星，未评价为 null */
    private Integer rating;

    private String reviewContent;
}
