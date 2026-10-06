# 业务职责权限设计

## 设计原则

- 不使用万能管理员角色。授权以“业务角色 + 权限码 + 数据范围”组成。
  > **2026-09-04 例外**：按需求新增 `SUPER_ADMIN`（超级管理员，可访问所有角色的所有功能）。
  > 它是**唯一的例外**，且实现方式是「按角色码动态装载 `sys_permission` 全表权限」，
  > 而不是给它逐条 `sys_role_permission` 授权 —— 后者在新增权限码时会静默漏授权。
  > 除该角色外，「万能管理员」仍然禁止。
- 菜单隐藏、前端路由守卫和后端 API 鉴权必须同时生效，前端隐藏不是安全边界。
- 业务角色可组合，一个用户可以同时拥有 HR 和 AI 配置管理员等多个角色。
- 业务数据默认按数据范围过滤：本人、部门、部门及下属、全组织。

## 角色

| 角色编码 | 角色 | 暴露功能 | 数据范围 |
|---|---|---|---|
| `EMPLOYEE` | 员工 | 本人能力画像、能力评估流程、AI 面试、本人匹配结果通知 | 仅本人 |
| `HR_SPECIALIST` | HR | 人员档案、评估运营、发起匹配、匹配审批、匹配结果和差距诊断、匹配策略与校准配置、岗位只读 | 全组织或授权部门 |
| `JOB_ARCHITECT` | 岗位体系管理员 | 岗位模型、能力要求、岗位模板、岗位全景、岗位演化、新兴岗位 **+ 知识资产/知识图谱/能力标签治理/过滤规则（2026-09-04 并入）** | 全组织 |
| `PLATFORM_ADMIN` | 平台管理员 | 用户、角色、权限授权、操作审计、企业 AI 模型、Agent 记忆、系统设置 | 全组织；**不含业务数据** |
| `SUPER_ADMIN` | 超级管理员 | **所有角色的所有功能**（权限由后端按角色动态装载全表权限码，前端菜单/守卫整体放行） | 全组织 |

> **2026-09-04 角色合并（V162）**：原 `AI_CONFIG_MANAGER`（AI 配置管理员）与 `SECURITY_ADMIN`（权限管理员）
> 已合并为 `PLATFORM_ADMIN`（平台管理员），角色数 5 → 4。理由与实施见
> `docs/role-restructure-design.md`；要点：
> - 原 AI 配置管理员这个角色是「平台基础设施」与「业务知识」两类职责的拼盘，
>   且实测其 8 个菜单项里有 4 个要求的权限码它并不持有（**永远不会显示**）；
> - 拆开后：**AI 基础设施 + 账号角色审计** → `PLATFORM_ADMIN`；
>   **知识资产（RAG）、知识图谱、能力标签治理、过滤规则** → `JOB_ARCHITECT`（复用 `POST:MANAGE`，不新增权限码）；
> - 旧角色**保留行、置废弃**（`status=0, is_deleted=1`），但其**绑定账号会被改挂到 `PLATFORM_ADMIN`**
>   —— 与 V159 废弃 `HR_LEAD` 时"不迁移账号"不同：那批账号本身废弃，而这批是正常账号，
>   不迁移会让引导账号 `security-admin` 直接失去全部权限。

> **HR 是单一角色**：`sys_role` 表中不存在独立的「HR 负责人」角色。匹配审批与匹配策略配置同样授予 `HR_SPECIALIST`，同一账号即可完成「发起匹配 → 审批 → 推送结果」全链路。

> **2026-09-04：新增 `SUPER_ADMIN` + 修复 `PLATFORM_ADMIN` 授权缺失**
>
> 1. **`SUPER_ADMIN`（超级管理员）**：可访问所有角色的所有功能。
>    - 后端 `SystemAuthenticationPortAdapter` 检测到该角色（启用且未删除）后，直接装载
>      `sys_permission` 全表权限码 + 全部 `ROLE_*`；**以后新增权限码自动生效**，无需补授权。
>    - 前端在 4 处整体放行：侧边栏菜单过滤、路由守卫、`hasPermission/hasRole`、工作台装载
>      （工作台复用「平台管理员」装载器）。角色码常量见 `frontend/src/utils/super-admin.ts`（须与后端一致）。
>    - 建角色 SQL：`sql/add_super_admin_and_repair_platform_admin.sql`（幂等，可重复执行）。
>
> 2. **`PLATFORM_ADMIN` 进不去功能的根因（已修）**：
>    - 直接原因：`sys_role_permission` 授权行缺失 —— 账号有角色、**零权限**，前端菜单全空且不报错；
>    - 干扰因素：`auth:authorities` 缓存 TTL 30 分钟且**按 userId 存**，手工改库不会触发 `@CacheEvict`，
>      **重新登录也读同一份旧值** → 表现为「SQL 执行成功、角色状态正确，却仍进不去」。
>      改库后请调 `POST /api/system/role/evict-authorities-cache`（或清 Redis `auth:authorities::*`）再重新登录；
>    - 加固 1（后端）：`SysUserServiceImpl.resolveRoles` 过滤 `status=0` 的停用角色并按 roleId 排序
>      —— 停用角色不会被 `@TableLogic` 自动排除，混进 `roles[0]` 会让菜单整体消失；
>    - 加固 2（前端）：`filterSidebarModules` 由「只看 `roles[0]` 精确匹配」改为「**任一角色命中即放行**」，
>      `AppSidebar` 传全量 `roles`；多角色账号不再被首角色锁死。
>
> **已清理（2026-09-04）**：以下历史残留已全部移除，系统现为 5 个角色的干净状态 ——
> - `frontend/src/config/sidebar-menu.ts`：删除 `HR_LEAD` 超集兼容分支，`MANAGEMENT_ROLES` 移除该角色
> - `V147__introduce_business_rbac.sql`：移除恒零行的 `HR_LEAD` 授权死语句，改为注释说明
> - `RoleSaveDTO` Swagger 示例、`KnowledgeSourceDocument` 角色注释、`permission.ts` JSDoc：已更新为实际角色编码
> - `RoleWorkbench.vue` 角色标签、`useWorkbenchData.ts` 角色分支、`management-workbench-logic.ts` 注释：已同步
> - `sidebar-menu.test.ts`：测试用例合并为单一 HR 角色口径

## 权限码

共 17 个权限码，2026-09-04 合并后**语义有几处收窄**（码本身不变，避免牵动 RBAC 与侧边栏规则）：

`ASSESSMENT:SELF` 本人评估；`NOTIFICATION:SELF` 本人通知；`LEARNING:SELF` 本人学习路径；`EMPLOYEE:READ` 人员查看；`ASSESSMENT:MANAGE` 评估运营；`MATCHING:READ` 匹配查看；`MATCHING:EXECUTE` 发起匹配；`MATCHING:APPROVE` 匹配审批；`MATCHING:CONFIG` 匹配策略；`POST:READ` 岗位查看；`POST:MANAGE` 岗位维护（**+ 知识资产/图谱/标签治理/过滤规则**）；`POST:EVOLUTION` 岗位演化；`AI:CONFIG` **平台 AI 基础设施**（模型配置、Agent 记忆；**不再涵盖业务知识**）；`ASSESSMENT:CONFIG` 评估规则配置；`USER:MANAGE` 用户管理；`ROLE:MANAGE` 角色授权；`AUDIT:READ` 审计查看。

## 人员域 / 岗位域 收口（2026-09-04）

口径：**HR 只看与人员能力相关的，岗位体系管理员只看与岗位相关的**（两处相同的数据源、相反的可见范围）。
采用「**同一页面按角色锁域**」（与既有的「人员 Harness 审核 / 岗位能力巡检」共用组件同一做法），
但**域隔离同时落在后端**，因为前端隐藏不是安全边界：

| 界面 | 域锁 | 后端真实边界 |
| --- | --- | --- |
| 证据中心 `/capability-brain/evidence` | HR→人员、岗位管理员→岗位（切换控件退化为说明） | `/api/contest/**` 两码均可（页面锁域），数据域由调用参数决定 |
| 知识图谱 `/kg/workbench` | HR→人员能力图谱、岗位管理员→岗位知识图谱 | `/api/kg/employee/**` 仅 `ASSESSMENT:MANAGE`；`/api/kg/panorama`、`/api/kg/post/**`、`/api/kg/{build,snapshots,neo4j,timeline}/**` 仅 `POST:MANAGE`；`/api/kg/memory-graph` 仅 `AI:CONFIG` |
| 岗位全景图谱 `/post/panorama` | 从 HR 菜单移除 | `/api/post/panorama/{graph,fact-graph}` 仅 `POST:MANAGE`（`/overview` 例外：HR 工作台在用） |

## 实现说明

- 数据库迁移：`V147__introduce_business_rbac.sql` 创建权限字典和角色权限关联，并停用旧 `ADMIN` 角色。
- 后端登录态同时加载 `ROLE_*` 和权限码，接口使用 `hasAuthority` 校验权限码。
- 前端侧边栏按权限码过滤，路由守卫按权限码拦截，按钮继续使用 `v-permission`。
- 首次启动账号为 `security-admin`，角色为 **`PLATFORM_ADMIN`**（2026-09-04 起；`DataInitializer` 已同步），
  初始密码仍建议部署后立即修改。

## 待清理项

无。`HR_LEAD` 残留已于 2026-09-04 清理；`AI_CONFIG_MANAGER` / `SECURITY_ADMIN` 已于 2026-09-04
合并进 `PLATFORM_ADMIN`（旧码仅保留在 403 页的角色名映射里，用于过渡期显示可读名称）。

**已知残留（未处理，非本次范围）**：`GET /api/governance/agent-memory/**` 未在 SecurityConfig 中声明，
落入 `anyRequest().authenticated()` —— 即**任意登录用户**都能列出 Agent 记忆。
写接口（POST/PUT/DELETE）已按 `AI:CONFIG` 收口。建议后续补上 GET 的 `AI:CONFIG` 限制。

**经验**：写角色-权限关联迁移时必须先确认角色行已存在。`INSERT IGNORE ... SELECT ... JOIN sys_role r WHERE r.role_code = 'X'` 在角色不存在时会**静默匹配零行并成功返回**，不报任何错误，极易被误读为「该角色存在且已授权」。

## 后续建议

1. 将人员、岗位、匹配、通知查询统一接入数据范围拦截器，避免仅靠 URL 权限。
2. 在角色管理页展示权限分组和数据范围，不允许普通 HR 修改 `PLATFORM_ADMIN`。
3. 为用户角色变更、权限变更、AI 配置变更和匹配审批增加二次确认及审计日志。
