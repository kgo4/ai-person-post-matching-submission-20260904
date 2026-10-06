/**
 * 前端路由守卫的「路径 → 权限码」规则表（单一来源）。
 *
 * 之前内联在 router/index.ts 的 beforeEach 里，导致三类消费方各自为政：
 * 1. 路由守卫按它放行/拦截；
 * 2. sidebar-menu 按「子项 permission」决定显隐，两套规则一旦不一致，
 *    就会出现「菜单可见但点进去被拦」或「有权限却看不到入口」；
 * 3. 测试无法复用守卫语义，只能复刻表格（必然漂移）。
 *
 * 收敛到这里后，侧边栏显隐与路由守卫可以用同一份规则做交叉校验。
 * 与后端 SecurityConfig 的对应关系见各条注释。
 */
export const PATH_PERMISSIONS: Array<[RegExp, string | string[]]> = [
  [/^\/employee\/(list|detail)/, 'EMPLOYEE:READ'],
  // PMS 项目分析：HR 专属独立功能（人员库 → PMS 项目分析）。
  // 后端 /api/employee/ability/pms/** 全族收口为
  // hasAnyAuthority(AI:CONFIG, ASSESSMENT:MANAGE)（见 SecurityConfig），
  // 前端只放 HR 侧的那个码；岗位体系管理员无此菜单项，不必放行。
  // 必须声明在 /^\/employee\/(list|detail)/ 之外单列——该规则只覆盖 list/detail，
  // 但若将来有人把它放宽成 /^\/employee\// 兜底，这条单列规则会先命中，防止误拦。
  [/^\/employee\/pms-analysis/, 'ASSESSMENT:MANAGE'],
  // 岗位建模类页面（能力配置/批量导入/模板/趋势发现/市场 JD 统计）都是岗位体系管理员的活；
  // 趋势发现页会把政府红头文件解析出的岗位草案直接呈现出来，因此含 GET 也收口为 POST:MANAGE
  // ——后端 /api/post/trend/** 全族同口径（见 SecurityConfig），只持 POST:READ 的 HR 不得进入。
  // 必须放在下面的 /^\/post\// (POST:READ) 兜底之前。
  [/^\/post\/(model-config|excel-import|trend-discovery|market-jd-stats|template)/, 'POST:MANAGE'],
  // 岗位全景图谱是岗位知识视图：2026-09-04 口径「HR 只看人员能力相关，不看岗位相关」，
  // 故入口只给岗位体系管理员。后端 /api/post/panorama/{graph,fact-graph} 同口径收口为 POST:MANAGE
  // （/overview 例外，HR 工作台在用）。必须放在下面的 /^\/post\// 之前。
  [/^\/post\/panorama/, 'POST:MANAGE'],
  // 岗位演化与市场 JD 采集同属「市场数据 → 能力更新」链路，后端 /api/post/evolution/** 全族
  // 都由 POST:EVOLUTION 收口（含爬虫代理 crawler/** 与市场 JD 池 market-jd/**）。
  // 必须声明在下面的 /^\/post\// (POST:READ) 兜底之前，否则会落入兜底而与路由 meta 不相交。
  [/^\/post\/(evolution|crawler-jd)/, 'POST:EVOLUTION'],
  [/^\/post\//, 'POST:READ'],
  [/^\/matching\/(execute)/, 'MATCHING:EXECUTE'],
  // 员工自助发起匹配：与 HR 的 /matching/execute 是两个页面、两个权限码
  // （员工固定本人 + 岗位单选 + 须已生成能力分析报告 → MATCHING:SELF）。
  // 必须声明在下面 my-result 规则与 /^\/matching\// 兜底**之前**，
  // 否则会被 NOTIFICATION:SELF / MATCHING:READ 抢先匹配，
  // 结果是「员工看得见侧边栏入口、点进去却被守卫按别的码拦下」。
  [/^\/matching\/self-execute/, 'MATCHING:SELF'],
  // （原 /matching/approval-tasks、/matching/approval-history 已随「审批」概念一起下线，
  //  规则一并移除，避免守卫与路由两层漂移）
  [/^\/matching\/(scoring-config|calibration|black-white-list)/, 'MATCHING:CONFIG'],
  // 员工侧匹配入口：我的匹配结果与我的差距诊断属于“仅本人”数据范围，
  // 管理端页面（result）同样放开给持有管理端权限的角色，因此用 SELF 权限统一放行。
  [/^\/matching\/(my-result|result|gap-diagnosis)/, 'NOTIFICATION:SELF'],
  [/^\/matching\//, 'MATCHING:READ'],
  [/^\/system\/(user)/, 'USER:MANAGE'],
  [/^\/system\/(role)/, 'ROLE:MANAGE'],
  [/^\/system\/(ai-model-config|agent-memory)/, 'AI:CONFIG'],
  [/^\/system\/(extend-field)/, 'ASSESSMENT:CONFIG'],
  [/^\/system\/(source-weight)/, 'MATCHING:CONFIG'],
  // 【2026-09-04 下线】/system/ability-tag 与 /system/tag-governance 的规则已删除：
  // 能力标签治理页已下线（热度词云迁到工作台，其余功能与岗位能力配置重复），
  // 路由不存在后保留规则只会让守卫与路由两层口径漂移。后端 /api/system/tag-governance/**
  // 仍要求 POST:MANAGE，如需重新开放页面，规则要一并恢复。
  [/^\/system\/governance-filter-rules/, 'POST:MANAGE'],
  // 操作日志：路由 meta 要 AUDIT:READ，而原先落到下面 /^\/system\// 的 ASSESSMENT:MANAGE 兜底 ——
  // 两者不相交，结果是**谁都进不去**（AUDIT:READ 持有者被兜底拦、ASSESSMENT:MANAGE 持有者被 meta 拦）。
  // 由 scripts 契约测试 permission-source-consistency.test.ts 发现。
  [/^\/system\/operation-log/, 'AUDIT:READ'],
  // 运行审计（token 消耗 / 响应时间 / Prompt 与 RAG 调用明细）：与操作日志同属审计域，
  // 同样必须声明在下面 /^\/system\// 兜底之前，否则会被兜底的 ASSESSMENT:MANAGE 盖住，
  // 而它与 meta 的 AUDIT:READ 不相交 → PLATFORM_ADMIN 反而进不去。
  [/^\/system\/runtime-audit/, 'AUDIT:READ'],
  [/^\/system\//, 'ASSESSMENT:MANAGE'],
  [/^\/learning\/resources/, 'ASSESSMENT:MANAGE'],
  // 学习成果复核（闭环 P4）：HR 专属复核动作，员工侧只提交不复核
  [/^\/learning\/outcome-review/, 'ASSESSMENT:MANAGE'],
  // 员工侧「我的学习路径」：与 HR 工作台 /learning/path 分开，人员范围固定为本人
  [/^\/learning\/my-path/, 'LEARNING:SELF'],
  [/^\/learning\//, 'LEARNING:SELF'],
  // AI 治理（岗位能力巡检）：后端 /api/ai-governance/harness/** 为
  // hasAnyAuthority(POST:MANAGE, ASSESSMENT:MANAGE)，与 rag/kg 的 AI:CONFIG 不同，
  // 必须拆开收口，否则持有 POST:MANAGE 的岗位体系管理员会被前端守卫误拦。
  [/^\/ai-governance\//, ['POST:MANAGE', 'ASSESSMENT:MANAGE']],
  // 知识资产（RAG）与知识图谱：2026-09-04 起
  //   · /rag/** → 岗位体系管理员（POST:MANAGE，内容是岗位—能力—技术栈的知识底座）
  //   · /kg/**  → 同一页面按角色锁域（HR→人员能力图谱 / 岗位管理员→岗位知识图谱），
  //     所以两个角色都要能进；真正的域隔离在后端（/api/kg/employee/** vs /api/kg/post|panorama）。
  [/^\/rag\//, 'POST:MANAGE'],
  [/^\/kg\//, ['POST:MANAGE', 'ASSESSMENT:MANAGE']],
  // 证据中心：同一页面承载人员域与岗位域，两个角色各持一个码（页面内按角色锁域）
  [/^\/capability-brain\//, ['POST:MANAGE', 'ASSESSMENT:MANAGE']],
]

/** 员工能力画像路径：管理端看全员、员工看本人，用 SELF/MANAGE 二选一放行 */
export function isAssessmentProfilePath(path: string): boolean {
  return /^\/employee\/ability-profile/.test(path)
}

/** 返回访问该路径所需的权限码（数组=任一满足即可）；无需特定权限时返回 undefined */
export function findRequiredPathPermission(path: string): string | string[] | undefined {
  return PATH_PERMISSIONS.find(([pattern]) => pattern.test(path))?.[1]
}

/** 按守卫语义判断权限集合是否可访问该路径 */
export function satisfiesPathPermission(
  path: string,
  permissions: string[],
): boolean {
  const required = findRequiredPathPermission(path)
  if (!required) return true
  const requiredList = Array.isArray(required) ? required : [required]
  return requiredList.some(code => permissions.includes(code))
}
