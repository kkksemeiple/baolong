package com.baolong.wsw.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 任务八状态（与《任务类设计说明》第 6 章一致）。
 *
 * <pre>
 * WAITING ──接单──▶ IN_PROGRESS ──提交凭证──▶ TO_CONFIRM ──确认──▶ COMPLETED ──结算──▶ SETTLED
 *    │                  │                        │                    │
 *    ├─取消─▶ CANCELLED ◀─协商取消─┘             ├─争议─▶ DISPUTED ◀──┴─争议─┘
 *    └─到期─▶ EXPIRED                            └─争议─▶ DISPUTED
 *                                        DISPUTED ──裁决──▶ SETTLED / CANCELLED
 * </pre>
 *
 * 终态（SETTLED / CANCELLED / EXPIRED）不再迁移；其余非法迁移统一报 40901。
 */
public enum TaskStatus {

    WAITING("待接单", false, true),
    IN_PROGRESS("进行中", false, true),
    TO_CONFIRM("待确认", false, true),
    COMPLETED("已完成", false, true),
    SETTLED("已结算", true, false),
    DISPUTED("争议中", false, true),
    CANCELLED("已取消", true, false),
    EXPIRED("已过期", true, false);

    private final String label;
    private final boolean finalState;
    /** 该状态下报酬是否仍冻结在委托方钱包里（用于对账） */
    private final boolean holdsFrozenMoney;

    TaskStatus(String label, boolean finalState, boolean holdsFrozenMoney) {
        this.label = label;
        this.finalState = finalState;
        this.holdsFrozenMoney = holdsFrozenMoney;
    }

    private static final Map<TaskStatus, Set<TaskStatus>> TRANSITIONS;

    static {
        Map<TaskStatus, Set<TaskStatus>> m = new EnumMap<>(TaskStatus.class);
        m.put(WAITING, EnumSet.of(IN_PROGRESS, CANCELLED, EXPIRED));
        m.put(IN_PROGRESS, EnumSet.of(TO_CONFIRM, CANCELLED, DISPUTED));
        m.put(TO_CONFIRM, EnumSet.of(COMPLETED, IN_PROGRESS, DISPUTED));
        m.put(COMPLETED, EnumSet.of(SETTLED, DISPUTED));
        m.put(DISPUTED, EnumSet.of(SETTLED, CANCELLED));
        m.put(SETTLED, EnumSet.noneOf(TaskStatus.class));
        m.put(CANCELLED, EnumSet.noneOf(TaskStatus.class));
        m.put(EXPIRED, EnumSet.noneOf(TaskStatus.class));
        TRANSITIONS = Collections.unmodifiableMap(m);
    }

    public String label() {
        return label;
    }

    public boolean isFinal() {
        return finalState;
    }

    public boolean holdsFrozenMoney() {
        return holdsFrozenMoney;
    }

    public boolean canTransitTo(TaskStatus target) {
        if (target == null) {
            return false;
        }
        Set<TaskStatus> allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    /** 全部合法迁移，供状态机白盒测试与文档生成使用 */
    public static Map<TaskStatus, Set<TaskStatus>> transitions() {
        return TRANSITIONS;
    }

    public static TaskStatus of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (TaskStatus s : values()) {
            if (s.name().equalsIgnoreCase(value)) {
                return s;
            }
        }
        return null;
    }
}
