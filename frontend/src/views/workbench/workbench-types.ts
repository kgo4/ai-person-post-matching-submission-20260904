/**
 * 角色工作台统一数据契约。
 *
 * 设计约束（与 docs/superpowers/specs/2026-09-04-role-aware-workbench-design.md 一致）：
 * 1. 工作台只展示当前角色有权限、且前端确实能取到的数据；取不到就落到业务空状态，
 *    不允许用 0 或假数据占位。
 * 2. 指标 / 待办 / 进度 / 通知四类区块都由角色数据装载器产出，视图层不做业务判断。
 * 3. 员工（EMPLOYEE）的装载器只允许调用“仅本人”接口，禁止调用人员档案分页等管理端接口。
 * 4. 逻辑层不引用 Vue 组件：图标用 iconKey 字符串表达，由视图层映射为具体图标，
 *    这样纯逻辑模块可以在 node 直跑的单测里断言。
 */

/** 图标键：视图层负责映射到具体图标组件 */
export type WorkbenchIconKey =
  | 'ability'
  | 'assessment'
  | 'matching'
  | 'learning'
  | 'employee'
  | 'post'
  | 'task'
  | 'audit'
  | 'ai'
  | 'notice'
  | 'score'

/**
 * 指标环比（对应参考图 KPI 卡右下角的「较昨日 ↑12」）。
 *
 * 只有在真的能算出对比期数值时才产出本对象；算不出（缺基线数据）时字段留 undefined，
 * 视图层不渲染环比行，而不是退化成 0 或 100%。
 */
export interface WorkbenchStatDelta {
  /** up=增长 down=下降 flat=持平 */
  direction: 'up' | 'down' | 'flat'
  /** 已格式化好的展示值，例如 '12' 或 '8.2%' */
  text: string
  /** 对比期文案，例如 '较昨日'、'较上周' */
  basis: string
}

/** 指标卡：对应参考图的“项目总数 / 进行中项目 …”一行 */
export interface WorkbenchStat {
  key: string
  label: string
  /** 展示值；数据不可用时为 '--' */
  value: string
  /** 副文案，例如“来自 3 份评估报告” */
  hint: string
  iconKey: WorkbenchIconKey
  /** 视觉色调，仅影响图标底色 */
  tone?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  /** 可点击跳转 */
  path?: string
  /** 环比；无基线数据时不设置 */
  delta?: WorkbenchStatDelta
}

/** 待办任务：对应参考图右上“待办任务”面板 */
export interface WorkbenchTodo {
  title: string
  desc: string
  /** 右侧时间/状态文案，例如“今天”“进行中” */
  meta: string
  path: string
  /** 紧急标记，对应参考图的红色“紧急”标签 */
  urgent?: boolean
}

/** 进度条：对应参考图左下“项目进度”面板 */
export interface WorkbenchProgress {
  label: string
  value: number
  status?: string
  path?: string
  iconKey?: WorkbenchIconKey
}

/** 系统通知：对应参考图右下“系统通知”面板 */
export interface WorkbenchNotice {
  title: string
  desc: string
  time: string
  kind: 'info' | 'success' | 'warning'
}

/** 趋势图数据点：双折线，对应参考图“项目统计” */
export interface WorkbenchTrendPoint {
  label: string
  primary: number
  secondary: number
}

export interface WorkbenchTrend {
  title: string
  subtitle: string
  primaryName: string
  secondaryName: string
  points: WorkbenchTrendPoint[]
}

/** 快捷入口 */
export interface WorkbenchAction {
  label: string
  desc: string
  path: string
  iconKey: WorkbenchIconKey
}

/**
 * 能力热度词云数据点。
 *
 * 数据源是岗位能力表（post_ability_model）：`value` = 该能力被多少个岗位引用。
 * 这与已下线的「能力标签治理」页里的词云是同一种热度语义 —— 但那个页面的词云
 * 是从标签库的统计快照（ability_tag_usage_stat）算出来的，而标签库本身的数据
 * 就来自岗位能力表，属于同一条数据的两次落库，所以这里直接从岗位能力表聚合。
 */
export interface WorkbenchAbilityHeat {
  name: string
  value: number
}

/** 装载器产出的完整工作台快照 */
export interface WorkbenchSnapshot {
  stats: WorkbenchStat[]
  trend: WorkbenchTrend
  todos: WorkbenchTodo[]
  progresses: WorkbenchProgress[]
  notices: WorkbenchNotice[]
  actions: WorkbenchAction[]
  /** 顶部欢迎区里的角色业务定位文案 */
  subtitle: string
  /** 右上角主行动按钮文案，空字符串表示不渲染 */
  primaryActionLabel: string
  primaryActionPath: string
  /**
   * 能力热度词云；仅岗位体系管理员产出。
   * 无数据（岗位能力表为空）时留空数组，由视图层整块隐藏，不渲染空图。
   */
  abilityCloud?: WorkbenchAbilityHeat[]
}

/**
 * 角色装载器的产出。
 *
 * 【2026-09-04】原先这里是 `Omit<WorkbenchSnapshot, 'members'>`，用于把
 * 「团队成员」从各角色装载器里剥离、由 useWorkbenchData 统一合并。
 * 该区块已按需求从工作台移除（不再展示他人档案），成员数据链路一并删除，
 * 因此本别名与完整快照等价 —— 保留类型名是为了不改动 5 个角色装载器的签名。
 */
export type RoleWorkbenchSnapshot = WorkbenchSnapshot

/** 空快照工厂：所有区块为空，配合视图层的业务空状态 */
export function emptySnapshot(subtitle: string): WorkbenchSnapshot {
  return {
    stats: [],
    trend: { title: '趋势统计', subtitle: '', primaryName: '主指标', secondaryName: '次指标', points: [] },
    todos: [],
    progresses: [],
    notices: [],
    actions: [],
    subtitle,
    primaryActionLabel: '',
    primaryActionPath: '',
  }
}

/** 数值格式化：null/undefined/NaN 一律展示为 '--'，禁止把缺失数据显示成 0 */
export function formatMetric(value: number | null | undefined): string {
  if (value == null || Number.isNaN(Number(value))) return '--'
  return String(value)
}

/**
 * 计算环比。
 *
 * 前置约束：current 与 previous 必须都是有效数值才产出结果——任一缺失时返回 undefined，
 * 由视图层隐藏环比行。这是刻意的：把"没有基线数据"显示成"↑0%"会误导使用者。
 *
 * @param current  本期值
 * @param previous 对比期值
 * @param basis    对比期文案，默认「较昨日」
 * @param asPercent 是否用百分比表达（比率类指标传 true，计数类传 false）
 */
export function buildDelta(
  current: number | null | undefined,
  previous: number | null | undefined,
  basis = '较昨日',
  asPercent = false,
): WorkbenchStatDelta | undefined {
  if (current == null || previous == null) return undefined
  if (Number.isNaN(Number(current)) || Number.isNaN(Number(previous))) return undefined

  const diff = Number(current) - Number(previous)
  if (diff === 0) {
    return { direction: 'flat', text: asPercent ? '0%' : '0', basis }
  }

  const direction: WorkbenchStatDelta['direction'] = diff > 0 ? 'up' : 'down'
  if (asPercent) {
    // 基数为 0 时百分比无意义（除零），直接给不出比率 → 隐藏环比，避免出现 Infinity%
    if (Number(previous) === 0) return undefined
    const rate = Math.abs((diff / Number(previous)) * 100)
    // 不足 0.1% 的变化按 0.1% 展示，避免出现「↑0%」这种与实际不符的文案
    return { direction, text: `${rate < 0.1 ? '0.1' : rate.toFixed(1)}%`, basis }
  }
  return { direction, text: String(Math.abs(diff)), basis }
}

/**
 * 把后端返回的环比转换为视图契约。
 *
 * 后端只返回计数差值与方向，展示文案（是否带百分号、如何取整）在视图层决定，
 * 因此这里统一按计数语义格式化。后端 delta 为 null 时返回 undefined，
 * 与 {@link buildDelta} 保持同一约定：无基线就隐藏环比行。
 */
export function mapServerDelta(
  delta: { direction: 'up' | 'down' | 'flat'; diff: number; basis?: string } | null | undefined,
): WorkbenchStatDelta | undefined {
  if (!delta) return undefined
  const diff = Number(delta.diff)
  if (Number.isNaN(diff)) return undefined
  return {
    direction: delta.direction,
    text: String(Math.abs(diff)),
    basis: delta.basis || '较昨日',
  }
}

/** 从 ISO 时间取 MM-dd 轴标签 */
export function monthDayLabel(raw?: string | null): string {  if (!raw) return '--'
  const text = String(raw)
  const match = text.match(/(\d{4})-(\d{2})-(\d{2})/)
  if (match) return `${match[2]}-${match[3]}`
  return text.slice(0, 5)
}

/** 相对时间：供通知/待办右侧展示 */
export function relativeTime(raw?: string | null): string {
  if (!raw) return '--'
  const time = new Date(String(raw).replace(' ', 'T'))
  if (Number.isNaN(time.getTime())) return monthDayLabel(raw)
  const diffMs = Date.now() - time.getTime()
  const minutes = Math.floor(diffMs / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return monthDayLabel(raw)
}
