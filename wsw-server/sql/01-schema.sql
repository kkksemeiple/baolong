-- =====================================================================
-- 暴龙万事屋 · 校园任务大厅  数据库脚本
-- 数据库：MySQL 8.0 / 8.4    字符集：utf8mb4
-- 用法：Navicat 里连上本机 MySQL → 新建查询 → 打开本文件 → 全部执行
-- 说明：本脚本可重复执行（会先删表再建），执行后自动写入字典与配置数据。
--      演示账号与演示任务由后端启动时自动播种（见 DataSeeder）。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `wsw`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE `wsw`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 1. 用户表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`
(
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`          VARCHAR(32)     NOT NULL COMMENT '登录名',
    `password_hash`     VARCHAR(100)    NOT NULL COMMENT '密码哈希（BCrypt）',
    `nickname`          VARCHAR(32)     NOT NULL COMMENT '昵称',
    `avatar`            VARCHAR(255)             DEFAULT NULL COMMENT '头像地址',
    `phone`             VARCHAR(20)     NOT NULL COMMENT '手机号',
    `role`              VARCHAR(16)     NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN/SADMIN',
    `status`            VARCHAR(16)     NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/LIMITED/BANNED',
    `auth_status`       VARCHAR(16)     NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/APPROVED/REJECTED',
    `student_no`        VARCHAR(32)              DEFAULT NULL COMMENT '学号',
    `real_name`         VARCHAR(32)              DEFAULT NULL COMMENT '真实姓名',
    `cert_reject_reason` VARCHAR(255)            DEFAULT NULL COMMENT '实名驳回原因',
    `cert_image`        VARCHAR(255)             DEFAULT NULL COMMENT '学生证照片 URL，管理员审核时看',
    `tags`              VARCHAR(200)             DEFAULT NULL COMMENT '标签，英文逗号分隔（与 task.tags 保持一致）',
    `intro`             VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '个人简介',
    `recommend_enabled` TINYINT         NOT NULL DEFAULT 1 COMMENT '个性化推荐开关 1开 0关',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `last_active_at`    DATETIME                 DEFAULT NULL COMMENT '最近活跃时间（心跳）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_status` (`status`),
    KEY `idx_role` (`role`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户';

-- ---------------------------------------------------------------------
-- 2. 信用档案（信誉评分模块的落库结果）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `user_credit`;
CREATE TABLE `user_credit`
(
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `credit_score`    DECIMAL(5, 2)   NOT NULL DEFAULT 100.00 COMMENT '信用分 0~120',
    `level`           VARCHAR(16)     NOT NULL DEFAULT 'NORMAL' COMMENT 'EXCELLENT/GOOD/NORMAL/LIMITED/FROZEN',
    `finish_rate`     DECIMAL(5, 4)   NOT NULL DEFAULT 0.9000 COMMENT '完成率',
    `on_time_rate`    DECIMAL(5, 4)   NOT NULL DEFAULT 0.9000 COMMENT '准时率',
    `review_score`    DECIMAL(3, 2)   NOT NULL DEFAULT 4.50 COMMENT '评价均分 0~5',
    `dispute_rate`    DECIMAL(5, 4)   NOT NULL DEFAULT 0.0500 COMMENT '争议率',
    `cancel_rate`     DECIMAL(5, 4)   NOT NULL DEFAULT 0.0500 COMMENT '取消率',
    `total_claims`    INT             NOT NULL DEFAULT 0 COMMENT '90天窗口内接单总数',
    `finished_claims` INT             NOT NULL DEFAULT 0 COMMENT '90天窗口内完成单数',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `version`         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='信用档案';

-- ---------------------------------------------------------------------
-- 3. 钱包账户
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `wallet_account`;
CREATE TABLE `wallet_account`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `available`  BIGINT          NOT NULL DEFAULT 0 COMMENT '可用余额',
    `frozen`     BIGINT          NOT NULL DEFAULT 0 COMMENT '冻结余额',
    `debt`       BIGINT          NOT NULL DEFAULT 0 COMMENT '欠款',
    `version`    INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='钱包账户';

-- ---------------------------------------------------------------------
-- 4. 资金流水（只增不改）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `wallet_transaction`;
CREATE TABLE `wallet_transaction`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `account_id`    BIGINT UNSIGNED NOT NULL COMMENT '钱包账户ID',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '冗余用户ID，便于按人查询',
    `trans_type`    VARCHAR(16)     NOT NULL COMMENT 'FREEZE/UNFREEZE/INCOME/PAY/PENALTY/GRANT/REVERSAL',
    `biz_type`      VARCHAR(16)     NOT NULL COMMENT 'TASK/DISPUTE/ADMIN/REGISTER',
    `biz_id`        BIGINT UNSIGNED          DEFAULT NULL COMMENT '关联业务ID',
    `biz_title`     VARCHAR(128)             DEFAULT NULL COMMENT '关联业务标题，便于展示',
    `amount`        BIGINT          NOT NULL COMMENT '变动金额，正数入账负数出账',
    `balance_after` BIGINT          NOT NULL COMMENT '变动后可用余额',
    `frozen_after`  BIGINT          NOT NULL DEFAULT 0 COMMENT '变动后冻结余额',
    `remark`        VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '备注',
    `idem_key`      VARCHAR(64)              DEFAULT NULL COMMENT '幂等键（Idempotency-Key）',
    `origin_type`   VARCHAR(16)              DEFAULT NULL COMMENT '幂等来源类型',
    `origin_id`     BIGINT UNSIGNED          DEFAULT NULL COMMENT '幂等来源ID',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_idem` (`idem_key`),
    UNIQUE KEY `uk_origin` (`origin_type`, `origin_id`, `trans_type`, `account_id`),
    KEY `idx_account_id` (`account_id`, `id`),
    KEY `idx_user_time` (`user_id`, `created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='资金流水';

-- ---------------------------------------------------------------------
-- 5. 任务
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `task`;
CREATE TABLE `task`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `title`         VARCHAR(64)     NOT NULL COMMENT '标题',
    `description`   TEXT            NOT NULL COMMENT '描述',
    `type`          VARCHAR(16)     NOT NULL COMMENT 'PICKUP/BUY/RENT/TUTOR/MOVE/OTHER',
    `reward`        BIGINT          NOT NULL COMMENT '报酬（屋币整数，与设计文档的「分」同义）',
    `deadline`      DATETIME        NOT NULL COMMENT '截止时间',
    `start_time`    DATETIME                 DEFAULT NULL COMMENT '期望开始时间（冲突检测用）',
    `duration_minutes` INT          NOT NULL DEFAULT 60 COMMENT '预计时长（分钟），时间窗=start_time+该值',
    `location`      VARCHAR(128)    NOT NULL DEFAULT '' COMMENT '地点',
    `tags`          VARCHAR(200)             DEFAULT NULL COMMENT '标签，英文逗号分隔（推荐特征）',
    `people_needed` INT             NOT NULL DEFAULT 1 COMMENT '人数需求',
    `images`        JSON                     DEFAULT NULL COMMENT '图片地址数组',
    `status`        VARCHAR(16)     NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/IN_PROGRESS/TO_CONFIRM/COMPLETED/SETTLED/DISPUTED/CANCELLED/EXPIRED',
    `publisher_id`  BIGINT UNSIGNED NOT NULL COMMENT '委托方',
    `claimant_id`   BIGINT UNSIGNED          DEFAULT NULL COMMENT '当前承接人',
    `view_count`    INT             NOT NULL DEFAULT 0,
    `favor_count`   INT             NOT NULL DEFAULT 0,
    `claimed_at`    DATETIME                 DEFAULT NULL COMMENT '接单时间',
    `submitted_at`  DATETIME                 DEFAULT NULL COMMENT '提交凭证时间',
    `confirmed_at`  DATETIME                 DEFAULT NULL COMMENT '确认完成时间',
    `settled_at`    DATETIME                 DEFAULT NULL COMMENT '结算时间',
    `closed_at`     DATETIME                 DEFAULT NULL COMMENT '取消/过期/裁决关闭时间',
    `cancel_reason` VARCHAR(255)             DEFAULT NULL COMMENT '取消原因或争议说明',
    `version`       INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status_deadline` (`status`, `deadline`),
    KEY `idx_status_created` (`status`, `created_at`),
    KEY `idx_publisher` (`publisher_id`, `id`),
    KEY `idx_claimant` (`claimant_id`, `id`),
    KEY `idx_type` (`type`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='任务';

-- ---------------------------------------------------------------------
-- 6. 接单记录（TaskClaim：承接人、凭证提交与确认的载体）
--    uk_task_id = (task_id) 保证「一单一人」，配合 Task.version 乐观锁双重兜底
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `task_claim`;
CREATE TABLE `task_claim`
(
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `task_id`        BIGINT UNSIGNED NOT NULL,
    `claimer_id`     BIGINT UNSIGNED NOT NULL COMMENT '接单人',
    `status`         VARCHAR(16)     NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS/SUBMITTED/COMPLETED/CANCELLED',
    `claimed_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接单时间',
    `submitted_at`   DATETIME                 DEFAULT NULL COMMENT '提交完成时间',
    `confirmed_at`   DATETIME                 DEFAULT NULL COMMENT '确认时间',
    `finished_at`    DATETIME                 DEFAULT NULL COMMENT '结束时间',
    `submit_content` TEXT                     DEFAULT NULL COMMENT '交付说明',
    `submit_proof`   JSON                     DEFAULT NULL COMMENT '交付凭证图片数组',
    `cancel_reason`  VARCHAR(255)             DEFAULT NULL,
    `cancel_by`      VARCHAR(16)              DEFAULT NULL COMMENT 'PUBLISHER/CLAIMER/SYSTEM，用于区分谁的责任',
    `rating`         TINYINT                  DEFAULT NULL COMMENT '发布方评价 1~5 星',
    `review_content` VARCHAR(500)             DEFAULT NULL COMMENT '评价内容',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`),
    KEY `idx_claimer` (`claimer_id`, `id`),
    KEY `idx_claimer_status` (`claimer_id`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='接单记录';

-- ---------------------------------------------------------------------
-- 7. 议价
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `bargain_record`;
CREATE TABLE `bargain_record`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `task_id`      BIGINT UNSIGNED NOT NULL,
    `from_user_id` BIGINT UNSIGNED NOT NULL COMMENT '报价方',
    `to_user_id`   BIGINT UNSIGNED NOT NULL COMMENT '被报价方',
    `amount`       BIGINT          NOT NULL COMMENT '报价金额',
    `status`       VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED/INVALID',
    `parent_id`    BIGINT UNSIGNED          DEFAULT NULL COMMENT '上一轮报价',
    `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_task` (`task_id`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='议价记录';

-- ---------------------------------------------------------------------
-- 8. 收藏
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `favorite`;
CREATE TABLE `favorite`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT UNSIGNED NOT NULL,
    `target_type` VARCHAR(16)     NOT NULL COMMENT 'TASK/POST',
    `target_id`   BIGINT UNSIGNED NOT NULL,
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
    KEY `idx_target` (`target_type`, `target_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='收藏';

-- ---------------------------------------------------------------------
-- 9. 浏览历史
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `browse_history`;
CREATE TABLE `browse_history`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT UNSIGNED NOT NULL,
    `target_type` VARCHAR(16)     NOT NULL COMMENT 'TASK/POST',
    `target_id`   BIGINT UNSIGNED NOT NULL,
    `viewed_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
    KEY `idx_user_time` (`user_id`, `viewed_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='浏览历史';

-- ---------------------------------------------------------------------
-- 10. 会话 / 11. 消息
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `conversation`;
CREATE TABLE `conversation`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `task_id`         BIGINT UNSIGNED          DEFAULT NULL COMMENT '最近一次相关的任务，仅留痕',
    `user_a`          BIGINT UNSIGNED NOT NULL COMMENT '小ID一方',
    `user_b`          BIGINT UNSIGNED NOT NULL COMMENT '大ID一方',
    `last_message`    VARCHAR(255)    NOT NULL DEFAULT '',
    `last_message_at` DATETIME                 DEFAULT NULL,
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pair` (`user_a`, `user_b`),
    KEY `idx_user_a` (`user_a`),
    KEY `idx_user_b` (`user_b`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='会话';

DROP TABLE IF EXISTS `message`;
CREATE TABLE `message`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT UNSIGNED NOT NULL,
    `from_user_id`    BIGINT UNSIGNED NOT NULL,
    `to_user_id`      BIGINT UNSIGNED NOT NULL,
    `content`         VARCHAR(1000)   NOT NULL,
    `content_type`    VARCHAR(16)     NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/IMAGE',
    `read_flag`       TINYINT         NOT NULL DEFAULT 0,
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_conv` (`conversation_id`, `id`),
    KEY `idx_to_unread` (`to_user_id`, `read_flag`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='私信';

-- ---------------------------------------------------------------------
-- 12. 通知
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `type`       VARCHAR(16)     NOT NULL COMMENT 'TASK/FUND/FORUM/SYSTEM',
    `title`      VARCHAR(64)     NOT NULL,
    `content`    VARCHAR(500)    NOT NULL DEFAULT '',
    `biz_id`     BIGINT UNSIGNED          DEFAULT NULL,
    `link`       VARCHAR(255)             DEFAULT NULL COMMENT '前端跳转路径',
    `read_flag`  TINYINT         NOT NULL DEFAULT 0,
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`, `id`),
    KEY `idx_user_unread` (`user_id`, `read_flag`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='通知';

-- ---------------------------------------------------------------------
-- 13. 帖子 / 14. 回帖 / 15. 点赞
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `post`;
CREATE TABLE `post`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `title`       VARCHAR(128)    NOT NULL,
    `content`     TEXT            NOT NULL,
    `author_id`   BIGINT UNSIGNED NOT NULL,
    `tags`        VARCHAR(200)             DEFAULT NULL COMMENT '标签，英文逗号分隔',
    `reply_count` INT             NOT NULL DEFAULT 0,
    `like_count`  INT             NOT NULL DEFAULT 0,
    `status`      VARCHAR(16)     NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/OFFLINE/DELETED',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_author` (`author_id`, `id`),
    KEY `idx_status_id` (`status`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='帖子';

DROP TABLE IF EXISTS `post_reply`;
CREATE TABLE `post_reply`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `post_id`    BIGINT UNSIGNED NOT NULL,
    `parent_id`  BIGINT UNSIGNED          DEFAULT NULL COMMENT '二级回复的父回复',
    `content`    VARCHAR(1000)   NOT NULL,
    `author_id`  BIGINT UNSIGNED NOT NULL,
    `status`     VARCHAR(16)     NOT NULL DEFAULT 'NORMAL',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_post` (`post_id`, `id`),
    KEY `idx_parent` (`parent_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='回帖';

DROP TABLE IF EXISTS `post_like`;
CREATE TABLE `post_like`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `post_id`    BIGINT UNSIGNED NOT NULL,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_user` (`post_id`, `user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='帖子点赞';

-- ---------------------------------------------------------------------
-- 16. 举报
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `report`;
CREATE TABLE `report`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `type`         VARCHAR(24)     NOT NULL COMMENT 'ILLEGAL_CONTENT/NO_SHOW/FAKE_DELIVERY/HARASS/OTHER',
    `task_id`      BIGINT UNSIGNED          DEFAULT NULL,
    `post_id`      BIGINT UNSIGNED          DEFAULT NULL,
    `target_title` VARCHAR(128)    NOT NULL DEFAULT '',
    `reporter_id`  BIGINT UNSIGNED NOT NULL,
    `reason`       VARCHAR(500)    NOT NULL,
    `evidence`     VARCHAR(1000)            DEFAULT NULL COMMENT '证据说明（图片地址用逗号分隔）',
    `status`       VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RESOLVED/REJECTED',
    `result`       VARCHAR(255)             DEFAULT NULL,
    `created_at`   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `handled_at`   DATETIME                 DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='举报';

-- ---------------------------------------------------------------------
-- 17. 争议仲裁
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `dispute`;
CREATE TABLE `dispute`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `task_id`       BIGINT UNSIGNED NOT NULL,
    `publisher_id`  BIGINT UNSIGNED NOT NULL,
    `taker_id`      BIGINT UNSIGNED NOT NULL,
    `frozen_amount` BIGINT          NOT NULL COMMENT '争议中的冻结金额',
    `initiator_id`  BIGINT UNSIGNED NOT NULL COMMENT '发起人',
    `reason`        VARCHAR(500)    NOT NULL,
    `evidence`      VARCHAR(1000)            DEFAULT NULL COMMENT '证据说明（图片地址用逗号分隔）',
    `status`        VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/JUDGED',
    `result`        VARCHAR(16)              DEFAULT NULL COMMENT 'PUBLISHER/TAKER/PARTIAL/INSUFFICIENT',
    `remark`        VARCHAR(500)             DEFAULT NULL COMMENT '裁决说明',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `judged_at`     DATETIME                 DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task` (`task_id`),
    KEY `idx_status` (`status`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='争议';

-- ---------------------------------------------------------------------
-- 18. 内容审核队列（敏感词命中后进入）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `audit_item`;
CREATE TABLE `audit_item`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `target_type` VARCHAR(16)     NOT NULL COMMENT 'TASK/POST/REPLY',
    `target_id`   BIGINT UNSIGNED NOT NULL,
    `title`       VARCHAR(128)    NOT NULL DEFAULT '',
    `content`     TEXT            NOT NULL,
    `hit_reason`  VARCHAR(255)    NOT NULL DEFAULT '' COMMENT '命中的敏感词',
    `author_id`   BIGINT UNSIGNED NOT NULL,
    `status`      VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `handled_at`  DATETIME                 DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='内容审核';

-- ---------------------------------------------------------------------
-- 19. 系统配置
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `system_config`;
CREATE TABLE `system_config`
(
    `cfg_key`    VARCHAR(64)  NOT NULL,
    `value`      VARCHAR(255) NOT NULL,
    `remark`     VARCHAR(128) NOT NULL DEFAULT '',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`cfg_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统配置';

-- ---------------------------------------------------------------------
-- 20. 登录日志
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `login_log`;
CREATE TABLE `login_log`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `ip`         VARCHAR(64)     NOT NULL DEFAULT '',
    `device`     VARCHAR(128)    NOT NULL DEFAULT '',
    `success`    TINYINT         NOT NULL DEFAULT 1,
    `msg`        VARCHAR(128)    NOT NULL DEFAULT '',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='登录日志';

-- ---------------------------------------------------------------------
-- 21. 管理操作日志
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `admin_op_log`;
CREATE TABLE `admin_op_log`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `admin_id`   BIGINT UNSIGNED NOT NULL,
    `action`     VARCHAR(64)     NOT NULL COMMENT '动作',
    `target`     VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '对象类型:ID',
    `detail`     VARCHAR(500)    NOT NULL DEFAULT '',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_admin` (`admin_id`, `id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='管理操作日志';

-- ---------------------------------------------------------------------
-- 22. 幂等记录（前端每个写请求都带 Idempotency-Key）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `idem_record`;
CREATE TABLE `idem_record`
(
    `idem_key`   VARCHAR(64)     NOT NULL,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `api`        VARCHAR(128)    NOT NULL DEFAULT '',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`idem_key`),
    KEY `idx_created` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='幂等记录';

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 字典与配置初始数据
-- =====================================================================
INSERT INTO `system_config` (`cfg_key`, `value`, `remark`)
VALUES ('initGrant', '100', '新用户注册赠送屋币'),
       ('maxHolding', '100000', '单人持有上限'),
       ('grantLimit', '5000', '单次发放上限'),
       ('autoConfirmHours', '72', '待确认自动确认时长（小时）'),
       ('penaltyCancel', '5', '违约金-无责取消额度外'),
       ('penaltyNoShow', '20', '违约金-爽约')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

-- =====================================================================
-- 演示数据说明
-- 演示账号（linxiaoman / zhaolei / chenyu，密码均为 abc12345）
-- 以及 12 条演示任务、若干帖子，由后端首次启动时自动写入，
-- 这样密码哈希永远和当前后端一致，避免手写哈希对不上。
-- =====================================================================
