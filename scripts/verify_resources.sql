SELECT COUNT(*) AS seeded_resources FROM learning_resource WHERE resource_code LIKE 'POST能力_%';
SELECT ability_name, title FROM learning_resource WHERE resource_code LIKE 'POST能力_%' ORDER BY id DESC LIMIT 10;
