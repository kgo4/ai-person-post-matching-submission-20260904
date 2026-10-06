/**
 * 管理端角色工作台纯逻辑（HR / 岗位体系 / AI 配置 / 权限管理）。
 *
 * 只做「原始接口数据 → 工作台快照」的映射，不发起请求、不引用 Vue，
 * 便于在 node 直跑的单测里覆盖有数据 / 空数据两种情况。
 *
 * 设计口径：
 * - 四个角色共享同一套区块形态（KPI / 趋势 / 待办 / 进度 / 通知 / 快捷入口），
 *   差异只体现在取值口径与文案上，因此公共部分统一构造，避免复制粘贴；
 * - 取不到的数据一律落到业务空状态，禁止用 0 或假数据占位；
 * - 本模块不产出 members（团队成员来自工作台指标接口，由 useWorkbenchData 合并）。
 */
// 显式带上 .ts 后缀：该模块会被 node --experimental-strip-types 直跑的单测导入。
import {
  formatMetric,
  monthDayLabel,
  relativeTime,
  type RoleWorkbenchSnapshot,
  type WorkbenchAbilityHeat,
  type WorkbenchNotice,
  type WorkbenchProgress,
  type WorkbenchStat,
  type WorkbenchTodo,
  type WorkbenchTrendPoint,
} from './workbench-types.ts'

/* ============================ 通用工具 ============================ */

/** 从分页响应取 total；取不到返回 null（不返回 0，0 是有效业务值） */
export function totalOf(page: unknown): number | null {
  const total = (page as { total?: unknown } | null)?.total
  return total == null ? null : Number(total)
}

/** 从分页响应取 records；非数组一律返回空数组 */
export function recordsOf<T>(page: unknown): T[] {
  const records = (page as { records?: unknown } | null)?.records
  return Array.isArray(records) ? (records as T[]) : []
}

/** 比值百分比：分母为 0 或缺失时返回 null（表示"暂无口径"，不是 0%） */
export function ratioPercent(numerator: number | null | undefined, denominator: number | null | undefined): number | null {
  const num = Number(numerator ?? 0)
  const den = Number(denominator ?? 0)
  if (!den || Number.isNaN(num) || Number.isNaN(den)) return null
  return Math.round((num / den) * 100)
}

/** 匹配记录形状（跨角色复用，字段与后端一致） */
export interface MatchingRecordLike {
  createdTime?: string
  aiMatchScore?: number | string
  finalMatchScore?: number | string
}

/**
 * 通用双折线趋势：取最近 N 条记录，按时间正序。
 *
 * 后端返回的记录按时间倒序，这里 reverse 后取尾部，
 * 保证 x 轴时间从左到右递增。
 */
export function recordTrend(records: MatchingRecordLike[], size = 8) {
  return [...records]
    .reverse()
    .slice(-size)
    .map(record => ({
      label: monthDayLabel(record.createdTime),
      primary: Number(record.aiMatchScore ?? 0),
      secondary: Number(record.finalMatchScore ?? record.aiMatchScore ?? 0),
    }))
}

/** 匹配任务形状 */
export interface MatchingTaskLike {
  id?: string | number
  status?: number
  refName?: string
  message?: string
  createdTime?: string
}

/** 匹配任务状态：1 执行中，2 成功，3 失败 */
export function countRunningTasks(tasks: MatchingTaskLike[]): number {
  return tasks.filter(task => task.status === 1).length
}

/** 匹配任务 → 系统通知（取最近若干条） */
export function tasksToNotices(tasks: MatchingTaskLike[], size = 4): WorkbenchNotice[] {
  return tasks.slice(0, size).map(task => ({
    title: String(task.refName || task.id || '匹配任务'),
    desc: task.message || '后台匹配任务状态更新',
    time: relativeTime(task.createdTime),
    kind: task.status === 3 ? 'warning' : task.status === 2 ? 'success' : 'info',
  }))
}

/** 审计日志形状（权限管理员用） */
export interface AuditLogLike {
  realName?: string
  operationDesc?: string
  operationType?: string
  operationTime?: string
}

/** 审计日志 → 系统通知 */
export function logsToNotices(logs: AuditLogLike[]): WorkbenchNotice[] {
  return logs.map(log => ({
    title: log.operationDesc || log.operationType || '系统操作',
    desc: `${log.realName || '系统'} 执行了该操作`,
    time: relativeTime(log.operationTime),
    kind: 'info' as const,
  }))
}

/** 进度条统一构造：percent 为 null 时展示 0 宽度但状态文案保留口径说明 */
export function progressOf(
  label: string,
  percent: number | null,
  status: string,
  path: string,
  iconKey: WorkbenchProgress['iconKey'],
): WorkbenchProgress {
  return { label, value: percent ?? 0, status: percent == null ? `暂无口径（${status}）` : status, path, iconKey }
}

/* ============================= HR 侧 ============================= */

export interface HrWorkbenchInput {
  employeeTotal: number | null
  enabledTotal: number | null
  postTotal: number | null
  matching: {
    total?: number | null
    status1?: number | null
    status2?: number | null
    /** 尚未推送给员工的记录数，是 HR「还有多少条要确认」的真实待办依据 */
    pendingPublish?: number | null
    recent?: MatchingRecordLike[]
  } | null
  tasks: MatchingTaskLike[]
  /**
   * 学习成果复核队列。
   * <p>
   * HR 工作台关心的是「有多少员工提交的学习成果还没复核」，
   * 不是「全公司学习计划的步骤完成率」——后者是员工自己的进度，不构成 HR 待办。
   * `pending` 取不到时为 null（不伪造 0）。
   */
  outcomeReview: {
    pending?: number | null
    total?: number | null
  } | null
}

/**
 * HR 工作台。
 *
 * 职责：管理人员能力证据、发起匹配并在确认后向员工发布结果、复核员工提交的学习成果。
 * HR 是单一角色，同时承担发起、审批、结果发布与学习成果复核职责。
 */
export function buildHrWorkbench(input: HrWorkbenchInput): RoleWorkbenchSnapshot {
  const matchingTotal = input.matching?.total ?? null
  const status2 = input.matching?.status2 ?? null
  const runningTasks = countRunningTasks(input.tasks)
  const matchingRecent = input.matching?.recent ?? []
  const outcomePending = input.outcomeReview?.pending ?? null
  const outcomeTotal = input.outcomeReview?.total ?? null
  // 已处理 = 总数 - 待处理；任一项取不到时不给百分比（宁可显示"暂无口径"也不编数字）
  const outcomeReviewed = outcomeTotal != null && outcomePending != null
    ? Math.max(outcomeTotal - outcomePending, 0)
    : null

  const stats: WorkbenchStat[] = [
    { key: 'employees', label: '员工总数', value: formatMetric(input.employeeTotal), hint: input.enabledTotal == null ? '人员档案总量' : `启用 ${input.enabledTotal} 人`, iconKey: 'employee', tone: 'primary', path: '/employee/list' },
    { key: 'posts', label: '岗位总数', value: formatMetric(input.postTotal), hint: '岗位体系规模', iconKey: 'post', tone: 'success', path: '/post/list' },
    { key: 'matching', label: '匹配记录', value: formatMetric(matchingTotal), hint: '累计匹配结果', iconKey: 'matching', tone: 'primary', path: '/matching/result' },
    { key: 'running', label: '进行中匹配', value: formatMetric(input.matching?.status1 ?? null), hint: '后台匹配任务', iconKey: 'task', tone: 'warning', path: '/matching/tasks' },
    { key: 'outcomes', label: '待复核学习成果', value: formatMetric(outcomePending), hint: outcomePending == null ? '复核队列暂不可用' : (outcomePending > 0 ? '员工提交的成果等待复核' : '暂无待复核成果'), iconKey: 'learning', tone: 'info', path: '/learning/outcome-review' },
  ]

  /*
   * 待办只放「有真实未处理状态」的事项。「发起人岗匹配 / 维护人员档案」这类
   * 常驻入口不是待办（已在下方 actions 快捷入口里），常驻会让面板看着像假的。
   */
  const pendingPublish = input.matching?.pendingPublish ?? null
  const todos: WorkbenchTodo[] = []

  if (pendingPublish != null && pendingPublish > 0) {
    todos.push({
      title: '确认并发布匹配结果',
      desc: `${pendingPublish} 条匹配结果尚未推送给员工，推送后员工才能看到`,
      meta: '待处理',
      path: '/matching/result',
      urgent: true,
    })
  }
  if (runningTasks > 0) {
    todos.push({
      title: '有匹配任务正在执行',
      desc: `${runningTasks} 个匹配任务执行中，完成后需确认并发布结果`,
      meta: '执行中',
      path: '/matching/tasks',
    })
  }
  if (outcomePending != null && outcomePending > 0) {
    todos.push({
      title: '复核员工学习成果',
      desc: `${outcomePending} 条学习成果等待复核，复核通过后才计入员工能力`,
      meta: '待处理',
      path: '/learning/outcome-review',
      urgent: true,
    })
  }

  return {
    stats,
    trend: {
      title: '匹配结果趋势',
      subtitle: '最近匹配记录的分数变化',
      primaryName: 'AI 匹配分',
      secondaryName: '最终分',
      points: recordTrend(matchingRecent),
    },
    todos,
    progresses: [
      progressOf('匹配完成率', ratioPercent(status2, matchingTotal), `完成 ${status2 ?? 0} / 共 ${matchingTotal ?? 0}`, '/matching/result', 'matching'),
      progressOf('人员启用率', ratioPercent(input.enabledTotal, input.employeeTotal), `${input.enabledTotal ?? 0} 启用 / 共 ${input.employeeTotal ?? 0}`, '/employee/list', 'employee'),
      progressOf('学习成果复核处理率', ratioPercent(outcomeReviewed, outcomeTotal), `已复核 ${outcomeReviewed ?? 0} / 共 ${outcomeTotal ?? 0} 条`, '/learning/outcome-review', 'learning'),
    ],
    notices: tasksToNotices(input.tasks),
    actions: [
      { label: '发起人岗匹配', desc: '选择岗位与人员执行匹配', path: '/matching/execute', iconKey: 'matching' },
      { label: '人员档案', desc: '查看评估进度与能力', path: '/employee/list', iconKey: 'employee' },
      { label: '匹配结果确认', desc: '确认并发布匹配结果', path: '/matching/result', iconKey: 'assessment' },
      { label: '差距诊断', desc: '查看能力差距与建议', path: '/matching/gap-diagnosis', iconKey: 'ability' },
    ],
    subtitle: '管理人员能力证据、发起匹配并在确认后向员工发布结果',
    primaryActionLabel: '+ 发起人岗匹配',
    primaryActionPath: '/matching/execute',
  }
}

/* ========================= 岗位体系管理员 ========================= */

/** 岗位能力项（词云数据来源：post_ability_model；同时用于「岗位能力规模」按岗位聚合） */
export interface PostAbilityLike {
  tagName?: string | null
  /** 所属岗位；缺失时该条不参与按岗位聚合（没有归属就无法计入任何岗位规模） */
  postId?: number | null
  /** 是否核心能力；后端返回 1/0 时由视图层已归一为 boolean */
  isCore?: boolean | null
}

/** 岗位清单条目（取岗位名称用） */
export interface PostScalePostLike {
  id?: number | null
  postName?: string | null
}

export interface JobArchitectWorkbenchInput {
  postTotal: number | null
  panorama: {
    stats?: { abilityCount?: number | null; skillPointCount?: number | null } | null
    /** 岗位能力明细，用于聚合能力热度词云 */
    abilities?: PostAbilityLike[] | null
    /** 岗位清单，用于「岗位能力规模」按岗位聚合时取岗位名称 */
    posts?: PostScalePostLike[] | null
  } | null
  inspection: { abilityCount?: number | null; riskyCount?: number | null; aiSourceCount?: number | null } | null
  /** 岗位趋势发现待人工审核汇总（跨任务口径）；取不到时为 null，不伪造 0 */
  trendPending?: TrendPendingLike | null
}

/** 岗位趋势待审汇总（对应后端 TrendPendingSummaryVO） */
export interface TrendPendingLike {
  pendingCandidateCount?: number | null
  pendingNewPostCount?: number | null
  pendingChangeCount?: number | null
  awaitingTaskCount?: number | null
}

/**
 * 能力热度聚合：按能力名称统计被多少个岗位能力项引用，降序取前 N。
 *
 * 口径来源是岗位能力表（post_ability_model），不依赖已下线的标签库统计快照 ——
 * 后者的底层数据本来就来自同一张表，多存一份只会产生第二套可能漂移的热度值。
 * 名称为空的能力项直接跳过（未归一能力不应该出现在热度榜上）。
 */
export function buildAbilityHeat(abilities: PostAbilityLike[] | null | undefined, topN = 30): WorkbenchAbilityHeat[] {
  const counter = new Map<string, number>()
  for (const ability of abilities ?? []) {
    const name = (ability?.tagName || '').trim()
    if (!name) continue
    counter.set(name, (counter.get(name) ?? 0) + 1)
  }
  return [...counter.entries()]
    .map(([name, value]) => ({ name, value }))
    // 同热度时按名称排序，保证同一份数据每次渲染顺序一致（否则词云会"跳来跳去"）
    .sort((left, right) => right.value - left.value || left.name.localeCompare(right.name))
    .slice(0, topN)
}

/**
 * 岗位能力规模：按岗位统计能力项数量与其中的核心能力数量，降序取前 N 个岗位。
 *
 * 为什么不用时间轴：岗位能力体系没有「按天产生」的时间序列，硬画一条时间折线
 * 只会永远显示空图。而「岗位能力规模」本来问的就是「哪些岗位的能力模型最厚」，
 * 按岗位横向聚合才是这个指标的真实口径，两个序列都能从岗位能力表直接算出来。
 *
 * 岗位名缺失时回落到「岗位 {id}」（而不是丢点），这样数据仍在图上，
 * 界面不会被伪造出来的空态盖住。无任何带 postId 的能力项时返回空数组，
 * 由视图层落到业务空状态（不造假点）。
 */
export function buildPostAbilityScale(
  posts: PostScalePostLike[] | null | undefined,
  abilities: PostAbilityLike[] | null | undefined,
  topN = 6,
): WorkbenchTrendPoint[] {
  const stats = new Map<number, { total: number; core: number }>()
  for (const ability of abilities ?? []) {
    // 先判空再转数字：Number(null) === 0，会让"没有岗位归属"的能力项被归到「岗位 0」上
    const rawPostId = ability?.postId
    if (rawPostId == null) continue
    const postId = Number(rawPostId)
    if (!Number.isFinite(postId)) continue
    const current = stats.get(postId) ?? { total: 0, core: 0 }
    current.total += 1
    if (ability?.isCore) current.core += 1
    stats.set(postId, current)
  }
  if (!stats.size) return []

  const nameOf = new Map<number, string>()
  for (const post of posts ?? []) {
    const rawId = post?.id
    if (rawId == null) continue
    const id = Number(rawId)
    if (!Number.isFinite(id)) continue
    const name = (post?.postName || '').trim()
    if (name) nameOf.set(id, name)
  }

  return [...stats.entries()]
    .map(([postId, value]) => ({
      label: nameOf.get(postId) ?? `岗位 ${postId}`,
      primary: value.total,
      secondary: value.core,
    }))
    // 规模大的岗位排前；同规模按名称排序，保证同一份数据每次渲染顺序一致
    .sort((left, right) => right.primary - left.primary || left.label.localeCompare(right.label))
    .slice(0, topN)
}

/** 岗位体系管理员工作台：岗位建模、全景图谱与能力巡检。 */
export function buildJobArchitectWorkbench(input: JobArchitectWorkbenchInput): RoleWorkbenchSnapshot {
  const riskyCount = input.inspection?.riskyCount ?? null
  const aiSourceCount = input.inspection?.aiSourceCount ?? null
  const abilityCount = input.inspection?.abilityCount ?? input.panorama?.stats?.abilityCount ?? null

  // 待办只放「确有未处理项」的两类巡检结果；「维护岗位能力配置 / 批量导入 JD」
  // 是常驻入口（已在 actions 里），不占待办位。
  const todos: WorkbenchTodo[] = []
  // 趋势候选待审：只有真的还有 PENDING 候选才占待办位（后端已把 APPROVED/REJECTED 排除在外），
  // 不置 urgent —— 建岗由管理员自己决定节奏，不该被「紧急」催着批量点确认。
  const pendingTrendCount = input.trendPending?.pendingCandidateCount ?? null
  if ((pendingTrendCount ?? 0) > 0) {
    const newPostCount = input.trendPending?.pendingNewPostCount ?? 0
    const changeCount = input.trendPending?.pendingChangeCount ?? 0
    todos.push({
      title: '审核岗位趋势候选',
      desc: `${pendingTrendCount} 个候选待确认（新岗位 ${newPostCount} · 能力变更 ${changeCount}）`,
      meta: '待确认',
      path: '/post/trend-discovery',
    })
  }
  if ((aiSourceCount ?? 0) > 0) {
    todos.push({
      title: '审核 AI 抽取的岗位能力',
      desc: `${aiSourceCount} 项能力来自 AI 抽取，等待确认`,
      meta: '待确认',
      path: '/ai-governance/records',
    })
  }
  if ((riskyCount ?? 0) > 0) {
    todos.push({
      title: '处理高风险能力',
      desc: `${riskyCount} 项能力存在风险标记，需人工复核`,
      meta: '需处理',
      path: '/ai-governance/records',
      urgent: true,
    })
  }

  return {
    stats: [
      { key: 'posts', label: '岗位总数', value: formatMetric(input.postTotal), hint: '岗位体系规模', iconKey: 'post', tone: 'primary', path: '/post/list' },
      { key: 'abilities', label: '岗位能力项', value: formatMetric(abilityCount), hint: '能力模型统计', iconKey: 'ability', tone: 'success', path: '/post/panorama' },
      { key: 'skills', label: '技能知识点', value: formatMetric(input.panorama?.stats?.skillPointCount ?? null), hint: '全景图谱统计', iconKey: 'learning', tone: 'info', path: '/post/panorama' },
      { key: 'risky', label: '高风险能力', value: formatMetric(riskyCount), hint: '需人工复核', iconKey: 'notice', tone: 'danger', path: '/ai-governance/records' },
      { key: 'aiSource', label: 'AI 来源能力', value: formatMetric(aiSourceCount), hint: 'AI 抽取待确认', iconKey: 'ai', tone: 'warning', path: '/ai-governance/records' },
    ],
    trend: {
      title: '岗位能力规模',
      // 岗位体系没有时间序列（能力项不是按天产生的），所以按岗位横向聚合：
      // 「规模」比「时间趋势」更贴切，也不会画出一条永远为空的时间轴。
      subtitle: '按岗位统计能力项与其中的核心能力，取能力项最多的前 6 个岗位',
      primaryName: '能力项',
      secondaryName: '核心能力',
      points: buildPostAbilityScale(input.panorama?.posts, input.panorama?.abilities),
    },
    todos,
    progresses: [
      progressOf('高风险能力占比', ratioPercent(riskyCount, abilityCount), `${riskyCount ?? 0} / ${abilityCount ?? 0} 项`, '/ai-governance/records', 'notice'),
      progressOf('AI 来源能力占比', ratioPercent(aiSourceCount, abilityCount), `${aiSourceCount ?? 0} 项待确认`, '/ai-governance/records', 'ai'),
    ],
    notices: [],
    actions: [
      { label: '岗位档案', desc: '查看岗位与市场 JD', path: '/post/list', iconKey: 'post' },
      { label: '岗位能力配置', desc: '维护岗位能力表', path: '/post/model-config', iconKey: 'ability' },
      { label: '岗位趋势发现', desc: '上传权威材料解析岗位', path: '/post/trend-discovery', iconKey: 'ai' },
      { label: '全景图谱', desc: '查看岗位能力全景', path: '/post/panorama', iconKey: 'ai' },
      { label: '岗位能力巡检', desc: '检查能力质量与风险', path: '/ai-governance/records', iconKey: 'notice' },
    ],
    subtitle: '维护岗位模型、能力配置与岗位趋势，形成岗位体系闭环',
    /*
     * 【2026-09-04】岗位工作台顶部不再放主行动按钮。
     *
     * 原先是「+ 导入 JD」→ /post/excel-import。它有两个问题：
     *   1. 岗位体系管理员的日常主线是「建模 → 配能力 → 看趋势 → 巡检」，
     *      「导入 JD」只是其中一个偶发动作，放在顶部主位会误导优先级；
     *   2. 导入 JD 的完整入口本来就在「岗位档案」页里，顶部再放一个是重复入口。
     * 置空 label 即可让 RoleWorkbench 的顶部主按钮不渲染（v-if="primaryActionLabel"）。
     *
     * path 仍保留，因为「业务进度」面板的「查看全部」用它跳转
     * （本角色两条进度都是能力质量口径，落点就是巡检页）。
     */
    primaryActionLabel: '',
    primaryActionPath: '/ai-governance/records',
    // 能力热度词云（原「能力标签治理」页的热度词云迁到这里）：
    // 岗位体系管理员需要一个「哪些能力在岗位体系里被引用最多」的全局视角，
    // 原先要专门进标签治理页才看得到，而那个页面的其余部分与岗位能力配置重复。
    abilityCloud: buildAbilityHeat(input.panorama?.abilities),
  }
}

/* ======================= 平台管理员 ======================= */
// 原「AI 配置管理员」的 buildAiConfigWorkbench 已删除（2026-09-04 角色合并）：
// 它的数据源（/api/rag/**、云端知识库状态）随角色合并归岗位体系管理员，
// 平台管理员已无权限调用，保留构建器只会留下不可达的死代码。
// 这些知识资产指标需要的话，应加到 buildJobArchitectWorkbench。

/* ========================== 平台管理员 ========================== */

export interface PlatformAdminWorkbenchInput {
  userTotal: number | null
  enabledRoles: unknown[] | null
  roleTotal: number | null
  logTotal: number | null
  logs: AuditLogLike[]
}

/** 平台管理员工作台：账号、角色与审计（合并自原「权限管理员」）。 */
export function buildPlatformAdminWorkbench(input: PlatformAdminWorkbenchInput): RoleWorkbenchSnapshot {
  const roleList = Array.isArray(input.enabledRoles) ? input.enabledRoles : []
  const roleCount = roleList.length || input.roleTotal

  return {
    stats: [
      { key: 'users', label: '用户账号', value: formatMetric(input.userTotal), hint: '系统账号总量', iconKey: 'employee', tone: 'primary', path: '/system/user' },
      { key: 'roles', label: '启用角色', value: formatMetric(roleCount), hint: '业务角色数量', iconKey: 'audit', tone: 'success', path: '/system/role' },
      { key: 'logs', label: '审计记录', value: formatMetric(input.logTotal), hint: '关键操作留痕', iconKey: 'task', tone: 'info', path: '/system/user' },
      { key: 'recent', label: '近期操作', value: formatMetric(input.logs.length || null), hint: '最近 5 条操作记录', iconKey: 'notice', tone: 'warning', path: '/system/user' },
    ],
    trend: {
      title: '审计记录趋势',
      subtitle: '按操作时间统计，暂无可聚合的时间序列',
      primaryName: '操作次数',
      secondaryName: '失败次数',
      points: [],
    },
    /*
     * 平台端没有可判定的「未处理状态」：账号、角色、审计都是查阅型数据，
     * 三条常驻事项（核对绑定 / 检查启用状态 / 查看审计）本质是入口，不是待办 ——
     * 它们已在下方 actions 里，这里保持为空，由视图层展示"当前没有待处理事项"。
     * 若后续接入真实待处理信号（如待审批的账号申请），在这里按数据条件追加即可。
     */
    todos: [],
    progresses: [
      progressOf('角色配置完整度', roleList.length ? 100 : null, `${roleList.length} 个启用角色`, '/system/role', 'audit'),
    ],
    notices: logsToNotices(input.logs),
    actions: [
      { label: '用户管理', desc: '账号状态与角色分配', path: '/system/user', iconKey: 'employee' },
      { label: '角色权限', desc: '查看权限边界与授权', path: '/system/role', iconKey: 'audit' },
      { label: '岗位数据范围', desc: '核对业务数据范围', path: '/post/list', iconKey: 'post' },
    ],
    subtitle: '管理账号、角色和审计记录，确保业务入口与数据范围清晰可控',
    primaryActionLabel: '+ 分配角色',
    primaryActionPath: '/system/user',
  }
}
