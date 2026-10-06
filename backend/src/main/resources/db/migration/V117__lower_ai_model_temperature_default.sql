-- 降低全局企业 AI 模型默认温度：结构化提取类任务需要确定性输出（0.20 -> 0.10）。
-- 仅更新仍为旧默认值 0.20 的行（无法区分默认值与管理员显式设置的 0.20，
-- 但 0.20 -> 0.10 方向安全）；其他自定义温度值保持不变。
UPDATE system_ai_model_config
SET temperature = 0.10
WHERE temperature = 0.20;
