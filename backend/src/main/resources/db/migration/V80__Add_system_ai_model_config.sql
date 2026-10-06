CREATE TABLE IF NOT EXISTS system_ai_model_config (
    id BIGINT PRIMARY KEY COMMENT '固定为 1，只允许一条全局企业模型配置',
    enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用企业模型',
    base_url VARCHAR(500) DEFAULT NULL COMMENT '企业 OpenAI-compatible 网关地址',
    model_name VARCHAR(200) DEFAULT NULL COMMENT '模型名称',
    api_key_ciphertext VARCHAR(2000) DEFAULT NULL COMMENT '加密保存的密钥（不存明文）',
    timeout_seconds INT NOT NULL DEFAULT 60 COMMENT '请求超时（秒）',
    temperature DECIMAL(4,2) NOT NULL DEFAULT 0.20 COMMENT '默认温度',
    updated_by BIGINT DEFAULT NULL COMMENT '最后更新人',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局企业 AI 模型配置（单行，id=1）';

INSERT INTO system_ai_model_config (id, enabled, base_url, model_name, api_key_ciphertext, timeout_seconds, temperature)
VALUES (1, 0, NULL, NULL, NULL, 60, 0.20)
ON DUPLICATE KEY UPDATE id = id;
