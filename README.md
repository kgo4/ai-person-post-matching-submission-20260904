# 多源异构岗位与能力图谱平台

参赛源码仓库：`https://github.com/kgo4/ai-person-post-matching-submission-20260904`

平台覆盖岗位 JD 清洗与解析、岗位能力图谱、岗位演化、简历解析、人员能力评估、AI 面试、人岗匹配、学习路径和治理功能。

## 技术栈

- 后端：Spring Boot、MyBatis-Plus、MySQL、Redis、RabbitMQ、Milvus、Neo4j
- 前端：Vue 3、Vite

## 从源码构建

1. 复制 `.env.example` 为 `.env`，填写数据库、消息队列、模型与图谱服务配置。
2. 数据库结构由 `backend/src/main/resources/db/migration/` 下的 Flyway 迁移脚本创建。
3. 在仓库根目录执行：

```bash
docker compose -f docker-compose.yml -f docker-compose.submission.yml build --no-cache backend frontend
docker compose -f docker-compose.yml -f docker-compose.submission.yml up -d
```

`docker-compose.submission.yml` 仅覆盖提交环境的后端构建方式，不改变服务器当前运行镜像或业务源码。

