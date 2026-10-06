SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO learning_resource
(resource_code,ability_name,tag_id,title,resource_type,difficulty_level,url,description,platform,platform_icon,duration,sort_order,status)
VALUES
('JAVA_AI_APP_001','Java后端开发',NULL,'Java后端开发实战','COURSE',3,'https://docs.oracle.com/en/java/','Java 官方文档与后端开发基础。','OTHER','book-open','约10小时',1,1),
('JAVA_AI_APP_002','Spring Boot框架开发',NULL,'Spring Boot官方指南','DOC',3,'https://spring.io/guides','Spring Boot 官方快速入门、配置与 Web 服务实践。','OTHER','book-open','约8小时',2,1),
('JAVA_AI_APP_003','Python开发',NULL,'Python官方教程','COURSE',3,'https://docs.python.org/zh-cn/3/tutorial/','Python 语法、工程实践与自动化开发。','OTHER','book-open','约8小时',3,1),
('JAVA_AI_APP_004','OCR文字识别',NULL,'OCR文字识别实践','DOC',3,'https://github.com/PaddlePaddle/PaddleOCR','文字检测、识别与文档解析实践。','GITHUB','github','约6小时',4,1),
('JAVA_AI_APP_005','RAG检索增强生成',NULL,'RAG检索增强生成指南','COURSE',3,'https://python.langchain.com/docs/concepts/rag/','文档切分、检索、重排与生成式问答。','OTHER','book-open','约8小时',5,1),
('JAVA_AI_APP_006','Embedding向量嵌入',NULL,'Embedding向量嵌入基础','DOC',3,'https://huggingface.co/docs/sentence-transformers/','向量嵌入、相似度计算与语义检索。','OTHER','book-open','约5小时',6,1),
('JAVA_AI_APP_007','Redis缓存',NULL,'Redis官方学习资源','COURSE',3,'https://redis.io/docs/latest/','缓存设计、数据结构与高可用实践。','OTHER','book-open','约6小时',7,1),
('JAVA_AI_APP_008','RabbitMQ消息队列',NULL,'RabbitMQ官方教程','DOC',3,'https://www.rabbitmq.com/tutorials','消息队列、可靠投递与消费者确认机制。','OTHER','book-open','约6小时',8,1),
('JAVA_AI_APP_009','智能问答系统开发',NULL,'智能问答系统开发实践','COURSE',3,'https://python.langchain.com/docs/tutorials/','问答链路、上下文管理与服务化部署。','OTHER','book-open','约10小时',9,1),
('JAVA_AI_APP_010','文档解析',NULL,'文档解析与结构化抽取','DOC',3,'https://tika.apache.org/','PDF、Word 等文档解析与文本抽取。','OTHER','book-open','约5小时',10,1),
('JAVA_AI_APP_011','检索增强',NULL,'检索增强技术实践','DOC',3,'https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html','全文检索、召回与相关性排序。','OTHER','book-open','约6小时',11,1),
('JAVA_AI_APP_012','异步批处理开发',NULL,'异步批处理开发实践','COURSE',3,'https://docs.spring.io/spring-batch/reference/','任务编排、批处理、重试与断点续跑。','OTHER','book-open','约7小时',12,1),
('JAVA_AI_APP_013','Prompt工程',NULL,'Prompt工程指南','DOC',3,'https://platform.openai.com/docs/guides/prompt-engineering','结构化提示词、约束输出与评估方法。','OTHER','book-open','约5小时',13,1),
('JAVA_AI_APP_014','接口测试',NULL,'接口测试自动化实践','COURSE',3,'https://www.postman.com/api-platform/api-testing/','API 测试、断言、Mock 与回归测试。','OTHER','book-open','约6小时',14,1),
('JAVA_AI_APP_015','AI能力业务集成',NULL,'AI能力业务集成实战','COURSE',3,'https://docs.spring.io/spring-ai/reference/','大模型能力接入、工具调用与业务工作流集成。','OTHER','book-open','约10小时',15,1)
ON DUPLICATE KEY UPDATE
  ability_name=VALUES(ability_name), title=VALUES(title), url=VALUES(url),
  description=VALUES(description), resource_type=VALUES(resource_type),
  difficulty_level=VALUES(difficulty_level), status=1;
COMMIT;
