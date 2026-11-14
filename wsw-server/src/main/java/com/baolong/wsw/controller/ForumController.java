package com.baolong.wsw.controller;

import com.baolong.wsw.common.CursorPage;
import com.baolong.wsw.common.Result;
import com.baolong.wsw.dto.ForumDto;
import com.baolong.wsw.security.AuthContext;
import com.baolong.wsw.service.ForumService;
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

/**
 * 论坛接口（对应前端 {@code src/api/forum.ts}）。
 * <p>列表与详情对游客开放，其余操作需要登录。
 */
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class ForumController {

    private final ForumService forumService;

    /** 帖子列表 */
    @GetMapping
    public Result<CursorPage<ForumDto.PostVO>> list(
            @RequestParam(required = false) String keyword,
            /** 前端 forum.ts 传的是 tag（板块）与 sortBy（LATEST / HOT），
             *  原先只收 sort，tag 与 sortBy 会被 Spring 静默丢弃，表现为筛选和排序都不生效。 */
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "latest") String sort,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        String finalSort = (sortBy == null || sortBy.isBlank()) ? sort : sortBy;
        return Result.ok(forumService.posts(keyword, tag, finalSort, cursor, size,
                AuthContext.userIdOrNull()));
    }

    /** 我发布的帖子（个人中心「我的帖子」；含已下架的，状态原样返回） */
    @GetMapping("/mine")
    public Result<CursorPage<ForumDto.PostVO>> mine(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(forumService.myPosts(AuthContext.requireUserId(), cursor, size));
    }

    /** 帖子详情 */
    @GetMapping("/{id}")
    public Result<ForumDto.PostVO> detail(@PathVariable Long id) {
        return Result.ok(forumService.detail(id, AuthContext.userIdOrNull()));
    }

    /** 发帖 */
    @PostMapping
    public Result<ForumDto.PostVO> create(@Valid @RequestBody ForumDto.CreatePostReq req) {
        return Result.ok(forumService.create(AuthContext.requireUserId(), req));
    }

    /** 编辑帖子（作者或管理员） */
    @PutMapping("/{id}")
    public Result<ForumDto.PostVO> update(@PathVariable Long id,
                                          @Valid @RequestBody ForumDto.UpdatePostReq req) {
        return Result.ok(forumService.update(AuthContext.requireUserId(), id, req));
    }

    /** 删除帖子（逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        forumService.delete(AuthContext.requireUserId(), id);
        return Result.ok(null);
    }

    /** 回帖列表 */
    @GetMapping("/{id}/replies")
    public Result<List<ForumDto.ReplyVO>> replies(@PathVariable Long id) {
        return Result.ok(forumService.replies(id, AuthContext.userIdOrNull()));
    }

    /** 回帖 */
    @PostMapping("/{id}/replies")
    public Result<ForumDto.ReplyVO> reply(@PathVariable Long id,
                                          @Valid @RequestBody ForumDto.CreateReplyReq req) {
        return Result.ok(forumService.reply(AuthContext.requireUserId(), id, req));
    }

    /** 删除回帖 */
    @DeleteMapping("/replies/{replyId}")
    public Result<Void> deleteReply(@PathVariable Long replyId) {
        forumService.deleteReply(AuthContext.requireUserId(), replyId);
        return Result.ok(null);
    }

    /** 点赞 */
    @PostMapping("/{id}/like")
    public Result<Void> like(@PathVariable Long id) {
        forumService.like(AuthContext.requireUserId(), id);
        return Result.ok(null);
    }

    /** 取消点赞 */
    @DeleteMapping("/{id}/like")
    public Result<Void> unlike(@PathVariable Long id) {
        forumService.unlike(AuthContext.requireUserId(), id);
        return Result.ok(null);
    }

    /** 收藏帖子 */
    @PostMapping("/{id}/favorite")
    public Result<Void> favorite(@PathVariable Long id) {
        forumService.favorite(AuthContext.requireUserId(), id);
        return Result.ok(null);
    }

    /** 取消收藏 */
    @DeleteMapping("/{id}/favorite")
    public Result<Void> unfavorite(@PathVariable Long id) {
        forumService.unfavorite(AuthContext.requireUserId(), id);
        return Result.ok(null);
    }
}
