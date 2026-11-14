package com.baolong.wsw.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人中心模块的入参与出参。
 */
public final class UserCenterDto {

    private UserCenterDto() {
    }

    /** 他人主页 / 我的资料 */
    public record ProfileVO(
            Long id,
            String username,
            String nickname,
            String avatar,
            String phone,
            Integer creditScore,
            String level,
            String status,
            String authStatus,
            Integer finishedCount,
            Integer goodRate,
            List<String> tags,
            String intro,
            Boolean recommendEnabled,
            Boolean self,
            LocalDateTime createdAt
    ) {
    }

    /** 修改资料 */
    public record UpdateProfileReq(
            @Size(max = 20, message = "昵称最多 20 字") String nickname,
            String avatar,
            @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String phone,
            @Size(max = 200, message = "个人简介最多 200 字") String intro,
            List<String> tags,
            Boolean recommendEnabled
    ) {
    }

    /** 收藏项 */
    public record FavoriteVO(
            Long id,
            String targetType,
            Long targetId,
            String title,
            String summary,
            LocalDateTime createdAt
    ) {
    }

    /** 浏览记录 */
    public record HistoryVO(
            Long id,
            String targetType,
            Long targetId,
            String title,
            LocalDateTime viewedAt
    ) {
    }

    /** 钱包概况 */
    public record WalletVO(
            Long userId,
            Long available,
            Long frozen,
            Long debt,
            Long total
    ) {
    }

    /**
     * 冻结明细（发布中任务占用的报酬）。
     * <p>字段名必须与前端 {@code FrozenItem} 对齐：
     * {@code taskId / taskTitle / amount / frozenAt}。
     * 早先写成 {@code id / title / reward}，页面上就渲染成「undefined WB」。
     */
    public record FrozenVO(
            Long taskId,
            String taskTitle,
            Long amount,
            LocalDateTime frozenAt,
            String status,
            String statusLabel
    ) {
    }
}
