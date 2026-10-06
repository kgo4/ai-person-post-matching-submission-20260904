-- 站内通知中心（HR 匹配闭环 P1 基础设施）。
-- 设计文档：docs/hr-matching-closed-loop-design.md 3.1 节。
-- 唯一键 uk_notification_dedup 兼作防骚扰：同类型 + 同业务 + 同接收人天然只发一条；
-- 「提醒评估」类通知支持重复提醒（发送前按业务规则清理旧提醒或更新已有记录由服务层处理）。
CREATE TABLE sys_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    receiver_user_id BIGINT NOT NULL COMMENT '接收人用户ID',
    type VARCHAR(32) NOT NULL COMMENT '通知类型：ASSESSMENT_REMINDER/REPORT_READY/LEARNING_REVIEW/INVITE_MEETING/MATCHING_PUBLISHED',
    title VARCHAR(128) NOT NULL,
    content VARCHAR(512) NULL,
    biz_type VARCHAR(32) NULL COMMENT '业务类型：EMPLOYEE/MATCHING_RECORD/REPORT/INTERVIEW',
    biz_id BIGINT NULL COMMENT '业务主键，用于前端跳转',
    read_status TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    read_time DATETIME NULL,
    created_by BIGINT NULL COMMENT '发送人用户ID（系统触发时为空）',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notification_receiver (receiver_user_id, read_status, created_time),
    UNIQUE KEY uk_notification_dedup (receiver_user_id, type, biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';
