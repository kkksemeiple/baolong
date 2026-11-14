package com.baolong.wsw.controller;

import com.baolong.wsw.common.CursorPage;
import com.baolong.wsw.common.Result;
import com.baolong.wsw.dto.TaskDto;
import com.baolong.wsw.enums.TaskType;
import com.baolong.wsw.security.AuthContext;
import com.baolong.wsw.service.RecommendService;
import com.baolong.wsw.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务相关接口（对应前端 {@code src/api/task.ts}）。
 *
 * <p>状态出参经过 {@link com.baolong.wsw.dto.TaskWire} 翻译成前端词汇，
 * 所以这里写 {@code status=OPEN} 就是前端看到的样子。
 */
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final RecommendService recommendService;

    // ------------------------------------------------------------------ 查询

    /** 任务大厅列表（游标分页） */
    @GetMapping
    public Result<CursorPage<TaskDto.TaskVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long minReward,
            @RequestParam(required = false) Long maxReward,
            @RequestParam(required = false) String sort,
            /** 前端 types.ts 的 TaskQuery 用的是 sortBy（DEFAULT/REWARD/LATEST/DEADLINE），
             *  这里两个名字都收，谁传了用谁——否则排序参数会被 Spring 静默丢弃。 */
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "12") Integer size) {

        TaskDto.TaskQuery query =
                new TaskDto.TaskQuery(keyword, type, status, minReward, maxReward,
                        pickSort(sortBy, sort), cursor, size);
        return Result.ok(taskService.list(query, AuthContext.userIdOrNull()));
    }

    /** 搜索：与列表同一套查询，前端单独调用便于做埋点 */
    @GetMapping("/search")
    public Result<CursorPage<TaskDto.TaskVO>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long minReward,
            @RequestParam(required = false) Long maxReward,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "12") Integer size) {

        TaskDto.TaskQuery query =
                new TaskDto.TaskQuery(keyword, type, null, minReward, maxReward,
                        pickSort(sortBy, sort), cursor, size);
        return Result.ok(taskService.list(query, AuthContext.userIdOrNull()));
    }

    /** sortBy 优先，其次老的 sort；都没有就交给 service 走默认 */
    private static String pickSort(String sortBy, String sort) {
        if (sortBy != null && !sortBy.isBlank()) {
            return sortBy;
        }
        return (sort == null || sort.isBlank()) ? null : sort;
    }

    /** 个性化推荐（偏好推荐开关打开时才返回结果） */
    @GetMapping("/recommend")
    public Result<List<TaskDto.TaskVO>> recommend(
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "6") Integer size) {
        return Result.ok(recommendService.recommend(AuthContext.userIdOrNull(), type, size));
    }

    /** 任务分类（前端发布表单与筛选下拉用） */
    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (TaskType type : TaskType.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", type.name());
            item.put("label", type.getLabel());
            result.add(item);
        }
        return Result.ok(result);
    }

    /** 我发布 / 我接取的任务 */
    @GetMapping("/mine")
    public Result<CursorPage<TaskDto.TaskVO>> mine(
            @RequestParam(defaultValue = "publisher") String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(taskService.mine(AuthContext.requireUserId(), role, status, cursor, size));
    }

    /** 任务详情（游客可看，计入浏览量） */
    @GetMapping("/{id}")
    public Result<TaskDto.TaskVO> detail(@PathVariable Long id) {
        return Result.ok(taskService.detail(id, AuthContext.userIdOrNull(), true));
    }

    // ------------------------------------------------------------------ 发布与修改

    /** 发布任务：报酬立刻冻结 */
    @PostMapping
    public Result<TaskDto.TaskVO> publish(@Valid @RequestBody TaskDto.CreateReq req) {
        return Result.ok(taskService.publish(AuthContext.requireUserId(), req));
    }

    /** 修改任务：只有待接单状态可改；改报酬会先解冻旧额再冻结新额 */
    @PutMapping("/{id}")
    public Result<TaskDto.TaskVO> update(@PathVariable Long id,
                                         @Valid @RequestBody TaskDto.UpdateReq req) {
        return Result.ok(taskService.update(AuthContext.requireUserId(), id, req));
    }

    // ------------------------------------------------------------------ 状态流转

    /** 抢单：带版本号做 CAS，200 并发只成功一条 */
    @PostMapping("/{id}/claim")
    public Result<TaskDto.TaskVO> claim(@PathVariable Long id,
                                       @RequestParam(required = false) Integer version) {
        return Result.ok(taskService.claim(AuthContext.requireUserId(), id, version));
    }

    /** 提交完成 */
    @PostMapping("/{id}/submit")
    public Result<TaskDto.TaskVO> submit(@PathVariable Long id,
                                         @Valid @RequestBody TaskDto.SubmitReq req) {
        return Result.ok(taskService.submit(AuthContext.requireUserId(), id, req));
    }

    /** 确认完成 → 结算（解冻委托方、打款接单方、双方重算信誉） */
    @PostMapping("/{id}/confirm")
    public Result<TaskDto.TaskVO> confirm(@PathVariable Long id,
                                          @RequestBody(required = false) TaskDto.ConfirmReq req) {
        TaskDto.ConfirmReq body = req == null ? new TaskDto.ConfirmReq(null, null) : req;
        return Result.ok(taskService.confirm(AuthContext.requireUserId(), id, body));
    }

    /** 打回重做 */
    @PostMapping("/{id}/reject")
    public Result<TaskDto.TaskVO> reject(@PathVariable Long id,
                                         @RequestBody(required = false) TaskDto.ConfirmReq req) {
        TaskDto.ConfirmReq body = req == null ? new TaskDto.ConfirmReq(null, null) : req;
        return Result.ok(taskService.reject(AuthContext.requireUserId(), id, body));
    }

    /** 发布人取消（仅待接单），全额解冻 */
    @PostMapping("/{id}/cancel")
    public Result<TaskDto.TaskVO> cancel(@PathVariable Long id,
                                         @RequestBody(required = false) TaskDto.CancelReq req) {
        String reason = req == null ? null : req.reason();
        return Result.ok(taskService.cancelByPublisher(AuthContext.requireUserId(), id, reason));
    }

    /** 接单方放弃：任务终止，放弃方承担违约金 */
    @PostMapping("/{id}/abandon")
    public Result<TaskDto.TaskVO> abandon(@PathVariable Long id,
                                          @RequestBody(required = false) TaskDto.CancelReq req) {
        String reason = req == null ? null : req.reason();
        return Result.ok(taskService.abandon(AuthContext.requireUserId(), id, reason));
    }

    /** 发起争议 */
    @PostMapping("/{id}/dispute")
    public Result<TaskDto.TaskVO> dispute(@PathVariable Long id,
                                          @Valid @RequestBody TaskDto.DisputeReq req) {
        return Result.ok(taskService.raiseDispute(AuthContext.requireUserId(), id, req.reason()));
    }

    // ------------------------------------------------------------------ 收藏

    @PostMapping("/{id}/favorite")
    public Result<Void> favorite(@PathVariable Long id) {
        taskService.favorite(AuthContext.requireUserId(), id);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}/favorite")
    public Result<Void> unfavorite(@PathVariable Long id) {
        taskService.unfavorite(AuthContext.requireUserId(), id);
        return Result.ok(null);
    }
}
