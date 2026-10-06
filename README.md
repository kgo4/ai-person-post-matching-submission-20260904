# 多源异构岗位与能力图谱平台

> 面向新一代信息技术领域的岗位发现、能力图谱构建、人员能力评估与人岗精准匹配平台。

[![Backend](https://img.shields.io/badge/Backend-Spring%20Boot%203.x-6DB33F?logo=springboot&logoColor=white)](backend)
[![Frontend](https://img.shields.io/badge/Frontend-Vue%203-42B883?logo=vuedotjs&logoColor=white)](frontend)
[![Database](https://img.shields.io/badge/Database-MySQL%208-4479A1?logo=mysql&logoColor=white)](docker-compose.yml)
[![AI](https://img.shields.io/badge/AI-LangChain4j%20%7C%20Milvus%20%7C%20Neo4j-7B61FF)](backend/src/main)

## 项目简介

平台融合招聘 JD、岗位能力表、人员正式能力表、简历、AI 测试、AI 面试、PMS 项目材料和行业知识资料，形成从数据治理到岗位能力演化、从人员能力核验到人岗匹配诊断的完整闭环。

核心数据原则：岗位能力表是岗位需求主要数据源，人员正式能力表是人员能力主要数据源；系统标签库用于能力标准化、跨岗位复用、统计与召回增强，不阻断岗位和人员业务。

## 核心能力

### 岗位与能力图谱

- 岗位 JD 导入、清洗、去噪、去重与能力提取
- 岗位能力模型配置和人工维护
- 岗位视图展示“岗位 → 技术栈”
- 技术栈视图展示“技术栈 → 关联岗位”
- 新兴岗位发现与岗位能力动态演化
- 岗位能力来源证据和变更记录追踪

### 人员能力评估

- PDF、Word 等简历解析
- 简历能力提取与证据定位
- AI 测试能力核验
- AI 面试能力核验、追问和评估报告
- Harness 能力审核与状态机流转
- 通过核验的能力进入人员正式能力表

### 人岗匹配与成长建议

- 多维度人岗匹配评分与硬性条件筛选
- 精确名称、别名归一化和语义相似能力匹配
- 能力差距诊断
- 学习路径生成、学习测评与资源关联
- AI 知识资产和常读图谱

### 数据治理与知识增强

- 从岗位能力表汇聚系统标签库
- 标签引用统计、词云和热力分析
- 行业白皮书、市场资料和趋势资料接入
- RAG 知识检索与来源引用
- 清洗规则配置和治理结果追踪

## 业务闭环

```text
多源数据采集 → 清洗、去噪、去重 → 岗位能力提取 / 新兴岗位发现
→ 岗位能力表与标签库增强 → 岗位能力图谱演化
→ 简历解析与人员能力提取 → AI 测试 + AI 面试核验
→ Harness 审核 / 正式能力入库 → 人岗匹配、差距分析与学习路径
```

## Agent 与 JSON 约束

平台采用“通用基础防护 + 业务专属契约”的 Agent 设计：

- 通用层负责 JSON 语法校验、重试、输入防护和调用日志。
- 岗位能力提取、岗位演化、人岗匹配、学习路径、PMS 分析等 Agent 使用独立输出契约。
- 每个业务 Agent 都有字段说明、空结果结构、DTO 映射和业务校验。
- 证据引用、能力等级、动作类型和权重等关键字段进入业务前进行确定性校验。
- Agent 异常时保留可追踪的失败原因和业务降级状态，不直接破坏主业务数据。

## 技术架构

| 层次 | 主要组件 |
| --- | --- |
| 前端 | Vue 3、Vite、TypeScript、ECharts |
| 后端 | Spring Boot、MyBatis-Plus、LangChain4j |
| 关系数据 | MySQL 8 |
| 缓存与消息 | Redis、RabbitMQ |
| 向量检索 | Milvus |
| 图谱存储 | Neo4j |
| AI 能力 | 文本模型、视觉模型、ASR、Embedding、RAG |
| 部署 | Docker Compose、Nginx |

## 快速部署

### 1. 获取源码

```bash
git clone https://github.com/kgo4/ai-person-post-matching-submission-20260904.git
cd ai-person-post-matching-submission-20260904
```

### 2. 配置环境变量

```bash
cp .env.example .env
```

请填写 MySQL、Redis、RabbitMQ、Neo4j、Milvus、模型服务和前端访问来源配置。真实密钥、证书和用户上传文件不要提交到仓库。

### 3. 初始化数据库

数据库结构由**唯一一个建库脚本** `系统数据库结构.sql` 提供（全表结构 + 账号 / 角色权限 / 人员记录；提交目录与源码包根各一份）。

> ⚠️ 脚本不含 `CREATE DATABASE` / `USE`，**导入时必须指定库名**，否则报 `No database selected`。

先只启动数据库容器并等它就绪：

```bash
docker compose -f docker-compose.yml -f docker-compose.submission.yml up -d mysql
docker compose -f docker-compose.yml -f docker-compose.submission.yml ps mysql
```

再建库并导入（库名与 `.env` 的 `MYSQL_DATABASE` 一致，默认 `hrms_db`）：

```bash
docker compose -f docker-compose.yml -f docker-compose.submission.yml exec -T mysql \
  mysql -uroot -p"<MYSQL_PASSWORD>" -e \
  "CREATE DATABASE IF NOT EXISTS hrms_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

docker compose -f docker-compose.yml -f docker-compose.submission.yml exec -T mysql \
  mysql -uroot -p"<MYSQL_PASSWORD>" hrms_db < 系统数据库结构.sql
```

> 完整步骤与校验方式见 `部署说明.md`。完整演示数据快照体量较大，未随包提供；重建演示数据请依据 `测试数据/` 与 `测试用例/` 中的语料导入。

### 4. 构建并启动

提交环境使用专用 Docker 构建文件：

```bash
docker compose -f docker-compose.yml -f docker-compose.submission.yml build --no-cache backend frontend
docker compose -f docker-compose.yml -f docker-compose.submission.yml up -d
docker compose -f docker-compose.yml -f docker-compose.submission.yml ps
```

后端服务健康后，即可访问前端页面进行岗位导入、岗位图谱、简历解析、人岗匹配和人员能力评估验证。

## 仓库目录

```text
backend/                         Spring Boot 后端与 Agent 服务
frontend/                        Vue 3 前端
测试数据/                        岗位 JD、简历和图谱示例数据
测试用例/                        JD、简历、治理评测脚本与输入样例
系统数据库结构.sql               数据库建库脚本（结构 + 账号/角色/人员）
docker-compose.yml               完整服务编排
docker-compose.submission.yml    提交环境覆盖配置（源码编译 + 关闭自动迁移 + 免证书 Nginx）
部署配置/                        容器化部署文件（Dockerfile / compose / nginx / env 模板）
```

## 评审材料

- 部署步骤：见 [部署说明](部署说明.md)
- 测试方案：见 [测试方案](测试方案.md)
- 单元测试与覆盖率：见 [单元测试与覆盖率说明](单元测试与覆盖率说明.md)
- 测试数据：见 [测试数据](测试数据)
- 测试用例：见 [测试用例](测试用例)
- 人岗匹配评测：见 [人岗匹配评测](人岗匹配评测)
- 在线访问地址：见提交产物目录中的 `在线访问地址.docx`
- 作品材料与演示视频：由参赛提交目录统一提供

## 项目信息

- 项目名称：**多源异构岗位与能力图谱平台**
- 源码仓库：[ai-person-post-matching-submission-20260904](https://github.com/kgo4/ai-person-post-matching-submission-20260904)
- 离线源码包：提交目录 `项目源码.zip`，内容与本地最新源码一致
- 数据原则：岗位能力表是岗位需求主要数据源，人员正式能力表是人员能力主要数据源；系统标签库用于能力标准化、跨岗位复用、统计与召回增强，不阻断岗位和人员业务

## 许可证与说明

本仓库用于赛事作品提交、部署复现和技术评审。第三方模型、数据源和基础设施服务需遵守其对应的服务条款与授权要求。
