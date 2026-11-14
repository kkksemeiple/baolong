package com.baolong.wsw.service;

import com.baolong.wsw.common.BizException;
import com.baolong.wsw.common.CursorPage;
import com.baolong.wsw.dto.ForumDto;
import com.baolong.wsw.entity.Favorite;
import com.baolong.wsw.entity.Post;
import com.baolong.wsw.entity.PostLike;
import com.baolong.wsw.entity.PostReply;
import com.baolong.wsw.entity.User;
import com.baolong.wsw.entity.UserCredit;
import com.baolong.wsw.enums.FavTargetType;
import com.baolong.wsw.enums.UserRole;
import com.baolong.wsw.mapper.BrowseHistoryMapper;
import com.baolong.wsw.mapper.FavoriteMapper;
import com.baolong.wsw.mapper.PostLikeMapper;
import com.baolong.wsw.mapper.PostMapper;
import com.baolong.wsw.mapper.PostReplyMapper;
import com.baolong.wsw.mapper.UserCreditMapper;
import com.baolong.wsw.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
 * 论坛服务：帖子、回帖、点赞、收藏。
 *
 * <p>点赞用「联合唯一索引 + 原子自增」：重复点赞直接撞 {@code uk_post_user}，
 * 计数用 {@code set like_count = like_count + 1}，不会出现「读到旧值再写回」的丢失更新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumService {

    private static final String STATUS_NORMAL = "NORMAL";
    private static final String STATUS_DELETED = "DELETED";

    private final PostMapper postMapper;
    private final PostReplyMapper replyMapper;
    private final PostLikeMapper likeMapper;
    private final FavoriteMapper favoriteMapper;
    private final BrowseHistoryMapper browseHistoryMapper;
    private final UserMapper userMapper;
    private final UserCreditMapper userCreditMapper;
    private final AuditService auditService;
    private final NotificationService notificationService;

    // ------------------------------------------------------------------ 帖子

    public CursorPage<ForumDto.PostVO> posts(String keyword, String tag, String sort,
                                             Long cursor, Integer size, Long currentUserId) {
        int limit = Math.max(1, Math.min(size == null ? 10 : size, 50));
        var wrapper = Wrappers.<Post>lambdaQuery()
                .eq(Post::getStatus, STATUS_NORMAL)
                .lt(cursor != null, Post::getId, cursor);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Post::getTitle, keyword.trim());
        }
        // tag 在库里是英文逗号串（如 "学习,自习"），用 like 做包含匹配即可
        if (tag != null && !tag.isBlank()) {
            wrapper.like(Post::getTags, tag.trim());
        }
        if ("hot".equalsIgnoreCase(sort)) {
            wrapper.orderByDesc(Post::getLikeCount).orderByDesc(Post::getId);
        } else {
            wrapper.orderByDesc(Post::getId);
        }
        List<Post> rows = postMapper.selectList(wrapper.last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }
        List<ForumDto.PostVO> list = toVOs(rows, currentUserId, false);
        String next = hasMore ? String.valueOf(rows.get(rows.size() - 1).getId()) : null;
        return CursorPage.of(list, next);
    }

    public ForumDto.PostVO detail(Long postId, Long currentUserId) {
        Post post = requirePost(postId);
        // 记浏览历史：只对登录用户记，失败不影响帖子打开
        if (currentUserId != null) {
            try {
                browseHistoryMapper.upsert(currentUserId, FavTargetType.POST.name(), postId);
            } catch (Exception e) {
                log.debug("帖子浏览历史写入失败: user={}, post={}", currentUserId, postId, e);
            }
        }
        return toVOs(List.of(post), currentUserId, true).get(0);
    }

    /**
     * 我发布的帖子（个人中心「我的帖子」）。
     * 与大厅列表不同：这里包含已下架 / 作者已删除的，但状态会原样返回，
     * 让作者知道「我的帖子被下架了」，而不是凭空消失。
     */
    public CursorPage<ForumDto.PostVO> myPosts(Long userId, Long cursor, Integer size) {
        int limit = Math.max(1, Math.min(size == null ? 10 : size, 50));
        var wrapper = Wrappers.<Post>lambdaQuery()
                .eq(Post::getAuthorId, userId)
                .ne(Post::getStatus, "DELETED")
                .lt(cursor != null, Post::getId, cursor)
                .orderByDesc(Post::getId);
        List<Post> rows = postMapper.selectList(wrapper.last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }
        List<ForumDto.PostVO> list = toVOs(rows, userId, false);
        String next = hasMore ? String.valueOf(rows.get(rows.size() - 1).getId()) : null;
        return CursorPage.of(list, next);
    }

    @Transactional(rollbackFor = Exception.class)
    public ForumDto.PostVO create(Long userId, ForumDto.CreatePostReq req) {
        Post post = new Post();
        post.setTitle(req.title().trim());
        post.setContent(req.content());
        post.setAuthorId(userId);
        post.setTags(writeTags(req.tags()));
        post.setReplyCount(0);
        post.setLikeCount(0);
        post.setStatus(STATUS_NORMAL);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.insert(post);

        // 敏感词初筛：命中不阻断发布，只入审核队列（先发后审）
        String hitWord = com.baolong.wsw.algorithm.SensitiveWords.hit(post.getTitle(), post.getContent());
        if (hitWord != null) {
            auditService.enqueue("POST", post.getId(), post.getTitle(),
                    post.getContent(), userId, "敏感词：" + hitWord);
        }
        return detail(post.getId(), userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ForumDto.PostVO update(Long userId, Long postId, ForumDto.UpdatePostReq req) {
        Post post = requirePost(postId);
        requireOwnerOrAdmin(post.getAuthorId(), userId, "只能编辑自己的帖子");
        if (req.title() != null) {
            post.setTitle(req.title().trim());
        }
        if (req.content() != null) {
            post.setContent(req.content());
        }
        if (req.tags() != null) {
            post.setTags(writeTags(req.tags()));
        }
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.updateById(post);
        return detail(postId, userId);
    }

    /** 删除帖子：逻辑删除，回帖与点赞保留但不再展示 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long postId) {
        Post post = requirePost(postId);
        requireOwnerOrAdmin(post.getAuthorId(), userId, "只能删除自己的帖子");
        post.setStatus(STATUS_DELETED);
        post.setUpdatedAt(LocalDateTime.now());
        postMapper.updateById(post);
    }

    // ------------------------------------------------------------------ 回帖

    public List<ForumDto.ReplyVO> replies(Long postId, Long currentUserId) {
        requirePost(postId);
        List<PostReply> rows = replyMapper.selectList(
                Wrappers.<PostReply>lambdaQuery()
                        .eq(PostReply::getPostId, postId)
                        .eq(PostReply::getStatus, STATUS_NORMAL)
                        .orderByAsc(PostReply::getId)
                        .last("LIMIT 200"));
        Map<Long, User> users = userMap(rows.stream().map(PostReply::getAuthorId).distinct().toList());
        Map<Long, UserCredit> credits = creditMap(users.keySet());
        List<ForumDto.ReplyVO> result = new ArrayList<>(rows.size());
        for (PostReply reply : rows) {
            result.add(new ForumDto.ReplyVO(
                    reply.getId(),
                    reply.getPostId(),
                    reply.getParentId(),
                    reply.getContent(),
                    reply.getAuthorId(),
                    brief(reply.getAuthorId(), users, credits),
                    reply.getAuthorId().equals(currentUserId),
                    reply.getCreatedAt()));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public ForumDto.ReplyVO reply(Long userId, Long postId, ForumDto.CreateReplyReq req) {
        Post post = requirePost(postId);

        // 回复可以接在任何一条回复下面（楼中楼），但父回复必须存在、
        // 属于同一个帖子、且没被删——否则会出现「回复 @某人」指向别的帖子的人。
        PostReply parent = null;
        if (req.parentId() != null) {
            parent = replyMapper.selectById(req.parentId());
            if (parent == null || !postId.equals(parent.getPostId())
                    || !STATUS_NORMAL.equals(parent.getStatus())) {
                throw BizException.badRequest("要回复的楼层不存在或已删除");
            }
        }

        PostReply reply = new PostReply();
        reply.setPostId(postId);
        reply.setParentId(req.parentId());
        reply.setContent(req.content());
        reply.setAuthorId(userId);
        reply.setStatus(STATUS_NORMAL);
        reply.setCreatedAt(LocalDateTime.now());
        replyMapper.insert(reply);
        postMapper.incrReply(postId, 1);

        // 通知：被回复的人（不是自己）+ 楼主（也不是自己、且不是同一个被回复人）
        if (parent != null && !parent.getAuthorId().equals(userId)) {
            notificationService.push(parent.getAuthorId(), com.baolong.wsw.enums.NotifType.FORUM,
                    "有人回复了你的评论",
                    "帖子「" + post.getTitle() + "」：" + abbreviate(req.content()),
                    postId, "/forum/" + postId);
        }
        if (!post.getAuthorId().equals(userId) && (parent == null || !post.getAuthorId().equals(parent.getAuthorId()))) {
            notificationService.push(post.getAuthorId(), com.baolong.wsw.enums.NotifType.FORUM,
                    "你的帖子有新回复",
                    "帖子「" + post.getTitle() + "」：" + abbreviate(req.content()),
                    postId, "/forum/" + postId);
        }

        User author = userMapper.selectById(userId);
        return new ForumDto.ReplyVO(reply.getId(), postId, req.parentId(), reply.getContent(), userId,
                author == null ? null : new ForumDto.UserBrief(author.getId(), author.getNickname(),
                        author.getAvatar(), null),
                true, reply.getCreatedAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteReply(Long userId, Long replyId) {
        PostReply reply = replyMapper.selectById(replyId);
        if (reply == null || !STATUS_NORMAL.equals(reply.getStatus())) {
            throw BizException.notFound("回复不存在");
        }
        requireOwnerOrAdmin(reply.getAuthorId(), userId, "只能删除自己的回复");
        reply.setStatus(STATUS_DELETED);
        replyMapper.updateById(reply);
        postMapper.incrReply(reply.getPostId(), -1);
    }

    // ------------------------------------------------------------------ 点赞 / 收藏

    @Transactional(rollbackFor = Exception.class)
    public void like(Long userId, Long postId) {
        Post post = requirePost(postId);
        PostLike like = new PostLike();
        like.setPostId(postId);
        like.setUserId(userId);
        like.setCreatedAt(LocalDateTime.now());
        try {
            likeMapper.insert(like);
        } catch (DuplicateKeyException e) {
            throw BizException.conflict("你已经点过赞了");
        }
        postMapper.incrLike(postId, 1);
        log.debug("点赞: post={}, user={}, 当前点赞数={}", postId, userId, post.getLikeCount());
    }

    @Transactional(rollbackFor = Exception.class)
    public void unlike(Long userId, Long postId) {
        int deleted = likeMapper.delete(
                Wrappers.<PostLike>lambdaQuery()
                        .eq(PostLike::getPostId, postId)
                        .eq(PostLike::getUserId, userId));
        if (deleted > 0) {
            postMapper.incrLike(postId, -1);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void favorite(Long userId, Long postId) {
        requirePost(postId);
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setTargetType(FavTargetType.POST.name());
        favorite.setTargetId(postId);
        favorite.setCreatedAt(LocalDateTime.now());
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            throw BizException.conflict("已经收藏过了");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void unfavorite(Long userId, Long postId) {
        favoriteMapper.delete(
                Wrappers.<Favorite>lambdaQuery()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getTargetType, FavTargetType.POST.name())
                        .eq(Favorite::getTargetId, postId));
    }

    // ------------------------------------------------------------------ 内部

    private Post requirePost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null || !STATUS_NORMAL.equals(post.getStatus())) {
            throw BizException.notFound("帖子不存在或已删除");
        }
        return post;
    }

    private void requireOwnerOrAdmin(Long ownerId, Long currentUserId, String message) {
        if (currentUserId == null) {
            throw BizException.of(com.baolong.wsw.common.ResultCode.UNAUTHORIZED);
        }
        if (ownerId.equals(currentUserId)) {
            return;
        }
        User user = userMapper.selectById(currentUserId);
        if (user != null && UserRole.ADMIN.name().equals(user.getRole())
                || user != null && UserRole.SADMIN.name().equals(user.getRole())) {
            return;
        }
        throw BizException.forbidden(message);
    }

    private List<ForumDto.PostVO> toVOs(List<Post> posts, Long currentUserId, boolean withContent) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = userMap(posts.stream().map(Post::getAuthorId).distinct().toList());
        Map<Long, UserCredit> credits = creditMap(users.keySet());
        Set<Long> likedIds = new HashSet<>();
        Set<Long> favoritedIds = new HashSet<>();
        if (currentUserId != null) {
            List<Long> ids = posts.stream().map(Post::getId).toList();
            for (PostLike like : likeMapper.selectList(Wrappers.<PostLike>lambdaQuery()
                    .eq(PostLike::getUserId, currentUserId).in(PostLike::getPostId, ids))) {
                likedIds.add(like.getPostId());
            }
            for (Favorite favorite : favoriteMapper.selectList(Wrappers.<Favorite>lambdaQuery()
                    .eq(Favorite::getUserId, currentUserId)
                    .eq(Favorite::getTargetType, FavTargetType.POST.name())
                    .in(Favorite::getTargetId, ids))) {
                favoritedIds.add(favorite.getTargetId());
            }
        }
        List<ForumDto.PostVO> result = new ArrayList<>(posts.size());
        for (Post post : posts) {
            result.add(new ForumDto.PostVO(
                    post.getId(),
                    post.getTitle(),
                    withContent ? post.getContent() : abbreviate(post.getContent()),
                    post.getAuthorId(),
                    brief(post.getAuthorId(), users, credits),
                    readTags(post.getTags()),
                    post.getReplyCount(),
                    post.getLikeCount(),
                    likedIds.contains(post.getId()),
                    favoritedIds.contains(post.getId()),
                    post.getAuthorId().equals(currentUserId),
                    post.getStatus(),
                    post.getCreatedAt(),
                    post.getUpdatedAt()));
        }
        return result;
    }

    private Map<Long, User> userMap(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, User> map = new HashMap<>();
        for (User user : userMapper.selectBatchIds(ids)) {
            map.put(user.getId(), user);
        }
        return map;
    }

    private Map<Long, UserCredit> creditMap(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, UserCredit> map = new HashMap<>();
        for (UserCredit credit : userCreditMapper.selectBatchIds(ids)) {
            map.put(credit.getUserId(), credit);
        }
        return map;
    }

    private ForumDto.UserBrief brief(Long userId, Map<Long, User> users, Map<Long, UserCredit> credits) {
        User user = users.get(userId);
        if (user == null) {
            return null;
        }
        UserCredit credit = credits.get(userId);
        Integer score = credit == null || credit.getCreditScore() == null
                ? null : (int) Math.round(credit.getCreditScore().doubleValue());
        return new ForumDto.UserBrief(user.getId(), user.getNickname(), user.getAvatar(), score);
    }

    private static String abbreviate(String content) {
        if (content == null) {
            return null;
        }
        String flat = content.replaceAll("\\s+", " ");
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "...";
    }

    private static String writeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return tags.stream().filter(t -> t != null && !t.isBlank())
                .map(String::trim).distinct().limit(5).collect(Collectors.joining(","));
    }

    private static List<String> readTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }
}
