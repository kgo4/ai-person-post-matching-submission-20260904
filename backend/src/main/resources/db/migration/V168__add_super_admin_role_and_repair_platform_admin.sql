-- =============================================================================
-- V168：新增「超级管理员 SUPER_ADMIN」+ 修复 PLATFORM_ADMIN 授权缺失
--
-- 背景（2026-09-04）：
--   1. 线上 security-admin 执行了「合并 AI 管理员/权限管理员 → PLATFORM_ADMIN」的修复脚本，
--      角色状态（status=1 / is_deleted=0）与账号绑定（security-admin → PLATFORM_ADMIN）均已正确，
--      但仍然进不去平台管理员的功能。根因是**授权行（sys_role_permission）缺失**
--      —— 该脚本第 1 步只建角色、第 2 步才授权，若第 2 步没落库（或权限码不存在），
--      账号就是「有角色、零权限」，前端表现为菜单全空且不报错。
--   2. 按需求新增可访问**所有角色所有功能**的 SUPER_ADMIN。
--
-- ⚠️ 本迁移与手工执行版 sql/add_super_admin_and_repair_platform_admin.sql 内容一致，
--    Flyway 已禁用（生产手工建库），实际执行请用 sql/ 下那份。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) 权限码兜底：V147 已建这些码；线上若是手工建库可能缺行，补齐（INSERT IGNORE 幂等）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type, description) VALUES
('USER:MANAGE', '用户管理', 'MENU', '管理账号和用户角色分配'),
('ROLE:MANAGE', '角色权限管理', 'MENU', '管理角色、权限码和授权关系'),
('AUDIT:READ', '操作日志查看', 'MENU', '查看系统操作审计记录'),
('AI:CONFIG', 'AI模型配置', 'MENU', '配置企业AI模型与连接参数'),
('ASSESSMENT:CONFIG', '评估规则配置', 'MENU', '配置面试题数量与评估规则');

-- ---------------------------------------------------------------------------
-- 2) 修复 PLATFORM_ADMIN 授权（只补缺，不删除该角色上已有的其他授权）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r
  JOIN sys_permission p ON p.permission_code IN
       ('USER:MANAGE', 'ROLE:MANAGE', 'AUDIT:READ', 'AI:CONFIG', 'ASSESSMENT:CONFIG')
 WHERE r.role_code = 'PLATFORM_ADMIN';

-- ---------------------------------------------------------------------------
-- 3) 新增 SUPER_ADMIN 角色（可访问所有角色的所有功能）
--
--    注意：后端 **不靠** 下面的逐条授权来实现超管 —— SystemAuthenticationPortAdapter
--    检测到该角色后会直接装载 sys_permission 全表权限，因此**以后新增权限码无需再补授权**。
--    这里仍然落库授权，是为了让「角色权限」页能直观看到它的授权范围。
-- ---------------------------------------------------------------------------
INSERT INTO sys_role (role_code, role_name, description, data_scope, status, is_deleted, created_by)
SELECT 'SUPER_ADMIN', '超级管理员',
       '可访问所有角色的所有功能（权限由后端按角色动态装载全部权限码）', 1, 1, 0, 0
 WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'SUPER_ADMIN');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
  FROM sys_role r
  JOIN sys_permission p
 WHERE r.role_code = 'SUPER_ADMIN';
