package com.baolong.wsw.enums;

/**
 * 仲裁裁决结果。
 */
public enum JudgeResult {

    /** 支持委托方：冻结全额退回 */
    PUBLISHER,
    /** 支持接单方：冻结全额划转给接单方 */
    TAKER,
    /** 部分支持：按比例拆分 */
    PARTIAL,
    /** 证据不足：按原状处理 */
    INSUFFICIENT
}
