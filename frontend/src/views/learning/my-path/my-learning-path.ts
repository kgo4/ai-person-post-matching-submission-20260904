/**
 * 员工侧「我的学习路径」纯逻辑。
 *
 * 与 `components/learning/types.ts` 的分工：
 * - 那边是**状态码 → 中文/配色**的唯一来源（`getStatusMeta` 等），这里**不重复一份**；
 * - 这边只管「选哪个目标岗位、能不能申请、HR 会看到什么、点差距怎么定位」这类**决策逻辑**。
 *
 * 刻意不 import 任何 `@/` 别名模块（`components/learning/types.ts` 引了 `@/api`），
 * 只用结构类型描述入参，保证本文件能在 node（`--experimental-strip-types`）下直跑测试。
 *
 * 两条设计不变式（改这个文件前先看这两句）：
 * 1. **计划与差距同源**：右栏的差距行一律从左栏的 steps 派生，不另取一份数据 ——
 *    否则两栏会出现「左边说学完了、右边还说差 2 级」这种自相矛盾。
 * 2. **申请随时可提交，但必须如实告知 HR 会看到什么**：不通过评估题也能申请，
 *    但提示语必须点出「评估题未通过」，不能让人误以为交上去就是完整证据。
 */

/** 目标岗位候选（来自「本人已发布的人岗匹配记录」） */
export interface TargetCandidateLike {
  matchingRecordId: number
  postId?: number | null
  postName?: string | null
  matchScore?: number | null
  createdTime?: string | null
}

/** 归一化后的目标岗位 */
export interface TargetCandidate {
  matchingRecordId: number
  postId: number | null
  postName: string
  matchScore: number | null
  createdTime: string | null
}

const UNNAMED_POST = '未命名岗位'

/**
 * 从本人的匹配记录里整理出可选的目标岗位。
 *
 * - 岗位名为空时给占位名（不能渲染成空白，否则下拉里是一排空行）；
 * - **不**过滤没有岗位名的记录：岗位名缺失是数据问题，静默丢掉会让员工
 *   「明明有匹配记录却选不到目标」而无从排查；
 * - 按 matchingRecordId 倒序（新的在前）—— 匹配记录 id 递增，比解析时间字符串可靠。
 */
export function buildTargetCandidates(records?: TargetCandidateLike[] | null): TargetCandidate[] {
  const seen = new Set<number>()
  const out: TargetCandidate[] = []
  for (const record of records ?? []) {
    if (!record || record.matchingRecordId == null || seen.has(record.matchingRecordId)) {
      continue
    }
    seen.add(record.matchingRecordId)
    const name = (record.postName ?? '').trim()
    out.push({
      matchingRecordId: record.matchingRecordId,
      postId: record.postId ?? null,
      postName: name || UNNAMED_POST,
      matchScore: typeof record.matchScore === 'number' ? record.matchScore : null,
      createdTime: record.createdTime ?? null,
    })
  }
  return out.sort((a, b) => b.matchingRecordId - a.matchingRecordId)
}

/**
 * 选默认目标岗位。
 *
 * 优先级：**已有学习计划的岗位 > 分数最高 > 最新一条**。
 * 「已有计划的」排第一是因为：员工上次学到一半，再进来应当接着那条继续，
 * 而不是被重置成另一个岗位的空白路径。
 */
export function pickDefaultTarget(
  candidates?: TargetCandidate[] | null,
  plannedRecordIds?: number[] | null,
): number | null {
  const list = candidates ?? []
  if (list.length === 0) {
    return null
  }
  const planned = new Set(plannedRecordIds ?? [])
  const withPlan = list.filter((item) => planned.has(item.matchingRecordId))
  if (withPlan.length > 0) {
    return withPlan[0].matchingRecordId
  }
  let best = list[0]
  for (const item of list) {
    if ((item.matchScore ?? -1) > (best.matchScore ?? -1)) {
      best = item
    }
  }
  return best.matchingRecordId
}

/** 差距行 —— 由步骤派生，保证与左栏同源 */
export interface GapRow {
  stepId: number
  abilityName: string
  currentLevel: number | null
  targetLevel: number | null
  gapLevels: number | null
  priority: string
  gapType: string
  status: string
}

export interface StepLike {
  id: number
  abilityName?: string | null
  currentLevel?: number | null
  targetLevel?: number | null
  priority?: string | null
  gapType?: string | null
  status?: string | null
}

/**
 * 把步骤派生成右栏的差距行。
 *
 * 排序：差距大的在前（差距级数降序），同级按高优先级在前 —— 员工第一眼该看到最该补的。
 * 未知 priority 一律按最低处理，不会因为后端新增枚举值就把排序打乱。
 */
export function buildGapRows(steps?: StepLike[] | null): GapRow[] {
  const rows: GapRow[] = []
  for (const step of steps ?? []) {
    if (!step || step.id == null) {
      continue
    }
    const current = typeof step.currentLevel === 'number' ? step.currentLevel : null
    const target = typeof step.targetLevel === 'number' ? step.targetLevel : null
    rows.push({
      stepId: step.id,
      abilityName: (step.abilityName ?? '').trim() || '未命名能力',
      currentLevel: current,
      targetLevel: target,
      gapLevels: current !== null && target !== null ? Math.max(target - current, 0) : null,
      priority: step.priority ?? '',
      gapType: step.gapType ?? '',
      status: step.status ?? '',
    })
  }
  return rows.sort((a, b) => {
    const gapDiff = (b.gapLevels ?? -1) - (a.gapLevels ?? -1)
    if (gapDiff !== 0) return gapDiff
    return priorityWeight(b.priority) - priorityWeight(a.priority)
  })
}

/** 优先级权重：未知值按最低，避免新增枚举把排序打乱 */
function priorityWeight(priority?: string | null): number {
  switch ((priority ?? '').toUpperCase()) {
    case 'HIGH':
      return 2
    case 'MEDIUM':
      return 1
    default:
      return 0
  }
}

/**
 * 点右侧差距 → 左侧步骤的定位：
 * 返回该步骤在列表中的下标；找不到返回 -1（调用方据此提示「该差距暂无对应步骤」而不是静默不动）。
 */
export function locateStepIndex(steps?: StepLike[] | null, stepId?: number | null): number {
  if (stepId == null) {
    return -1
  }
  const list = steps ?? []
  for (let i = 0; i < list.length; i++) {
    if (list[i]?.id === stepId) {
      return i
    }
  }
  return -1
}

/** 学习记录（用于申请前的如实告知，也用于 HR 复核时的证据展示） */
export interface LearningRecordInput {
  /** 该能力对应步骤是否已学完 */
  stepCompleted?: boolean
  /** 评估题结论：PASSED / NOT_PASSED / PENDING / 空 */
  assessmentStatus?: string | null
  /** 评估题得分（0-100） */
  assessmentScore?: number | null
  /** 已完成的资源数 */
  completedResourceCount?: number | null
  /** 该能力推荐资源总数 */
  resourceCount?: number | null
}

export interface ImprovementEligibility {
  /** 是否允许提交。按需求「随时可申请」，恒为 true */
  allowed: boolean
  /** 是否已具备完整学习记录（步骤学完 + 评估题通过） */
  complete: boolean
  /** 给员工看的一句话，说明当前提交会附带什么记录 */
  hint: string
}

const ASSESSMENT_PASSED = 'PASSED'
const ASSESSMENT_NOT_PASSED = 'NOT_PASSED'

/**
 * 「能力提升申请」的资格与告知。
 *
 * 需求明确：**随时可以申请**，所以 `allowed` 恒为 true；
 * 但每种学习状态必须给出不同提示，让员工知道 HR 会看到什么 ——
 * 统一写成「可提交」会让人误以为交上去就是完整证据，被驳回时觉得莫名其妙。
 */
export function resolveImprovementEligibility(input?: LearningRecordInput | null): ImprovementEligibility {
  const data = input ?? {}
  const stepCompleted = data.stepCompleted === true
  const status = (data.assessmentStatus ?? '').trim().toUpperCase()
  const assessmentPassed = status === ASSESSMENT_PASSED
  const complete = stepCompleted && assessmentPassed

  if (complete) {
    return {
      allowed: true,
      complete: true,
      hint: '学习记录完整（步骤已学完、评估题已通过），提交后 HR 复核通过即可更新能力等级。',
    }
  }
  if (!stepCompleted && status === ASSESSMENT_NOT_PASSED) {
    return {
      allowed: true,
      complete: false,
      hint: '仍可提交，但 HR 会看到：步骤尚未学完，且评估题未通过。建议先补完再提交。',
    }
  }
  if (!stepCompleted) {
    return {
      allowed: true,
      complete: false,
      hint: '仍可提交，但 HR 会看到：对应学习步骤尚未学完。',
    }
  }
  if (status === ASSESSMENT_NOT_PASSED) {
    return {
      allowed: true,
      complete: false,
      hint: '仍可提交，但 HR 会看到：评估题未通过。可以再学一遍这一条后重新作答。',
    }
  }
  return {
    allowed: true,
    complete: false,
    hint: '仍可提交，但 HR 会看到：评估题尚未完成。',
  }
}

/**
 * 学习记录摘要 —— 一句话 + 明细项。
 *
 * 用途有两个，且**必须用同一份实现**：
 * ① 员工提交前确认「我这次附带了什么」；
 * ② HR 复核时看「这个人到底学了多少」。
 * 两边各写一份必然漂移（历史教训：状态码文案前后端各存一份，导致结果对不上）。
 */
export function summarizeLearningRecord(input?: LearningRecordInput | null): {
  text: string
  items: string[]
} {
  const data = input ?? {}
  const items: string[] = []

  items.push(data.stepCompleted === true ? '学习步骤：已学完' : '学习步骤：未学完')

  const status = (data.assessmentStatus ?? '').trim().toUpperCase()
  if (status === ASSESSMENT_PASSED) {
    const score = typeof data.assessmentScore === 'number' ? `（${Math.round(data.assessmentScore)} 分）` : ''
    items.push(`评估题：已通过${score}`)
  } else if (status === ASSESSMENT_NOT_PASSED) {
    const score = typeof data.assessmentScore === 'number' ? `（${Math.round(data.assessmentScore)} 分）` : ''
    items.push(`评估题：未通过${score}`)
  } else if (status === 'PENDING') {
    items.push('评估题：已完成待判分')
  } else {
    items.push('评估题：未作答')
  }

  if (typeof data.resourceCount === 'number' && data.resourceCount > 0) {
    const done = typeof data.completedResourceCount === 'number' ? data.completedResourceCount : 0
    items.push(`学习资源：已学 ${Math.max(Math.min(done, data.resourceCount), 0)} / ${data.resourceCount} 个`)
  }

  const complete = data.stepCompleted === true && status === ASSESSMENT_PASSED
  return {
    text: complete ? '学习记录完整' : '学习记录不完整（HR 会看到明细）',
    items,
  }
}

/** 空态文案：员工没有任何可用目标岗位时，必须说清「为什么」而不是只显示空白 */
export function emptyTargetHint(hasAnyRecord: boolean): string {
  return hasAnyRecord
    ? '已有匹配记录，但都属于未发布状态，暂不能作为学习目标。'
    : '还没有人岗匹配结果，无法生成学习路径。请联系 HR 先完成一次人岗匹配。'
}

/** 步骤列表为空时的说明（区分「还没生成计划」与「计划里没有步骤」） */
export function emptyPlanHint(hasPlan: boolean): string {
  return hasPlan
    ? '该计划暂无学习步骤，可联系 HR 重新生成。'
    : '该目标岗位还没有学习计划，点击「生成学习计划」开始。'
}
