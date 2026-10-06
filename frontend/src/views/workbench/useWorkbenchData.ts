/**
 * 角色工作台数据装载。
 *
 * 每个角色只调用自己有权限的接口；单个接口失败不影响整页（逐个兜底），
 * 全部区块都为空时由视图层展示“加载失败 + 重试”，而不是假装“没有数据”。
 *
 * 员工角色的调用清单被刻意限制为“仅本人”接口：
 *   /employee/me（身份）、能力画像、能力清单、评估工作流、评估报告、
 *   匹配结果汇总（后端按 token 收口，仅返回本人可见的匹配结果）、本人学习计划。
 * 它不会调用 /employee/page、/employee/stats 这类管理端接口。
 */
import { computed, ref } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { getMatchingDashboardSummary, pageEmployees, pageLearningPaths, pageMatchingTasks, pagePosts } from '@/api'
import { getAbilityProfile, getMyEmployee, listAbilities } from '@/api/employee'
import { getEmployeeStats } from '@/api/employee'
import { getPendingLearningOutcomeCount, pageLearningOutcomeReviews } from '@/api/learning-outcome'
import { getWorkbenchMetrics, type WorkbenchMetricsVO } from '@/api/workbench'
import { getPostInspectionSummary } from '@/api/post-ability-inspection'
import { getPostPanoramaOverview } from '@/api/post-panorama'
import { SUPER_ADMIN_ROLE, isSuperAdmin } from '@/utils/super-admin'
import { getTrendPendingSummary } from '@/api/post-trend'
import { listEnabledRoles, pageLogs, pageRoles, pageUsers } from '@/api/system'
import { getActiveWorkflow, getAssessmentProfile, listAssessmentReports } from '@/api/assessment'
import type { EmpAbility } from '@/api/employee/types'
import type { AssessmentReportListItem } from '@/api/assessment'
import {
  buildEmployeeWorkbench,
  employeePaths,
  type EmployeeMatchingRecordLike,
  type EmployeePlanLike,
  type EmployeeReportLike,
  type EmployeeWorkflowLike,
} from './employee-workbench-logic'
import {
  buildHrWorkbench,
  buildJobArchitectWorkbench,
  buildPlatformAdminWorkbench,
  recordsOf,
  totalOf,
  type AuditLogLike,
  type HrWorkbenchInput,
  type MatchingTaskLike,
} from './management-workbench-logic'
import { emptySnapshot, mapServerDelta, type RoleWorkbenchSnapshot, type WorkbenchSnapshot } from './workbench-types'

/** 接口兜底：失败时返回 null，由调用方按业务空状态处理 */
async function pick<T>(request: Promise<{ data: T }>): Promise<T | null> {
  try {
    const res = await request
    return (res?.data ?? null) as T | null
  } catch {
    return null
  }
}

/**
 * 把工作台指标（KPI 环比）合并进角色快照。
 *
 * 采用"按 key 就地补字段"的方式，而不是让 5 个角色装载器各自处理：
 * 环比对所有角色语义一致，集中在一处避免重复分支。
 * 指标接口失败时保持不变（环比缺失即不渲染），不影响角色快照本身的业务内容。
 *
 * 【2026-09-04】原先此处还负责把「团队成员」合并进快照。
 * 该区块已从工作台移除（工作台不展示他人档案），成员数据链路一并删除。
 */
function mergeMetrics(snapshot: RoleWorkbenchSnapshot, metrics: WorkbenchMetricsVO | null): WorkbenchSnapshot {
  if (!metrics) return snapshot
  const deltaByKey = new Map(metrics.cards.map(card => [card.key, card.delta]))
  return {
    ...snapshot,
    stats: snapshot.stats.map(stat => {
      const delta = mapServerDelta(deltaByKey.get(stat.key) ?? null)
      return delta ? { ...stat, delta } : stat
    }),
  }
}

export function useWorkbenchData() {
  const userStore = useUserStore()
  const role = computed(() => (userStore.roles[0] || 'EMPLOYEE').toUpperCase())
  /**
   * 数据装载用的「有效角色」。
   *
   * 超级管理员不看 roles[0] 的位置（多角色账号首位未必是它），一律按平台管理员装载；
   * `role` 本身仍保留首位角色，用于展示（侧边栏/工作台的角标文案）。
   */
  const effectiveRole = computed(() => (isSuperAdmin(userStore.roles) ? SUPER_ADMIN_ROLE : role.value))
  const loading = ref(false)
  const error = ref('')
  const snapshot = ref<WorkbenchSnapshot>(emptySnapshot(''))

  /**
   * 员工工作台：所有数据都必须来自本人。
   * 未绑定人员档案（empId 为空）时不发起业务请求，直接产出“待绑定”待办，
   * 避免用管理端接口拿全量数据来假装有内容。
   */
  async function loadEmployee(): Promise<RoleWorkbenchSnapshot> {
    const empId = await userStore.ensureEmpId()
    if (empId == null) {
      return buildEmployeeWorkbench({
        hasEmployeeIdentity: false,
        empId: null,
        abilityCount: null,
        abilityOverallScore: null,
        confirmedCount: null,
        provisionalCount: null,
        workflow: null,
        reports: [],
        matchingTotal: null,
        matchingRecent: [],
        plans: [],
      })
    }

    const [profile, abilities, assessmentProfile, workflow, reports, matching, plans] = await Promise.all([
      pick(getAbilityProfile(empId)),
      pick(listAbilities(empId)),
      pick(getAssessmentProfile(empId)),
      pick(getActiveWorkflow(empId)),
      pick(listAssessmentReports(empId)),
      pick(getMatchingDashboardSummary()),
      pick(pageLearningPaths({ current: 1, size: 10, empId })),
    ])

    const abilityList: EmpAbility[] = Array.isArray(abilities) ? abilities : []
    const assessment = (assessmentProfile ?? {}) as {
      confirmed?: unknown[]
      provisional?: unknown[]
      assessmentPending?: boolean
    }
    const confirmedCount = Array.isArray(assessment.confirmed) ? assessment.confirmed.length : null
    const provisionalCount = Array.isArray(assessment.provisional) ? assessment.provisional.length : null
    // 2026-09-04：审核期间能力项对员工不可见（后端闸门会返回空画像）。
    // 此时把数量置 null 让指标卡显示「--」，而不是显示 0 ——
    // 「0 项已确立能力」与「暂时不展示」是两件事，不能混。
    //
    // 【明确口径】「从未评估」也显示「--」：能力项数量在评估定稿前一律不可展示，
    // 这里不按 NOT_STARTED 回落成 0（0 会让人误以为系统认定他没有能力）。
    // 区分「从未评估 / 进行中 / 待审核」的文案由能力画像页按 assessmentState 分别渲染。
    const assessmentPending = Boolean(assessment.assessmentPending)
    const matchingData = (matching ?? {}) as { total?: number | null; recent?: EmployeeMatchingRecordLike[] }

    return buildEmployeeWorkbench({
      hasEmployeeIdentity: true,
      empId,
      // 能力清单是正式画像的能力项；接口不可用时退回已确立能力数量
      abilityCount: assessmentPending ? null : abilityList.length || confirmedCount,
      abilityOverallScore: assessmentPending
        ? null
        : (profile as { overallScore?: number | null } | null)?.overallScore ?? null,
      confirmedCount,
      provisionalCount,
      workflow: (workflow ?? null) as EmployeeWorkflowLike | null,
      reports: (Array.isArray(reports) ? reports : []) as AssessmentReportListItem[] as EmployeeReportLike[],
      matchingTotal: matchingData.total ?? null,
      matchingRecent: Array.isArray(matchingData.recent) ? matchingData.recent : [],
      plans: recordsOf<EmployeePlanLike>(plans),
    })
  }

  /**
   * HR：人员档案、岗位、匹配运营与「学习成果复核」队列。
   * 注意：HR 看的是「有多少员工提交的学习成果还没复核」（真正属于 HR 的待办），
   * 而不是全公司学习计划的步骤完成率（那是员工自己的进度）。
   */
  async function loadHr(): Promise<RoleWorkbenchSnapshot> {
    const [employeeStats, employeePage, postPage, matching, taskPage, outcomePending, outcomePage] = await Promise.all([
      pick(getEmployeeStats()),
      pick(pageEmployees({ current: 1, size: 1 })),
      pick(pagePosts({ current: 1, size: 1 })),
      pick(getMatchingDashboardSummary()),
      pick(pageMatchingTasks({ current: 1, size: 5 })),
      pick(getPendingLearningOutcomeCount()),
      pick(pageLearningOutcomeReviews({ current: 1, size: 1 })),
    ])

    return buildHrWorkbench({
      employeeTotal: employeeStats?.total ?? totalOf(employeePage),
      enabledTotal: employeeStats?.enabled ?? null,
      postTotal: totalOf(postPage),
      matching: (matching ?? null) as HrWorkbenchInput['matching'],
      tasks: recordsOf<MatchingTaskLike>(taskPage),
      outcomeReview: {
        // 两个接口都可能失败：分别为 null 时前端显示「--」而不是 0，避免"看起来已经处理完了"
        pending: outcomePending ?? null,
        total: totalOf(outcomePage),
      },
    })
  }

  /** 岗位体系管理员：岗位建模、全景图谱与能力巡检 */
  async function loadJobArchitect(): Promise<RoleWorkbenchSnapshot> {
    const [postPage, panorama, inspection, trendPending] = await Promise.all([
      pick(pagePosts({ current: 1, size: 1 })),
      pick(getPostPanoramaOverview()),
      pick(getPostInspectionSummary()),
      // 岗位趋势待审汇总：失败时返回 null，管理端待办区自然不出现该条，不伪造 0
      pick(getTrendPendingSummary()),
    ])

    return buildJobArchitectWorkbench({
      postTotal: totalOf(postPage),
      panorama: panorama ?? null,
      inspection: inspection ?? null,
      trendPending: trendPending ?? null,
    })
  }

  // 【2026-09-04 角色合并】原 loadAiConfig()（模型 / 知识资产 / 调用质量）已删除：
  // 它的数据源全是 /api/rag/**，而这些接口随角色合并改挂 POST:MANAGE（归岗位体系管理员），
  // 合并后的 PLATFORM_ADMIN 调用会 403 —— 保留一个必然报错的装载器等于留了个坑。
  // 知识资产指标若需要，应加到岗位体系管理员的工作台（本文件 loadJobArchitect）。

  /** 平台管理员：账号、角色与审计（合并自原「权限管理员」，数据源权限完全兼容） */
  async function loadPlatformAdmin(): Promise<RoleWorkbenchSnapshot> {
    const [userPage, roles, rolePage, logs] = await Promise.all([
      pick(pageUsers({ current: 1, size: 1 })),
      pick(listEnabledRoles()),
      pick(pageRoles({ current: 1, size: 1 })),
      pick(pageLogs({ current: 1, size: 5 })),
    ])

    return buildPlatformAdminWorkbench({
      userTotal: totalOf(userPage),
      enabledRoles: Array.isArray(roles) ? roles : null,
      roleTotal: totalOf(rolePage),
      logTotal: totalOf(logs),
      logs: recordsOf<AuditLogLike>(logs),
    })
  }


  async function load() {
    loading.value = true
    error.value = ''
    try {
      const current = effectiveRole.value
      // 指标接口与角色装载器并发：它只影响 KPI 环比行与团队成员区块，
      // 失败时由 mergeMetrics 保持原快照，不整页失败。
      const [next, metrics] = await Promise.all([
        current === 'HR_SPECIALIST'
          ? loadHr()
          : current === 'JOB_ARCHITECT'
            ? loadJobArchitect()
            : current === 'PLATFORM_ADMIN' || current === SUPER_ADMIN_ROLE
              // 超级管理员工作台复用「平台管理员」装载器：
              // 它取的是平台级指标（账号/角色/模型/审计），不掺业务数据，
              // 对超管是安全且不空的默认视图（菜单已可进所有业务页）。
              ? loadPlatformAdmin()
              : loadEmployee(),
        pick(getWorkbenchMetrics()),
      ])
      const merged = mergeMetrics(next, metrics)
      const isEmpty =
        !merged.stats.length && !merged.todos.length && !merged.progresses.length && !merged.notices.length
      if (isEmpty) error.value = '工作台数据加载失败，请检查网络后重试'
      snapshot.value = merged
    } catch (e) {
      error.value = e instanceof Error ? e.message : '工作台数据加载失败'
      snapshot.value = emptySnapshot('')
    } finally {
      loading.value = false
    }
  }

  return { role, loading, error, snapshot, load, employeePaths }
}
