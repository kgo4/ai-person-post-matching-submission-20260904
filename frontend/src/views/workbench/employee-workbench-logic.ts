/**
 * 员工（EMPLOYEE）工作台纯逻辑。
 *
 * 只做「原始接口数据 → 工作台快照」的映射，不发起请求、不引用 Vue，
 * 便于在 node 直跑的单测里覆盖有数据 / 空数据 / 流程失败三种情况。
 *
 * 业务口径：
 * - 员工的数据范围是“仅本人”，本模块产出的所有入口都落在本人页面上；
 * - 能力、匹配结果、学习计划的可见性完全依赖后端（匹配结果仅 HR 发布后可见），
 *   前端不做二次放大，也不伪造数据。
 */
// 显式带上 .ts 后缀：该模块会被 node --experimental-strip-types 直跑的单测导入，
// 裸扩展名在 node ESM 下无法解析（Vite/vue-tsc 两种写法都支持）。
import { formatMetric, monthDayLabel, relativeTime, type RoleWorkbenchSnapshot, type WorkbenchTodo } from './workbench-types.ts'

/** 评估流程五阶段顺序（与后端 work_flow_status 枚举对齐） */
export const STAGE_ORDER = ['RESUME', 'TEST', 'INTERVIEW', 'AGGREGATE', 'CONFIRM'] as const

/** 工作流状态 → 阶段下标；未知状态返回 null，交由调用方按“未开始”处理 */
export function assessmentStageIndexOf(status?: string | null): number | null {
  switch (status) {
    case 'RESUME_REQUIRED':
    case 'RESUME_PARSING':
    case 'RESUME_EVIDENCE_READY':
      return 0
    case 'TEST_GENERATING':
    case 'TEST_IN_PROGRESS':
    case 'TEST_EVALUATING':
    case 'TEST_EVIDENCE_READY':
      return 1
    case 'INTERVIEW_PREPARING':
    case 'INTERVIEW_IN_PROGRESS':
    case 'INTERVIEW_ANALYZING':
      return 2
    case 'AGGREGATE_HARNESS_RUNNING':
      return 3
    case 'LEVEL_CONFIRMING':
    case 'COMPLETED':
    case 'REVIEW_REQUIRED':
      return 4
    default:
      return null
  }
}

/** 评估流程完成百分比；未开始时为 0 */
export function assessmentStagePercent(status?: string | null): number {
  const index = assessmentStageIndexOf(status)
  if (index == null) return 0
  if (status === 'COMPLETED') return 100
  return Math.round(((index + 1) / STAGE_ORDER.length) * 100)
}

/** 工作流状态是否为“流程中断，需要人处理” */
export function isWorkflowBlocked(status?: string | null): boolean {
  return status === 'FAILED' || status === 'RECOVERY_REQUIRED' || status === 'REVIEW_REQUIRED'
}

export interface EmployeeReportLike {
  workflowId: number
  overallScore?: number | null
  postMatchScore?: number | null
  startedAt?: string | null
  completedAt?: string | null
  reportStatus?: string | null
}

export interface EmployeeMatchingRecordLike {
  aiMatchScore?: number | null
  finalMatchScore?: number | null
  createdTime?: string | null
  postName?: string | null
  matchStatus?: number
}

export interface EmployeePlanLike {
  planTitle?: string | null
  planStatus?: string | null
  totalStepCount?: number | null
  completedStepCount?: number | null
  updatedTime?: string | null
}

export interface EmployeeWorkflowLike {
  status?: string | null
  displayStatus?: string | null
  nextStepHint?: string | null
  availableActions?: string[] | null
  failedReason?: string | null
}

export interface EmployeeWorkbenchInput {
  /** 是否已把登录账号关联到人员档案（未关联时全部入口不可用） */
  hasEmployeeIdentity: boolean
  empId: number | null
  /** 已确立能力项数量；接口失败时传 null */
  abilityCount: number | null
  /** 能力画像综合得分 */
  abilityOverallScore: number | null
  confirmedCount: number | null
  provisionalCount: number | null
  workflow: EmployeeWorkflowLike | null
  reports: EmployeeReportLike[]
  matchingTotal: number | null
  matchingRecent: EmployeeMatchingRecordLike[]
  plans: EmployeePlanLike[]
}

const EMPLOYEE_SUBTITLE = '完成能力评估、查看匹配结果，并按能力差距推进学习计划'

/** 员工可见页面入口；empId 存在时带上，便于管理端代看同一页面 */
export function employeePaths(empId: number | null) {
  const suffix = empId != null ? `?empId=${empId}` : ''
  const join = (base: string) => (suffix ? `${base}${suffix}` : base)
  return {
    profile: '/employee/ability-profile',
    assessment: join('/employee/ability-profile/assessment'),
    resume: join('/employee/ability-profile/resume-parse'),
    result: '/matching/my-result',
    gap: '/matching/gap-diagnosis',
    learning: '/learning/path',
  }
}

function maxScore(records: EmployeeMatchingRecordLike[]): number | null {
  const scores = records
    .map(record => Number(record.finalMatchScore ?? record.aiMatchScore))
    .filter(score => Number.isFinite(score))
  if (!scores.length) return null
  return Math.round(Math.max(...scores) * 10) / 10
}

/** 报告按开始时间升序，供趋势图从左到右展示 */
function ascendingReports(reports: EmployeeReportLike[]): EmployeeReportLike[] {
  return [...reports].sort((a, b) => {
    const left = String(a.completedAt ?? a.startedAt ?? '')
    const right = String(b.completedAt ?? b.startedAt ?? '')
    return left.localeCompare(right)
  })
}

/** 学习计划汇总完成度 */
function planCompletion(plans: EmployeePlanLike[]): { done: number; total: number; percent: number | null } {
  const done = plans.reduce((sum, plan) => sum + Number(plan.completedStepCount ?? 0), 0)
  const total = plans.reduce((sum, plan) => sum + Number(plan.totalStepCount ?? 0), 0)
  return { done, total, percent: total > 0 ? Math.round((done / total) * 100) : null }
}

export function buildEmployeeWorkbench(input: EmployeeWorkbenchInput): RoleWorkbenchSnapshot {
  const paths = employeePaths(input.empId)
  const stagePercent = assessmentStagePercent(input.workflow?.status)
  const blocked = isWorkflowBlocked(input.workflow?.status)
  const workflowDone = input.workflow?.status === 'COMPLETED'
  const reportsAsc = ascendingReports(input.reports)
  const latestReport = reportsAsc[reportsAsc.length - 1]
  const topScore = maxScore(input.matchingRecent)
  const completion = planCompletion(input.plans)
  const abilityTotal = input.abilityCount

  const stats: RoleWorkbenchSnapshot['stats'] = [
    {
      key: 'ability',
      label: '已确立能力',
      value: formatMetric(abilityTotal),
      hint: input.abilityOverallScore == null ? '尚未生成能力画像' : `综合得分 ${Math.round(input.abilityOverallScore)}`,
      iconKey: 'ability',
      tone: 'primary',
      path: paths.profile,
    },
    {
      key: 'reports',
      label: '评估报告',
      value: formatMetric(input.reports.length),
      hint: latestReport ? `最近 ${monthDayLabel(latestReport.completedAt ?? latestReport.startedAt)}` : '暂无评估报告',
      iconKey: 'assessment',
      tone: 'success',
      path: paths.assessment,
    },
    {
      key: 'matching',
      label: '匹配结果',
      value: formatMetric(input.matchingTotal),
      hint: 'HR 确认后可见的匹配结果',
      iconKey: 'matching',
      tone: 'primary',
      path: paths.result,
    },
    {
      key: 'topScore',
      label: '最高匹配分',
      value: topScore == null ? '--' : topScore.toFixed(1),
      hint: input.matchingRecent.length ? `近 ${input.matchingRecent.length} 条匹配记录` : '暂无匹配记录',
      iconKey: 'score',
      tone: 'danger',
      path: paths.result,
    },
  ]

  /*
   * 趋势优先用评估报告（本人评估链路最完整）；没有可画分数的报告时退回匹配记录。
   *
   * 关键：**只画已经下发了分数的报告**。综合评分属于评估结论，后端在
   * 「HR 未把全部能力项审核完」时会把 overallScore/postMatchScore 置 null
   * （见 CapabilityAssessmentFacadeImpl.listAssessmentReports）。
   * 若沿用 `Number(x ?? 0)`，这些"还没有分"的评估会被画成一条 **0 分**的曲线 ——
   * 既是错误信息，又正好把「审核中」误报成「考了 0 分」。所以先过滤再决定走哪条分支。
   */
  const scoredReports = reportsAsc.filter(
    report => report.overallScore != null || report.postMatchScore != null,
  )
  const trend = scoredReports.length
    ? {
        title: '能力评估趋势',
        subtitle: '最近几次评估的得分变化',
        primaryName: '评估总分',
        secondaryName: '岗位匹配分',
        points: scoredReports.slice(-8).map(report => ({
          label: monthDayLabel(report.completedAt ?? report.startedAt),
          primary: Number(report.overallScore ?? 0),
          secondary: Number(report.postMatchScore ?? 0),
        })),
      }
    : {
        title: '匹配结果趋势',
        subtitle: '匹配记录的分数变化',
        primaryName: 'AI 匹配分',
        secondaryName: '最终分',
        points: [...input.matchingRecent]
          .reverse()
          .slice(-8)
          .map(record => ({
            label: monthDayLabel(record.createdTime),
            primary: Number(record.aiMatchScore ?? 0),
            secondary: Number(record.finalMatchScore ?? record.aiMatchScore ?? 0),
          })),
      }

  /*
   * 待办 = 「卡在本人身上、必须由本人推进」的事项，判据必须是真实状态。
   *
   * 这里刻意不再收录「查看匹配结果 / 查看能力差距诊断 / 生成学习路径」这类条目：
   * 它们是页面入口或建议，不是待办 —— 把它们塞进待办面板会让面板看起来是假的
   * （用户反馈：没有匹配结果的人也会被提示"该去学习了"）。
   * 这些入口已经由下方 actions（快捷入口）承载，不需要在待办里再出现一次。
   */
  const todos: WorkbenchTodo[] = []

  if (!input.hasEmployeeIdentity) {
    todos.push({
      title: '账号尚未绑定人员档案',
      desc: '评估、匹配结果与学习计划都需要人员档案，请联系 HR 或权限管理员完成绑定',
      meta: '待处理',
      path: '',
      urgent: true,
    })
  } else if (!input.workflow) {
    todos.push({
      title: '开始能力评估',
      desc: '上传简历 → AI 能力测试 → AI 面试，三步生成能力画像',
      meta: '未开始',
      path: paths.assessment,
    })
  } else if (!workflowDone) {
    todos.push({
      title: blocked ? '处理中断的评估流程' : '继续能力评估',
      desc: [input.workflow.displayStatus, input.workflow.nextStepHint].filter(Boolean).join(' · ') || '评估流程进行中',
      meta: blocked ? '需处理' : '进行中',
      path: paths.assessment,
      urgent: blocked,
    })
  }

  // 学习类待办只在「已经存在未完成的学习计划」时出现 —— 计划是本人未做完的事，
  // 而"还没有学习计划"不是待办，是入口。
  const activePlan = input.plans.find(plan => {
    const total = Number(plan.totalStepCount ?? 0)
    const done = Number(plan.completedStepCount ?? 0)
    return total > 0 && done < total
  })
  if (activePlan) {
    todos.push({
      title: `继续学习：${activePlan.planTitle || '学习计划'}`,
      desc: `已完成 ${Number(activePlan.completedStepCount ?? 0)}/${Number(activePlan.totalStepCount ?? 0)} 个学习步骤`,
      meta: '学习中',
      path: paths.learning,
    })
  }

  const progresses: RoleWorkbenchSnapshot['progresses'] = []
  if (input.workflow) {
    progresses.push({
      label: '能力评估流程',
      value: stagePercent,
      status: input.workflow.displayStatus || input.workflow.status || '',
      path: paths.assessment,
      iconKey: 'assessment',
    })
  }
  if (completion.percent != null) {
    progresses.push({
      label: '学习计划完成度',
      value: completion.percent,
      status: `${completion.done}/${completion.total} 步`,
      path: paths.learning,
      iconKey: 'learning',
    })
  }
  const notices: RoleWorkbenchSnapshot['notices'] = []
  if (blocked && input.workflow?.failedReason) {
    notices.push({
      title: '评估流程需要处理',
      desc: input.workflow.failedReason,
      time: '待处理',
      kind: 'warning',
    })
  }
  if (latestReport) {
    if (latestReport.reportStatus === 'READY') {
      notices.push({
        title: '评估报告已生成',
        desc: `系统已完成 ${monthDayLabel(latestReport.completedAt ?? latestReport.startedAt)} 的评估报告`,
        time: relativeTime(latestReport.completedAt ?? latestReport.startedAt),
        kind: 'success',
      })
    } else if (latestReport.reportStatus === 'FAILED') {
      notices.push({
        title: '评估报告生成失败',
        desc: '可在能力评估页重新生成报告或联系 HR 处理',
        time: relativeTime(latestReport.completedAt ?? latestReport.startedAt),
        kind: 'warning',
      })
    }
  }
  const latestRecord = input.matchingRecent[0]
  if (latestRecord) {
    notices.push({
      title: `新增匹配结果${latestRecord.postName ? `：${latestRecord.postName}` : ''}`,
      desc: `最终匹配分 ${Number(latestRecord.finalMatchScore ?? latestRecord.aiMatchScore ?? 0).toFixed(1)}`,
      time: relativeTime(latestRecord.createdTime),
      kind: 'info',
    })
  }

  const primaryActionLabel = !input.hasEmployeeIdentity
    ? ''
    : !input.workflow || workflowDone
      ? '+ 开始能力评估'
      : '+ 继续能力评估'

  return {
    stats,
    trend,
    todos,
    progresses,
    notices: notices.slice(0, 4),
    actions: [
      { label: '能力画像', desc: '查看本人已确立能力', path: paths.profile, iconKey: 'ability' },
      { label: workflowDone ? '查看评估报告' : '能力评估', desc: '简历、AI 测试与 AI 面试', path: paths.assessment, iconKey: 'assessment' },
      { label: '匹配结果', desc: '查看本人匹配结果与得分', path: paths.result, iconKey: 'matching' },
      { label: '学习路径', desc: '围绕能力差距安排学习', path: paths.learning, iconKey: 'learning' },
    ],
    subtitle: EMPLOYEE_SUBTITLE,
    primaryActionLabel,
    primaryActionPath: paths.assessment,
  }
}
