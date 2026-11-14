package com.baolong.wsw.algorithm;

import com.baolong.wsw.dto.TaskWire;
import com.baolong.wsw.enums.ClaimStatus;
import com.baolong.wsw.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 白盒测试①：任务状态机的迁移合法性。
 *
 * <p>状态机是整个交易闭环的骨架，一旦允许非法迁移（比如「已结算」又变回「待接单」），
 * 钱和任务状态就会错位。这里把文档里的迁移表逐条钉死。
 */
@DisplayName("白盒-状态机：TaskStatus 迁移")
class TaskStatusTest {

    @Test
    @DisplayName("待接单只能走向「进行中 / 已取消 / 已过期」")
    void waitingTransitions() {
        assertTrue(TaskStatus.WAITING.canTransitTo(TaskStatus.IN_PROGRESS));
        assertTrue(TaskStatus.WAITING.canTransitTo(TaskStatus.CANCELLED));
        assertTrue(TaskStatus.WAITING.canTransitTo(TaskStatus.EXPIRED));

        assertFalse(TaskStatus.WAITING.canTransitTo(TaskStatus.SETTLED));
        assertFalse(TaskStatus.WAITING.canTransitTo(TaskStatus.COMPLETED));
        assertFalse(TaskStatus.WAITING.canTransitTo(TaskStatus.TO_CONFIRM));
    }

    @Test
    @DisplayName("进行中不允许回到待接单（放弃接单即终止）")
    void inProgressCannotGoBack() {
        assertFalse(TaskStatus.IN_PROGRESS.canTransitTo(TaskStatus.WAITING));
        assertTrue(TaskStatus.IN_PROGRESS.canTransitTo(TaskStatus.TO_CONFIRM));
        assertTrue(TaskStatus.IN_PROGRESS.canTransitTo(TaskStatus.DISPUTED));
    }

    @Test
    @DisplayName("终态不再接受任何迁移")
    void finalStates() {
        assertTrue(TaskStatus.SETTLED.isFinal());
        assertTrue(TaskStatus.CANCELLED.isFinal());
        assertTrue(TaskStatus.EXPIRED.isFinal());
        assertFalse(TaskStatus.WAITING.isFinal());

        for (TaskStatus target : TaskStatus.values()) {
            assertFalse(TaskStatus.SETTLED.canTransitTo(target),
                    "已结算不应再迁移到 " + target);
        }
    }

    @Test
    @DisplayName("只有占用报酬的状态才算冻结")
    void frozenMoneyStates() {
        assertTrue(TaskStatus.WAITING.holdsFrozenMoney());
        assertTrue(TaskStatus.IN_PROGRESS.holdsFrozenMoney());
        assertTrue(TaskStatus.TO_CONFIRM.holdsFrozenMoney());
        assertTrue(TaskStatus.DISPUTED.holdsFrozenMoney());

        assertFalse(TaskStatus.SETTLED.holdsFrozenMoney());
        assertFalse(TaskStatus.CANCELLED.holdsFrozenMoney());
        assertFalse(TaskStatus.EXPIRED.holdsFrozenMoney());
    }

    @Test
    @DisplayName("内核词汇 ↔ 前端词汇的翻译不能有遗漏")
    void wireTranslation() {
        assertEquals("OPEN", TaskWire.toWire(TaskStatus.WAITING));
        assertEquals("TAKEN", TaskWire.toWire(TaskStatus.IN_PROGRESS));
        assertEquals("SUBMITTED", TaskWire.toWire(TaskStatus.TO_CONFIRM));
        assertEquals("COMPLETED", TaskWire.toWire(TaskStatus.COMPLETED));
        assertEquals("SETTLED", TaskWire.toWire(TaskStatus.SETTLED));

        assertEquals(TaskStatus.WAITING.name(), TaskWire.fromWire("OPEN"));
        assertEquals(TaskStatus.IN_PROGRESS.name(), TaskWire.fromWire("TAKEN"));

        assertEquals("ACTIVE", TaskWire.claimToWire(ClaimStatus.IN_PROGRESS));
        assertEquals("FINISHED", TaskWire.claimToWire(ClaimStatus.COMPLETED));
    }

    @Test
    @DisplayName("每个状态都有中文标签")
    void everyStatusHasLabel() {
        for (TaskStatus status : TaskStatus.values()) {
            assertTrue(status.label() != null && !status.label().isBlank(),
                    status + " 缺少中文标签");
        }
    }
}
