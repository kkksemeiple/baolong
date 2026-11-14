package com.baolong.wsw.enums;

/**
 * 接单记录状态（与《任务类设计说明》中 ClaimStatus 一致）。
 * <p>一个任务至多一条承接记录，由 {@code task_claim} 的 {@code uk_task_id(task_id)} 唯一约束保证。
 * 承接记录的生命周期跟着任务状态走：
 * <ul>
 *   <li>任务进入 IN_PROGRESS → 记录 IN_PROGRESS</li>
 *   <li>承接人提交凭证（任务 TO_CONFIRM）→ 记录 SUBMITTED</li>
 *   <li>发布人确认（任务 COMPLETED / SETTLED）→ 记录 COMPLETED</li>
 *   <li>任务取消或过期 → 记录 CANCELLED</li>
 * </ul>
 */
public enum ClaimStatus {

    /** 进行中：承接人正在履约 */
    IN_PROGRESS("进行中"),
    /** 已提交：凭证已交，等发布人确认 */
    SUBMITTED("已提交"),
    /** 已完成：发布人已确认 */
    COMPLETED("已完成"),
    /** 已取消：放弃接单或任务被取消 */
    CANCELLED("已取消");

    private final String label;

    ClaimStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
