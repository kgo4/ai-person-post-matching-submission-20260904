# 后端架构规范（Architecture Conventions）

> 本文件是后端代码**唯一**的架构规范来源。新增、重构代码时必须遵守。
> 历史遗留代码可能不完全符合，迁移优先级见文末"存量迁移"。

## 1. 分层结构

Controller → Application(Facade) → Service(Interface + Impl) → Port/Adapter 或 Mapper

| 层 | 包 | 职责 | 依赖规则 |
|---|---|---|---|
| 接入层 | `controller` | HTTP 入参校验、鉴权、返回 `R<T>` | 只依赖 `application` 层 Facade，禁止直接注入 Service/Mapper |
| 应用层 | `application` | 用例编排、DTO↔VO 转换 | 只依赖 `service` 层接口 |
| 领域/服务层 | `service/<domain>`（接口）+ `service/<domain>/impl`（实现） | 业务规则 | 依赖 `port` 或 `mapper` |
| 端口层 | `port/<domain>` | 领域读查询契约（接口） | 接口只含 DTO 返回值，禁止暴露 Entity/Mapper |
| 适配层 | `infrastructure/persistence` | Port 的 MyBatis 实现（`*PortAdapter`） | 实现 `port` 接口，内部使用 Mapper |
| 数据层 | `mapper/<domain>` | MyBatis-Plus Mapper | 只被 Adapter/Service 调用 |

## 2. 强制约定（新代码必须遵守）

1. **Service 必须 Interface + Impl**：接口放 `service/<domain>/XxxService.java`，实现放
   `service/<domain>/impl/XxxServiceImpl.java`（`@Service`）。禁止裸 `@Service` 类，
   除非该服务无任何外部调用方且仅作内部工具。
2. **领域读查询必须走 Port**：跨域读取（如员工域读岗位数据、匹配域读员工数据）
   一律通过 `port/<domain>` 接口 + `infrastructure/persistence` 的 Adapter 实现。
   本域内简单 CRUD 可直接注入 Mapper。
3. **禁止 `@RequestBody Map<...>` / 裸 String 请求体**：HTTP 请求体必须使用带字段的
   DTO（`dto/<domain>/api`），字段用 `record` + `@Valid` 校验注解。
4. **Controller 禁止注入 Service/Mapper**：一律经 `application` 层 Facade。
5. **请求/响应边界**：请求用 `dto/<domain>/api/*Request`，响应用 `vo` 或 `dto` 中的 VO，
   不直接序列化 Entity。
6. **跨域 Mapper 依赖零新增（CI 强制）**：跨域查询只能依赖 `port.*`；Mapper 只能出现在
   本域基础设施或 `infrastructure.persistence` Adapter。`ArchitectureRulesTest` 的
   `crossDomainMapperNoNewViolations` 以 `src/test/resources/architecture/
   cross-domain-mapper-baseline.txt` 为基线，**实际违规集 − 基线必须为空**。
   新增例外必须同时提交 owner、原因与目标迁移版本，禁止无限白名单。

## 3. Port/Adapter 示例（照抄这个结构）

```java
// port/post/PostQueryPort.java —— 接口，只暴露 DTO
public interface PostQueryPort {
    List<PostDTO> findByIds(Collection<Long> ids);
    record PostDTO(Long id, String title) {}
}

// infrastructure/persistence/PostQueryPortAdapter.java —— 实现，内部用 Mapper
@Component
@RequiredArgsConstructor
public class PostQueryPortAdapter implements PostQueryPort {
    private final PostPostMapper postPostMapper;
    @Override
    public List<PostDTO> findByIds(Collection<Long> ids) {
        return postPostMapper.selectBatchIds(ids).stream()
                .map(p -> new PostDTO(p.getId(), p.getTitle())).toList();
    }
}
```

## 4. 现状与迁移

当前是**渐进式改造**状态：已有 15 个 Port（`port/closure|contest|evolution|kg|learning|matching|post|system|tag|talent|vectorsearch`），
同时存在部分 Service 直接注入 Mapper 的存量代码。这是历史债务，不是允许双规范的借口。

**新代码一律按第 2 节执行。** 存量迁移优先级：

1. 跨域读聚合类服务 → 拆分到对应 `port/<domain>` + Adapter；
2. 新增跨域查询时禁止直接注入他域 Mapper，必须新建/复用 Port；
3. 逐步收敛 `@RequestBody Map` 遗留端点（现仅 3 处，已全部 DTO 化）。

### 已完成迁移（截至 2026-08-05）

| 服务 | 原跨域 Mapper | 现在 |
|---|---|---|
| CapabilityBrainServiceImpl | 10 | SystemDataStatsPort |
| EvidenceCenterServiceImpl | 8 | Talent/Post/Tag 三 Port |
| LearningPathPlanServiceImpl | 4 | Matching/Post/Talent/Tag 四 Port |
| EvidenceBackfillService | 8 | Post/Talent/Matching/Tag 四 Port |
| ContestReportGenerationEngine | 7 | Graph/Matching/Post/Talent/Tag 五 Port |
| CapabilityClosureServiceImpl | 7 | Post/Evolution/Matching/Talent/Tag 五 Port |
| ComprehensiveDiagnosisFactBuilder | 5 | Talent/Post/Tag 三 Port |
| VideoInterviewServiceImpl | 3 | Talent/Post/Tag 三 Port |
| AbilityTagGovernanceServiceImpl | 2 | Post/Talent 二 Port |
| HallucinationGuardServiceImpl | 3 | Post/Tag 二 Port |

### 新增 Port 方法一览

PostQueryPort:`countRequirementsByPostId`、`countRequirementsByTagId`、`countPrototypeTagsByTagId`、`getRequirementByPostAndTag`、`listRequirementsByTagId`、`countAllPosts`、`listAllPosts`、`listAllPostAbilityModels`

TalentQueryPort:`countAllEmployees`、`countAbilitiesByTagId`、`listAllAbilities`、`getEmpAbility`、`listCompletedResumeParses`(含 ResumeParseDTO)、`findLatestCompletedResumeParse`(含 ResumeParseDetailDTO)

MatchingQueryPort:`countAllRecordsWithAiScore`、`listAllRecordsWithAiScore`、`listRecentRecordsByEmpAndPosts`、`listRecentFeedback`(含 MatchingFeedbackDTO)

TagQueryPort:`listAllTags`、`getTagByName`、`batchFindConfirmedSimilarRelations`(含 TagRelationDTO)

EvolutionQueryPort:`getTaskById`、`listApprovedChangeItems`(含 EvolutionChangeItemDTO)

GraphQueryPort:`countNodesByType`(新建)

SystemDataStatsPort:`countSnapshot`(新建)

## 6. 跨域 Mapper 基线治理（2026-08-07 起）

- CI 规则：`ArchitectureRulesTest.crossDomainMapperNoNewViolations` —— 实际跨域
  Mapper 依赖（严格按包段判定域，修正了旧实现中根包名 `com.example.matching`
  包含 `.matching.` 导致同域误判的缺陷）减去基线文件必须为空。
- 基线文件：`src/test/resources/architecture/cross-domain-mapper-baseline.txt`
  （当前 144 对）。基线的唯一合法变化方向是**减少**。
- 豁免规则（`MAPPER_ACCESS_EXEMPT_SOURCE_PREFIXES` + `mapper.common` 目标域）：以下横切层
  职责天然需要跨域聚合数据，不属于「业务域跨界」，不计入违规——`agent.*`（LLM 工具/编排）、
  `ai.*`（AI 上下文/编排）、`service.common.*`（公共服务/工具）、`infrastructure.persistence.*`
  与 `port.*`（Port Adapter 是 Mapper 唯一合法落点）、以及依赖 `mapper.common.*`（公共技术表，
  如 JobLock / DynamicCredibilityWeight / KnowledgeProjectionTask）的访问。业务域之间的跨域读
  （matching / interview / evolution / ... 读他域 Mapper）仍必须走 `port.*`。
- 已迁移（匹配域首轮批次，2026-08-07）：

| 类 | 原跨域 Mapper | 现在 |
|---|---|---|
| SemanticMatchEngine | AbilityTagMapper(system) | TagQueryPort |
| TagCanonicalResolver | AbilityTagMapper / AbilityTagRelationMapper(system) | TagQueryPort |
| CalibrationDataService | PostPostMapper(post) | PostQueryPort |
| HardConditionEvaluator | EmpResumeParseMapper(employee) | TalentQueryPort |
| MatchingAlgorithmServiceImpl | AbilityTagMapper(system，测试构造器) | TagQueryPort |

- 剩余基线按域分布（owner 待认领，迁移顺序建议：
  interview → assessment → matching → evolution → ability → employee/post/system → learning/governance）：

| 源域 | 对数量 | 说明 |
|---|---|---|
| interview | 31 | 视频面试会话/问题/观察读 employee、system、post、matching |
| assessment | 25 | 评估工作流/画像读 workflow、ability、employee、harness |
| matching | 21 | 推荐/取数服务读 employee、post、system、ability、closure、workflow |
| ability | 14 | 能力治理/提取读 employee、system、contest、governance、harness、interview |
| employee | 12 | PMS/测评/能力导入读 system、ability、post、workflow |
| evolution | 12 | 演化任务/证据读 post、matching、rag、governance、system |
| post | 8 | 岗位域读 system（标签）、evolution |
| system | 8 | 审计/标签治理读 employee、interview、post、rag、harness、workflow |
| governance | 6 | 治理准入读写 ability、harness、employee、post |
| learning | 6 | 学习建议/路径读 matching、contest、employee、post、system |
| closure | 1 | 诊断读 matching |

- 专项过渡点（勿新增同类代码）：`MatchingDataQueryService` /
  `EmployeePostRecommendServiceImpl` 向匹配算法返回实体，改造需连同算法层重构。

### 已知过渡点（暂缓改造，勿新增同类代码）

- `MatchingDataQueryService` / `EmployeePostRecommendServiceImpl`：向匹配算法
  （`MatchingAlgorithmService`、`MatchingScoreService`）返回实体，改造需连同算法层
  一起重构，列为专项任务。
- `PostEvolutionServiceImpl`：跨域写（`PostAbilityModel` insert/update），需先定义
  跨域命令端口再迁移。
- `GovernedAdmissionEntityBuilder`：跨域写（`EmpAbility`/`PostAbilityModel` insert/update），同上。

## 5. 常见违规自查

- 新文件没有 `*ServiceImpl` 后缀却在 `service` 下 → 违反第 2.1
- Service 里 import 了非本域的 Mapper → 违反第 2.2，改走 Port
- Controller 里出现 `@RequestBody Map`、`@RequestBody String` → 违反第 2.3
- Controller 直接 `@Autowired` Service → 违反第 2.4
