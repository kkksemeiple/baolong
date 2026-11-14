package com.baolong.wsw.service;

import com.baolong.wsw.common.BizException;
import com.baolong.wsw.common.CursorPage;
import com.baolong.wsw.dto.TaskWire;
import com.baolong.wsw.dto.UserCenterDto;
import com.baolong.wsw.entity.BrowseHistory;
import com.baolong.wsw.entity.Favorite;
import com.baolong.wsw.entity.Post;
import com.baolong.wsw.entity.Task;
import com.baolong.wsw.entity.User;
import com.baolong.wsw.entity.UserCredit;
import com.baolong.wsw.entity.WalletAccount;
import com.baolong.wsw.enums.FavTargetType;
import com.baolong.wsw.enums.TaskStatus;
import com.baolong.wsw.enums.UserRole;
import com.baolong.wsw.mapper.BrowseHistoryMapper;
import com.baolong.wsw.mapper.FavoriteMapper;
import com.baolong.wsw.mapper.PostMapper;
import com.baolong.wsw.mapper.TaskMapper;
import com.baolong.wsw.mapper.UserCreditMapper;
import com.baolong.wsw.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 个人中心服务：资料、收藏、浏览历史、钱包概况、心跳。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCenterService {

    private final UserMapper userMapper;
    private final UserCreditMapper userCreditMapper;
    private final FavoriteMapper favoriteMapper;
    private final BrowseHistoryMapper historyMapper;
    private final TaskMapper taskMapper;
    private final PostMapper postMapper;
    private final WalletService walletService;

    // ------------------------------------------------------------------ 资料

    public UserCenterDto.ProfileVO profile(Long targetUserId, Long currentUserId) {
        User user = userMapper.selectById(targetUserId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        UserCredit credit = userCreditMapper.selectById(targetUserId);
        int score = credit == null || credit.getCreditScore() == null
                ? 100 : (int) Math.round(credit.getCreditScore().doubleValue());
        int goodRate = credit == null || credit.getReviewScore() == null
                ? 100 : (int) Math.round(credit.getReviewScore().doubleValue() / 5 * 100);
        int finished = credit == null || credit.getFinishedClaims() == null
                ? 0 : credit.getFinishedClaims();
        boolean self = targetUserId.equals(currentUserId);
        return new UserCenterDto.ProfileVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                // 只有本人能看到完整手机号，其他人都脱敏
                self ? user.getPhone() : AuthService.maskPhone(user.getPhone()),
                score,
                credit == null ? null : credit.getLevel(),
                user.getStatus(),
                user.getAuthStatus(),
                finished,
                goodRate,
                splitTags(user.getTags()),
                user.getIntro(),
                user.getRecommendEnabled(),
                self,
                user.getCreatedAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public UserCenterDto.ProfileVO updateProfile(Long userId, UserCenterDto.UpdateProfileReq req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(com.baolong.wsw.common.ResultCode.UNAUTHORIZED);
        }
        if (req.phone() != null && !req.phone().equals(user.getPhone())) {
            Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                    .eq(User::getPhone, req.phone()).ne(User::getId, userId));
            if (exists != null && exists > 0) {
                throw BizException.conflict("该手机号已被其他账号绑定");
            }
            user.setPhone(req.phone());
        }
        if (req.nickname() != null && !req.nickname().isBlank()) {
            user.setNickname(req.nickname().trim());
        }
        if (req.avatar() != null) {
            user.setAvatar(req.avatar());
        }
        if (req.intro() != null) {
            user.setIntro(req.intro());
        }
        if (req.tags() != null) {
            user.setTags(req.tags().stream().filter(t -> t != null && !t.isBlank())
                    .map(String::trim).distinct().limit(5).collect(Collectors.joining(",")));
        }
        if (req.recommendEnabled() != null) {
            user.setRecommendEnabled(req.recommendEnabled());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return profile(userId, userId);
    }

    /** 心跳：更新最近活跃时间，用于「在线状态」与风控 */
    @Transactional(rollbackFor = Exception.class)
    public void heartbeat(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        user.setLastActiveAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    // ------------------------------------------------------------------ 收藏

    /**
     * 我的收藏（任务 / 帖子）。
     *
     * 只隐藏、不删除：目标已被作者删除或管理员下架时，这条收藏不返回，
     * 但 favorite 表里的行保持不动。这样内容恢复后收藏会自动重新出现，
     * 也避免「读接口偷偷写库」导致用户收藏莫名消失。
     *
     * 因为过滤发生在查出之后，这里多扫几倍行数，保证一页仍能填满。
     */
    public CursorPage<UserCenterDto.FavoriteVO> favorites(Long userId, String targetType, Long cursor, Integer size) {
        int limit = Math.max(1, Math.min(size == null ? 10 : size, 50));
        int scanLimit = Math.min(limit * 3, 150);
        var wrapper = Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .lt(cursor != null, Favorite::getId, cursor)
                .orderByDesc(Favorite::getId);
        if (targetType != null && !targetType.isBlank()) {
            wrapper.eq(Favorite::getTargetType, targetType.toUpperCase());
        }
        List<Favorite> scanned = new ArrayList<>(
                favoriteMapper.selectList(wrapper.last("LIMIT " + (scanLimit + 1))));
        boolean rawHasMore = scanned.size() > scanLimit;
        if (rawHasMore) {
            scanned = new ArrayList<>(scanned.subList(0, scanLimit));
        }

        List<Favorite> visible = filterVisible(scanned);
        boolean hasMore = rawHasMore || visible.size() > limit;
        List<Favorite> pageRows = visible.size() > limit ? visible.subList(0, limit) : visible;

        String next = null;
        if (hasMore) {
            if (!pageRows.isEmpty()) {
                next = String.valueOf(pageRows.get(pageRows.size() - 1).getId());
            } else if (!scanned.isEmpty()) {
                // 这一批全被隐藏了，也要给出游标，否则前端无法继续翻页
                next = String.valueOf(scanned.get(scanned.size() - 1).getId());
            }
        }
        return CursorPage.of(toFavoriteVOs(pageRows), next);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeFavorite(Long userId, String targetType, Long targetId) {
        favoriteMapper.delete(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, targetType.toUpperCase())
                .eq(Favorite::getTargetId, targetId));
    }

    // ------------------------------------------------------------------ 浏览历史

    public CursorPage<UserCenterDto.HistoryVO> history(Long userId, Long cursor, Integer size) {
        int limit = Math.max(1, Math.min(size == null ? 10 : size, 50));
        List<BrowseHistory> rows = historyMapper.selectList(
                Wrappers.<BrowseHistory>lambdaQuery()
                        .eq(BrowseHistory::getUserId, userId)
                        .lt(cursor != null, BrowseHistory::getId, cursor)
                        .orderByDesc(BrowseHistory::getId)
                        .last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }
        Map<Long, String> titles = taskTitles(rows.stream()
                .filter(h -> FavTargetType.TASK.name().equals(h.getTargetType()))
                .map(BrowseHistory::getTargetId).toList());
        List<UserCenterDto.HistoryVO> list = new ArrayList<>(rows.size());
        for (BrowseHistory history : rows) {
            String title = FavTargetType.TASK.name().equals(history.getTargetType())
                    ? titles.get(history.getTargetId())
                    : "帖子 #" + history.getTargetId();
            list.add(new UserCenterDto.HistoryVO(history.getId(), history.getTargetType(),
                    history.getTargetId(), title, history.getViewedAt()));
        }
        String next = hasMore ? String.valueOf(rows.get(rows.size() - 1).getId()) : null;
        return CursorPage.of(list, next);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteHistory(Long userId, Long id) {
        historyMapper.delete(Wrappers.<BrowseHistory>lambdaQuery()
                .eq(BrowseHistory::getId, id)
                .eq(BrowseHistory::getUserId, userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void clearHistory(Long userId) {
        historyMapper.delete(Wrappers.<BrowseHistory>lambdaQuery()
                .eq(BrowseHistory::getUserId, userId));
    }

    // ------------------------------------------------------------------ 钱包

    public UserCenterDto.WalletVO wallet(Long userId) {
        WalletAccount account = walletService.getOrCreate(userId);
        long available = account.getAvailable() == null ? 0 : account.getAvailable();
        long frozen = account.getFrozen() == null ? 0 : account.getFrozen();
        long debt = account.getDebt() == null ? 0 : account.getDebt();
        return new UserCenterDto.WalletVO(userId, available, frozen, debt, available + frozen - debt);
    }

    /** 冻结明细：我作为发布人、还在占用报酬的任务 */
    public List<UserCenterDto.FrozenVO> frozenDetail(Long userId) {
        List<String> holding = Arrays.stream(TaskStatus.values())
                .filter(TaskStatus::holdsFrozenMoney)
                .map(Enum::name)
                .toList();
        List<Task> tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getPublisherId, userId)
                .in(Task::getStatus, holding)
                .orderByDesc(Task::getId)
                .last("LIMIT 50"));
        List<UserCenterDto.FrozenVO> result = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            TaskStatus status = TaskStatus.of(task.getStatus());
            result.add(new UserCenterDto.FrozenVO(task.getId(), task.getTitle(), task.getReward(),
                    task.getCreatedAt(),
                    TaskWire.toWire(status), status == null ? null : status.label()));
        }
        return result;
    }

    /** 记录一次浏览（任务详情页调用，失败不影响主流程） */
    public void recordView(Long userId, String targetType, Long targetId) {
        if (userId == null) {
            return;
        }
        try {
            historyMapper.upsert(userId, targetType, targetId);
        } catch (Exception e) {
            log.debug("浏览记录写入失败: user={}, target={}", userId, targetId, e);
        }
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 过滤掉「目标已不可见」的收藏：
     * - 任务：只要还在（任务从不物理删除，软状态都会保留），就展示；
     * - 帖子：作者删除（DELETED）或管理员下架（OFFLINE）时隐藏。
     * 注意：这里只过滤、不删库，内容恢复后收藏会自动重新出现。
     */
    private List<Favorite> filterVisible(List<Favorite> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        List<Long> taskIds = rows.stream()
                .filter(f -> FavTargetType.TASK.name().equals(f.getTargetType()))
                .map(Favorite::getTargetId).toList();
        List<Long> postIds = rows.stream()
                .filter(f -> FavTargetType.POST.name().equals(f.getTargetType()))
                .map(Favorite::getTargetId).toList();

        Set<Long> aliveTasks = new HashSet<>();
        if (!taskIds.isEmpty()) {
            for (Task task : taskMapper.selectBatchIds(taskIds)) {
                aliveTasks.add(task.getId());
            }
        }
        Set<Long> alivePosts = new HashSet<>();
        if (!postIds.isEmpty()) {
            for (Post post : postMapper.selectBatchIds(postIds)) {
                // NORMAL 之外的（DELETED / OFFLINE）都视为不可见
                if ("NORMAL".equals(post.getStatus())) {
                    alivePosts.add(post.getId());
                }
            }
        }
        List<Favorite> visible = new ArrayList<>(rows.size());
        for (Favorite favorite : rows) {
            boolean keep = FavTargetType.TASK.name().equals(favorite.getTargetType())
                    ? aliveTasks.contains(favorite.getTargetId())
                    : alivePosts.contains(favorite.getTargetId());
            if (keep) {
                visible.add(favorite);
            }
        }
        return visible;
    }

    private List<UserCenterDto.FavoriteVO> toFavoriteVOs(List<Favorite> rows) {
        List<Long> taskIds = rows.stream()
                .filter(f -> FavTargetType.TASK.name().equals(f.getTargetType()))
                .map(Favorite::getTargetId).toList();
        List<Long> postIds = rows.stream()
                .filter(f -> FavTargetType.POST.name().equals(f.getTargetType()))
                .map(Favorite::getTargetId).toList();

        Map<Long, Task> tasks = new HashMap<>();
        if (!taskIds.isEmpty()) {
            for (Task task : taskMapper.selectBatchIds(taskIds)) {
                tasks.put(task.getId(), task);
            }
        }
        Map<Long, Post> posts = new HashMap<>();
        if (!postIds.isEmpty()) {
            for (Post post : postMapper.selectBatchIds(postIds)) {
                posts.put(post.getId(), post);
            }
        }

        List<UserCenterDto.FavoriteVO> result = new ArrayList<>(rows.size());
        for (Favorite favorite : rows) {
            String title;
            String summary = null;
            if (FavTargetType.TASK.name().equals(favorite.getTargetType())) {
                Task task = tasks.get(favorite.getTargetId());
                title = task == null ? "任务已删除" : task.getTitle();
                summary = task == null ? null : task.getReward() + " 屋币";
            } else {
                Post post = posts.get(favorite.getTargetId());
                title = post == null ? "帖子已删除" : post.getTitle();
            }
            result.add(new UserCenterDto.FavoriteVO(favorite.getId(), favorite.getTargetType(),
                    favorite.getTargetId(), title, summary, favorite.getCreatedAt()));
        }
        return result;
    }

    private Map<Long, String> taskTitles(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> map = new HashMap<>();
        for (Task task : taskMapper.selectBatchIds(ids)) {
            map.put(task.getId(), task.getTitle());
        }
        return map;
    }

    private static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    /** 只给管理员用：判断是否管理员 */
    static boolean isAdmin(User user) {
        return user != null && (UserRole.ADMIN.name().equals(user.getRole())
                || UserRole.SADMIN.name().equals(user.getRole()));
    }
}
