package com.baolong.wsw.service;

import com.baolong.wsw.algorithm.CreditCalculator;
import com.baolong.wsw.dto.CreditDto;
import com.baolong.wsw.entity.User;
import com.baolong.wsw.entity.UserCredit;
import com.baolong.wsw.enums.ClaimStatus;
import com.baolong.wsw.enums.CreditLevel;
import com.baolong.wsw.enums.UserStatus;
import com.baolong.wsw.mapper.CreditMapper;
import com.baolong.wsw.mapper.UserCreditMapper;
import com.baolong.wsw.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 信誉评分服务 —— 负责「取数 → 交给 {@link CreditCalculator} → 落库 → 同步接单权限」。
 *
 * <p>评分本身是纯函数（见 {@code algorithm.CreditCalculator}），这里只做三件事：
 * 1. 从 90 天滚动窗口里聚合五项指标；
 * 2. 把结果写进 {@code user_credit}（带乐观锁，重复触发不会写脏）；
 * 3. 分数掉到 80 以下时把 {@code user.status} 置为 LIMITED，恢复后自动改回 NORMAL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private final CreditMapper creditMapper;
    private final UserCreditMapper userCreditMapper;
    private final UserMapper userMapper;
    private final ConfigService configService;

    /**
     * 重算并保存某用户的信用档案。
     * <p>由「任务结算」「发布评价」「管理员裁决」等事件触发，可被重复调用。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserCredit refresh(Long userId) {
        if (userId == null) {
            return null;
        }
        int windowDays = configService.getInt("creditWindowDays", 90);
        LocalDateTime since = LocalDateTime.now().minusDays(windowDays);

        Map<String, Object> stats = creditMapper.claimStats(userId, since);
        int total = toInt(stats, "total");
        int finished = toInt(stats, "finished");
        int onTime = toInt(stats, "on_time");
        int cancelled = toInt(stats, "cancelled");
        double reviewAvg = toDouble(stats, "review_avg");
        int disputes = creditMapper.disputeCount(userId, since);

        CreditCalculator.Metrics metrics =
                new CreditCalculator.Metrics(total, finished, onTime, disputes, cancelled, reviewAvg);
        CreditCalculator.Result result = CreditCalculator.compute(metrics);

        UserCredit credit = userCreditMapper.selectById(userId);
        boolean create = credit == null;
        if (create) {
            credit = new UserCredit();
            credit.setUserId(userId);
            credit.setVersion(0);
        }
        credit.setCreditScore(BigDecimal.valueOf(result.score()));
        credit.setLevel(result.level().name());
        credit.setFinishRate(round(result.finishRate()));
        credit.setOnTimeRate(round(result.onTimeRate()));
        credit.setReviewScore(round(result.reviewScore()));
        credit.setDisputeRate(round(result.disputeRate()));
        credit.setCancelRate(round(result.cancelRate()));
        credit.setTotalClaims(total);
        credit.setFinishedClaims(finished);
        credit.setUpdatedAt(LocalDateTime.now());
        if (create) {
            userCreditMapper.insert(credit);
        } else {
            userCreditMapper.updateById(credit);
        }

        syncUserStatus(userId, result.level());
        log.debug("信誉重算完成: user={}, score={}, level={}, 样本={}",
                userId, result.score(), result.level(), total);
        return credit;
    }

    /** 读分数，没有档案就即时算一次 */
    public double scoreOf(Long userId) {
        UserCredit credit = userCreditMapper.selectById(userId);
        if (credit == null || credit.getCreditScore() == null) {
            UserCredit created = refresh(userId);
            return created == null ? 0 : created.getCreditScore().doubleValue();
        }
        return credit.getCreditScore().doubleValue();
    }

    public CreditLevel levelOf(Long userId) {
        UserCredit credit = userCreditMapper.selectById(userId);
        if (credit == null || credit.getLevel() == null) {
            return CreditLevel.NORMAL;
        }
        return CreditLevel.valueOf(credit.getLevel());
    }

    /** 是否允许接单：分数 ≥ 80 */
    public boolean canAccept(Long userId) {
        return scoreOf(userId) >= 80;
    }

    public UserCredit find(Long userId) {
        return userCreditMapper.selectById(userId);
    }

    // ------------------------------------------------------------------

    private void syncUserStatus(Long userId, CreditLevel level) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        // 封禁是管理员行为，不因分数变化自动解除
        if (UserStatus.BANNED.name().equals(user.getStatus())) {
            return;
        }
        String want = level.canAccept() ? UserStatus.NORMAL.name() : UserStatus.LIMITED.name();
        if (!want.equals(user.getStatus())) {
            user.setStatus(want);
            userMapper.updateById(user);
            log.info("信用等级变化导致用户状态调整: user={}, {} -> {}", userId, user.getStatus(), want);
        }
    }

    private static BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private static int toInt(Map<String, Object> map, String key) {
        if (map == null) {
            return 0;
        }
        Object v = map.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static double toDouble(Map<String, Object> map, String key) {
        if (map == null) {
            return 0;
        }
        Object v = map.get(key);
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    /**
     * 信誉分明细：分数 + 五个比率 + 参与计算的最近接单记录。
     * <p>用户看到明细才知道「为什么掉分」，否则只会觉得分数是黑盒。
     */
    public CreditDto.DetailVO detail(Long userId) {
        UserCredit credit = userCreditMapper.selectById(userId);
        if (credit == null) {
            credit = refresh(userId);
        }
        int windowDays = configService.getInt("creditWindowDays", 90);
        LocalDateTime since = LocalDateTime.now().minusDays(windowDays);

        List<CreditDto.RecordVO> records = new ArrayList<>();
        for (Map<String, Object> row : creditMapper.claimRecords(userId, since, 20)) {
            String status = toStr(row, "status");
            records.add(new CreditDto.RecordVO(
                    toLong(row, "task_id"),
                    toStr(row, "task_title"),
                    status,
                    claimStatusLabel(status),
                    row.get("rating") instanceof Number n ? n.intValue() : null,
                    toTime(row, "claimed_at"),
                    toTime(row, "submitted_at"),
                    toInt(row, "on_time") == 1));
        }

        double score = credit.getCreditScore() == null ? 0 : credit.getCreditScore().doubleValue();
        CreditLevel level = CreditLevel.of(score);
        return new CreditDto.DetailVO(
                score,
                level.name(),
                levelLabel(level),
                credit.getFinishRate() == null ? 0 : credit.getFinishRate().doubleValue(),
                credit.getOnTimeRate() == null ? 0 : credit.getOnTimeRate().doubleValue(),
                credit.getReviewScore() == null ? 0 : credit.getReviewScore().doubleValue(),
                credit.getDisputeRate() == null ? 0 : credit.getDisputeRate().doubleValue(),
                credit.getCancelRate() == null ? 0 : credit.getCancelRate().doubleValue(),
                credit.getTotalClaims() == null ? 0 : credit.getTotalClaims(),
                credit.getFinishedClaims() == null ? 0 : credit.getFinishedClaims(),
                windowDays,
                level.canAccept(),
                records);
    }

    /** 等级中文名，前端直接用，避免两边各写一份映射 */
    public static String levelLabel(CreditLevel level) {
        return switch (level) {
            case EXCELLENT -> "优秀";
            case GOOD -> "良好";
            case NORMAL -> "正常";
            case LIMITED -> "受限";
            case FROZEN -> "冻结";
        };
    }

    private static String claimStatusLabel(String status) {
        if (status == null) {
            return "未知";
        }
        try {
            return ClaimStatus.valueOf(status).label();
        } catch (IllegalArgumentException e) {
            return status;
        }
    }

    private static String toStr(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static long toLong(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static LocalDateTime toTime(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        if (v instanceof LocalDateTime t) {
            return t;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }
}
