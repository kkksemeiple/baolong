package com.baolong.wsw.controller;

import com.baolong.wsw.common.CursorPage;
import com.baolong.wsw.common.Result;
import com.baolong.wsw.dto.CreditDto;
import com.baolong.wsw.dto.UserCenterDto;
import com.baolong.wsw.entity.WalletTransaction;
import com.baolong.wsw.security.AuthContext;
import com.baolong.wsw.service.CreditService;
import com.baolong.wsw.service.UserCenterService;
import com.baolong.wsw.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 个人中心接口（对应前端 {@code src/api/user.ts}）。
 * <p>路径混用了 {@code /users/**} 与 {@code /me/**}：前者看「某个用户」，后者看「我自己」。
 */
@RestController
@RequiredArgsConstructor
public class UserCenterController {

    private final UserCenterService userCenterService;
    private final WalletService walletService;
    private final CreditService creditService;

    // ------------------------------------------------------------------ 资料

    /** 我的资料 */
    @GetMapping("/users/me")
    public Result<UserCenterDto.ProfileVO> me() {
        Long userId = AuthContext.requireUserId();
        return Result.ok(userCenterService.profile(userId, userId));
    }

    /** 修改我的资料 */
    @PutMapping("/users/me")
    public Result<UserCenterDto.ProfileVO> updateMe(@Valid @RequestBody UserCenterDto.UpdateProfileReq req) {
        return Result.ok(userCenterService.updateProfile(AuthContext.requireUserId(), req));
    }

    /** 任意用户主页 */
    @GetMapping("/users/{id}")
    public Result<UserCenterDto.ProfileVO> profile(@PathVariable Long id) {
        return Result.ok(userCenterService.profile(id, AuthContext.userIdOrNull()));
    }

    /** 信誉分明细：点顶栏「109 分」或主页信用分时展开 */
    @GetMapping("/users/{id}/credit")
    public Result<CreditDto.DetailVO> credit(@PathVariable Long id) {
        return Result.ok(creditService.detail(id));
    }

    /** 心跳，前端每 60 秒调一次 */
    @PostMapping("/users/heartbeat")
    public Result<Void> heartbeat() {
        userCenterService.heartbeat(AuthContext.requireUserId());
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ 收藏

    @GetMapping("/me/favorites")
    public Result<CursorPage<UserCenterDto.FavoriteVO>> favorites(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(userCenterService.favorites(
                AuthContext.requireUserId(), targetType, cursor, size));
    }

    /** 取消收藏（前端传 targetType + targetId） */
    @DeleteMapping("/me/favorites")
    public Result<Void> removeFavorite(@RequestParam String targetType,
                                       @RequestParam Long targetId) {
        userCenterService.removeFavorite(AuthContext.requireUserId(), targetType, targetId);
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ 浏览历史

    @GetMapping("/me/history")
    public Result<CursorPage<UserCenterDto.HistoryVO>> history(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(userCenterService.history(AuthContext.requireUserId(), cursor, size));
    }

    /** 删除单条历史 */
    @DeleteMapping("/me/history/{id}")
    public Result<Void> deleteHistory(@PathVariable Long id) {
        userCenterService.deleteHistory(AuthContext.requireUserId(), id);
        return Result.ok(null);
    }

    /** 清空历史 */
    @DeleteMapping("/me/history")
    public Result<Void> clearHistory() {
        userCenterService.clearHistory(AuthContext.requireUserId());
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ 钱包

    /** 钱包概况：可用 / 冻结 / 欠款 / 总额 */
    @GetMapping("/me/wallet")
    public Result<UserCenterDto.WalletVO> wallet() {
        return Result.ok(userCenterService.wallet(AuthContext.requireUserId()));
    }

    /** 资金流水（游标分页，可按类型筛选） */
    @GetMapping("/me/wallet/transactions")
    public Result<CursorPage<WalletTransaction>> transactions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return Result.ok(walletService.transactions(
                AuthContext.requireUserId(), type, cursor, size == null ? 10 : size));
    }

    /** 冻结明细 */
    @GetMapping("/me/wallet/frozen")
    public Result<List<UserCenterDto.FrozenVO>> frozen() {
        return Result.ok(userCenterService.frozenDetail(AuthContext.requireUserId()));
    }
}
