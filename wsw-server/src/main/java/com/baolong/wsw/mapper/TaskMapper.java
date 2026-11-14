package com.baolong.wsw.mapper;

import com.baolong.wsw.entity.Task;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 任务表的手写 SQL。
 * <p>这里的方法全部是「带状态守卫的原子 UPDATE」：把状态判断写进 WHERE，
 * 影响行数为 0 就说明这次操作在并发中输了或者状态已经变了，
 * 由服务层重新读一次任务来判断该报哪个错误码（40901/40902/40904/40906）。
 * 状态取值见 {@code TaskStatus}：WAITING / IN_PROGRESS / TO_CONFIRM / COMPLETED / SETTLED / DISPUTED / CANCELLED / EXPIRED。
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /**
     * 抢单的最终闸门（对应设计文档 8.1 的核心 CAS）。
     * <p>200 个请求同时点「接单」，只有一行能返回影响行数 1。
     * 返回 0 时服务层重读任务，区分：不存在 / 已被接走 / 已过期 / 版本冲突。
     */
    @Update("""
            UPDATE task
               SET status      = 'IN_PROGRESS',
                   claimant_id = #{claimantId},
                   claimed_at  = NOW(),
                   version     = version + 1,
                   updated_at  = NOW()
             WHERE id = #{taskId}
               AND status = 'WAITING'
               AND (version = #{expectedVersion} OR #{expectedVersion} IS NULL)
               AND deadline > NOW()
            """)
    int claim(@Param("taskId") Long taskId,
              @Param("claimantId") Long claimantId,
              @Param("expectedVersion") Integer expectedVersion);

    /** 承接人提交凭证：进行中 → 待确认 */
    @Update("""
            UPDATE task
               SET status      = 'TO_CONFIRM',
                   submitted_at = NOW(),
                   version     = version + 1,
                   updated_at  = NOW()
             WHERE id = #{taskId}
               AND status = 'IN_PROGRESS'
               AND claimant_id = #{claimantId}
            """)
    int submit(@Param("taskId") Long taskId, @Param("claimantId") Long claimantId);

    /** 发布人确认完成：待确认 → 已完成 */
    @Update("""
            UPDATE task
               SET status       = 'COMPLETED',
                   confirmed_at = NOW(),
                   version      = version + 1,
                   updated_at   = NOW()
             WHERE id = #{taskId}
               AND status = 'TO_CONFIRM'
               AND publisher_id = #{publisherId}
            """)
    int confirm(@Param("taskId") Long taskId, @Param("publisherId") Long publisherId);

    /** 发布人驳回：待确认 → 进行中，让承接人重新提交 */
    @Update("""
            UPDATE task
               SET status  = 'IN_PROGRESS',
                   version = version + 1,
                   updated_at = NOW()
             WHERE id = #{taskId}
               AND status = 'TO_CONFIRM'
               AND publisher_id = #{publisherId}
            """)
    int reject(@Param("taskId") Long taskId, @Param("publisherId") Long publisherId);

    /** 结算：已完成 → 已结算（资金划转成功后才调用） */
    @Update("""
            UPDATE task
               SET status     = 'SETTLED',
                   settled_at = NOW(),
                   version    = version + 1,
                   updated_at = NOW()
             WHERE id = #{taskId}
               AND status = 'COMPLETED'
            """)
    int settle(@Param("taskId") Long taskId);

    /** 发布人取消：待接单 / 进行中 → 已取消 */
    @Update("""
            UPDATE task
               SET status        = 'CANCELLED',
                   closed_at     = NOW(),
                   cancel_reason = #{reason},
                   version       = version + 1,
                   updated_at    = NOW()
             WHERE id = #{taskId}
               AND publisher_id = #{publisherId}
               AND status IN ('WAITING', 'IN_PROGRESS')
            """)
    int cancelByPublisher(@Param("taskId") Long taskId,
                          @Param("publisherId") Long publisherId,
                          @Param("reason") String reason);

    /**
     * 管理员强制下架：任何未终结状态都可以直接取消。
     * <p>不能用 cancelByPublisher —— 那条 SQL 只放行 WAITING/IN_PROGRESS，
     * 争议中 / 待确认的任务会更新 0 行，接口却返回成功，表现为「点了下架任务还在」。
     */
    @Update("""
            UPDATE task
               SET status        = 'CANCELLED',
                   closed_at     = NOW(),
                   cancel_reason = #{reason},
                   version       = version + 1,
                   updated_at    = NOW()
             WHERE id = #{taskId}
               AND status NOT IN ('SETTLED', 'CANCELLED', 'EXPIRED')
            """)
    int adminTakeDown(@Param("taskId") Long taskId, @Param("reason") String reason);

    /**
     * 接单方超时未交付，系统自动关闭任务（只允许 IN_PROGRESS）。
     * <p>与 adminTakeDown 分开：这条是定时任务用的，语义是「接单方违约导致关闭」，
     * 状态守卫收到 IN_PROGRESS，避免把刚提交待确认（TO_CONFIRM）的任务误判关闭。
     */
    @Update("""
            UPDATE task
               SET status        = 'CANCELLED',
                   closed_at     = NOW(),
                   cancel_reason = #{reason},
                   version       = version + 1,
                   updated_at    = NOW()
             WHERE id = #{taskId}
               AND status = 'IN_PROGRESS'
            """)
    int timeoutClose(@Param("taskId") Long taskId, @Param("reason") String reason);

    /**
     * 承接人放弃：任务<b>退回待接取</b>，清掉接单人，报酬继续冻结。
     *
     * <p>这是迁移表之外唯一允许的回退：放弃是接单方单方面的行为，
     * 把发布人的任务一起终止（CANCELLED）等于让一个人放弃就毁掉整条委托。
     * CAS 条件 claimant_id + status 保证「只有当前接单人能放弃、且只成功一次」。
     */
    @Update("""
            UPDATE task
               SET status      = 'WAITING',
                   claimant_id = NULL,
                   claimed_at  = NULL,
                   version     = version + 1,
                   updated_at  = NOW()
             WHERE id = #{taskId}
               AND claimant_id = #{claimantId}
               AND status = 'IN_PROGRESS'
            """)
    int reopen(@Param("taskId") Long taskId, @Param("claimantId") Long claimantId);

    /** 承接人放弃 → 已取消（历史语义，保留给管理员/特殊流程使用） */
    @Update("""
            UPDATE task
               SET status        = 'CANCELLED',
                   closed_at     = NOW(),
                   cancel_reason = #{reason},
                   version       = version + 1,
                   updated_at    = NOW()
             WHERE id = #{taskId}
               AND claimant_id = #{claimantId}
               AND status = 'IN_PROGRESS'
            """)
    int cancelByClaimant(@Param("taskId") Long taskId,
                         @Param("claimantId") Long claimantId,
                         @Param("reason") String reason);

    /** 发起争议：进行中 / 待确认 → 争议中 */
    @Update("""
            UPDATE task
               SET status        = 'DISPUTED',
                   cancel_reason = #{reason},
                   version       = version + 1,
                   updated_at    = NOW()
             WHERE id = #{taskId}
               AND status IN ('IN_PROGRESS', 'TO_CONFIRM')
            """)
    int raiseDispute(@Param("taskId") Long taskId, @Param("reason") String reason);

    /** 管理员裁决：争议中 → 已结算 / 已取消；裁决为完成时补写 confirmed_at 供信誉按完成计 */
    @Update("""
            UPDATE task
               SET status        = #{targetStatus},
                   settled_at    = CASE WHEN #{targetStatus} = 'SETTLED' THEN NOW() ELSE settled_at END,
                   confirmed_at  = CASE WHEN #{targetStatus} = 'SETTLED' AND confirmed_at IS NULL THEN NOW() ELSE confirmed_at END,
                   closed_at     = CASE WHEN #{targetStatus} = 'CANCELLED' THEN NOW() ELSE closed_at END,
                   cancel_reason = #{reason},
                   version       = version + 1,
                   updated_at    = NOW()
             WHERE id = #{taskId}
               AND status = 'DISPUTED'
            """)
    int resolveDispute(@Param("taskId") Long taskId,
                       @Param("targetStatus") String targetStatus,
                       @Param("reason") String reason);

    /** 过期：只处理待接单且已过截止时间的任务，进行中的任务不自动过期 */
    @Update("""
            UPDATE task
               SET status    = 'EXPIRED',
                   closed_at = NOW(),
                   version   = version + 1,
                   updated_at = NOW()
             WHERE id = #{taskId}
               AND status = 'WAITING'
               AND deadline < NOW()
            """)
    int expire(@Param("taskId") Long taskId);

    /** 浏览量原子自增（不会丢更新） */
    @Update("UPDATE task SET view_count = view_count + 1 WHERE id = #{taskId}")
    int incrView(@Param("taskId") Long taskId);

    /** 收藏数原子自增/自减 */
    @Update("UPDATE task SET favor_count = GREATEST(favor_count + #{delta}, 0) WHERE id = #{taskId}")
    int incrFavor(@Param("taskId") Long taskId, @Param("delta") int delta);
}
