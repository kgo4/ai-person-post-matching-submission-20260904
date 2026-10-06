CREATE TABLE IF NOT EXISTS vector_sync_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_key VARCHAR(64) NOT NULL COMMENT '业务唯一键：EMPLOYEE:{empId} / POST:{postId}',
    entity_type VARCHAR(20) NOT NULL COMMENT 'EMPLOYEE / POST',
    entity_id BIGINT NOT NULL COMMENT '员工ID或岗位ID',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SUCCEEDED/FAILED',
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 10,
    next_retry_time DATETIME DEFAULT NULL,
    error_message VARCHAR(1000) DEFAULT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    published_time DATETIME DEFAULT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vector_sync_business_key (business_key),
    INDEX idx_vector_sync_status_retry (status, next_retry_time),
    INDEX idx_vector_sync_entity (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='向量同步任务：监听器只负责入队，后台任务负责写入，失败指数退避重试';
