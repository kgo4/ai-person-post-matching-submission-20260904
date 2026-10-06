CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    permission_code VARCHAR(120) NOT NULL,
    permission_name VARCHAR(160) NOT NULL,
    permission_type VARCHAR(24) NOT NULL DEFAULT 'API',
    description VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (permission_code)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(id),
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
);

INSERT INTO sys_permission (permission_code, permission_name, permission_type, description) VALUES
('ASSESSMENT:SELF', '本人能力评估', 'MENU', '进入并完成本人能力评估流程'),
('NOTIFICATION:SELF', '本人通知', 'MENU', '查看本人匹配与评估通知'),
('LEARNING:SELF', '本人学习路径', 'MENU', '查看本人已发布匹配结果并生成学习路径'),
('EMPLOYEE:READ', '人员档案查看', 'API', '查看组织范围内人员档案'),
('ASSESSMENT:MANAGE', '评估运营', 'API', '查看和管理人员能力评估任务'),
('MATCHING:READ', '匹配查看', 'MENU', '查看匹配任务、结果和差距诊断'),
('MATCHING:EXECUTE', '发起人岗匹配', 'API', '执行人岗匹配'),
('MATCHING:APPROVE', '匹配审批', 'API', '审批匹配建议'),
('MATCHING:CONFIG', '匹配策略配置', 'MENU', '配置匹配权重与校准规则'),
('POST:READ', '岗位体系查看', 'MENU', '查看岗位模型与岗位档案'),
('POST:MANAGE', '岗位体系管理', 'API', '维护岗位模型、能力要求和模板'),
('POST:EVOLUTION', '岗位演化管理', 'MENU', '运行和审核岗位演化任务'),
('AI:CONFIG', 'AI模型配置', 'MENU', '配置企业AI模型与连接参数'),
('ASSESSMENT:CONFIG', '评估规则配置', 'MENU', '配置面试题数量与评估规则'),
('USER:MANAGE', '用户管理', 'MENU', '管理账号和用户角色分配'),
('ROLE:MANAGE', '角色权限管理', 'MENU', '管理角色、权限码和授权关系'),
('AUDIT:READ', '操作日志查看', 'MENU', '查看系统操作审计记录');

INSERT INTO sys_role (role_code, role_name, description, data_scope, status, is_deleted)
SELECT 'EMPLOYEE', '员工', '仅本人能力评估和本人通知', 4, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'EMPLOYEE');
INSERT INTO sys_role (role_code, role_name, description, data_scope, status, is_deleted)
SELECT 'HR_SPECIALIST', 'HR专员', '人员档案、能力评估运营与人岗匹配执行', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'HR_SPECIALIST');
INSERT INTO sys_role (role_code, role_name, description, data_scope, status, is_deleted)
SELECT 'JOB_ARCHITECT', '岗位体系管理员', '岗位模型、岗位演化和岗位能力体系管理', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'JOB_ARCHITECT');
INSERT INTO sys_role (role_code, role_name, description, data_scope, status, is_deleted)
SELECT 'AI_CONFIG_MANAGER', 'AI配置管理员', 'AI模型和能力评估规则配置', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'AI_CONFIG_MANAGER');
INSERT INTO sys_role (role_code, role_name, description, data_scope, status, is_deleted)
SELECT 'SECURITY_ADMIN', '权限管理员', '账号、角色、授权关系和审计管理，不含业务数据操作', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'SECURITY_ADMIN');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('ASSESSMENT:SELF', 'NOTIFICATION:SELF', 'LEARNING:SELF') WHERE r.role_code = 'EMPLOYEE';
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('ASSESSMENT:SELF', 'NOTIFICATION:SELF', 'EMPLOYEE:READ', 'ASSESSMENT:MANAGE', 'MATCHING:READ', 'MATCHING:EXECUTE', 'MATCHING:APPROVE', 'MATCHING:CONFIG', 'POST:READ') WHERE r.role_code = 'HR_SPECIALIST';
-- 注意：HR 是单一角色，不再拆分「HR 专员 / HR 负责人」。审批与策略配置权限已并入上面的 HR_SPECIALIST。
-- 此处原本还有一条 WHERE r.role_code = 'HR_LEAD' 的授权语句，因 sys_role 中不存在该角色行而恒为零行、从不生效，已移除。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('POST:READ', 'POST:MANAGE', 'POST:EVOLUTION') WHERE r.role_code = 'JOB_ARCHITECT';
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('AI:CONFIG', 'ASSESSMENT:CONFIG') WHERE r.role_code = 'AI_CONFIG_MANAGER';
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('USER:MANAGE', 'ROLE:MANAGE', 'AUDIT:READ') WHERE r.role_code = 'SECURITY_ADMIN';

DELETE ur FROM sys_user_role ur
JOIN sys_role r ON r.id = ur.role_id
WHERE r.role_code = 'ADMIN';
UPDATE sys_role SET status = 0, is_deleted = 1, description = '已废弃：请按业务职责分配新角色' WHERE role_code = 'ADMIN';
