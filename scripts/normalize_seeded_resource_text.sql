UPDATE learning_resource
SET title = CONCAT('学习路径：', ability_name),
    description = CONCAT('面向岗位能力“', ability_name, '”的基础概念、工程实践与进阶练习。')
WHERE LEFT(resource_code, 4) = 'POST';
