package com.baolong.wsw.service;

import com.baolong.wsw.common.BizException;
import com.baolong.wsw.common.CursorPage;
import com.baolong.wsw.entity.WalletAccount;
import com.baolong.wsw.entity.WalletTransaction;
import com.baolong.wsw.enums.BizType;
import com.baolong.wsw.enums.TransType;
import com.baolong.wsw.mapper.WalletAccountMapper;
import com.baolong.wsw.mapper.WalletTransactionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 钱包服务 —— 全站唯一允许改余额的地方。
 *
 * <p><b>记账口径</b>：
 * <ul>
 *   <li>{@code amount} 表示本次操作对「可用余额」的有符号影响：正数入账、负数出账</li>
 *   <li>恒等式 {@code available - debt = 该账户流水 amount 之和}</li>
 *   <li>{@code frozen} 不来自流水，而是「冻结中任务报酬」的镜像，
 *       与 {@code task} 表按状态聚合对账</li>
 * </ul>
 * 所以冻结时记 -50（可用少了 50），解冻时记 +50，结算时接单方记 +50，
 * 委托方那一侧只是冻结额度释放，不再记流水 —— 每一行流水都能被用户读懂。
 *
 * <p><b>并发</b>：所有余额变动都走带条件的原子 UPDATE，不做「查-改-写」；
 * 影响行数为 0 即代表余额不足，直接拒绝。金额一律用整数（屋币不可拆分）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletAccountMapper accountMapper;
    private final WalletTransactionMapper txMapper;
    private final ConfigService configService;

    // ------------------------------------------------------------------ 账户

    /** 取账户，没有就开一个并发放注册赠送 */
    @Transactional(rollbackFor = Exception.class)
    public WalletAccount getOrCreate(Long userId) {
        WalletAccount account = findAccount(userId);
        if (account != null) {
            return account;
        }
        WalletAccount created = new WalletAccount();
        created.setUserId(userId);
        created.setAvailable(0L);
        created.setFrozen(0L);
        created.setDebt(0L);
        created.setVersion(0);
        try {
            accountMapper.insert(created);
        } catch (DuplicateKeyException e) {
            // 并发注册，别人先建好了，直接取
            return findAccount(userId);
        }
        long initGrant = configService.getLong("initGrant", 100);
        if (initGrant > 0) {
            grant(userId, initGrant, BizType.REGISTER, userId,
                    "新用户注册赠送", "REGISTER", userId);
        }
        return findAccount(userId);
    }

    public WalletAccount findAccount(Long userId) {
        return accountMapper.selectOne(
                Wrappers.<WalletAccount>lambdaQuery().eq(WalletAccount::getUserId, userId));
    }

    public WalletAccount requireAccount(Long userId) {
        WalletAccount account = findAccount(userId);
        if (account == null) {
            throw BizException.notFound("钱包账户不存在");
        }
        return account;
    }

    // ------------------------------------------------------------------ 记账

    /** 管理员发币 / 注册赠送 */
    @Transactional(rollbackFor = Exception.class)
    public void grant(Long userId, long amount, BizType bizType, Long bizId,
                      String title, String originType, Long originId) {
        if (amount <= 0) {
            throw BizException.badRequest("发放金额必须大于 0");
        }
        long cap = configService.getLong("maxHolding", 100_000);
        WalletAccount account = requireAccount(userId);
        if (account.getAvailable() + amount > cap) {
            throw BizException.badRequest("超出单人持有上限 " + cap);
        }
        if (alreadyRecorded(TransType.GRANT, originType, originId, userId)) {
            return;
        }
        accountMapper.addAvailable(userId, amount);
        record(userId, TransType.GRANT, bizType, bizId, title, amount, originType, originId,
                "入账 " + amount + " 屋币");
    }

    /** 冻结：发布任务时把报酬锁住 */
    @Transactional(rollbackFor = Exception.class)
    public void freeze(Long userId, long amount, BizType bizType, Long bizId,
                       String title, String originType, Long originId) {
        if (amount <= 0) {
            throw BizException.badRequest("冻结金额必须大于 0");
        }
        if (alreadyRecorded(TransType.FREEZE, originType, originId, userId)) {
            return;
        }
        int rows = accountMapper.freeze(userId, amount);
        if (rows == 0) {
            throw BizException.badRequest("可用余额不足，无法发布该任务");
        }
        record(userId, TransType.FREEZE, bizType, bizId, title, -amount, originType, originId,
                "冻结 " + amount + " 屋币");
    }

    /** 解冻：取消 / 过期，钱回到可用余额 */
    @Transactional(rollbackFor = Exception.class)
    public void unfreeze(Long userId, long amount, BizType bizType, Long bizId,
                         String title, String originType, Long originId) {
        if (amount <= 0) {
            return;
        }
        if (alreadyRecorded(TransType.UNFREEZE, originType, originId, userId)) {
            return;
        }
        int rows = accountMapper.unfreeze(userId, amount);
        if (rows == 0) {
            // 冻结额不够说明账目已经不一致（例如冻结被重复消费）。
            // 以前这里只打 warn 就返回，结果是「任务取消了但钱没退」，必须让调用方失败回滚。
            log.error("解冻失败，冻结余额不足: user={}, amount={}", userId, amount);
            throw BizException.conflict("冻结余额不足，无法退回（数据异常，请联系管理员核对账目）");
        }
        record(userId, TransType.UNFREEZE, bizType, bizId, title, amount, originType, originId,
                "解冻退回 " + amount + " 屋币");
    }

    /** 接单方收入：任务结算到账 */
    @Transactional(rollbackFor = Exception.class)
    public void income(Long userId, long amount, BizType bizType, Long bizId,
                       String title, String originType, Long originId) {
        if (amount <= 0) {
            return;
        }
        if (alreadyRecorded(TransType.INCOME, originType, originId, userId)) {
            return;
        }
        accountMapper.addAvailable(userId, amount);
        record(userId, TransType.INCOME, bizType, bizId, title, amount, originType, originId,
                "任务收入 " + amount + " 屋币");
    }

    /**
     * 委托方冻结额度释放（结算给接单方时调用）。
     * <p>可用余额在这里不变，所以不写流水——流水里委托方那一笔在冻结时已经记过了。
     */
    @Transactional(rollbackFor = Exception.class)
    public void consumeFrozen(Long userId, long amount) {
        if (amount <= 0) {
            return;
        }
        int rows = accountMapper.consumeFrozen(userId, amount);
        if (rows == 0) {
            // 关键：0 行意味着委托方冻结额不够了。以前只打 warn 继续往下走，
            // 调用方随后照样 income 给接单方 —— 等于凭空印钱。必须抛出让事务回滚。
            log.error("释放冻结失败，冻结余额不足: user={}, amount={}", userId, amount);
            throw BizException.conflict("冻结余额不足，无法完成结算（数据异常，请联系管理员核对账目）");
        }
    }

    /** 违约金：优先扣可用余额，不够的部分挂欠款 */
    @Transactional(rollbackFor = Exception.class)
    public void penalty(Long userId, long amount, BizType bizType, Long bizId,
                        String title, String originType, Long originId) {
        if (amount <= 0) {
            return;
        }
        if (alreadyRecorded(TransType.PENALTY, originType, originId, userId)) {
            return;
        }
        WalletAccount account = requireAccount(userId);
        // 一条原子 UPDATE 完成「先扣可用、不足挂欠款」，
        // 避免中间被并发花掉导致 available 与 debt 各自记一半。
        long available = account.getAvailable() == null ? 0 : account.getAvailable();
        if (available < amount) {
            log.info("违约金不足，余额 {} / 应扣 {}，差额挂欠款", available, amount);
        }
        accountMapper.chargeOff(userId, amount);
        record(userId, TransType.PENALTY, bizType, bizId, title, -amount, originType, originId,
                "违约扣罚 " + amount + " 屋币");
    }

    /** 管理员冲正：反向修正一笔错账，必须留痕 */
    @Transactional(rollbackFor = Exception.class)
    public void reversal(Long userId, long amount, BizType bizType, Long bizId,
                         String title, String remark, String originType, Long originId) {
        if (amount == 0) {
            return;
        }
        if (amount > 0) {
            accountMapper.addAvailable(userId, amount);
        } else {
            // 与违约金同一套：扣得动多少扣多少，差额挂欠款，且整件事是一条原子 UPDATE
            accountMapper.chargeOff(userId, -amount);
        }
        record(userId, TransType.REVERSAL, bizType, bizId, title, amount, originType, originId, remark);
    }

    // ------------------------------------------------------------------ 查询

    /** 资金流水：游标分页，可按流水类型筛选 */
    public CursorPage<WalletTransaction> transactions(Long userId, String type, Long cursor, int size) {
        int limit = Math.max(1, Math.min(size, 50));
        boolean hasType = type != null && !type.isBlank();
        List<WalletTransaction> rows = txMapper.selectList(
                Wrappers.<WalletTransaction>lambdaQuery()
                        .eq(WalletTransaction::getUserId, userId)
                        // 前端按流水类型筛选（FREEZE / INCOME / GRANT ...），
                        // 之前 Controller 没接这个参数，所以选了也没反应
                        .eq(hasType, WalletTransaction::getTransType,
                                hasType ? type.trim().toUpperCase() : null)
                        .lt(cursor != null, WalletTransaction::getId, cursor)
                        .orderByDesc(WalletTransaction::getId)
                        .last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        if (hasMore) {
            rows = rows.subList(0, limit);
        }
        String next = hasMore && !rows.isEmpty()
                ? String.valueOf(rows.get(rows.size() - 1).getId())
                : null;
        return CursorPage.of(rows, next);
    }

    public CursorPage<WalletTransaction> transactions(Long userId, Long cursor, int size) {
        return transactions(userId, null, cursor, size);
    }

    /** 对账：可用 - 欠款 应等于流水之和 */
    public boolean checkInvariant(Long userId) {
        WalletAccount account = requireAccount(userId);
        long expected = account.getAvailable() - account.getDebt();
        long actual = txMapper.sumAmount(account.getId());
        if (expected != actual) {
            log.error("对账不一致: user={}, 账面={}, 流水={}", userId, expected, actual);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ 内部

    private boolean alreadyRecorded(TransType type, String originType, Long originId, Long userId) {
        if (originType == null || originId == null) {
            return false;
        }
        String key = idemKey(type, originType, originId, userId);
        Long count = txMapper.selectCount(
                Wrappers.<WalletTransaction>lambdaQuery().eq(WalletTransaction::getIdemKey, key));
        return count != null && count > 0;
    }

    private String idemKey(TransType type, String originType, Long originId, Long userId) {
        return type.name() + ":" + originType + ":" + originId + ":" + userId;
    }

    private void record(Long userId, TransType type, BizType bizType, Long bizId,
                        String title, long amount, String originType, Long originId, String remark) {
        WalletAccount account = requireAccount(userId);
        WalletTransaction tx = new WalletTransaction();
        tx.setAccountId(account.getId());
        tx.setUserId(userId);
        tx.setTransType(type.name());
        tx.setBizType(bizType == null ? BizType.TASK.name() : bizType.name());
        tx.setBizId(bizId);
        tx.setBizTitle(title);
        tx.setAmount(amount);
        tx.setBalanceAfter(account.getAvailable());
        tx.setFrozenAfter(account.getFrozen());
        tx.setRemark(remark);
        tx.setOriginType(originType);
        tx.setOriginId(originId);
        tx.setIdemKey(idemKey(type, originType, originId, userId));
        tx.setCreatedAt(LocalDateTime.now());
        txMapper.insert(tx);
    }
}
