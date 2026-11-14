package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务（任务类的持久化形态），对应设计文档表 1 的 22 个属性。
 * <p>两处与设计文档一致的约定：
 * <ul>
 *   <li>金额用 {@code Long} 存整数（屋币，与设计文档的「分」同义），不出现小数</li>
 *   <li>时间窗是 {@code startTime + durationMinutes}，而不是另存一个结束时间，
 *       避免两个字段互相矛盾；冲突检测时按 {@code startTime.plusMinutes(durationMinutes)} 计算</li>
 * </ul>
 * 状态只能经 {@code TaskMapper} 的带守卫 UPDATE 迁移，外部不得直接改库。
 */
@Data
@TableName("task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    /** PICKUP / BUY / RENT / TUTOR / MOVE / OTHER */
    private String type;

    /** 报酬（屋币整数） */
    private Long reward;

    private LocalDateTime deadline;

    /** 期望开始时间（冲突检测用） */
    private LocalDateTime startTime;

    /** 预计时长（分钟），时间窗 = startTime + 该值 */
    private Integer durationMinutes;

    private String location;

    /** 英文逗号分隔的标签，推荐算法内容特征 */
    private String tags;

    private Integer peopleNeeded;

    /** JSON 数组字符串 */
    private String images;

    /**
     * WAITING / IN_PROGRESS / TO_CONFIRM / COMPLETED / SETTLED / DISPUTED / CANCELLED / EXPIRED
     */
    private String status;

    private Long publisherId;

    /** 当前承接人；取消时按来源保留或清空，供信誉评分归责 */
    private Long claimantId;

    private Integer viewCount;

    private Integer favorCount;

    private LocalDateTime claimedAt;

    private LocalDateTime submittedAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime settledAt;

    /** 取消 / 过期 / 裁决关闭时间 */
    private LocalDateTime closedAt;

    /** 取消原因或争议裁决说明 */
    private String cancelReason;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
