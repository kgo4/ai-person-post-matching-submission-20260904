-- V159: 对齐业务角色授权 + 废弃历史遗留的 HR_LEAD 角色
--
-- 背景（线上实测）：sys_role_permission 与迁移脚本的定义已分叉 ——
--   · 实际承担 HR 职责的 HR_SPECIALIST 缺 MATCHING:APPROVE，
--     导致 HR 修改/推送匹配结果时 PUT /api/matching/record/** 返回 403；
--   · 线上仍残留历史遗留的 HR_LEAD 角色（且它反而持有 MATCHING:APPROVE）。
-- 业务口径已明确：**不存在「HR 负责人」角色；HR 为单一角色，全权负责匹配相关事项**
-- （与 V147 的注释一致，V147 早已移除 HR_LEAD 的授权语句）。
--
-- 本迁移只做「补齐」与「废弃」，**不删除任何既有授权**，避免误伤线上有意调整的配置：
--   1) 按 V147 定义的权限矩阵补齐 5 个业务角色的缺失授权；
--   2) 废弃 HR_LEAD 角色。
--
-- 幂等性：sys_role_permission 主键为 (role_id, permission_id)，INSERT IGNORE 可重复执行；
--         UPDATE 带 role_code 条件，重复执行结果一致。

-- 1) 补齐各业务角色授权（只补缺，不删多） ----------------------------------

-- EMPLOYEE：本人评估 / 通知 / 学习路径 / 本人发起匹配
-- （MATCHING:SELF 由 V153 授予，此处一并兜底）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
    ('ASSESSMENT:SELF', 'NOTIFICATION:SELF', 'LEARNING:SELF', 'MATCHING:SELF')
WHERE r.role_code = 'EMPLOYEE';

-- HR_SPECIALIST：人员档案 + 评估运营 + 匹配全链路（执行 / 审批 / 策略配置）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
    ('ASSESSMENT:SELF', 'NOTIFICATION:SELF', 'EMPLOYEE:READ', 'ASSESSMENT:MANAGE',
     'MATCHING:READ', 'MATCHING:EXECUTE', 'MATCHING:APPROVE', 'MATCHING:CONFIG', 'POST:READ')
WHERE r.role_code = 'HR_SPECIALIST';

-- JOB_ARCHITECT：岗位体系维护与演化
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
    ('POST:READ', 'POST:MANAGE', 'POST:EVOLUTION')
WHERE r.role_code = 'JOB_ARCHITECT';

-- AI_CONFIG_MANAGER：AI 模型与评估规则配置
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
    ('AI:CONFIG', 'ASSESSMENT:CONFIG')
WHERE r.role_code = 'AI_CONFIG_MANAGER';

-- SECURITY_ADMIN：账号 / 角色 / 审计
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
    ('USER:MANAGE', 'ROLE:MANAGE', 'AUDIT:READ')
WHERE r.role_code = 'SECURITY_ADMIN';

-- 2) 废弃历史遗留的 HR_LEAD 角色 -------------------------------------------
-- 口径：不存在 HR 负责人角色，匹配相关事项由 HR_SPECIALIST 全权负责。
--
-- 刻意「不」将绑定 HR_LEAD 的账号迁移到 HR_SPECIALIST：按业务口径这些账号本身即废弃，
-- 自动迁移会让废弃账号重新获得权限。若其中确有需要保留的账号，
-- 请先在「系统管理 → 用户」中改绑 HR_SPECIALIST，再启用。
UPDATE sys_role
SET status = 0,
    is_deleted = 1,
    description = '已废弃：不存在 HR 负责人角色，匹配相关事项由 HR_SPECIALIST 全权负责'
WHERE role_code = 'HR_LEAD';
