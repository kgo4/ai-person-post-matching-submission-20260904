-- =============================================================================
-- V162：合并 AI 管理员 + 权限管理员 为「平台管理员 PLATFORM_ADMIN」
--
-- 依据：docs/role-restructure-design.md（2026-09-04 已确认方案 A）
--
-- 为什么要合并：
--   · AI_CONFIG_MANAGER 里一半是**平台基础设施**（模型配置含凭据、Agent 记忆、系统设置），
--     一半是**业务知识**（知识库、图谱、标签治理、过滤规则）—— 两类东西的授权逻辑不同，
--     塞进一个角色导致「角色描述说不清、菜单配不对」；
--   · 实测该角色只持有 AI:CONFIG / ASSESSMENT:CONFIG，而侧边栏给它配的 8 条菜单里
--     有 4 条要求 POST:MANAGE 或 ASSESSMENT:MANAGE → **永远不会显示**（配置与生效不一致）。
--
-- 合并后的职责边界：
--   · PLATFORM_ADMIN = 账号/角色/审计 + AI 基础设施 + 系统设置（不参与任何业务数据）
--   · JOB_ARCHITECT  = 岗位体系 + 知识资产/图谱/标签治理/过滤规则（其 API 改挂 POST:MANAGE）
--
-- ⚠️ 与 V159 的关键差异（务必理解）：
--   V159 废弃 HR_LEAD 时**不迁移**其绑定账号 —— 因为按业务口径那些账号本身就是废弃的。
--   本次相反：**旧角色下的账号是正常账号，只是角色被合并**。若不迁移，引导账号
--   security-admin 会立刻失去全部权限、无人能管理账号。因此本迁移**必须**改挂角色。
--
-- 幂等性：
--   sys_role_permission 主键 (role_id, permission_id) → INSERT IGNORE 可重复执行；
--   sys_role 用 NOT EXISTS 兜底；绑定用 NOT EXISTS / DELETE JOIN，重复执行结果一致。
-- =============================================================================

-- 1) 新建平台管理员角色 ------------------------------------------------------
INSERT INTO sys_role (role_code, role_name, description, data_scope, status, is_deleted, created_by)
SELECT 'PLATFORM_ADMIN', '平台管理员',
       '账号与角色、操作审计、企业AI模型、Agent 记忆与系统设置；不参与任何业务数据',
       1, 1, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'PLATFORM_ADMIN');

-- 2) 授权（原 SECURITY_ADMIN 的三个 + 原 AI_CONFIG_MANAGER 的两个） -----------
--    只补缺：不删除该角色上可能存在的其他授权，避免误伤线上有意调整的配置。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN
    ('USER:MANAGE', 'ROLE:MANAGE', 'AUDIT:READ', 'AI:CONFIG', 'ASSESSMENT:CONFIG')
WHERE r.role_code = 'PLATFORM_ADMIN';

-- 3) 账号改挂新角色（先加后删，避免中间态无角色） -----------------------------
INSERT INTO sys_user_role (user_id, role_id, created_time)
SELECT ur.user_id, np.id, NOW()
FROM sys_user_role ur
JOIN sys_role op ON op.id = ur.role_id AND op.role_code IN ('AI_CONFIG_MANAGER', 'SECURITY_ADMIN')
JOIN sys_role np ON np.role_code = 'PLATFORM_ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user_role x WHERE x.user_id = ur.user_id AND x.role_id = np.id
);

DELETE ur FROM sys_user_role ur
JOIN sys_role r ON r.id = ur.role_id
WHERE r.role_code IN ('AI_CONFIG_MANAGER', 'SECURITY_ADMIN');

-- 4) 旧角色置为废弃（保留行，避免历史操作日志里的 role_id 变悬空引用） ----------
UPDATE sys_role
   SET status = 0,
       is_deleted = 1,
       description = '已废弃：AI 基础设施并入 PLATFORM_ADMIN，业务知识并入 JOB_ARCHITECT（见 docs/role-restructure-design.md）'
 WHERE role_code = 'AI_CONFIG_MANAGER';

UPDATE sys_role
   SET status = 0,
       is_deleted = 1,
       description = '已废弃：账号/角色/审计并入 PLATFORM_ADMIN（见 docs/role-restructure-design.md）'
 WHERE role_code = 'SECURITY_ADMIN';

-- 5) JOB_ARCHITECT 无需新增授权：知识资产/图谱改挂 POST:MANAGE，它已持有 ----------
--    （后端 SecurityConfig 与前端菜单在同一次提交里改，见 V162 对应的代码改动）
