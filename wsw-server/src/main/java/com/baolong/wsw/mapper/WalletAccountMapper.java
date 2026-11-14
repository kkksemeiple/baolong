package com.baolong.wsw.mapper;

import com.baolong.wsw.entity.WalletAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 钱包账户 Mapper。
 * <p><b>关键</b>：所有余额变动都写成「带条件的原子 UPDATE」，不做「先查后改再写回」。
 * 条件不满足时影响行数为 0，业务层据此判断余额不足 —— 这样即使没有分布式锁，
 * 并发扣款也不会把余额扣成负数。
 */
@Mapper
public interface WalletAccountMapper extends BaseMapper<WalletAccount> {

    /** 可用 → 冻结（发布任务）。要求可用余额足够 */
    @Update("""
            UPDATE wallet_account
               SET available = available - #{amt},
                   frozen    = frozen + #{amt},
                   version   = version + 1,
                   updated_at = NOW()
             WHERE user_id = #{userId}
               AND available >= #{amt}
            """)
    int freeze(@Param("userId") Long userId, @Param("amt") long amt);

    /** 冻结 → 可用（取消/过期退款） */
    @Update("""
            UPDATE wallet_account
               SET frozen    = frozen - #{amt},
                   available = available + #{amt},
                   version   = version + 1,
                   updated_at = NOW()
             WHERE user_id = #{userId}
               AND frozen >= #{amt}
            """)
    int unfreeze(@Param("userId") Long userId, @Param("amt") long amt);

    /** 冻结直接扣除（结算给接单方时，委托方这一侧） */
    @Update("""
            UPDATE wallet_account
               SET frozen    = frozen - #{amt},
                   version   = version + 1,
                   updated_at = NOW()
             WHERE user_id = #{userId}
               AND frozen >= #{amt}
            """)
    int consumeFrozen(@Param("userId") Long userId, @Param("amt") long amt);

    /** 可用余额增加（接单方到账、管理员发币、注册赠送） */
    @Update("""
            UPDATE wallet_account
               SET available = available + #{amt},
                   version   = version + 1,
                   updated_at = NOW()
             WHERE user_id = #{userId}
            """)
    int addAvailable(@Param("userId") Long userId, @Param("amt") long amt);

    /** 欠款增加（违约金不足时记账） */
    @Update("""
            UPDATE wallet_account
               SET debt    = debt + #{amt},
                   version = version + 1,
                   updated_at = NOW()
             WHERE user_id = #{userId}
            """)
    int addDebt(@Param("userId") Long userId, @Param("amt") long amt);

    /** 可用余额扣减（有欠款时优先还款场景） */
    @Update("""
            UPDATE wallet_account
               SET available = available - #{amt},
                   version   = version + 1,
                   updated_at = NOW()
             WHERE user_id = #{userId}
               AND available >= #{amt}
            """)
    int deductAvailable(@Param("userId") Long userId, @Param("amt") long amt);

    /** 欠款减少 */
    @Update("""
            UPDATE wallet_account
               SET debt    = debt - #{amt},
                   version = version + 1,
                   updated_at = NOW()
             WHERE user_id = #{userId}
               AND debt >= #{amt}
            """)
    int reduceDebt(@Param("userId") Long userId, @Param("amt") long amt);
}
