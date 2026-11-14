package com.baolong.wsw.enums;

/**
 * 资金变动类型：钱是怎么动的。
 */
public enum TransType {

    /** 冻结（发布任务时把报酬锁住） */
    FREEZE,
    /** 解冻（取消 / 过期，钱退回可用） */
    UNFREEZE,
    /** 收入（任务完成，接单方到账） */
    INCOME,
    /** 支出（任务结算，委托方冻结转出） */
    PAY,
    /** 违约扣罚 */
    PENALTY,
    /** 管理员发放 */
    GRANT,
    /** 冲正（管理员纠错） */
    REVERSAL
}
