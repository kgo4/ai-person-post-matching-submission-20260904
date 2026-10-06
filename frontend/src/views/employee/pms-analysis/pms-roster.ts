/**
 * PMS 项目分析页的纯逻辑（无 Vue 依赖，便于 .test.mjs 直接跑）。
 *
 * 【为什么这个页面是独立的一等公民】
 * PMS 平台上的人**先于**本系统存在：同步过来时他们还没有本系统账号，
 * 等员工注册之后 HR 才把两边绑成一个员工。所以「已同步未绑定」是常态而不是异常，
 * 页面的第一职责就是让这个中间态可见、可处理。
 *
 * 【为什么状态判定要抽出来】
 * 「未绑定 / 已绑定未注册 / 已绑定已注册」这三种状态的用户可见文案、
 * 可执行动作（分析能不能点、导入能不能点）都不同。写散在模板里必然漂移：
 * 典型 bug 是「未绑定的人也能点导入，点完才报 400」。
 */

export type TagTone = 'primary' | 'success' | 'warning' | 'danger' | 'info'

/** PMS 分析任务状态；与后端 PmsAnalysisTask.analysisStatus 对齐 */
export const ANALYSIS_STATUS_META: Record<number, { text: string; tone: TagTone }> = {
  0: { text: '待分析', tone: 'info' },
  1: { text: '分析中', tone: 'warning' },
  // 2 是「分析已完成、能力还没写进员工画像」——文案必须说清下一步是导入，
  // 否则 HR 会以为已经完成而不再操作。
  2: { text: '待导入', tone: 'success' },
  3: { text: '失败', tone: 'danger' },
  6: { text: '已导入', tone: 'success' },
}

export function analysisStatusMeta(status: number | null | undefined): { text: string; tone: TagTone } {
  if (status === null || status === undefined) {
    return { text: '未分析', tone: 'info' }
  }
  // 未知状态码不隐藏：显示原始值，避免「后端加了新状态码，前端静默显示空白」
  return ANALYSIS_STATUS_META[status] ?? { text: `状态 ${status}`, tone: 'info' }
}

export type BindingState = 'unbound' | 'bound-without-account' | 'bound-with-account'

export interface RosterBindingLike {
  bound: boolean
  empId: number | null
  empName?: string | null
  empCode?: string | null
  empHasAccount?: boolean | null
}

export function rosterBindingState(item: RosterBindingLike): BindingState {
  if (!item.bound || item.empId === null || item.empId === undefined) {
    return 'unbound'
  }
  return item.empHasAccount ? 'bound-with-account' : 'bound-without-account'
}

export function rosterBindingMeta(item: RosterBindingLike): { state: BindingState; text: string; tone: TagTone } {
  const state = rosterBindingState(item)
  if (state === 'unbound') {
    return { state, text: '未绑定', tone: 'warning' }
  }
  if (state === 'bound-without-account') {
    // 已绑定但对方还没注册账号：能力可以导入画像，但员工自己还看不到，
    // 所以文案要提示「待注册」，不能用「已完成」这种收尾语气。
    return { state, text: '已绑定 · 待注册', tone: 'info' }
  }
  return { state, text: '已绑定', tone: 'success' }
}

/** 员工侧显示名：姓名缺失时退回工号，再退回 ID —— 绝不渲染 "undefined" */
export function employeeDisplayName(item: {
  empName?: string | null
  empCode?: string | null
  empId?: number | null
}): string {
  const name = item.empName?.trim()
  if (name) return name
  const code = item.empCode?.trim()
  if (code) return code
  return item.empId ? `员工#${item.empId}` : '—'
}

/** PMS 侧显示名：昵称 → 用户名 → PMS#id */
export function pmsDisplayName(item: {
  pmsNickname?: string | null
  pmsUsername?: string | null
  pmsUserId: number
}): string {
  const nickname = item.pmsNickname?.trim()
  if (nickname) return nickname
  const username = item.pmsUsername?.trim()
  if (username) return username
  return `PMS#${item.pmsUserId}`
}

/**
 * 导入能力是否可用：这是整条链路里唯一需要绑定关系的动作。
 *
 * 【反面提醒】分析按钮**不要**用 :disabled="!row.bound"。
 * 口径是「能分析，导入才要求绑定」——分析只依赖 PMS 数据，
 * 未绑定的 PMS 人员也应该能先跑一遍看结果，再决定绑给谁。
 */
export function canImportAbilities(item: RosterBindingLike): boolean {
  return rosterBindingState(item) !== 'unbound'
}

/**
 * 绑定弹窗里需要置灰的本地员工。
 *
 * `boundEmpIds` 是全量已占用员工，但**当前这一行自己已绑的员工必须排除**，
 * 否则 HR 打开一个已绑定人员的弹窗时会看到自己当前的绑定项是灰的 —— 无法确认、也无法改绑。
 */
export function selectableConflictEmpIds(boundEmpIds: readonly number[], currentEmpId: number | null | undefined): number[] {
  return boundEmpIds.filter(id => id !== currentEmpId)
}

export interface ImportActionState {
  disabled: boolean
  hint: string
}

/**
 * 「写入人员能力画像」按钮的状态与提示文案。
 *
 * 为什么要把 hint 和 disabled 绑在同一个返回值里：只给 disabled 就会**静默禁用** ——
 * HR 看到灰按钮却不知道差什么（未绑定？没分析成功？一项没选？），只能靠猜。
 * 这里保证「按钮什么时候不能点」和「为什么不能点」永远同一份逻辑产出。
 */
export function importActionState(params: {
  item: RosterBindingLike | null | undefined
  analysisStatus: number | null | undefined
  selectedCount: number
  abilityCount: number
}): ImportActionState {
  const { item, analysisStatus, selectedCount, abilityCount } = params
  if (!item) return { disabled: true, hint: '请先选择一个 PMS 人员' }
  if (!canImportAbilities(item)) return { disabled: true, hint: '绑定到本系统员工后才能写入人员能力画像' }
  if (analysisStatus === 6) {
    return { disabled: true, hint: '该次分析的能力已写入过画像；如需重新导入请再跑一次分析' }
  }
  if (analysisStatus !== 2) return { disabled: true, hint: '只有分析成功的任务才能写入能力画像' }
  if (selectedCount === 0) {
    return { disabled: true, hint: `已选 0 / ${abilityCount} 项，请至少选择一项` }
  }
  return { disabled: false, hint: `已选 ${selectedCount} / ${abilityCount} 项` }
}

/**
 * 绑定弹窗内无法提交的原因（返回 null 表示可以提交）。
 * 前端先拦一道是为了「即时可见」，真正的唯一约束仍由后端 409 收口。
 */
export function bindBlockReason(params: {
  pmsUserId: number | null | undefined
  empId: number | null | undefined
  conflictEmpIds: readonly number[]
}): string | null {
  const { pmsUserId, empId, conflictEmpIds } = params
  if (!pmsUserId) return '未选中 PMS 人员'
  if (!empId) return '请选择要绑定的员工'
  if (conflictEmpIds.includes(empId)) return '该员工已绑定其他 PMS 人员，请先解绑'
  return null
}

/** 同步结果文案。旧口径的「自动创建员工 N 人」已随实现消失，不能再出现。 */
export function summarizeSyncResult(result: {
  newSynced: number
  totalPmsUsers: number
  alreadySynced: number
  autoBound: number
}): string {
  const parts = [`PMS 人员 ${result.totalPmsUsers} 人`, `本次新同步 ${result.newSynced} 人`]
  if (result.alreadySynced > 0) parts.push(`此前已同步 ${result.alreadySynced} 人`)
  if (result.autoBound > 0) parts.push(`按工号自动绑定 ${result.autoBound} 人`)
  return parts.join('，')
}

/** 空列表的诊断文案：宁可显示原因，也不要一个空表格 */
export function emptyRosterReason(message: string | null | undefined): string {
  const text = message?.trim()
  if (text) return text
  // 有 items 却走到这里说明调用方用错了，兜底也不能是空白
  return 'PMS 平台当前没有可显示的人员。'
}

export interface RosterFilterLike {
  pmsNickname?: string | null
  pmsUsername?: string | null
  pmsEmployeeId?: string | null
  pmsRole?: string | null
  empName?: string | null
  empCode?: string | null
}

/** 关键字过滤：同时匹配 PMS 侧身份与本系统绑定人身份，大小写不敏感 */
export function rosterMatchesKeyword(item: RosterFilterLike, keyword: string): boolean {
  const needle = keyword.trim().toLowerCase()
  if (!needle) return true
  const haystack = [
    item.pmsNickname,
    item.pmsUsername,
    item.pmsEmployeeId,
    item.pmsRole,
    item.empName,
    item.empCode,
  ]
    .filter((value): value is string => typeof value === 'string')
    .join(' ')
    .toLowerCase()
  return haystack.includes(needle)
}
