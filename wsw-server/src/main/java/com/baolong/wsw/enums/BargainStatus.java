package com.baolong.wsw.enums;

/**
 * 议价记录状态（对应 bargain_record.status）。
 * <p>规则：同一任务同一时刻至多一条 PENDING；
 * 被同意后置 ACCEPTED，其余 PENDING 置 INVALID；
 * 任务被接单/取消/过期时，所有 PENDING 一并置 INVALID（失效）。
 */
public enum BargainStatus {

    PENDING("待处理"),
    ACCEPTED("已同意"),
    REJECTED("已拒绝"),
    INVALID("已失效");

    private final String label;

    BargainStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
