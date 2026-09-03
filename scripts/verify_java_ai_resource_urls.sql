SET NAMES utf8mb4;
SELECT resource_code, ability_name, url, status
FROM learning_resource
WHERE resource_code LIKE 'JAVA_AI_APP_%'
ORDER BY resource_code;
