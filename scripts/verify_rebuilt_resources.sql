SELECT COUNT(*) AS resource_count FROM learning_resource;
SELECT ability_name, title, resource_type, status FROM learning_resource ORDER BY id DESC LIMIT 10;
