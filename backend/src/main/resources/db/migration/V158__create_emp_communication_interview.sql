-- HR-员工视频终面记录（HR 匹配闭环 P5）。
-- 设计文档：docs/hr-matching-closed-loop-design.md 5.3 节。
--
-- 路线：匹配通过后由 HR 手动发起真实视频沟通作为入职前最终确认。
-- 平台不做媒体：只管「发起邀请 → 通知 → 留痕 → 定论」，媒体走讯飞会议（链接由 HR 粘贴）。
-- meeting_source 预留 API 自动创建能力，起步阶段固定 MANUAL。
CREATE TABLE emp_communication_interview (
    id BIGINT NOT NULL AUTO_INCREMENT,
    emp_id BIGINT NOT NULL COMMENT '员工档案ID',
    post_id BIGINT NULL COMMENT '关联岗位；沟通可发生在匹配记录之外时为空',
    matching_record_id BIGINT NULL COMMENT '关联的匹配记录（若有）',
    meeting_url VARCHAR(512) NOT NULL COMMENT '会议邀请链接（域名走可配置白名单）',
    meeting_source VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL手动粘贴 / API自动创建（预留）',
    scheduled_time DATETIME NULL COMMENT '预约沟通时间；空=尽快',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待沟通 1已完成 2已取消',
    result TINYINT NULL COMMENT '沟通结论：1通过(人工沟通确认) 2不通过 3待定',
    comment VARCHAR(1024) NULL COMMENT 'HR 沟通纪要与评价（员工侧可读原文，需求方明确不脱敏）',
    briefing_snapshot JSON NULL COMMENT '发起时的沟通要点快照（辅助材料，无需追溯实时性）',
    created_by BIGINT NOT NULL COMMENT '发起的 HR 用户ID',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_time DATETIME NULL,
    finished_time DATETIME NULL COMMENT '录入结论时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    KEY idx_comm_interview_emp (emp_id, status),
    KEY idx_comm_interview_record (matching_record_id),
    KEY idx_comm_interview_status (status, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HR-员工视频终面（讯飞会议）记录';
