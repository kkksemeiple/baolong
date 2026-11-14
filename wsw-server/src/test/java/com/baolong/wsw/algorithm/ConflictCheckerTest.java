package com.baolong.wsw.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 白盒测试④：时间/地点冲突检测。
 *
 * <p>这个模块必须「只读且无副作用」——它只回答「能不能接」，
 * 绝不改任何状态；真正抢单靠数据库的条件更新兜底。
 */
@DisplayName("白盒-冲突检测：ConflictChecker")
class ConflictCheckerTest {

    private static final LocalDateTime T10 = LocalDateTime.of(2026, 11, 10, 10, 0);
    private static final LocalDateTime T12 = LocalDateTime.of(2026, 11, 10, 12, 0);
    private static final LocalDateTime T14 = LocalDateTime.of(2026, 11, 10, 14, 0);

    @Test
    @DisplayName("没有任何已接任务时直接放行")
    void emptyOccupied() {
        ConflictChecker.Result result = ConflictChecker.check(
                new ConflictChecker.Candidate(T10, T12, "图书馆", true), List.of());
        assertEquals(ConflictChecker.Level.NONE, result.level());
        assertFalse(result.blocked());
    }

    @Test
    @DisplayName("必须独占的任务时间重叠 → BLOCK 阻断")
    void exclusiveOverlapBlocks() {
        List<ConflictChecker.Occupied> occupied = List.of(
                new ConflictChecker.Occupied(1L, "高数辅导", T10, T12, "图书馆"));
        ConflictChecker.Result result = ConflictChecker.check(
                new ConflictChecker.Candidate(T10.plusMinutes(30), T12, "二食堂", true), occupied);

        assertEquals(ConflictChecker.Level.BLOCK, result.level());
        assertTrue(result.blocked());
        assertEquals(1L, result.taskId());
        assertTrue(result.message().contains("高数辅导"));
    }

    @Test
    @DisplayName("不独占的任务时间重叠 → 只给 WARN 提示")
    void nonExclusiveOverlapWarns() {
        List<ConflictChecker.Occupied> occupied = List.of(
                new ConflictChecker.Occupied(2L, "取快递", T10, T12, "菜鸟驿站"));
        ConflictChecker.Result result = ConflictChecker.check(
                new ConflictChecker.Candidate(T10, T12, "图书馆", false), occupied);

        assertEquals(ConflictChecker.Level.WARN, result.level());
        assertFalse(result.blocked());
    }

    @Test
    @DisplayName("时间不重叠、同地点且相隔 2 小时内 → WARN")
    void neighborLocationWarns() {
        List<ConflictChecker.Occupied> occupied = List.of(
                new ConflictChecker.Occupied(3L, "搬行李", T10, T12, "1 号楼"));
        // 12:00 结束，13:30 开始，相隔 1.5 小时，同一地点
        ConflictChecker.Result result = ConflictChecker.check(
                new ConflictChecker.Candidate(T12.plusMinutes(90), T14, "1 号楼", true), occupied);

        assertEquals(ConflictChecker.Level.WARN, result.level());
        assertFalse(result.blocked());
    }

    @Test
    @DisplayName("时间不重叠、地点也不同 → 放行")
    void differentPlacePasses() {
        List<ConflictChecker.Occupied> occupied = List.of(
                new ConflictChecker.Occupied(4L, "取快递", T10, T12, "菜鸟驿站"));
        // 12:00 接上一条任务结束，时间不重叠，地点也不同
        ConflictChecker.Result result = ConflictChecker.check(
                new ConflictChecker.Candidate(T12, T14, "图书馆", true), occupied);

        assertEquals(ConflictChecker.Level.NONE, result.level());
    }

    @Test
    @DisplayName("没有明确时间窗的任务不参与时间冲突判定")
    void nullWindowSkips() {
        ConflictChecker.Result result = ConflictChecker.check(
                new ConflictChecker.Candidate(null, null, "图书馆", true),
                List.of(new ConflictChecker.Occupied(5L, "随便", T10, T12, "图书馆")));
        assertEquals(ConflictChecker.Level.NONE, result.level());
    }
}
