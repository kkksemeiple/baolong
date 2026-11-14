package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 钱包账户。
 * <p>恒等式：{@code available + frozen - debt = 该账户下所有流水 amount 之和}。
 * 任何一个字段单独变动都是 bug，必须走 {@code WalletService} 的记账方法。
 */
@Data
@TableName("wallet_account")
public class WalletAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 可用余额 */
    private Long available;

    /** 冻结余额 */
    private Long frozen;

    /** 欠款 */
    private Long debt;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
