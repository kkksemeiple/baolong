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
