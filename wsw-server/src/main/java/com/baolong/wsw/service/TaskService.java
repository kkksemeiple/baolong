package com.baolong.wsw.service;

import com.baolong.wsw.algorithm.ConflictChecker;
import com.baolong.wsw.algorithm.TaskTypeMeta;
import com.baolong.wsw.common.BizException;
import com.baolong.wsw.common.CursorPage;
import com.baolong.wsw.common.ResultCode;
import com.baolong.wsw.dto.TaskDto;
import com.baolong.wsw.dto.TaskWire;
import com.baolong.wsw.entity.Dispute;
import com.baolong.wsw.entity.Favorite;
import com.baolong.wsw.entity.Task;
import com.baolong.wsw.entity.TaskClaim;
import com.baolong.wsw.entity.User;
import com.baolong.wsw.entity.UserCredit;
import com.baolong.wsw.enums.BizType;
import com.baolong.wsw.enums.ClaimStatus;
import com.baolong.wsw.enums.FavTargetType;
import com.baolong.wsw.enums.TaskStatus;
import com.baolong.wsw.enums.TaskType;
import com.baolong.wsw.mapper.BrowseHistoryMapper;
import com.baolong.wsw.mapper.FavoriteMapper;
import com.baolong.wsw.mapper.TaskClaimMapper;
import com.baolong.wsw.mapper.TaskMapper;
import com.baolong.wsw.mapper.UserCreditMapper;
import com.baolong.wsw.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务服务 —— 对应设计文档「任务类」的 17 个业务方法在服务层的实现。
 *
 * <p>三条铁律：
 * <ol>
 *   <li><b>状态只能经 {@code TaskMapper} 的带守卫 UPDATE 迁移</b>，服务层不直接 setStatus 后 updateById</li>
 *   <li><b>钱的每一次变动都走 {@link WalletService}</b>，任务服务不写余额</li>
 *   <li><b>冲突检测只读不写</b>，真正防并发靠 CAS + {@code task_claim} 唯一约束</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskClaimMapper claimMapper;
    private final FavoriteMapper favoriteMapper;
    private final BrowseHistoryMapper browseHistoryMapper;
    private final UserMapper userMapper;
    private final UserCreditMapper userCreditMapper;
    private final WalletService walletService;
    private final CreditService creditService;
    private final NotificationService notificationService;
    private final ConfigService configService;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final com.baolong.wsw.mapper.DisputeMapper disputeMapper;
    private final MessageService messageService;

    /** 取消方标识，用于信誉评分归责 */
    private static final String BY_PUBLISHER = "PUBLISHER";
    private static final String BY_CLAIMER = "CLAIMER";

    // ================================================================== 发布

    @Transactional(rollbackFor = Exception.class)
    public TaskDto.TaskVO publish(Long userId, TaskDto.CreateReq req) {
        TaskType type = parseType(req.type());
        TaskTypeMeta meta = TaskTypeMeta.of(type);

        if (!req.deadline().isAfter(LocalDateTime.now())) {
            throw BizException.badRequest("截止时间必须晚于当前时间");
        }
        if (meta.needLocation() && isBlank(req.location())) {
            throw BizException.badRequest("该类型任务必须填写地点");
        }
        if (!creditService.canAccept(userId)) {
            throw BizException.of(ResultCode.CREDIT_LIMIT);
        }

        LocalDateTime start = req.startTime() != null ? req.startTime() : req.deadline().minusHours(2);
        int duration = req.durationMinutes() != null && req.durationMinutes() > 0
                ? req.durationMinutes() : 60;

        Task task = new Task();
        task.setTitle(req.title().trim());
        task.setDescription(req.description());
        task.setType(type.name());
        task.setReward(req.reward());
        task.setDeadline(req.deadline());
        task.setStartTime(start);
        task.setDurationMinutes(duration);
        task.setLocation(req.location());
        task.setPeopleNeeded(req.peopleNeeded() == null ? 1 : req.peopleNeeded());
        task.setImages(writeJson(req.images()));
        task.setTags(writeTags(req.tags()));
        task.setStatus(TaskStatus.WAITING.name());
        task.setPublisherId(userId);
        task.setViewCount(0);
        task.setFavorCount(0);
        task.setVersion(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        // 钱先冻住，任务才算数；余额不足会在这里抛异常，整个事务回滚
        walletService.freeze(userId, req.reward(), BizType.TASK, task.getId(),
                task.getTitle(), "TASK", task.getId());

        // 敏感词初筛：命中不阻断发布，只入审核队列（先发后审）。
        // 必须放在冻结成功之后：audit_item 是 REQUIRES_NEW 独立提交的，
        // 若先入队再冻结，余额不足导致发布回滚时会留下一条指向不存在任务的孤儿审核记录。
        String hitWord = com.baolong.wsw.algorithm.SensitiveWords.hit(task.getTitle(), task.getDescription());
        if (hitWord != null) {
            auditService.enqueue("TASK", task.getId(), task.getTitle(),
                    task.getDescription(), userId, "敏感词：" + hitWord);
        }

        return toVO(task, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskDto.TaskVO update(Long userId, Long taskId, TaskDto.UpdateReq req) {
        Task task = require(taskId);
        if (!task.getPublisherId().equals(userId)) {
            throw BizException.forbidden("只有发布人才能修改任务");
        }
        if (!TaskStatus.WAITING.name().equals(task.getStatus())) {
            throw BizException.of(ResultCode.TASK_STATUS_INVALID);
        }
        long oldReward = task.getReward();
        if (req.title() != null) {
            task.setTitle(req.title().trim());
        }
        if (req.description() != null) {
            task.setDescription(req.description());
        }
        if (req.type() != null) {
            task.setType(parseType(req.type()).name());
        }
        if (req.deadline() != null) {
            // 改截止时间也必须是「将来的时间」：否则任务一保存就过期，
            // 大厅查不到、却还占着冻结额度，等定时任务下一轮才清掉。
            if (!req.deadline().isAfter(LocalDateTime.now())) {
                throw BizException.badRequest("截止时间必须晚于当前时间");
            }
            task.setDeadline(req.deadline());
        }
        if (req.location() != null) {
            task.setLocation(req.location());
        }
        if (req.peopleNeeded() != null) {
            task.setPeopleNeeded(req.peopleNeeded());
        }
        if (req.images() != null) {
            task.setImages(writeJson(req.images()));
        }
        if (req.tags() != null) {
            task.setTags(writeTags(req.tags()));
        }
        if (req.startTime() != null) {
            task.setStartTime(req.startTime());
        }
        if (req.durationMinutes() != null && req.durationMinutes() > 0) {
            task.setDurationMinutes(req.durationMinutes());
        }
        if (req.reward() != null && req.reward() != oldReward) {
            // 报酬变了，冻结额度要跟着调：先退旧的，再冻新的。
            //
            // 这里不能用 originType="TASK" + originId=taskId：
            // 钱包的幂等键是 transType:originType:originId:userId，
            // 同一任务第二次改价时 "UNFREEZE:TASK:{taskId}:{userId}" 已经存在，
            // 第二次解冻会被当成重复请求直接跳过 —— 结果旧冻结没退、又冻了一笔新金额，
            // 用户账面冻结额越改越多、可用余额却被白扣。
            // 因此改价用独立的 originType，并把乐观锁版本号拼进 originId，
            // 保证每次改价是不同的一对（解冻/冻结）流水，同时仍能追溯到任务。
            long adjustOrigin = taskId * 1_000_000L + (task.getVersion() == null ? 0L : task.getVersion());
            walletService.unfreeze(userId, oldReward, BizType.TASK, taskId,
                    task.getTitle(), "TASK_EDIT", adjustOrigin);
            walletService.freeze(userId, req.reward(), BizType.TASK, taskId,
                    task.getTitle(), "TASK_EDIT", adjustOrigin);
            task.setReward(req.reward());
        }
        task.setUpdatedAt(LocalDateTime.now());
        // 必须判影响行数：Task 带 @Version，乐观锁会在 SQL 里追加 AND version = 旧值。
        // 如果这期间有人抢单（version 被 +1），这里会更新 0 行 ——
        // 而上面已经把钱「解冻 + 重新冻结」完了，不报错就是「钱动了、单据没动」的脏账。
        if (taskMapper.updateById(task) == 0) {
            throw BizException.conflict("任务已被他人修改或已被接单，无法保存修改，请刷新后重试");
        }
        return toVO(task, userId);
    }

    // ================================================================== 接单

    /**
     * 抢单。并发安全分两层：
     * 第一层是这里的冲突检测与资格校验（可能被并发穿过），
     * 第二层是 {@code taskMapper.claim} 的 CAS 与 {@code task_claim} 的唯一约束（真正兜底）。
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskDto.TaskVO claim(Long userId, Long taskId, Integer expectedVersion) {
        Task task = require(taskId);
        if (task.getPublisherId().equals(userId)) {
            throw BizException.of(ResultCode.SELF_ACCEPT);
        }
        if (!TaskStatus.WAITING.name().equals(task.getStatus())) {
            throw BizException.of(ResultCode.TASK_ALREADY_TAKEN);
        }
        if (task.getDeadline().isBefore(LocalDateTime.now())) {
            throw BizException.of(ResultCode.DEADLINE_EXPIRED);
        }
        if (!creditService.canAccept(userId)) {
            throw BizException.of(ResultCode.CREDIT_LIMIT);
        }

        ConflictChecker.Result conflict = checkConflict(userId, task);
        if (conflict.blocked()) {
            throw BizException.of(ResultCode.CONFLICT_SCHEDULE);
        }

        int rows = taskMapper.claim(taskId, userId, expectedVersion);
        if (rows == 0) {
            throw explainClaimFailure(taskId);
        }

        // 放弃过的任务会退回大厅重新被接：此时 task_claim 里已经有一行 CANCELLED 记录，
        // 而 uk_task_id 保证「一个任务只有一行」。所以第二次接单不能再 insert，
        // 必须把旧行重置为新的进行中记录 —— 否则撞唯一键，接口直接 500。
        TaskClaim existing = claimMapper.selectOne(
                Wrappers.<TaskClaim>lambdaQuery().eq(TaskClaim::getTaskId, taskId));
        if (existing == null) {
            TaskClaim claim = new TaskClaim();
            claim.setTaskId(taskId);
            claim.setClaimerId(userId);
            claim.setStatus("IN_PROGRESS");
            claim.setClaimedAt(LocalDateTime.now());
            claimMapper.insert(claim);
        } else if (claimMapper.reopenForClaim(taskId, userId) == 0) {
            // 旧记录不是 CANCELLED（正常流程不会出现），回滚本次抢单，避免状态错乱
            throw BizException.conflict("接单记录状态异常，请刷新后重试");
        }

        notificationService.pushTask(task.getPublisherId(), "有人接了你的任务",
                "「" + task.getTitle() + "」已被接单，请留意后续进度", taskId);

        // 接单即建会话：进「消息」就能看到对方，不用再点一次「联系对方」
        messageService.ensureForTask(taskId);

        return toVO(require(taskId), userId);
    }

    /** 承接人提交完成 */
    @Transactional(rollbackFor = Exception.class)
    public TaskDto.TaskVO submit(Long userId, Long taskId, TaskDto.SubmitReq req) {
        Task task = require(taskId);
        if (!userId.equals(task.getClaimantId())) {
            throw BizException.forbidden("只有接单方才能提交");
        }
        TaskTypeMeta meta = TaskTypeMeta.of(parseType(task.getType()));
        if (meta.needProof() && (req.images() == null || req.images().isEmpty())) {
            throw BizException.badRequest("该类型任务必须上传交付凭证");
        }
        int rows = taskMapper.submit(taskId, userId);
        if (rows == 0) {
            throw BizException.of(ResultCode.TASK_STATUS_INVALID);
        }
        claimMapper.submit(taskId, req.content(), writeJson(req.images()));
        notificationService.pushTask(task.getPublisherId(), "任务已提交，待你确认",
                "「" + task.getTitle() + "」接单方已提交完成，请在 7 天内确认", taskId);
        return toVO(require(taskId), userId);
    }

    /** 委托方确认完成 → 结算 */
    @Transactional(rollbackFor = Exception.class)
    public TaskDto.TaskVO confirm(Long userId, Long taskId, TaskDto.ConfirmReq req) {
        Task task = require(taskId);
        if (!task.getPublisherId().equals(userId)) {
            throw BizException.forbidden("只有发布人才能确认完成");
        }
        // 评价必须落在 1~5：这个值会直接进信誉分（评价占 25% 权重），
        // 不校验的话，随便传个 999 或 -10 就能把人刷成满分或打到冻结。
        if (req != null && req.rating() != null && (req.rating() < 1 || req.rating() > 5)) {
            throw BizException.badRequest("评价必须是 1~5 星");
        }
        int rows = taskMapper.confirm(taskId, userId);
        if (rows == 0) {
            throw BizException.of(ResultCode.TASK_STATUS_INVALID);
        }
        // confirm 只是把 TO_CONFIRM 改成 COMPLETED，settle 才是 COMPLETED → SETTLED。
        // 这一步以前不判行数，若并发下任务已不是 COMPLETED，钱照发、状态没推进。
        if (taskMapper.settle(taskId) == 0) {
            throw BizException.conflict("任务状态已变更，无法结算，请刷新后重试");
        }
        claimMapper.confirm(taskId, req == null ? null : req.rating(),
                req == null ? null : req.content());

        Long claimant = task.getClaimantId();
        // 委托方：冻结额度释放（可用余额早已扣除，这里只是把 frozen 减掉）
        walletService.consumeFrozen(task.getPublisherId(), task.getReward());
        // 接单方：到账
        if (claimant != null) {
            walletService.income(claimant, task.getReward(), BizType.TASK, taskId,
                    task.getTitle(), "TASK", taskId);
            notificationService.pushFund(claimant, "任务收入到账",
                    "「" + task.getTitle() + "」已结算，" + task.getReward() + " 屋币已入账", taskId);
            creditService.refresh(claimant);
        }
        creditService.refresh(task.getPublisherId());
        return toVO(require(taskId), userId);
    }

    /** 委托方驳回，要求返工：待确认 → 进行中 */
    @Transactional(rollbackFor = Exception.class)
    public TaskDto.TaskVO reject(Long userId, Long taskId, TaskDto.ConfirmReq req) {
        Task task = require(taskId);
        if (!task.getPublisherId().equals(userId)) {
            throw BizException.forbidden("只有发布人才能驳回");
        }
        int rows = taskMapper.reject(taskId, userId);
        if (rows == 0) {
            throw BizException.of(ResultCode.TASK_STATUS_INVALID);
        }
        Long claimant = task.getClaimantId();
        if (claimant != null) {
            notificationService.pushTask(claimant, "任务被驳回，请返工",
                    req != null && req.content() != null ? req.content() : "发布人认为交付不合格，请修改后重新提交",
                    taskId);
        }
        return toVO(require(taskId), userId);
    }

    // ================================================================== 取消

    /** 发布人取消：退冻结；若已被接单，记发布人责任 */
    @Transactional(rollbackFor = Exception.class)
    public TaskDto.TaskVO cancelByPublisher(Long userId, Long taskId, String reason) {
        Task task = require(taskId);
        if (!task.getPublisherId().equals(userId)) {
            throw BizException.forbidden("只有发布人才能取消任务");
        }
        int rows = taskMapper.cancelByPublisher(taskId, userId, reason);
        if (rows == 0) {
            throw BizException.of(ResultCode.TASK_STATUS_INVALID);
        }
        claimMapper.cancel(taskId, reason, BY_PUBLISHER);
        walletService.unfreeze(userId, task.getReward(), BizType.TASK, taskId,
                task.getTitle(), "TASK", taskId);

        Long claimant = task.getClaimantId();
        if (claimant != null) {
            notificationService.pushTask(claimant, "任务被发布人取消",
                    "「" + task.getTitle() + "」已被取消，不计入你的违约记录", taskId);
            creditService.refresh(claimant);
        }
        return toVO(require(taskId), userId);
    }

    /** 接单方放弃：终止任务，扣违约金给发布人 */
    @Transactional(rollbackFor = Exception.class)
    public TaskDto.TaskVO abandon(Long userId, Long taskId, String reason) {
        Task task = require(taskId);
        if (!userId.equals(task.getClaimantId())) {
            throw BizException.forbidden("只有接单方才能放弃");
        }
        int rows = taskMapper.reopen(taskId, userId);
        if (rows == 0) {
            throw BizException.of(ResultCode.TASK_STATUS_INVALID);
        }
        // 幂等键里要放「这一次接单」的 id，而不是任务 id：
        // 否则同一个任务被同一个接单方放弃第二次（退回大厅后又被接、又放弃）时，
        // 违约金与补偿都会被当成重复请求跳过 —— 白放弃不罚钱。
        TaskClaim activeClaim = claimMapper.selectOne(Wrappers.<TaskClaim>lambdaQuery()
                .eq(TaskClaim::getTaskId, taskId)
                .ne(TaskClaim::getStatus, ClaimStatus.CANCELLED.name())
                .orderByDesc(TaskClaim::getId)
                .last("LIMIT 1"));
        long originId = activeClaim != null && activeClaim.getId() != null
                ? activeClaim.getId() : taskId;
        claimMapper.cancel(taskId, reason, BY_CLAIMER);
        // 注意：这里**不解冻**。任务是退回大厅等别人接，报酬还得继续冻结着，
        // 否则发布人可能把钱花掉，等下一个接单方完成时没钱可付。
        // 只有发布人主动取消 / 任务过期 / 争议裁决，才真正解冻。

        long penalty = configService.getLong("penaltyNoShow", 20);
        if (penalty > 0) {
            walletService.penalty(userId, penalty, BizType.TASK, taskId,
                    "放弃任务违约金", "ABANDON", originId);
            walletService.income(task.getPublisherId(), penalty, BizType.TASK, taskId,
                    "放弃任务违约金补偿", "ABANDON", originId);
        }
        creditService.refresh(userId);
        notificationService.pushTask(task.getPublisherId(), "接单方放弃了任务",
                "「" + task.getTitle() + "」已重新回到任务大厅，报酬 " + task.getReward() + " 屋币仍在冻结中",
                taskId);
        return toVO(require(taskId), userId);
    }

    // ================================================================== 争议

    @Transactional(rollbackFor = Exception.class)
    public TaskDto.TaskVO raiseDispute(Long userId, Long taskId, String reason) {
        Task task = require(taskId);
        boolean party = task.getPublisherId().equals(userId)
                || Objects.equals(task.getClaimantId(), userId);
        if (!party) {
            throw BizException.forbidden("只有任务相关方才能发起争议");
        }
        int rows = taskMapper.raiseDispute(taskId, reason);
        if (rows == 0) {
            throw BizException.of(ResultCode.TASK_STATUS_INVALID);
        }
        // 任务状态变了还不够——后台「争议」页读的是 dispute 表，
        // 不建档管理员就看不到可裁决的争议。
        recordDispute(task, userId, reason);
        Long other = task.getPublisherId().equals(userId) ? task.getClaimantId() : task.getPublisherId();
        if (other != null) {
            notificationService.pushTask(other, "任务进入争议处理",
                    "「" + task.getTitle() + "」已被发起争议，等待管理员介入", taskId);
        }
        return toVO(require(taskId), userId);
    }

    /**
     * 建争议单（幂等：同一任务已有 PENDING 争议就不再建）。
     * <p>冻结金额记录当时的任务报酬，裁决时按这个数分配，避免过程中价格变动导致算错。
     */
    private void recordDispute(Task task, Long initiatorId, String reason) {
        Long exists = disputeMapper.selectCount(Wrappers.<Dispute>lambdaQuery()
                .eq(Dispute::getTaskId, task.getId())
                .eq(Dispute::getStatus, "PENDING"));
        if (exists != null && exists > 0) {
            return;
        }
        Dispute dispute = new Dispute();
        dispute.setTaskId(task.getId());
        dispute.setPublisherId(task.getPublisherId());
        dispute.setTakerId(task.getClaimantId());
        dispute.setFrozenAmount(task.getReward());
        dispute.setInitiatorId(initiatorId);
        dispute.setReason(reason);
        dispute.setStatus("PENDING");
        dispute.setCreatedAt(LocalDateTime.now());
        disputeMapper.insert(dispute);
    }

    // ================================================================== 查询

    public TaskDto.TaskVO detail(Long taskId, Long currentUserId, boolean countView) {
        Task task = require(taskId);
        if (countView) {
            taskMapper.incrView(taskId);
            task.setViewCount(task.getViewCount() == null ? 1 : task.getViewCount() + 1);
            // 记一条浏览历史：只对登录用户记，失败不影响详情页打开
            if (currentUserId != null) {
                try {
                    browseHistoryMapper.upsert(currentUserId, FavTargetType.TASK.name(), taskId);
                } catch (Exception e) {
                    log.debug("浏览历史写入失败: user={}, task={}", currentUserId, taskId, e);
                }
            }
        }
        return toVO(task, currentUserId);
    }

    public CursorPage<TaskDto.TaskVO> list(TaskDto.TaskQuery query, Long currentUserId) {
        int size = query.size() == null ? 10 : Math.max(1, Math.min(query.size(), 30));

        LambdaQueryWrapper<Task> wrapper = Wrappers.<Task>lambdaQuery();
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String kw = query.keyword().trim();
            wrapper.and(w -> w.like(Task::getTitle, kw).or().like(Task::getDescription, kw));
        }
        if (query.type() != null && !query.type().isBlank()) {
            wrapper.eq(Task::getType, parseType(query.type()).name());
        }
        String status = TaskWire.fromWire(query.status());
        boolean publicList = status == null || status.isBlank();
        wrapper.eq(Task::getStatus, publicList ? TaskStatus.WAITING.name() : status);
        if (publicList) {
            // 大厅/搜索只展示「还能接」的任务：已经过截止时间的不再出现。
            // 不能只依赖定时任务把状态改成 EXPIRED——它最多有 10 分钟延迟，
            // 这段时间里过期任务会一直挂在大厅里（用户实测就是这种情况）。
            wrapper.gt(Task::getDeadline, LocalDateTime.now());
        }
        if (query.minReward() != null) {
            wrapper.ge(Task::getReward, query.minReward());
        }
        if (query.maxReward() != null) {
            wrapper.le(Task::getReward, query.maxReward());
        }

        // 前端传的是大写枚举（DEFAULT/REWARD/LATEST/DEADLINE），统一转小写再分支；
        // 之前直接比较大写与 "reward"/"deadline"，导致选任何排序都落到默认分支。
        String sort = query.sort() == null ? "latest" : query.sort().trim().toLowerCase();
        if ("default".equals(sort)) {
            sort = "latest";
        }
        switch (sort) {
            case "reward" -> wrapper.orderByDesc(Task::getReward).orderByDesc(Task::getId);
            case "deadline" -> wrapper.orderByAsc(Task::getDeadline).orderByDesc(Task::getId);
            default -> wrapper.orderByDesc(Task::getId);
        }
        if (query.cursor() != null && "latest".equals(sort)) {
            wrapper.lt(Task::getId, query.cursor());
        }

        List<Task> rows = taskMapper.selectList(wrapper.last("LIMIT " + (size + 1)));
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = rows.subList(0, size);
        }
        String next = hasMore ? String.valueOf(rows.get(rows.size() - 1).getId()) : null;
        return CursorPage.of(toVOs(rows, currentUserId), next);
    }

    /** 我的任务：我发布的 / 我接取的 */
    public CursorPage<TaskDto.TaskVO> mine(Long userId, String role, String status, Long cursor, Integer size) {
        int limit = size == null ? 10 : Math.max(1, Math.min(size, 30));
        LambdaQueryWrapper<Task> wrapper = Wrappers.<Task>lambdaQuery();
        if ("taker".equalsIgnoreCase(role)) {
            wrapper.eq(Task::getClaimantId, userId);
        } else {
            wrapper.eq(Task::getPublisherId, userId);
        }
        String internal = TaskWire.fromWire(status);
        if (internal != null && !internal.isBlank()) {
            wrapper.eq(Task::getStatus, internal);
        }
        if (cursor != null) {
            wrapper.lt(Task::getId, cursor);
        }
        wrapper.orderByDesc(Task::getId);

        List<Task> rows = taskMapper.selectList(wrapper.last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }
        String next = hasMore ? String.valueOf(rows.get(rows.size() - 1).getId()) : null;
        return CursorPage.of(toVOs(rows, userId), next);
    }

    // ================================================================== 收藏

    @Transactional(rollbackFor = Exception.class)
    public void favorite(Long userId, Long taskId) {
        require(taskId);
        Favorite exist = favoriteMapper.selectOne(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, FavTargetType.TASK.name())
                .eq(Favorite::getTargetId, taskId));
        if (exist != null) {
            return;
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setTargetType(FavTargetType.TASK.name());
        favorite.setTargetId(taskId);
        favorite.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(favorite);
        taskMapper.incrFavor(taskId, 1);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unfavorite(Long userId, Long taskId) {
        int deleted = favoriteMapper.delete(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, FavTargetType.TASK.name())
                .eq(Favorite::getTargetId, taskId));
        if (deleted > 0) {
            taskMapper.incrFavor(taskId, -1);
        }
    }

    // ================================================================== 内部

    public Task require(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BizException.of(ResultCode.TASK_NOT_FOUND);
        }
        return task;
    }

    /** 冲突检测：拿接单人手上有进行中的任务比对时间窗 */
    private ConflictChecker.Result checkConflict(Long userId, Task task) {
        List<Task> active = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getClaimantId, userId)
                .eq(Task::getStatus, TaskStatus.IN_PROGRESS.name()));
        if (active.isEmpty()) {
            return ConflictChecker.Result.none();
        }
        List<ConflictChecker.Occupied> occupied = new ArrayList<>();
        for (Task occupiedTask : active) {
            if (occupiedTask.getStartTime() == null) {
                continue;
            }
            int minutes = occupiedTask.getDurationMinutes() == null ? 60 : occupiedTask.getDurationMinutes();
            occupied.add(new ConflictChecker.Occupied(
                    occupiedTask.getId(),
                    occupiedTask.getTitle(),
                    occupiedTask.getStartTime(),
                    occupiedTask.getStartTime().plusMinutes(minutes),
                    occupiedTask.getLocation()));
        }
        LocalDateTime start = task.getStartTime();
        int minutes = task.getDurationMinutes() == null ? 60 : task.getDurationMinutes();
        TaskTypeMeta meta = TaskTypeMeta.of(parseType(task.getType()));
        ConflictChecker.Candidate candidate = new ConflictChecker.Candidate(
                start,
                start == null ? null : start.plusMinutes(minutes),
                task.getLocation(),
                meta.overlapPolicy() == TaskTypeMeta.OverlapPolicy.EXCLUSIVE);
        return ConflictChecker.check(candidate, occupied);
    }

    /** CAS 失败后重读一次，区分到底输在哪 */
    private BizException explainClaimFailure(Long taskId) {
        Task latest = taskMapper.selectById(taskId);
        if (latest == null) {
            return BizException.of(ResultCode.TASK_NOT_FOUND);
        }
        if (latest.getDeadline() != null && latest.getDeadline().isBefore(LocalDateTime.now())) {
            return BizException.of(ResultCode.DEADLINE_EXPIRED);
        }
        if (!TaskStatus.WAITING.name().equals(latest.getStatus())) {
            return BizException.of(ResultCode.TASK_ALREADY_TAKEN);
        }
        return BizException.of(ResultCode.VERSION_CONFLICT);
    }

    public TaskDto.TaskVO toVO(Task task, Long currentUserId) {
        return toVOs(List.of(task), currentUserId).get(0);
    }

    /** 批量组装出参：一次查完用户与收藏，避免 N+1 */
    public List<TaskDto.TaskVO> toVOs(List<Task> tasks, Long currentUserId) {
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = new HashSet<>();
        for (Task task : tasks) {
            userIds.add(task.getPublisherId());
            if (task.getClaimantId() != null) {
                userIds.add(task.getClaimantId());
            }
        }
        userIds.remove(null);

        Map<Long, User> users = new HashMap<>();
        Map<Long, UserCredit> credits = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : userMapper.selectBatchIds(userIds)) {
                users.put(user.getId(), user);
            }
            for (UserCredit credit : userCreditMapper.selectBatchIds(userIds)) {
                credits.put(credit.getUserId(), credit);
            }
        }

        Set<Long> favorited = new HashSet<>();
        if (currentUserId != null) {
            List<Long> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toList());
            List<Favorite> favorites = favoriteMapper.selectList(Wrappers.<Favorite>lambdaQuery()
                    .eq(Favorite::getUserId, currentUserId)
                    .eq(Favorite::getTargetType, FavTargetType.TASK.name())
                    .in(Favorite::getTargetId, taskIds));
            for (Favorite favorite : favorites) {
                favorited.add(favorite.getTargetId());
            }
        }

        // 一次性把接单记录查出来：发布方要看到「交付说明 + 凭证」才知道该不该确认
        Map<Long, TaskClaim> claims = new HashMap<>();
        List<Long> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toList());
        for (TaskClaim taskClaim : claimMapper.selectList(Wrappers.<TaskClaim>lambdaQuery()
                .in(TaskClaim::getTaskId, taskIds))) {
            claims.put(taskClaim.getTaskId(), taskClaim);
        }

        List<TaskDto.TaskVO> result = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            TaskDto.UserBrief publisher = brief(task.getPublisherId(), users, credits);
            TaskDto.UserBrief taker = brief(task.getClaimantId(), users, credits);
            TaskClaim claim = claims.get(task.getId());
            // 交付说明与凭证只给任务双方看：
            // GET /tasks/{id} 对游客开放，若不过滤，任何人都能看到别人任务的
            // 取件截图 / 小票 / 送达照片（这些往往带手机号、地址、订单号）。
            boolean canSeeDelivery = currentUserId != null
                    && (currentUserId.equals(task.getPublisherId())
                    || currentUserId.equals(task.getClaimantId()));
            String internalStatus = task.getStatus();
            TaskStatus statusEnum = safeStatus(internalStatus);
            TaskType typeEnum = parseType(task.getType());
            result.add(new TaskDto.TaskVO(
                    task.getId(),
                    task.getTitle(),
                    task.getDescription(),
                    task.getType(),
                    typeEnum.getLabel(),
                    task.getReward(),
                    task.getDeadline(),
                    task.getStartTime(),
                    task.getDurationMinutes(),
                    task.getLocation(),
                    task.getPeopleNeeded(),
                    readJson(task.getImages()),
                    TaskWire.toWire(internalStatus),
                    statusEnum == null ? internalStatus : statusEnum.label(),
                    task.getPublisherId(),
                    publisher,
                    task.getClaimantId(),
                    taker,
                    favorited.contains(task.getId()),
                    // mine 的含义是「这条任务是我发布的」，前端用它判断能不能抢单。
                    // 必须显式判空：游客的 currentUserId 是 null，若写成
                    // Objects.equals(publisherId, currentUserId) 在外键也为 null 时会得到 true。
                    currentUserId != null && currentUserId.equals(task.getPublisherId()),
                    task.getCreatedAt(),
                    task.getSubmittedAt(),
                    task.getConfirmedAt(),
                    readTags(task.getTags()),
                    task.getViewCount(),
                    task.getFavorCount(),
                    claim == null || !canSeeDelivery ? null : claim.getSubmitContent(),
                    claim == null || !canSeeDelivery
                            ? Collections.emptyList() : readJson(claim.getSubmitProof())));
        }
        return result;
    }

    private TaskDto.UserBrief brief(Long userId, Map<Long, User> users, Map<Long, UserCredit> credits) {
        if (userId == null) {
            return null;
        }
        User user = users.get(userId);
        if (user == null) {
            return null;
        }
        UserCredit credit = credits.get(userId);
        int score = credit == null || credit.getCreditScore() == null
                ? 100 : (int) Math.round(credit.getCreditScore().doubleValue());
        return new TaskDto.UserBrief(user.getId(), user.getNickname(), user.getAvatar(), score);
    }

    private TaskStatus safeStatus(String status) {
        try {
            return TaskStatus.valueOf(status);
        } catch (Exception e) {
            return null;
        }
    }

    public TaskType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BizException.of(ResultCode.PARAM_INVALID);
        }
        try {
            return TaskType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BizException.of(ResultCode.PARAM_INVALID);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String writeJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            log.warn("序列化图片列表失败", e);
            return null;
        }
    }

    private List<String> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static String writeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return tags.stream().filter(Objects::nonNull).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.joining(","));
    }

    private static List<String> readTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }
}
