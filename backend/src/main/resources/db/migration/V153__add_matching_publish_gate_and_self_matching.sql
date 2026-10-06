-- 员工侧匹配可见性闸门 + 员工自主发起匹配权限。
--
-- 背景：matching_record.approval_status = 2 原本同时承担“HR 已审核”与“已对员工可见”
-- 两种语义，导致 HR 审核通过即等于自动推送给员工，无法选择不推送。这里拆出独立的
-- publish_status，使“审核”与“推送”成为两个正交动作：
--   approval_status = 2 AND publish_status = 1  → 员工侧可见
-- 员工侧所有依赖匹配结果的功能（详情、差距诊断、学习路径等）统一按此条件收口。
--
-- 员工自助发起匹配需要独立的 MATCHING:SELF 权限：管理端的 MATCHING:EXECUTE 语义是
-- “运营人员执行人岗匹配”（可选任意人员/岗位、决定匹配模式），与“员工为自己选岗试配”
-- 不是同一个授权边界，不能共用，否则员工可借该权限对他人发起匹配。
--
-- 本次为新增列，默认 0（不推送）。历史数据已清库，无需回填。

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'matching_record'
                 AND column_name = 'publish_status') = 0,
              'ALTER TABLE matching_record ADD COLUMN publish_status TINYINT NOT NULL DEFAULT 0 COMMENT ''推送状态：0未推送，1已推送（员工侧可见）'' AFTER approval_status',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 员工侧列表按 (人员, 已推送) 过滤，加组合索引避免全表扫描。
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'matching_record'
                 AND index_name = 'idx_matching_record_publish') = 0,
              'CREATE INDEX idx_matching_record_publish ON matching_record (emp_id, approval_status, publish_status)',
              'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 权限码：员工自助发起匹配。
INSERT INTO sys_permission (permission_code, permission_name, permission_type, description)
SELECT 'MATCHING:SELF', '本人发起匹配', 'API', '员工为自己选择岗位发起匹配并查看已推送的匹配结果'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'MATCHING:SELF');

-- 授予 EMPLOYEE 角色。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code = 'MATCHING:SELF'
WHERE r.role_code = 'EMPLOYEE';
