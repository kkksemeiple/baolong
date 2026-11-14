package com.baolong.wsw.mapper;

import com.baolong.wsw.entity.WalletTransaction;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface WalletTransactionMapper extends BaseMapper<WalletTransaction> {

    /**
     * 对账用：某账户全部流水之和。
     * 恒等式 available + frozen - debt 必须等于这个值。
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction WHERE account_id = #{accountId}")
    long sumAmount(@Param("accountId") Long accountId);

    /** 窗口内某类业务的流水合计，供统计与信誉计算使用 */
    @Select("""
            SELECT COALESCE(SUM(amount), 0)
              FROM wallet_transaction
             WHERE user_id = #{userId}
               AND biz_type = #{bizType}
               AND created_at >= #{since}
            """)
    long sumByBizSince(@Param("userId") Long userId,
                       @Param("bizType") String bizType,
                       @Param("since") LocalDateTime since);

    /**
     * 全站某类流水的绝对值合计（只看金额，不区分正负）。
     * <p>用于看板的「累计发行」（GRANT 之和）与「累计回收」（PENALTY 之和）。
     */
    @Select("SELECT COALESCE(SUM(ABS(amount)), 0) FROM wallet_transaction WHERE trans_type = #{transType}")
    long sumAbsByType(@Param("transType") String transType);

    /** 某类流水里「正向」金额合计（如冲正退回的钱） */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction WHERE trans_type = #{transType} AND amount > 0")
    long sumPositiveByType(@Param("transType") String transType);

    /** 某类流水里「负向」金额的绝对值合计（如冲正扣回的钱） */
    @Select("SELECT COALESCE(SUM(-amount), 0) FROM wallet_transaction WHERE trans_type = #{transType} AND amount < 0")
    long sumNegativeAbsByType(@Param("transType") String transType);

    /**
     * 某个来源的收入合计。
     * <p>专门用来扣掉「违约金补偿」这类**左手倒右手**的钱：
     * 罚款从 A 扣走、又作为补偿发给 B，这笔钱并没有离开系统，
     * 若把它算进「已回收」，看板的恒等式就会一直差出这部分金额。
     */
    @Select("""
            SELECT COALESCE(SUM(amount), 0)
              FROM wallet_transaction
             WHERE trans_type = 'INCOME' AND origin_type = #{originType} AND amount > 0
            """)
    long sumIncomeByOrigin(@Param("originType") String originType);
}
