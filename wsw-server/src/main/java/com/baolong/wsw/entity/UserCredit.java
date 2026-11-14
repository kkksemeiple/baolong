package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 信用档案：信誉评分模块的输出物。
 * <p>五项指标 + 综合得分 + 等级。每次结算/评价后异步重算，用 {@code version} 做乐观锁保证幂等。
 */
@Data
@TableName("user_credit")
public class UserCredit {

    /** 与 user.id 一对一 */
    @TableId(type = IdType.INPUT)
    private Long userId;

    /** 0~120 */
    private BigDecimal creditScore;

    /** EXCELLENT / GOOD / NORMAL / LIMITED / FROZEN */
    private String level;

    private BigDecimal finishRate;

    private BigDecimal onTimeRate;

    private BigDecimal reviewScore;

    private BigDecimal disputeRate;

    private BigDecimal cancelRate;

    private Integer totalClaims;

    private Integer finishedClaims;

    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}
