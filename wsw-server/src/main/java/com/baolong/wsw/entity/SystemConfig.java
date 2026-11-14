package com.baolong.wsw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置。主键是 {@code cfg_key}（避开 MySQL 保留字 KEY）。
 */
@Data
@TableName("system_config")
public class SystemConfig {

    @TableId(type = IdType.INPUT)
    private String cfgKey;

    private String value;

    private String remark;

    private LocalDateTime updatedAt;
}
