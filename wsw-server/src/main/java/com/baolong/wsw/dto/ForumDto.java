package com.baolong.wsw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 论坛模块的入参与出参。
 */
public final class ForumDto {

    private ForumDto() {
    }

    /** 帖子列表 / 详情 */
    public record PostVO(
            Long id,
            String title,
            String content,
            Long authorId,
            UserBrief author,
            List<String> tags,
            Integer replyCount,
            Integer likeCount,
            Boolean liked,
            Boolean favorited,
            Boolean mine,
            /** 帖子状态：NORMAL 正常 / OFFLINE 已下架 —— 「我的帖子」要显示自己被下架了 */
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    /** 回帖 */
    public record ReplyVO(
            Long id,
            Long postId,
            Long parentId,
            String content,
            Long authorId,
            UserBrief author,
            Boolean mine,
            LocalDateTime createdAt
    ) {
    }

    /** 作者信息（与任务模块的 UserBrief 字段一致，前端复用同一个组件） */
    public record UserBrief(Long id, String nickname, String avatar, Integer creditScore) {
    }

    public record CreatePostReq(
            @NotBlank(message = "请填写标题")
            @Size(max = 60, message = "标题最多 60 字")
            String title,

            @NotBlank(message = "请填写正文")
            @Size(max = 5000, message = "正文最多 5000 字")
            String content,

            List<String> tags
    ) {
    }

    public record UpdatePostReq(
            @Size(max = 60, message = "标题最多 60 字") String title,
            @Size(max = 5000, message = "正文最多 5000 字") String content,
            List<String> tags
    ) {
    }

    public record CreateReplyReq(
            @NotBlank(message = "回复内容不能为空")
            @Size(max = 1000, message = "回复最多 1000 字")
            String content,
            Long parentId
    ) {
    }
}
