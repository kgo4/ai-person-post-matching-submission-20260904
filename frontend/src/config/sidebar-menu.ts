import type { Component } from 'vue'
import { Briefcase, Collection, Connection, DataAnalysis, HomeFilled, Reading, Setting, UserFilled, View } from '@element-plus/icons-vue'
import { isSuperAdmin, isMenuExcluded } from '@/utils/super-admin'

export interface SidebarChild {
  label: string
  path: string
  beta?: boolean
  permission?: string
  roles?: string[]
}

export interface SidebarModule {
  key: string
  label: string
  summary: string
  path: string
  icon: Component
  children: SidebarChild[]
  permission?: string
  roles?: string[]
}

// 2026-09-04：「dashboard」模块已合并进「工作台」（匹配分析区块，仅 HR 渲染），
// 因此不再作为独立模块出现在侧边栏。
export const MODULE_ORDER = ['home', 'employee', 'post', 'matching', 'learning', 'contest', 'knowledge-assets', 'ai-governance', 'system'] as const

/**
 * 非员工角色：员工的数据范围是“仅本人”，管理端看板与员工视角无关。
 */
// 【2026-09-04】原为 HR/JOB_ARCHITECT/AI_CONFIG_MANAGER/SECURITY_ADMIN。
// 后两者已合并为 PLATFORM_ADMIN（V162 / docs/role-restructure-design.md）：
// AI 基础设施 + 账号角色审计 → PLATFORM_ADMIN，业务知识 → JOB_ARCHITECT。
// 【2026-09-04】追加 SUPER_ADMIN（可访问所有角色的所有功能）。
export const MANAGEMENT_ROLES = ['HR_SPECIALIST', 'JOB_ARCHITECT', 'PLATFORM_ADMIN', 'SUPER_ADMIN']

/**
 * 按角色 + 权限码过滤侧边栏。
 *
 * 规则（与 AUTHORIZATION_DESIGN.md 一致）：
 * 1. 子项的 roles 与 permission 必须同时满足；
 * 2. 一个模块下没有任何可见子项时，整个模块不渲染；
 * 3. 模块 path 收敛为「本角色可见子项中的第一个」。
 *
 * 第 3 条是必须的：AppSidebar 点击模块标题跳的是 module.path，而配置里的模块 path
 * 通常写的是该模块的主推子项（例如 matching 写 /matching/execute）。该子项往往带 roles
 * 限制，于是其他角色点模块标题会被路由守卫判为无权限并跳 /403 —— 而 /403 复用 404 组件，
 * 用户看到的就是「404，点返回首页才能进入」。把模块 path 收敛到过滤后可见的第一个子项，
 * 既保证落点一定可达，也让「配置顺序即优先级」成为唯一规则，不再需要各角色单独维护入口。
 *
 * ⚠️ 角色入参支持「单个字符串」或「角色数组」：
 * - 传数组时按**任一命中**放行。此前只接受单个角色，调用方（AppSidebar）传的是 `roles[0]`，
 *   多角色账号一旦主角色不是目标角色，该角色专属菜单会**整体消失**且不报错；
 * - 超级管理员（SUPER_ADMIN）直接跳过 roles 判断（可访问所有角色的所有功能）——
 *   这与后端「按角色装载全量权限码」是同一口径。
 * 传字符串的旧调用方式保持兼容（既有单测即按字符串调用）。
 */
export function filterSidebarModules(
  modules: SidebarModule[],
  roleOrRoles: string | string[],
  permissions: string[],
): SidebarModule[] {
  const normalizedRoles: string[] = (Array.isArray(roleOrRoles) ? roleOrRoles : [roleOrRoles])
    .map(role => (role || '').replace(/^ROLE_/, '').toUpperCase())
    .filter(Boolean)
  const superAdmin = isSuperAdmin(normalizedRoles)
  const roleOf = (role: string) => role.replace(/^ROLE_/, '').toUpperCase()
  const canSee = (permission?: string, roles?: string[], path?: string) => {
    // 「菜单排除名单」优先级最高，**超管也不例外**：里面放的是尚未完成的页面，
    // 让超管看见只会得到"超管都点不动、那肯定是系统坏了"的误判。
    if (isMenuExcluded(path)) return false
    const roleAllowed =
      superAdmin || !roles?.length || roles.some(role => normalizedRoles.includes(roleOf(role)))
    // 超管同时跳过权限码判断：后端按角色动态装载全量权限码，
    // 前端不必（也不应）因为权限数组一时没刷新出来就把入口藏掉。
    const permissionAllowed = superAdmin || !permission || permissions.includes(permission)
    return roleAllowed && permissionAllowed
  }
  return modules
    .filter(module => canSee(module.permission, module.roles, module.path))
    .map(module => {
      const children = module.children.filter(child => canSee(child.permission, child.roles, child.path))
      return {
        ...module,
        children,
        // 模块标题落点必须是自己看得见的页面，否则点击会被守卫拦到 /403（渲染 404）
        path: children[0]?.path ?? module.path,
      }
    })
    .filter(module => module.children.length > 0)
    .sort(
      (a, b) =>
        (MODULE_ORDER as readonly string[]).indexOf(a.key) - (MODULE_ORDER as readonly string[]).indexOf(b.key),
    )
}

export function getSidebarModules(): SidebarModule[] {
  return [
    {
      key: 'home',
      label: '首页',
      summary: '角色工作台',
      path: '/workbench',
      icon: HomeFilled,
      children: [{ label: '工作台', path: '/workbench' }],
    },
    {
      key: 'employee',
      label: '能力画像',
      summary: '能力采集、画像与档案',
      path: '/employee/ability-profile',
      icon: UserFilled,
      children: [
        { label: '我的能力', path: '/employee/ability-profile', roles: ['EMPLOYEE'] },
        { label: '我的评估流程', path: '/employee/ability-profile/assessment', roles: ['EMPLOYEE'] },
        { label: '能力评估', path: '/employee/ability-profile', roles: ['HR_SPECIALIST'] },
        // 人员 Harness 审核：路由 /ai-governance/assessment-harness 早已存在（同一组件，
        // 靠 assessmentOnly 区分人员域与岗位域），此前只有 hidden 路由、没有菜单项，
        // 导致 HR（ASSESSMENT:MANAGE）无入口，只能绕到能力评估页里逐人审核。
        { label: '人员 Harness 审核', path: '/ai-governance/assessment-harness', permission: 'ASSESSMENT:MANAGE', roles: ['HR_SPECIALIST'] },
        { label: '员工档案', path: '/employee/list', permission: 'EMPLOYEE:READ', roles: ['HR_SPECIALIST'] },
        // PMS 项目分析：HR 管理「在 PMS 平台已有数据的人」的独立功能。
        // 它不挂在员工档案的逐行按钮上，因为 PMS 上的人可能还没在本系统注册；
        // 同步只登记映射、不写人员库，因此不影响非 PMS 员工。
        { label: 'PMS 项目分析', path: '/employee/pms-analysis', permission: 'ASSESSMENT:MANAGE', roles: ['HR_SPECIALIST'] },
      ],
    },
    {
      key: 'post',
      label: '岗位模型',
      summary: '岗位建模、模板与能力配置',
      path: '/post/model-config',
      icon: Briefcase,
      children: [
        { label: '能力配置', path: '/post/model-config', permission: 'POST:MANAGE', roles: ['JOB_ARCHITECT'] },
        // 岗位全景图谱 = 岗位知识视图；2026-09-04 口径「HR 只看人员能力相关」，故只给岗位体系管理员。
        { label: '全景图谱', path: '/post/panorama', permission: 'POST:READ', roles: ['JOB_ARCHITECT'] },
        { label: '模型发布记录', path: '/post/model-version', permission: 'POST:READ', roles: ['JOB_ARCHITECT'] },
        { label: '岗位演化', path: '/post/evolution', permission: 'POST:EVOLUTION', roles: ['JOB_ARCHITECT'] },
        // 市场 JD 采集（爬虫系统配合功能）：此前只有「岗位演化 → 资料输入 → 市场 JD 采集」抽屉，
        // 全站唯一入口藏得太深，且抓回来的数据在主系统里看不到。独立成页后与岗位演化同挂 POST:EVOLUTION。
        { label: '市场 JD 采集', path: '/post/crawler-jd', permission: 'POST:EVOLUTION', roles: ['JOB_ARCHITECT'] },
        { label: '岗位档案', path: '/post/list', permission: 'POST:READ', roles: ['JOB_ARCHITECT', 'HR_SPECIALIST'] },
        { label: 'JD 批量导入', path: '/post/excel-import', permission: 'POST:MANAGE', roles: ['JOB_ARCHITECT'] },
        // 岗位趋势发现 = 「上传权威材料 → AI 解析趋势岗位/能力变更 → 人工审核落地」的 LLM 主链路。
        { label: '岗位趋势发现', path: '/post/trend-discovery', permission: 'POST:MANAGE', roles: ['JOB_ARCHITECT'] },
        // 市场 JD 统计 = 原「岗位趋势发现」页（纯 PMI 共现、零 LLM），与本页是两套不能混用的子系统，故并列保留。
        { label: '市场 JD 统计', path: '/post/market-jd-stats', permission: 'POST:MANAGE', roles: ['JOB_ARCHITECT'] },
      ],
    },
    {
      key: 'matching',
      label: '人岗匹配',
      summary: '匹配执行与差距诊断',
      path: '/matching/execute',
      icon: Connection,
      children: [
        // 员工自助发起匹配（2026-09-04 新增独立入口）。
        // 与 HR 的「发起匹配」(/matching/execute) 同名但**不同页、不同权限码**：
        // 员工侧人员固定为本人、岗位单选，且要先走完全部能力项审核（能力分析报告已生成）。
        // 放在模块首位：这是员工在「人岗匹配」里最常做的动作，也是整条匹配链路的起点；
        // 也是模块标题的落点（见 filterSidebarModules 第 3 条规则）。
        { label: '发起匹配', path: '/matching/self-execute', permission: 'MATCHING:SELF', roles: ['EMPLOYEE'] },
        { label: '我的匹配结果', path: '/matching/my-result', permission: 'NOTIFICATION:SELF', roles: ['EMPLOYEE'] },
        { label: '我的差距诊断', path: '/matching/gap-diagnosis', permission: 'NOTIFICATION:SELF', roles: ['EMPLOYEE'] },
        { label: '发起匹配', path: '/matching/execute', permission: 'MATCHING:EXECUTE', roles: ['HR_SPECIALIST'] },
        { label: '全局权重配置', path: '/matching/scoring-config', permission: 'MATCHING:CONFIG', roles: ['HR_SPECIALIST'] },
        { label: '匹配任务', path: '/matching/tasks', permission: 'MATCHING:READ', roles: ['HR_SPECIALIST'] },
        { label: '匹配结果', path: '/matching/result', permission: 'NOTIFICATION:SELF', roles: ['HR_SPECIALIST'] },
        { label: '差距诊断', path: '/matching/gap-diagnosis', permission: 'NOTIFICATION:SELF', roles: ['HR_SPECIALIST'] },
        // 【2026-09-04 下线】原「审批任务」(/matching/approval-tasks) 入口已删除：
        // 该页读的是 /api/matching/approval-flow/pending/{userId}（多节点审批待办），
        // 而唯一能产生审批流程行的 POST /approval-flow/initiate/{id} 在「匹配结果」页的
        // 「发起审批」入口被移除后已无任何调用方 —— 队列没有生产者，页面恒为空。
        // 真正在用的审批动作走匹配详情页的「审批复核」（POST /approval-flow/approve，
        // 权限码 MATCHING:APPROVE 保留），后端端点与权限码均未下线。
        { label: '黑白名单', path: '/matching/black-white-list', permission: 'MATCHING:CONFIG', roles: ['HR_SPECIALIST'] },
        { label: '匹配校准数据', path: '/matching/calibration', permission: 'MATCHING:CONFIG', roles: ['HR_SPECIALIST'] },
        { label: '视频终面', path: '/matching/communication-interview', permission: 'MATCHING:READ', roles: ['HR_SPECIALIST'] },
        // 【2026-09-04】下面这项此前是**孤儿页**：路由存在、页面完整，但侧边栏没有登记，
        // 全站也没有任何 router.push 指向它 —— 结果只能手敲 URL 才进得去，
        // 超级管理员在侧边栏里同样看不到入口。补齐入口。
        { label: '反馈数据', path: '/matching/feedback', permission: 'MATCHING:READ', roles: ['HR_SPECIALIST'] },
        // ⛔ 「审批历史」(/matching/approval-history) **入口已撤除**，与上方「审批任务」同样的原因：
        //    它读的是 approval_flow 表，而唯一的生产者 `POST /approval-flow/initiate/{id}`
        //    在「匹配结果」页的「发起审批」被移除后就没有任何调用方了 —— 表永远是空的，
        //    页面只能显示「暂无审批流程」。匹配结果不需要审批（HR 人工确认即为审核动作，
        //    可直接推送），所以不再保留任何审批概念的前端入口。
        //    后端端点与 MATCHING:APPROVE 权限码保留未动，历史数据也原样保留。
      ],
    },
    {
      key: 'learning',
      label: '学习成长',
      summary: '学习路径与资源管理',
      path: '/learning/path',
      icon: Reading,
      children: [
        { label: '我的学习路径', path: '/learning/my-path', permission: 'LEARNING:SELF', roles: ['EMPLOYEE'] },
        { label: '学习路径', path: '/learning/path', permission: 'LEARNING:SELF', roles: ['HR_SPECIALIST'] },
        { label: '资源管理', path: '/learning/resources', permission: 'ASSESSMENT:MANAGE', roles: ['HR_SPECIALIST'] },
        { label: '学习成果复核', path: '/learning/outcome-review', permission: 'ASSESSMENT:MANAGE', roles: ['HR_SPECIALIST'] },
        // 【2026-09-04】孤儿页补入口：路由 /learning/path-enhanced 与页面（777 行）都在，
        // 但侧边栏未登记、也没有任何跳转指向它 → 只能手敲 URL。补齐。
        { label: '知识图谱学习路径', path: '/learning/path-enhanced', permission: 'LEARNING:SELF', roles: ['EMPLOYEE'] },
      ],
    },
    {
      key: 'contest',
      label: '可信数据',
      summary: '证据中心',
      path: '/capability-brain/evidence',
      icon: DataAnalysis,
      children: [
        // 证据中心同一套接口承载「人员能力证据链」与「岗位需求依据链」两个域，
        // 页面内按角色锁死能看哪个域（HR→人员 / 岗位管理员→岗位），因此不挂单一权限码：
        // 两个角色持有的码互不相同（ASSESSMENT:MANAGE vs POST:MANAGE），挂任一个都会挡掉另一方。
        { label: '证据中心', path: '/capability-brain/evidence', roles: ['HR_SPECIALIST', 'JOB_ARCHITECT'] },
        // 【2026-09-04 下线】原「能力标签治理」入口（/system/ability-tag）已移除：
        // 该页的主体（标签目录/编辑/合并/关系治理）本质是在维护 post_ability_model 的冗余副本，
        // 同一份能力数据在两处维护必然漂移；唯一有价值的热度词云已迁到岗位体系管理员工作台
        // （见 views/workbench/RoleWorkbench.vue 的 abilityCloud）。
        // 后端 ability_tag 与 /api/system/tag-governance/** 保持不动。
      ],
    },
    {
      key: 'knowledge-assets',
      // 2026-09-04：模块内同时装「知识资产（RAG）」与「知识图谱」两类内容，
      // 且图谱按角色分为人员域/岗位域两个入口，故模块名不再叫「知识资产」。
      label: '知识与图谱',
      summary: '知识库与关系图谱（按角色分域）',
      path: '/rag/knowledge',
      icon: Collection,
      children: [
        // 知识库与知识图谱的内容本质是「岗位—能力—技术栈」的知识底座，与 /post/panorama 同源，
        // 故 2026-09-04 起归岗位体系管理员；后端 /api/rag/**、/api/kg/** 同步改挂 POST:MANAGE。
        { label: '知识资产', path: '/rag/knowledge', permission: 'POST:MANAGE', roles: ['JOB_ARCHITECT'] },
        // 图谱同一页面、两个入口：按角色看到不同的名字，进页面后数据域被锁死
        // （HR→人员能力图谱，只出人员相关节点；岗位管理员→岗位知识图谱）。
        { label: '岗位知识图谱', path: '/kg/workbench', permission: 'POST:MANAGE', roles: ['JOB_ARCHITECT'] },
        { label: '人员能力图谱', path: '/kg/workbench', permission: 'ASSESSMENT:MANAGE', roles: ['HR_SPECIALIST'] },
        // 【2026-09-04】孤儿页补入口：/kg/snapshot 由 router/permission-source-consistency.test.ts
        // 明确要求"只对 POST:MANAGE 开放"，页面也在，但侧边栏一直没登记。
        { label: '图谱快照', path: '/kg/snapshot', permission: 'POST:MANAGE', roles: ['JOB_ARCHITECT'] },
      ],
    },
    {
      key: 'ai-governance',
      label: 'AI 治理',
      summary: '岗位能力巡检',
      path: '/ai-governance/records',
      icon: View,
      children: [
        { label: '岗位能力巡检', path: '/ai-governance/records', permission: 'POST:MANAGE', roles: ['JOB_ARCHITECT'] },
        { label: '过滤规则配置', path: '/system/governance-filter-rules', permission: 'POST:MANAGE', roles: ['JOB_ARCHITECT'] },
      ],
    },
    {
      key: 'system',
      label: '系统工具',
      summary: '检索知识库与系统设置',
      path: '/system/extend-field',
      icon: Setting,
      children: [
        { label: '系统设置', path: '/system/extend-field', permission: 'ASSESSMENT:CONFIG', roles: ['PLATFORM_ADMIN'] },
        { label: 'Agent 记忆管理', path: '/system/agent-memory', permission: 'AI:CONFIG', roles: ['PLATFORM_ADMIN'] },
        { label: '企业 AI 模型配置', path: '/system/ai-model-config', permission: 'AI:CONFIG', roles: ['PLATFORM_ADMIN'] },
        { label: '来源权重配置', path: '/system/source-weight', permission: 'MATCHING:CONFIG', roles: ['HR_SPECIALIST'] },
        { label: '用户管理', path: '/system/user', permission: 'USER:MANAGE', roles: ['PLATFORM_ADMIN'] },
        { label: '角色权限', path: '/system/role', permission: 'ROLE:MANAGE', roles: ['PLATFORM_ADMIN'] },
        // 操作日志一直只有隐藏路由、没有菜单入口，而 AUDIT:READ 归平台管理员 ——
        // 补上入口，否则平台管理员看不到自己的审计页（此前该页甚至因权限口径不一致而无人可进）。
        { label: '操作日志', path: '/system/operation-log', permission: 'AUDIT:READ', roles: ['PLATFORM_ADMIN'] },
        // 【2026-09-04】运行审计：AI 运行时的 token 消耗 / LLM 与工具响应时间 /
        // Prompt 调用明细 / RAG 检索延迟。数据在库里（prompt_invocation_log、rag_query_log）
        // 与运行时指标里一直都有，只是从来没有给平台管理员任何展示入口。
        { label: '运行审计', path: '/system/runtime-audit', permission: 'AUDIT:READ', roles: ['PLATFORM_ADMIN'] },
      ],
    },
  ]
}
