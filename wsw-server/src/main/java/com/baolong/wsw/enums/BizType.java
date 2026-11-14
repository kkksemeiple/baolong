package com.baolong.wsw.enums;

/**
 * 资金流水的业务来源：钱是因为什么动的。
 * <p>与 {@link TransType} 是两个维度——前者答「因为什么事」，后者答「怎么动」。
 */
public enum BizType {

    /** 任务相关（发布冻结、完成结算、取消解冻） */
    TASK,
    /** 争议裁决 */
    DISPUTE,
    /** 管理员操作（发币、冲正） */
    ADMIN,
    /** 注册赠送 */
    REGISTER
}
