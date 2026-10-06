-- =============================================================================
-- V164：视频终面 —— 员工响应（接受/放弃）与邀请邮件留痕
--
-- 背景：HR 选定面试时间后，系统向员工邮箱（emp_employee.email）发送 QQ 邮件通知，
--       员工需要在平台内「接受」或「放弃」终面，双端（员工端 / HR 端）同步状态与通知。
--
-- 关键口径：
--   · employee_response 是**员工侧唯一写入口**产生的结果字段；NULL = 尚未响应，
--     HR 侧据此展示「待响应 / 已接受 / 已放弃」。
--   · 员工放弃 = 该场终面直接置为「已取消」(status=2)，同时保留 response 与说明，
--     语义上区别于「HR 主动取消」（cancel 接口不写 employee_response）。
--   · invite_mail_* 只做**留痕**：邮件是站内通知之外的补充通道，发送失败绝不阻断发起。
--   · 本迁移只加列，不修改任何既有列与数据。
-- =============================================================================

ALTER TABLE emp_communication_interview
    ADD COLUMN employee_response         TINYINT      NULL
        COMMENT '员工响应：1接受 2放弃；NULL=尚未响应',
    ADD COLUMN employee_response_comment VARCHAR(500) NULL
        COMMENT '员工放弃终面的说明（可选）',
    ADD COLUMN employee_responded_time   DATETIME     NULL
        COMMENT '员工响应时间',
    ADD COLUMN invite_mail_status        TINYINT      NULL
        COMMENT '邀请邮件状态：0未发送 1已发送 2发送失败 3已跳过（未配置发信账号/员工无邮箱）',
    ADD COLUMN invite_mail_time          DATETIME     NULL
        COMMENT '邀请邮件最近一次尝试时间',
    ADD COLUMN invite_mail_error         VARCHAR(500) NULL
        COMMENT '邀请邮件失败原因（截断存储，仅用于排查）';
