package com.baolong.wsw.mapper;

import com.baolong.wsw.entity.TaskClaim;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 承接记录表。
 * <p>状态跟着任务状态联动，三个 UPDATE 都是幂等的：重复调用只会影响 0 行。
 */
@Mapper
public interface TaskClaimMapper extends BaseMapper<TaskClaim> {

    /** 承接人提交凭证：IN_PROGRESS → SUBMITTED */
    @Update("""
            UPDATE task_claim
               SET status         = 'SUBMITTED',
                   submitted_at   = NOW(),
                   submit_content = #{content},
                   submit_proof   = #{proof}
             WHERE task_id = #{taskId}
               AND status = 'IN_PROGRESS'
            """)
    int submit(@Param("taskId") Long taskId,
               @Param("content") String content,
               @Param("proof") String proof);

    /** 发布人确认：SUBMITTED → COMPLETED，同时写评价 */
    @Update("""
            UPDATE task_claim
               SET status         = 'COMPLETED',
                   confirmed_at   = NOW(),
                   finished_at    = NOW(),
                   rating         = #{rating},
                   review_content = #{review}
             WHERE task_id = #{taskId}
               AND status = 'SUBMITTED'
            """)
    int confirm(@Param("taskId") Long taskId,
                @Param("rating") Integer rating,
                @Param("review") String review);

    /**
     * 放弃后任务重新被接单：复用同一行接单记录。
     *
     * <p>{@code task_claim} 上有 {@code uk_task_id(task_id)} 唯一约束，一个任务终生只有一行。
     * 所以第二次接单不能再 insert（否则撞唯一键，接口 500），必须把上一次留下的
     * CANCELLED 行重置为一条全新的「进行中」记录，并清空上一次的交付、评价与取消信息。
     *
     * <p>用一条 UPDATE 显式置 NULL：MyBatis-Plus 的 {@code update-strategy: not_null}
     * 会忽略实体里的 null 字段，走 updateById 是清不掉旧值的。
     *
     * @return 影响行数；0 表示这行记录不是「已取消」状态（数据异常），调用方应回滚本次接单
     */
    @Update("""
            UPDATE task_claim
               SET claimer_id     = #{claimerId},
                   status         = 'IN_PROGRESS',
                   claimed_at     = NOW(),
                   submitted_at   = NULL,
                   confirmed_at   = NULL,
                   finished_at    = NULL,
                   submit_content = NULL,
                   submit_proof   = NULL,
                   cancel_reason  = NULL,
                   cancel_by      = NULL,
                   rating         = NULL,
                   review_content = NULL
             WHERE task_id = #{taskId}
               AND status = 'CANCELLED'
            """)
    int reopenForClaim(@Param("taskId") Long taskId, @Param("claimerId") Long claimerId);

    /** 任务取消 / 过期：记录置 CANCELLED 并标明责任方 */
    @Update("""
            UPDATE task_claim
               SET status        = 'CANCELLED',
                   finished_at   = NOW(),
                   cancel_reason = #{reason},
                   cancel_by     = #{cancelBy}
             WHERE task_id = #{taskId}
               AND status IN ('IN_PROGRESS', 'SUBMITTED')
            """)
    int cancel(@Param("taskId") Long taskId,
               @Param("reason") String reason,
               @Param("cancelBy") String cancelBy);
}
