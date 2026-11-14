package com.baolong.wsw.algorithm;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 冲突检测（ConflictChecker）。
 * <p><b>本模块只读、无副作用、不写库</b>：它只回答「能不能接」，
 * 真正的抢占由 {@code TaskMapper#claim} 的条件更新 + {@code task_claim} 唯一约束兜底。
 * 把「判断」和「落库」分开，是为了让判断逻辑可以纯函数测试。
 *
 * <p>两种结果：
 * <ul>
 *   <li>{@code BLOCK}：时间重叠且策略为「必须独占」，直接阻断</li>
 *   <li>{@code WARN}：地点邻近等弱冲突，返回提示但仍允许接单</li>
 * </ul>
 */
public final class ConflictChecker {

    /** 地点邻近判定的时间容差（小时） */
    public static final long NEIGHBOR_HOURS = 2;

    public enum Level {
        NONE, WARN, BLOCK
    }

    /**
     * @param level   冲突级别
     * @param message 给用户看的话术
     * @param taskId  冲突的任务 ID（NONE 时为 null）
     */
    public record Result(Level level, String message, Long taskId) {

        public static Result none() {
            return new Result(Level.NONE, null, null);
        }

        public boolean blocked() {
            return level == Level.BLOCK;
        }
    }

    /**
     * 已占用的时间段（承接人手上已有的任务）。
     */
    public record Occupied(long taskId, String title, LocalDateTime start, LocalDateTime end,
                           String location) {
    }

    /**
     * 待接任务的时间窗。
     *
     * @param exclusive 是否必须独占（来自 {@link TaskTypeMeta#overlapPolicy()}）
     */
    public record Candidate(LocalDateTime start, LocalDateTime end, String location,
                            boolean exclusive) {
    }

    private ConflictChecker() {
    }

    public static Result check(Candidate candidate, List<Occupied> occupied) {
        if (candidate == null || occupied == null || occupied.isEmpty()) {
            return Result.none();
        }
        if (candidate.start() == null || candidate.end() == null) {
            // 没有明确时间窗的任务不参与时间冲突判定
            return Result.none();
        }

        for (Occupied task : occupied) {
            if (task.start() == null || task.end() == null) {
                continue;
            }
            if (overlaps(candidate.start(), candidate.end(), task.start(), task.end())) {
                if (candidate.exclusive()) {
                    return new Result(Level.BLOCK,
                            "与已接任务「" + task.title() + "」时间重叠，无法接单", task.taskId());
                }
                return new Result(Level.WARN,
                        "与已接任务「" + task.title() + "」时间重叠，请自行确认是否赶得上", task.taskId());
            }
        }

        // 时间不重叠，再看地点是否邻近（弱冲突，只提示）
        for (Occupied task : occupied) {
            if (task.start() == null || task.location() == null || candidate.location() == null) {
                continue;
            }
            if (!task.location().equals(candidate.location())) {
                continue;
            }
            long gap = Math.min(
                    Math.abs(Duration.between(candidate.start(), task.end()).toHours()),
                    Math.abs(Duration.between(task.start(), candidate.end()).toHours()));
            if (gap <= NEIGHBOR_HOURS) {
                return new Result(Level.WARN,
                        "与「" + task.title() + "」在同一地点且时间相邻，注意安排", task.taskId());
            }
        }
        return Result.none();
    }

    /** 两个闭区间是否重叠 */
    private static boolean overlaps(LocalDateTime s1, LocalDateTime e1,
                                    LocalDateTime s2, LocalDateTime e2) {
        return s1.isBefore(e2) && s2.isBefore(e1);
    }
}
