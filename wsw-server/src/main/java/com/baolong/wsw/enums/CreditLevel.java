package com.baolong.wsw.enums;

/**
 * 信用等级，由信用分映射而来。
 * <p>阈值与信誉评分模块的文档一一对应：
 * <pre>
 *   &gt;=110 EXCELLENT  优先推荐、可接高价单
 *   &gt;=95  GOOD
 *   &gt;=80  NORMAL
 *   &gt;=60  LIMITED    只能接小额单
 *   &lt;60   FROZEN     不可接单
 * </pre>
 */
public enum CreditLevel {

    EXCELLENT,
    GOOD,
    NORMAL,
    LIMITED,
    FROZEN;

    public static CreditLevel of(double score) {
        if (score >= 110) {
            return EXCELLENT;
        }
        if (score >= 95) {
            return GOOD;
        }
        if (score >= 80) {
            return NORMAL;
        }
        if (score >= 60) {
            return LIMITED;
        }
        return FROZEN;
    }

    /** 是否还能接单 */
    public boolean canAccept() {
        return this == EXCELLENT || this == GOOD || this == NORMAL;
    }
}
