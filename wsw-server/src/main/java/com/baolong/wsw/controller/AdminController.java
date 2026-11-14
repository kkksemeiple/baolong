package com.baolong.wsw.controller;

import com.baolong.wsw.common.PageResult;
import com.baolong.wsw.common.Result;
import com.baolong.wsw.dto.AdminDto;
import com.baolong.wsw.security.AuthContext;
import com.baolong.wsw.service.AdminService;
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

import java.util.List;
import java.util.Map;

/**
 * 管理后台接口（对应前端 {@code src/api/admin.ts} 的 23 个方法）。
 * <p>整个 {@code /admin/**} 由 {@code AuthInterceptor} 统一要求 ADMIN / SADMIN 角色。
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ------------------------------------------------------------------ 概览

    @GetMapping("/stats/overview")
    public Result<AdminDto.OverviewVO> overview() {
        return Result.ok(adminService.overview());
    }

    @GetMapping("/stats/trend")
    public Result<List<AdminDto.TrendPoint>> trend(
            @RequestParam(required = false, defaultValue = "7") Integer days) {
        return Result.ok(adminService.trend(days == null ? 7 : days));
    }

    // ------------------------------------------------------------------ 用户

    @GetMapping("/users")
    public Result<PageResult<AdminDto.AdminUserVO>> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(adminService.users(keyword, status, page, size));
    }

    @PutMapping("/users/{id}/ban")
    public Result<Void> ban(@PathVariable Long id,
                            @RequestBody(required = false) AdminDto.BanReq req) {
        adminService.ban(AuthContext.requireUserId(), id, req == null ? null : req.reason());
        return Result.ok(null);
    }

    @PutMapping("/users/{id}/unban")
    public Result<Void> unban(@PathVariable Long id) {
        adminService.unban(AuthContext.requireUserId(), id);
        return Result.ok(null);
    }

    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(AuthContext.requireUserId(), id);
        return Result.ok(null);
    }

    /** 实名认证审核：兼容前端 {@code {pass, reason}} 与 {@code {approve, reason}} 两种写法 */
    @PutMapping("/users/{userId}/student-cert")
    public Result<Void> auditCert(@PathVariable Long userId,
                                  @RequestBody AdminDto.CertAuditReq req) {
        Boolean approve = req.approve() != null ? req.approve() : req.pass();
        if (approve == null) {
            throw com.baolong.wsw.common.BizException.badRequest("请指定是通过还是驳回");
        }
        adminService.auditCert(AuthContext.requireUserId(), userId, approve, req.reason());
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ 内容审核

    @GetMapping("/audits")
    public Result<PageResult<AdminDto.AuditVO>> audits(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(adminService.audits(status, page, size));
    }

    @PostMapping("/audits/{id}/approve")
    public Result<Void> approveAudit(@PathVariable Long id,
                                     @RequestBody(required = false) AdminDto.AuditHandleReq req) {
        adminService.handleAudit(AuthContext.requireUserId(), id, true,
                req == null ? null : req.reason());
        return Result.ok(null);
    }

    @PostMapping("/audits/{id}/reject")
    public Result<Void> rejectAudit(@PathVariable Long id,
                                    @RequestBody(required = false) AdminDto.AuditHandleReq req) {
        adminService.handleAudit(AuthContext.requireUserId(), id, false,
                req == null ? null : req.reason());
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ 举报

    @GetMapping("/reports")
    public Result<PageResult<AdminDto.ReportVO>> reports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(adminService.reports(status, page, size));
    }

    @PostMapping("/reports/{id}/handle")
    public Result<Void> handleReport(@PathVariable Long id,
                                     @Valid @RequestBody AdminDto.ReportHandleReq req) {
        adminService.handleReport(AuthContext.requireUserId(), id,
                Boolean.TRUE.equals(req.pass()), req.result(), req.penalty());
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ 争议

    @GetMapping("/disputes")
    public Result<PageResult<AdminDto.DisputeVO>> disputes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(adminService.disputes(status, page, size));
    }

    @PostMapping("/disputes/{id}/judge")
    public Result<Void> judge(@PathVariable Long id, @Valid @RequestBody AdminDto.JudgeReq req) {
        adminService.judgeDispute(AuthContext.requireUserId(), id, req.result(), req.remark());
        return Result.ok(null);
    }

    /** 删除已裁决的历史争议记录（只清记录，不回滚资金） */
    @DeleteMapping("/disputes/{id}")
    public Result<Void> deleteDispute(@PathVariable Long id) {
        adminService.deleteDispute(AuthContext.requireUserId(), id);
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ 任务

    @GetMapping("/tasks")
    public Result<PageResult<AdminDto.AdminTaskVO>> tasks(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(adminService.tasks(keyword, status, page, size));
    }

    @PostMapping("/tasks/{id}/offline")
    public Result<Void> offline(@PathVariable Long id,
                                @RequestBody(required = false) AdminDto.BanReq req) {
        adminService.offlineTask(AuthContext.requireUserId(), id, req == null ? null : req.reason());
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ 帖子管理

    /** 帖子列表：关键词搜标题、状态筛选（NORMAL / OFFLINE / DELETED） */
    @GetMapping("/posts")
    public Result<PageResult<AdminDto.AdminPostVO>> posts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(adminService.posts(keyword, status, page, size));
    }

    @PostMapping("/posts/{id}/offline")
    public Result<Void> offlinePost(@PathVariable Long id,
                                    @RequestBody(required = false) AdminDto.BanReq req) {
        adminService.offlinePost(AuthContext.requireUserId(), id, req == null ? null : req.reason());
        return Result.ok(null);
    }

    @PostMapping("/posts/{id}/restore")
    public Result<Void> restorePost(@PathVariable Long id,
                                    @RequestBody(required = false) AdminDto.BanReq req) {
        adminService.restorePost(AuthContext.requireUserId(), id, req == null ? null : req.reason());
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ 屋币

    /** 资金流水查询：后台冲正时按用户 / 类型 / 流水号或标题筛选，分页返回 */
    @GetMapping("/coins/transactions")
    public Result<com.baolong.wsw.common.PageResult<AdminDto.RecentTransactionVO>> recentTransactions(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String transType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(adminService.transactions(userId, transType, keyword,
                page == null ? 1 : page, size == null ? 10 : size));
    }

    @PostMapping("/coins/issue")
    public Result<Void> issue(@Valid @RequestBody AdminDto.IssueReq req) {
        adminService.issueCoins(AuthContext.requireUserId(), req.userId(), req.username(),
                req.amount(), req.reason());
        return Result.ok(null);
    }

    @PostMapping("/coins/issue-batch")
    public Result<Map<String, Integer>> issueBatch(@Valid @RequestBody AdminDto.IssueBatchReq req) {
        int success = adminService.issueCoinsBatch(AuthContext.requireUserId(),
                req.userIds(), req.amount(), req.reason());
        return Result.ok(Map.of("success", success, "total", req.userIds().size()));
    }

    @PostMapping("/coins/reverse")
    public Result<Void> reverse(@Valid @RequestBody AdminDto.ReverseReq req) {
        adminService.reverseCoins(AuthContext.requireUserId(), req.transactionId(), req.reason());
        return Result.ok(null);
    }

    /** 全平台对账：返回异常账户列表，空列表即全站平衡 */
    @GetMapping("/coins/audit")
    public Result<List<AdminDto.AccountAuditVO>> audit() {
        return Result.ok(adminService.auditCoins());
    }

    @GetMapping("/coins/check")
    public Result<Map<String, Object>> check(@RequestParam Long userId) {
        return Result.ok(adminService.checkWallet(userId));
    }

    // ------------------------------------------------------------------ 配置 / 日志

    @GetMapping("/config")
    public Result<Map<String, String>> config() {
        return Result.ok(adminService.config());
    }

    @PutMapping("/config")
    public Result<Void> updateConfig(@RequestBody Map<String, Object> body) {
        adminService.updateConfig(AuthContext.requireUserId(), body);
        return Result.ok(null);
    }

    @GetMapping("/logs")
    public Result<PageResult<AdminDto.OpLogVO>> logs(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return Result.ok(adminService.logs(page, size));
    }
}
