package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资金流水。
 * <p><b>只增不改</b>：这张表没有 update 也没有 delete，任何纠错都用一条 REVERSAL 反向流水表达，
 * 这样任何时候都能拿流水重放出余额，账目可审计。
 */
@Data
@TableName("wallet_transaction")
public class WalletTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long accountId;

    private Long userId;

    /** FREEZE / UNFREEZE / INCOME / PAY / PENALTY / GRANT / REVERSAL */
    private String transType;

    /** TASK / DISPUTE / ADMIN / REGISTER */
    private String bizType;

    private Long bizId;

    private String bizTitle;

    /** 正数入账，负数出账 */
    private Long amount;

    private Long balanceAfter;

    private Long frozenAfter;

    private String remark;

    /** 幂等键 */
    private String idemKey;

    private String originType;

    private Long originId;

    private LocalDateTime createdAt;
}
