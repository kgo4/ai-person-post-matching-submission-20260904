import { describe, expect, it } from 'vitest'
import { MODULE_ORDER, filterSidebarModules, getSidebarModules } from './sidebar-menu'
import { MENU_EXCLUDED_PATHS } from '@/utils/super-admin'

/** V147__introduce_business_rbac.sql 中每个角色实际持有的权限码 */
const ROLE_PERMISSIONS: Record<string, string[]> = {
  // 【2026-09-04】EMPLOYEE 补上 MATCHING:SELF —— 生产库原本缺这条授权，
  // 导致员工提交自助匹配时被 SecurityConfig 拦成 403（见 sql/hotfix_20260904_grant_matching_self.sql）。
  EMPLOYEE: ['ASSESSMENT:SELF', 'NOTIFICATION:SELF', 'LEARNING:SELF', 'MATCHING:SELF'],
  HR_SPECIALIST: [
    'ASSESSMENT:SELF',
    'NOTIFICATION:SELF',
    'EMPLOYEE:READ',
    'ASSESSMENT:MANAGE',
    'MATCHING:READ',
    'MATCHING:EXECUTE',
    'MATCHING:APPROVE',
    'MATCHING:CONFIG',
    'POST:READ',
  ],
  JOB_ARCHITECT: ['POST:READ', 'POST:MANAGE', 'POST:EVOLUTION'],
  // 2026-09-04：AI_CONFIG_MANAGER 与 SECURITY_ADMIN 合并为 PLATFORM_ADMIN（V162）
  PLATFORM_ADMIN: ['USER:MANAGE', 'ROLE:MANAGE', 'AUDIT:READ', 'AI:CONFIG', 'ASSESSMENT:CONFIG'],
}

function menuFor(role: string) {
  return filterSidebarModules(getSidebarModules(), role, ROLE_PERMISSIONS[role] ?? [])
}

function pathsFor(role: string): string[] {
  return menuFor(role).flatMap(module => module.children.map(child => child.path))
}

function keysFor(role: string): string[] {
  return menuFor(role).map(module => module.key)
}

describe('sidebar-menu role filtering', () => {
  it('员工只得到本人业务入口', () => {
    const keys = keysFor('EMPLOYEE')
    expect(keys).toEqual(['home', 'employee', 'matching', 'learning'])
    // 管理端页面绝不能出现在员工菜单里
    expect(pathsFor('EMPLOYEE')).not.toContain('/employee/list')
    expect(pathsFor('EMPLOYEE')).not.toContain('/matching/execute')
    expect(pathsFor('EMPLOYEE')).not.toContain('/post/list')
    // 员工的自助发起匹配走独立页面（MATCHING:SELF），不是 HR 的 /matching/execute
    expect(pathsFor('EMPLOYEE')).toContain('/matching/self-execute')
    // 「数据看板」已并入工作台（2026-09-04），侧边栏不再有任何 dashboard 模块
    expect(keys).not.toContain('dashboard')
  })

  it('员工看到的是“我的”业务命名而不是管理端命名', () => {
    const labels = menuFor('EMPLOYEE').flatMap(module => module.children.map(child => child.label))
    expect(labels).toContain('我的能力')
    expect(labels).toContain('我的匹配结果')
    expect(labels).toContain('我的学习路径')
    expect(labels).not.toContain('员工档案')

    // 管理端匹配结果控制台含审批/锁定/改分/导出等动作，员工侧必须走只读的 my-result
    const employeePaths = pathsFor('EMPLOYEE')
    expect(employeePaths).toContain('/matching/my-result')
    expect(employeePaths).not.toContain('/matching/result')

    // 学习路径同理：员工侧必须是独立页面。
    // HR 的 /learning/path 是工作台，第一步是「选择人岗匹配记录」（还能搜人员姓名），
    // 员工落到那里会看到一堆与自己无关的选人控件。
    expect(employeePaths).toContain('/learning/my-path')
    expect(employeePaths).not.toContain('/learning/path')
  })

  it('员工的「发起匹配」独立成页，且不与 HR 的发起匹配共用入口', () => {
    // 2026-09-04：员工原先只能从「我的匹配结果」的弹窗里发起匹配，入口既隐蔽、
    // 又因为指向 HR 页面而拿不到 MATCHING:EXECUTE（表现为 403「没有权限访问该资源」）。
    const employeePaths = pathsFor('EMPLOYEE')
    expect(employeePaths).toContain('/matching/self-execute')
    expect(employeePaths).not.toContain('/matching/execute')

    // HR 侧不受影响：仍然只有管理端发起匹配页
    const hrPaths = pathsFor('HR_SPECIALIST')
    expect(hrPaths).toContain('/matching/execute')
    expect(hrPaths).not.toContain('/matching/self-execute')
  })

  it('首页固定排在第一个模块', () => {
    for (const role of Object.keys(ROLE_PERMISSIONS)) {
      expect(keysFor(role)[0]).toBe('home')
    }
  })

  it('HR 得到人员档案、匹配运营与审批入口，且不越权到岗位维护', () => {
    const paths = pathsFor('HR_SPECIALIST')
    expect(paths).toContain('/employee/list')
    expect(paths).toContain('/matching/execute')
    expect(paths).toContain('/matching/result')
    // HR 是单一角色，同时持有匹配策略配置权限
    expect(paths).toContain('/matching/scoring-config')
    // 【2026-09-04 下线】「审批任务」(/matching/approval-tasks) 已随页面删除：
    // 该待办队列依赖 POST /approval-flow/initiate/{id} 产出行，而该入口已被移除，队列恒为空。
    // 审批动作本身仍在匹配详情页（POST /approval-flow/approve，MATCHING:APPROVE 保留）。
    expect(paths).not.toContain('/matching/approval-tasks')
    expect(paths).not.toContain('/post/model-config')
    expect(paths).not.toContain('/system/user')
  })

  it('岗位体系管理员不出现人员与匹配入口', () => {
    const keys = keysFor('JOB_ARCHITECT')
    expect(keys).toContain('post')
    expect(keys).not.toContain('employee')
    expect(keys).not.toContain('matching')
  })

  it('平台管理员只看到平台侧入口，不碰业务模块', () => {
    // 合并自原 AI_CONFIG_MANAGER + SECURITY_ADMIN：现在只保留「账号/角色/模型/系统设置」，
    // 业务知识（知识资产、图谱、标签治理）已归岗位体系管理员。
    const keys = keysFor('PLATFORM_ADMIN')
    expect(keys).toEqual(['home', 'system'])
    const paths = pathsFor('PLATFORM_ADMIN')
    expect(paths).toContain('/system/user')
    expect(paths).toContain('/system/role')
    expect(paths).toContain('/system/ai-model-config')
    expect(paths).toContain('/system/agent-memory')
    // 合并后不再持有业务知识入口
    expect(paths).not.toContain('/rag/knowledge')
    expect(paths).not.toContain('/kg/workbench')
    expect(paths).not.toContain('/capability-brain/evidence')
    expect(keys).not.toContain('post')
    expect(keys).not.toContain('matching')
  })

  it('岗位体系管理员接手知识资产与图谱，且看不到人员域', () => {
    // 2026-09-04：/rag/**、/kg/**、过滤规则 改挂 POST:MANAGE
    const paths = pathsFor('JOB_ARCHITECT')
    expect(paths).toContain('/rag/knowledge')
    expect(paths).toContain('/kg/workbench')
    expect(paths).toContain('/system/governance-filter-rules')
    expect(paths).toContain('/post/panorama')
    // 能力标签治理页已下线（其数据与岗位能力表重复），热度词云迁到工作台；
    // 入口不得再出现在侧边栏，否则会点进一个已删除的路由。
    expect(paths).not.toContain('/system/ability-tag')
    // 人员域（证据中心的人员侧由页面内按角色锁域，菜单可见但域被锁到岗位）
    expect(pathsFor('JOB_ARCHITECT')).not.toContain('/employee/list')
  })

  it('HR 不再看到岗位知识视图（全景图谱）', () => {
    // 2026-09-04 口径：HR 只看人员能力相关，不看岗位相关知识。
    // 岗位档案（/post/list）保留 —— 发起匹配时必须选岗位。
    const paths = pathsFor('HR_SPECIALIST')
    expect(paths).not.toContain('/post/panorama')
    expect(paths).toContain('/post/list')
  })

  it('没有任何可见子项的模块不渲染', () => {
    // 未知角色只能看到对所有角色开放的首页；其余模块都限定了角色/权限，因此整块不渲染
    const modules = filterSidebarModules(getSidebarModules(), 'UNKNOWN_ROLE', [])
    expect(modules.map(module => module.key)).toEqual(['home'])
    expect(modules.every(module => module.children.length > 0)).toBe(true)
  })

  it('「数据看板」已并入工作台，任何角色都不再有独立入口', () => {
    // 2026-09-04：原独立 /dashboard 页与侧边栏模块已移除，
    // 匹配驾驶舱内容作为工作台的「匹配分析」区块、**仅 HR 渲染**。
    for (const role of ['EMPLOYEE', 'HR_SPECIALIST', 'JOB_ARCHITECT', 'PLATFORM_ADMIN']) {
      expect(keysFor(role)).not.toContain('dashboard')
    }
  })

  it('权限码缺失时即使角色匹配也会被过滤', () => {
    const modules = filterSidebarModules(getSidebarModules(), 'HR_SPECIALIST', [])
    const employeeModule = modules.find(module => module.key === 'employee')
    // 员工角色入口按 roles 过滤后不再出现；HR 的能力评估不需要权限码，仍然保留
    expect(employeeModule?.children.map(child => child.path)).not.toContain('/employee/list')
  })
})

describe('多角色账号与超级管理员（2026-09-04 新增）', () => {
  const mergedEmployeeAdmin = [
    ...ROLE_PERMISSIONS.EMPLOYEE,
    ...ROLE_PERMISSIONS.PLATFORM_ADMIN,
  ]

  it('多角色账号取并集：任一角色命中即可见', () => {
    const paths = filterSidebarModules(getSidebarModules(), ['EMPLOYEE', 'PLATFORM_ADMIN'], mergedEmployeeAdmin)
      .flatMap(module => module.children.map(child => child.path))
    expect(paths).toContain('/employee/ability-profile')
    expect(paths).toContain('/system/user')
  })

  it('主角色不是目标角色时目标菜单仍可见（回归：原实现只看 roles[0] 会让菜单整体消失）', () => {
    // 只传单个主角色时该菜单确实要隐藏……
    expect(
      filterSidebarModules(getSidebarModules(), 'EMPLOYEE', ROLE_PERMISSIONS.PLATFORM_ADMIN).map(m => m.key),
    ).not.toContain('system')
    // ……但传全量角色数组时必须出现（角色顺序无关）
    expect(
      filterSidebarModules(getSidebarModules(), ['EMPLOYEE', 'PLATFORM_ADMIN'], ROLE_PERMISSIONS.PLATFORM_ADMIN).map(
        m => m.key,
      ),
    ).toContain('system')
  })

  it('超级管理员可见所有模块，且不受 roles 声明限制', () => {
    const keys = filterSidebarModules(getSidebarModules(), ['SUPER_ADMIN'], mergedEmployeeAdmin).map(m => m.key)
    // 员工、岗位、匹配、学习、系统等模块都在
    expect(keys).toEqual(expect.arrayContaining(['employee', 'post', 'matching', 'learning', 'system']))
  })

  it('超级管理员即便权限码数组为空也可见全部模块（后端按角色动态装载权限）', () => {
    const keys = filterSidebarModules(getSidebarModules(), ['SUPER_ADMIN'], []).map(m => m.key)
    expect(keys).toEqual(expect.arrayContaining(['employee', 'post', 'matching', 'learning', 'system']))
  })

  it('超级管理员码带 ROLE_ 前缀、大小写混写也能识别', () => {
    const keys = filterSidebarModules(getSidebarModules(), ['role_super_admin'], []).map(m => m.key)
    expect(keys).toContain('system')
  })
})

describe('超级管理员必须看到「全部」入口（回归：配置漏登记页面）', () => {
  /**
   * 这条是用户反馈「超管的侧边栏没有全部功能入口」后加的**结构性**回归。
   *
   * 当时的真相不是过滤逻辑错了（超管确实拿到了配置里的每一项），
   * 而是**配置本身漏登记了页面**（当时是这 4 个：审批历史 / 反馈数据 /
   * 知识图谱学习路径 / 图谱快照）。其中：
   *   · 审批历史 —— 后随「审批」概念一起**彻底下线**（路由与页面都删了）；
   *   · 知识图谱学习路径 —— 列入 MENU_EXCLUDED_PATHS（功能未完成，谁都不给看）；
   *   · 反馈数据、图谱快照 —— 保留入口，见下方断言。
   * 却没有侧边栏条目、也没有任何 router.push 指向它们 —— 只能手敲 URL。
   *
   * 因此这里断言的是「配置中的每一项，超管都看得见」，
   * 而不是「超管至少能看到某几个模块」—— 后者漏登记时依然会通过。
   */
  it('配置里的每一个子项都出现在超管菜单里（扣掉「菜单排除名单」后数量与集合都相等）', () => {
    const all = getSidebarModules().flatMap(m => m.children.map(c => c.path))
    // 排除名单里的页面超管也看不到（那是「尚未完成」的页面，不是权限问题）
    const expected = all.filter(p => !(MENU_EXCLUDED_PATHS as readonly string[]).includes(p))
    const visible = filterSidebarModules(getSidebarModules(), ['SUPER_ADMIN'], []).flatMap(m =>
      m.children.map(c => c.path),
    )
    expect(visible).toHaveLength(expected.length)
    expect(new Set(visible)).toEqual(new Set(expected))
  })

  it('超管看得到全部 9 个模块', () => {
    const keys = filterSidebarModules(getSidebarModules(), ['SUPER_ADMIN'], []).map(m => m.key)
    expect(keys).toHaveLength(MODULE_ORDER.length)
  })

  it('孤儿页已补入口（这几条一旦被删，超管就又会看不到它们）', () => {
    const paths = filterSidebarModules(getSidebarModules(), ['SUPER_ADMIN'], []).flatMap(m =>
      m.children.map(c => c.path),
    )
    expect(paths).toEqual(
      expect.arrayContaining(['/matching/feedback', '/kg/snapshot']),
    )
  })

  /**
   * 菜单排除名单：超管**也**看不到这些路径。
   *
   * 语义是「这份功能还没做完，先不给任何人看」，与权限无关 ——
   * 所以它必须能压过超管的"全放行"。知识图谱学习路径（`/learning/path-enhanced`）
   * 就在名单里：它的 empId 写死为 1、依赖的 knowledge_domain 没有任何种子数据，
   * 点「生成路径」只会静默返回空。让超管看见只会被误判成"系统坏了"。
   */
  it('菜单排除名单：超管也看不到，且不影响其它角色看到自己的入口', () => {
    expect(MENU_EXCLUDED_PATHS).toContain('/learning/path-enhanced')

    const superPaths = filterSidebarModules(getSidebarModules(), ['SUPER_ADMIN'], []).flatMap(m =>
      m.children.map(c => c.path),
    )
    for (const excluded of MENU_EXCLUDED_PATHS) {
      expect(superPaths).not.toContain(excluded)
    }

    // 员工也不该看到（原本这条是挂在 EMPLOYEE 上的）
    const employeePaths = filterSidebarModules(
      getSidebarModules(),
      ['EMPLOYEE'],
      ROLE_PERMISSIONS.EMPLOYEE,
    ).flatMap(m => m.children.map(c => c.path))
    expect(employeePaths).not.toContain('/learning/path-enhanced')
    // 但员工自己的主入口必须还在（别把整块学习模块一起藏掉）
    expect(employeePaths).toContain('/learning/my-path')
  })
})

describe('「审批」概念不得在前端重新出现（2026-09-04 口径）', () => {
  /**
   * 业务口径：**匹配结果不需要审批** —— HR 人工确认即为审核动作，确认后可直接推送。
   *
   * 曾经的矛盾状态：审批流的唯一写入方 `POST /approval-flow/initiate/{id}` 在
   * 「匹配结果」页的「发起审批」被移除后就没有调用方了，`approval_flow` 表恒空；
   * 但侧边栏「审批历史」入口、匹配详情页「审批流程」Tab + 通过/驳回按钮都还留着，
   * 于是用户看到「不需要审批了，为什么还有审批历史」。
   *
   * 现在这些入口全部撤除。本组断言防止有人"顺手"把它们加回来：
   * 真要恢复审批，必须连发起端、节点配置、待办队列一起恢复，而不是只挂一个空页面。
   */
  it('侧边栏不再有「审批历史」入口', () => {
    const allPaths = getSidebarModules().flatMap(m => m.children.map(c => c.path))
    expect(allPaths).not.toContain('/matching/approval-history')

    // 任何角色都不该看到
    for (const role of ['EMPLOYEE', 'HR_SPECIALIST', 'JOB_ARCHITECT', 'PLATFORM_ADMIN', 'SUPER_ADMIN']) {
      const paths = filterSidebarModules(
        getSidebarModules(),
        [role],
        ROLE_PERMISSIONS[role] ?? [],
      ).flatMap(m => m.children.map(c => c.path))
      expect(paths).not.toContain('/matching/approval-history')
    }
  })

  it('侧边栏里没有任何带「审批」字样的入口', () => {
    const labels = getSidebarModules().flatMap(m => [m.label, ...m.children.map(c => c.label)])
    const approvals = labels.filter(label => label.includes('审批'))
    expect(approvals).toEqual([])
  })

  it('匹配模块下「反馈数据」仍在（撤审批时不能连正常入口一起删）', () => {
    const paths = filterSidebarModules(
      getSidebarModules(),
      ['HR_SPECIALIST'],
      ROLE_PERMISSIONS.HR_SPECIALIST,
    ).flatMap(m => m.children.map(c => c.path))
    expect(paths).toContain('/matching/feedback')
    expect(paths).toContain('/matching/result')
    expect(paths).toContain('/matching/communication-interview')
  })
})
