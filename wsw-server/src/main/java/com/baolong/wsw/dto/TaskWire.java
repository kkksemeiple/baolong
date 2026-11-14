package com.baolong.wsw.dto;

import com.baolong.wsw.enums.ClaimStatus;
import com.baolong.wsw.enums.TaskStatus;

/**
 * 状态词汇翻译层。
 *
 * <p>设计文档（任务类设计说明 V1.0）用 {@code WAITING / IN_PROGRESS / TO_CONFIRM}
 * 表达「待接单 / 进行中 / 待确认」；而已经交付的前端契约
 * （{@code vue-wsw/src/api/types.ts}）用的是 {@code OPEN / TAKEN / SUBMITTED}。
 *
 * <p>处理原则：<b>内核用设计文档的词汇</b>（枚举、数据库、状态机），
 * <b>只在出参与查询入参这一层翻译成前端词汇</b>。这样既不破坏已交付的前端，
 * 也不用把文档里的状态名改掉；两边靠这张表对齐，改动集中在一个文件里。
 *
 * <p>对照表：
 * <pre>
 * 设计文档              前端契约        含义
 * WAITING               OPEN            待接单
 * IN_PROGRESS           TAKEN           进行中
 * TO_CONFIRM            SUBMITTED       待确认
 * COMPLETED             COMPLETED       已完成
 * SETTLED               SETTLED         已结算
 * DISPUTED              DISPUTED        争议中
 * CANCELLED             CANCELLED       已取消
 * EXPIRED               EXPIRED         已过期
 * </pre>
 */
public final class TaskWire {

    private TaskWire() {
    }

    /** 内核状态 → 前端状态 */
    public static String toWire(String internal) {
        if (internal == null) {
            return null;
        }
        return switch (internal) {
            case "WAITING" -> "OPEN";
            case "IN_PROGRESS" -> "TAKEN";
            case "TO_CONFIRM" -> "SUBMITTED";
            default -> internal;
        };
    }

    /** 前端状态 → 内核状态，兼容直接传内核词汇的情况 */
    public static String fromWire(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        return switch (wire.toUpperCase()) {
            case "OPEN", "WAITING" -> "WAITING";
            case "TAKEN", "IN_PROGRESS" -> "IN_PROGRESS";
            case "SUBMITTED", "TO_CONFIRM" -> "TO_CONFIRM";
            default -> wire.toUpperCase();
        };
    }

    /** 内核任务状态枚举 → 前端状态 */
    public static String toWire(TaskStatus status) {
        return status == null ? null : toWire(status.name());
    }

    /**
     * 接单记录状态 → 前端状态。
     * <p>前端只定义了 ACTIVE / FINISHED / CANCELLED 三种，内核多一个「已提交」，
     * 在前端语义里它仍属于「进行中」的接单，所以映射到 ACTIVE。
     */
    public static String claimToWire(String internal) {
        if (internal == null) {
            return null;
        }
        return switch (internal) {
            case "IN_PROGRESS", "SUBMITTED" -> "ACTIVE";
            case "COMPLETED" -> "FINISHED";
            default -> internal;
        };
    }

    /** 前端接单状态 → 内核状态 */
    public static String claimFromWire(String wire) {
        if (wire == null) {
            return null;
        }
        return switch (wire.toUpperCase()) {
            case "ACTIVE", "IN_PROGRESS" -> "IN_PROGRESS";
            case "FINISHED", "COMPLETED" -> "COMPLETED";
            default -> wire.toUpperCase();
        };
    }

    public static String claimToWire(ClaimStatus status) {
        return status == null ? null : claimToWire(status.name());
    }
}
