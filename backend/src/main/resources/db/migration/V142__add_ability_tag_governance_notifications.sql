CREATE TABLE ability_tag_governance_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT NOT NULL COMMENT '生成岗位能力导入的操作人',
    candidate_id BIGINT NOT NULL,
    proposal_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'UNREAD' COMMENT 'UNREAD, READ',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_time DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_proposal_recipient (proposal_id, recipient_user_id),
    KEY idx_recipient_status (recipient_user_id, status, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签治理挂载建议通知';
