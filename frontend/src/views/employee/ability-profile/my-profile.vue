<script setup lang="ts">
/**
 * 员工本人能力画像（图表化）。
 *
 * 数据可见性口径（2026-09-04 变更）：
 *   能力项**只有 HR 完成该员工全部能力项的人工审核后**才对员工可见。
 *   因此本页不再展示「待确立 / 待审核能力」这类审核期中间态 ——
 *   员工侧只看到已成型的画像结果，审核进度以流程状态说明表达。
 *
 * 呈现口径：画像以图表为主（能力雷达图 / 分类分布 / 等级分布 / 关系图谱），
 * 表格退居明细。empId 一律来自登录态（userStore.empId），URL 上的 empId 仅用于 HR 代看。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Document, Grid, Reading, Refresh, TrendCharts } from '@element-plus/icons-vue'
import { getAbilityProfile, getMyPassedPosts } from '@/api/employee'
import type { PassedPost } from '@/api/employee'
import {
  pageMyLearningOutcomes,
  learningOutcomeStatusText,
  learningOutcomeStatusTagType,
} from '@/api/learning-outcome'
import type { LearningOutcomeSubmission } from '@/api/learning-outcome'
import {
  pageMyInterviews,
  respondInterview,
  INTERVIEW_STATUS,
  INTERVIEW_RESULT,
  INTERVIEW_RESPONSE,
} from '@/api/communication-interview'
import type { CommunicationInterview } from '@/api/communication-interview'
// 「进入会议」按钮的状态判据：与 HR 追踪页共用同一份纯逻辑，避免两端口径漂移
import { resolveMeetingAction } from '@/views/matching/communication-interview/meeting-action'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resolveApiErrorMessage } from '@/utils/request-error-message'
import { getActiveWorkflow, getAssessmentProfile, getComprehensiveReportLatest } from '@/api/assessment'
import type {
  ComprehensiveAssessmentReportDetail,
  AssessmentReportListItem,
  WorkflowView,
} from '@/api/assessment'
import { listAssessmentReports } from '@/api/assessment'
import ReportContent from './assessment-report-content.vue'
import { reportStatusMeta } from './assessment-records'
import AbilityForceGraph from '@/components/graph/AbilityForceGraph.vue'
import type { ForceEdge, ForceNode } from '@/components/graph/AbilityForceGraph.vue'
import AbilityRadarChart from '@/components/graph/AbilityRadarChart.vue'
import EChartsWrapper from '@/components/chart/EChartsWrapper.vue'
import type { EChartsOption } from 'echarts'
import { buildAbilityForceGraph } from './ability-graph'
import type { EmpAbilityProfileVO } from '@/api/employee/types'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const error = ref('')
const profile = ref<EmpAbilityProfileVO | null>(null)
const reports = ref<AssessmentReportListItem[]>([])
const workflow = ref<WorkflowView | null>(null)
const empId = ref<number | null>(null)
/** 后端闸门结果：员工侧评估流程全部结束前，能力信息与报告一律不展示 */
const assessmentPending = ref(false)

/**
 * 后端给出的三态评估状态（与 assessmentPending 同步返回）。
 *
 * 历史 bug：只拿到布尔 assessmentPending，于是「从未评估」与「已在审核中」
 * 无法区分 —— 从没做过评估的员工也会看到「你的能力评估已提交，HR 正在完成人工审核」，
 * 等于凭空捏造了一个不存在的审核进度。现在按真实状态选择文案。
 */
const assessmentState = ref<'NOT_STARTED' | 'IN_PROGRESS' | 'PENDING_REVIEW' | 'READY' | ''>('')

/** 已通过岗位（闭环设计 P3）：人员范围由服务端按登录身份固定为本人 */
const passedPosts = ref<PassedPost[]>([])

/** 我的学习成果提交与复核状态（闭环设计 P4） */
const myOutcomes = ref<LearningOutcomeSubmission[]>([])

/** 我的视频沟通记录（闭环设计 P5）：只读，含 HR 评价原文 */
const myInterviews = ref<CommunicationInterview[]>([])

const abilityDetails = computed(() => profile.value?.abilityDetails ?? [])
const overallScore = computed(() => profile.value?.overallScore ?? null)

/* ===================== 画像统计（图表用） ===================== */

/**
 * 能力分类的中文标签。
 *
 * 后端历史上在标签解析不到时输出字面量 "UNKNOWN"，看上去像一个真实分类；
 * 这里统一把它与空值一并归入「未分类」，避免把「没查到的标签」当成一个类别展示。
 */
const CATEGORY_LABEL: Record<string, string> = {
  TECHNICAL: '技术能力',
  SOFT: '软技能',
  BUSINESS: '业务能力',
  GENERAL: '通用能力',
  OTHER: '其他',
}

function categoryLabel(raw?: string | null): string {
  if (!raw || raw.toUpperCase() === 'UNKNOWN') return '未分类'
  return CATEGORY_LABEL[raw.toUpperCase()] ?? raw
}

/** 图像整体：能力雷达 / 分类分布 / 等级分布都以「已确立能力」为唯一数据源 */
const categoryList = computed(() => {
  const map = new Map<string, number>()
  for (const item of abilityDetails.value) {
    const key = categoryLabel(item.tagCategory)
    map.set(key, (map.get(key) ?? 0) + 1)
  }
  return [...map.entries()].map(([name, value]) => ({ name, value }))
})

/**
 * 是否存在真实的分类数据。
 *
 * 全部落到「未分类」时画成饼图等于什么都没说（一个 100% 的灰圆），
 * 不如直接说明原因 —— 分类为空说明能力项没关联到标签分类，
 * 使用者该去查标签治理，而不是对着一个假图表猜。
 */
const hasCategoryData = computed(() => categoryList.value.some(item => item.name !== '未分类'))

const avgMastery = computed(() => {
  const levels = abilityDetails.value.map(item => Number(item.masteryLevel ?? 0)).filter(v => v > 0)
  if (!levels.length) return null
  return Math.round((levels.reduce((a, b) => a + b, 0) / levels.length) * 10) / 10
})

/** 等级刻度：按实际出现的最高等级自适应（至少 4 级，避免雷达图过扁） */
const masteryScale = computed(() =>
  Math.max(4, ...abilityDetails.value.map(item => Number(item.masteryLevel ?? 0))),
)

/**
 * 雷达图轴数上限。
 * 能力项过多时雷达图会糊成一团，取掌握等级最高的前 12 项代表本人能力面。
 */
const RADAR_AXIS_LIMIT = 12

const radarData = computed(() =>
  [...abilityDetails.value]
    .sort((a, b) => Number(b.masteryLevel ?? 0) - Number(a.masteryLevel ?? 0))
    .slice(0, RADAR_AXIS_LIMIT)
    .map(item => ({
      axis: item.tagName,
      value: Number(item.masteryLevel ?? 0),
      maxValue: masteryScale.value,
    })),
)

const LEVEL_LABELS = ['L1 初级', 'L2 中级', 'L3 高级', 'L4 专家', 'L5 权威']

const masteryOption = computed<EChartsOption>(() => {
  const counts = new Map<number, number>()
  for (const item of abilityDetails.value) {
    const level = Number(item.masteryLevel ?? 0)
    counts.set(level, (counts.get(level) ?? 0) + 1)
  }
  const levels = [...counts.keys()].sort((a, b) => a - b)
  return {
    grid: { top: 20, right: 16, bottom: 28, left: 36 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'category',
      data: levels.map(level => LEVEL_LABELS[level - 1] ?? `L${level}`),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e4e7ed' } },
      axisLabel: { color: '#8b95ab', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#8b95ab', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f0f3f9' } },
    },
    series: [{
      type: 'bar',
      barMaxWidth: 38,
      data: levels.map(level => counts.get(level) ?? 0),
      itemStyle: { borderRadius: [6, 6, 0, 0], color: '#2f6bff' },
      label: { show: true, position: 'top', color: '#414a63', fontSize: 11 },
    }],
  }
})

const categoryOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item', formatter: '{b}：{c} 项（{d}%）' },
  legend: { bottom: 0, textStyle: { color: '#8b95ab', fontSize: 11 } },
  series: [{
    type: 'pie',
    radius: ['46%', '70%'],
    center: ['50%', '44%'],
    avoidLabelOverlap: true,
    itemStyle: { borderColor: '#fff', borderWidth: 2 },
    label: { color: '#414a63', fontSize: 11 },
    data: categoryList.value,
  }],
}))

/* ===================== 能力关系图谱 ===================== */

const forceNodes = ref<ForceNode[]>([])
const forceEdges = ref<ForceEdge[]>([])
const graphWidth = ref(1100)

function updateGraphWidth() {
  const w = window.innerWidth
  if (w < 800) graphWidth.value = w - 60
  else if (w < 1400) graphWidth.value = w - 100
  else graphWidth.value = Math.min(1300, w - 160)
}

/** 评估流程当前阶段文案：以后端 displayStatus 为准，前端不自行推断业务状态 */
const workflowLabel = computed(() => {
  if (!workflow.value) return '尚未开始'
  return workflow.value.displayStatus || workflow.value.status || '进行中'
})

const assessmentPath = computed(() => (empId.value ? `/employee/ability-profile/assessment?empId=${empId.value}` : '/employee/ability-profile/assessment'))

/** 画像是否有可展示内容：图表区与明细区共用同一判据 */
const hasProfile = computed(() => !assessmentPending.value && abilityDetails.value.length > 0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const id = await userStore.ensureEmpId()
    empId.value = id
    if (id == null) {
      error.value = '当前账号尚未绑定人员档案，请联系 HR 或权限管理员完成绑定'
      return
    }
    // 刻意不再请求「待融合能力声明」：能力项只有 HR 审核全部完成后才对员工可见，
    // 审核期中间态不进入员工侧视图。
    const [profileRes, reportRes, workflowRes, assessmentRes] = await Promise.allSettled([
      getAbilityProfile(id),
      listAssessmentReports(id),
      getActiveWorkflow(id),
      getAssessmentProfile(id),
    ])
    profile.value = profileRes.status === 'fulfilled' ? profileRes.value.data : null
    // 能力关系图数据；无能力明细时置空，模板据此展示空态说明
    if (profile.value) {
      const graph = buildAbilityForceGraph({
        realName: profile.value.realName,
        abilityDetails: profile.value.abilityDetails,
      })
      forceNodes.value = graph.nodes
      forceEdges.value = graph.edges
    } else {
      forceNodes.value = []
      forceEdges.value = []
    }
    reports.value = reportRes.status === 'fulfilled' ? reportRes.value.data || [] : []
    workflow.value = workflowRes.status === 'fulfilled' ? workflowRes.value.data : null
    assessmentPending.value =
      assessmentRes.status === 'fulfilled' ? Boolean(assessmentRes.value.data?.assessmentPending) : false
    assessmentState.value =
      assessmentRes.status === 'fulfilled'
        ? ((assessmentRes.value.data?.assessmentState as typeof assessmentState.value) ?? '')
        : ''
    if (profileRes.status === 'rejected' && reportRes.status === 'rejected') {
      error.value = '能力画像加载失败，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

function go(path: string) {
  router.push(path)
}

/* ===================== 评估报告详情 ===================== */

const reportDialogVisible = ref(false)
const reportLoading = ref(false)
const reportDetail = ref<ComprehensiveAssessmentReportDetail | null>(null)

/**
 * 查看报告。
 * 改用「全方位评估报告」接口（能力评估域唯一的报告）：
 * 一次拿到四部分内容 + AI 综合洞察；后端闸门未放行时返回 available=false + 原因，
 * 这里按原因展示说明，而不是笼统的「报告尚未生成」。
 */
async function openReport() {
  if (empId.value == null) return
  reportDialogVisible.value = true
  reportLoading.value = true
  reportDetail.value = null
  try {
    const res = await getComprehensiveReportLatest(empId.value)
    reportDetail.value = res.data ?? null
  } catch (e: any) {
    ElMessage.error(e?.message || '评估报告加载失败，请稍后重试')
  } finally {
    reportLoading.value = false
  }
}

onMounted(() => {
  updateGraphWidth()
  window.addEventListener('resize', updateGraphWidth)
  load()
  loadPassedPosts()
  loadMyOutcomes()
  loadMyInterviews()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateGraphWidth)
})

/** 已通过岗位：只读聚合，失败时保持空列表，不影响画像页其余内容 */
async function loadPassedPosts() {
  try {
    const res = await getMyPassedPosts()
    passedPosts.value = res.data || []
  } catch {
    passedPosts.value = []
  }
}

/** 学习成果复核状态：提交后台进入「待复核」，HR 通过后能力画像才会更新 */
async function loadMyOutcomes() {
  try {
    const res = await pageMyLearningOutcomes({ current: 1, size: 5 })
    myOutcomes.value = res.data?.records || []
  } catch {
    myOutcomes.value = []
  }
}

/** 视频沟通记录：含 HR 评价原文（需求方明确不脱敏）与本人可用的接受/放弃操作 */
async function loadMyInterviews() {
  try {
    const res = await pageMyInterviews({ current: 1, size: 5 })
    myInterviews.value = res.data?.records || []
  } catch {
    myInterviews.value = []
  }
}

/**
 * 「进入会议」按钮的呈现方式（文案 / 禁用 / 是否显示 / 原因）。
 * 判据与 HR 追踪页同源（`@/views/matching/communication-interview/meeting-action`）：
 * 终面已完成（含不通过）、员工已放弃、HR 已取消之后不再给按钮，改为占位说明。
 */
function meetingAction(row: CommunicationInterview) {
  return resolveMeetingAction(row)
}

/**
 * 进入会议。带 row 重新判一次：列表可能是几十秒前的快照，
 * 期间 HR 可能已录入结论或取消，避免把员工送进一个已结束的会议室。
 */
function joinMeeting(row: CommunicationInterview) {
  const action = resolveMeetingAction(row)
  if (!action.visible || action.disabled) {
    ElMessage.warning(action.reason)
    return
  }
  window.open(row.meetingUrl, '_blank', 'noopener')
}

/* ===================== 终面响应（接受 / 放弃） ===================== */

/** 正在提交响应的记录 id：按钮级 loading，避免连点重复提交 */
const respondingId = ref<number | null>(null)

/**
 * 何时给出「接受 / 放弃」按钮。
 *
 * 只有「待沟通，且本人尚未表态」的场次才可响应 —— 后端是一次性响应，
 * 已响应或已结束/已取消再点只会拿到 409，不如直接不展示。
 */
function canRespond(row: CommunicationInterview): boolean {
  return row.status === INTERVIEW_STATUS.PENDING && row.employeeResponse == null
}

function responseTagType(response: number | null) {
  if (response === INTERVIEW_RESPONSE.ACCEPTED) return 'success'
  if (response === INTERVIEW_RESPONSE.DECLINED) return 'danger'
  return 'info'
}

/** 接受：只表达参加意愿，不改终面状态（状态仍为待沟通，等 HR 面完录入结论） */
async function handleAccept(row: CommunicationInterview) {
  try {
    await ElMessageBox.confirm(
      `确认接受本次视频终面吗？时间：${row.scheduledTime || '以 HR 通知为准'}。请在约定时间准时进入会议。`,
      '接受视频终面',
      { type: 'info', confirmButtonText: '确认接受', cancelButtonText: '再想想' },
    )
  } catch {
    return // 用户取消
  }
  respondingId.value = row.id
  try {
    await respondInterview(row.id, INTERVIEW_RESPONSE.ACCEPTED)
    ElMessage.success('已接受，请在约定时间进入会议')
    await loadMyInterviews()
  } catch (e) {
    ElMessage.error(resolveApiErrorMessage(e, '操作失败，请稍后重试'))
  } finally {
    respondingId.value = null
  }
}

/** 放弃：本场终面会被取消并通知 HR，可填写原因（选填，HR 能看到） */
async function handleDecline(row: CommunicationInterview) {
  let comment = ''
  try {
    const res = await ElMessageBox.prompt(
      '放弃后本场视频终面将被取消，如需继续请由 HR 重新发起。可填写原因（选填，HR 会看到）：',
      '放弃视频终面',
      {
        type: 'warning',
        confirmButtonText: '确认放弃',
        cancelButtonText: '再想想',
        inputType: 'textarea',
        inputPlaceholder: '选填，例如：时间冲突，希望改期',
        inputValidator: (value: string) =>
          !value || value.length <= 500 || '说明请控制在 500 字以内',
      },
    )
    comment = (res.value || '').trim()
  } catch {
    return // 用户取消
  }
  respondingId.value = row.id
  try {
    await respondInterview(row.id, INTERVIEW_RESPONSE.DECLINED, comment || undefined)
    ElMessage.success('已放弃本次视频终面，已通知 HR')
    await loadMyInterviews()
  } catch (e) {
    ElMessage.error(resolveApiErrorMessage(e, '操作失败，请稍后重试'))
  } finally {
    respondingId.value = null
  }
}

function interviewStatusTagType(status: number | null) {
  if (status === INTERVIEW_STATUS.PENDING) return 'warning'
  if (status === INTERVIEW_STATUS.FINISHED) return 'success'
  return 'info'
}

function interviewResultTagType(result: number | null) {
  if (result === INTERVIEW_RESULT.PASS) return 'success'
  if (result === INTERVIEW_RESULT.FAIL) return 'danger'
  if (result === INTERVIEW_RESULT.UNDECIDED) return 'warning'
  return 'info'
}

/**
 * 图表空态文案：统一解释「能力项什么时候才会出现」。
 *
 * 与顶部提示同口径 —— 空态也要区分「从未评估 / 进行中 / 待审核」，
 * 否则从没评估过的人会在这里再次读到「等 HR 审核」，等于凭空造了个不存在的进度。
 */
function chartEmptyText(kind: string): string {
  if (assessmentState.value === 'NOT_STARTED') {
    return `暂无${kind}：尚未开始能力评估，完成评估并通过 HR 审核后自动生成。`
  }
  if (assessmentState.value === 'IN_PROGRESS') {
    return `暂无${kind}：评估流程尚未走完，走完全部阶段并通过 HR 审核后自动生成。`
  }
  return `暂无${kind}：能力项在 HR 完成全部审核并并入画像后自动生成。`
}
</script>

<template>
  <div class="page-shell mp">
    <section class="page-hero">
      <div>
        <span class="page-hero__eyebrow">My Capability</span>
        <h1 class="page-hero__title">我的能力画像</h1>
        <p class="page-hero__desc">
          这里展示本人经 HR 审核确立的能力画像与评估报告。能力项在全部审核完成后才会呈现，匹配结果可在「我的匹配结果」查看。
        </p>
        <div class="page-hero__meta">
          <span class="hero-chip">当前评估流程：{{ workflowLabel }}</span>
          <span class="hero-chip">综合得分：{{ overallScore == null ? '--' : Math.round(overallScore) }}</span>
        </div>
      </div>
      <div class="mp__actions">
        <el-button type="primary" :icon="TrendCharts" @click="go(assessmentPath)">
          {{ workflow ? '继续能力评估' : '开始能力评估' }}
        </el-button>
        <el-button :icon="Document" @click="openReport">查看评估报告</el-button>
        <el-button :icon="Reading" @click="go('/learning/path')">我的学习路径</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </section>

    <!-- 已通过岗位（闭环设计 P3）：HR 审核通过 + 已推送 + 匹配状态为强适配/适配 -->
    <section v-if="passedPosts.length" class="glass-card" style="margin-bottom: 16px;">
      <div class="panel-body">
        <div style="font-weight: 600; margin-bottom: 10px;">已通过岗位</div>
        <div style="display: flex; flex-wrap: wrap; gap: 8px;">
          <el-tag
            v-for="post in passedPosts"
            :key="post.postId"
            :type="post.matchStatus === 1 ? 'success' : 'primary'"
            effect="plain"
            round
          >
            {{ post.postName || `岗位#${post.postId}` }}
            <span style="margin-left: 6px; opacity: 0.75;">
              {{ post.matchStatusName }}{{ post.matchScore != null ? ` · ${post.matchScore}` : '' }}
            </span>
          </el-tag>
        </div>
      </div>
    </section>

    <!-- 学习成果复核状态（闭环设计 P4）：提交后为「待复核」，HR 通过后能力画像才更新 -->
    <section v-if="myOutcomes.length" class="glass-card" style="margin-bottom: 16px;">
      <div class="panel-body">
        <div style="font-weight: 600; margin-bottom: 10px;">我的学习成果</div>
        <el-table :data="myOutcomes" size="small" style="width: 100%">
          <el-table-column label="能力项" min-width="160">
            <template #default="{ row }">
              {{ row.abilityName || (row.tagId != null ? `能力#${row.tagId}` : '--') }}
            </template>
          </el-table-column>
          <el-table-column label="自评等级" width="100">
            <template #default="{ row }">{{ row.confirmedLevel == null ? '--' : `L${row.confirmedLevel}` }}</template>
          </el-table-column>
          <el-table-column label="复核状态" width="110">
            <template #default="{ row }">
              <el-tag :type="learningOutcomeStatusTagType(row.reviewStatus)" effect="plain" round size="small">
                {{ learningOutcomeStatusText(row.reviewStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="复核意见" min-width="180">
            <template #default="{ row }">{{ row.reviewComment || '--' }}</template>
          </el-table-column>
          <el-table-column prop="createdTime" label="提交时间" width="170" />
        </el-table>
      </div>
    </section>

    <!-- 视频沟通（闭环设计 P5）：HR 发起后可见邀约（邮件 + 站内通知）；
         可在此接受/放弃，结论与 HR 评价原文对员工可见。响应一次性，之后不可更改。 -->
    <section v-if="myInterviews.length" class="glass-card" style="margin-bottom: 16px;">
      <div class="panel-body">
        <div style="font-weight: 600; margin-bottom: 4px;">我的视频沟通</div>
        <div style="font-size: 12px; opacity: 0.7; margin-bottom: 10px;">
          视频沟通在讯飞会议进行，平台只提供入会链接跳转，不承载音视频。HR 发起后会收到站内通知（档案邮箱可用时另有一封邀请邮件）。
          时间合适请点「接受」，无法参加请点「放弃」（本场终面将被取消并通知 HR，需继续请让 HR 重新发起）。
          到约定时间从对应记录右侧的「进入会议」进入；本场是否完成以 HR 录入的结论为准。
        </div>
        <el-table :data="myInterviews" size="small" style="width: 100%">
          <el-table-column label="预约时间" width="180">
            <template #default="{ row }">{{ row.scheduledTime || '尽快' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="interviewStatusTagType(row.status)" effect="plain" round size="small">
                {{ row.statusName || '--' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="我的响应" width="150">
            <template #default="{ row }">
              <el-tag :type="responseTagType(row.employeeResponse)" effect="plain" round size="small">
                {{ row.employeeResponseName || '待响应' }}
              </el-tag>
              <div v-if="row.employeeResponseComment" class="mp__cell-sub">
                原因：{{ row.employeeResponseComment }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="结论" width="170">
            <template #default="{ row }">
              <el-tag v-if="row.result" :type="interviewResultTagType(row.result)" effect="plain" round size="small">
                {{ row.resultName }}
              </el-tag>
              <span v-else style="opacity: 0.6;">--</span>
            </template>
          </el-table-column>
          <el-table-column label="HR 评价" min-width="200">
            <template #default="{ row }">{{ row.comment || '--' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <template v-if="canRespond(row)">
                <el-button
                  type="success"
                  link
                  :loading="respondingId === row.id"
                  @click="handleAccept(row)"
                >接受</el-button>
                <el-button
                  type="danger"
                  link
                  :loading="respondingId === row.id"
                  @click="handleDecline(row)"
                >放弃</el-button>
              </template>
              <!--
                会议按钮随终面状态变化（判据同 HR 追踪页，见 `meetingAction`）：
                已完成 / 已放弃 / 已取消后不再显示按钮，改为一句占位说明；
                直接消失会让员工以为界面漏渲染了自己那场会。
              -->
              <template v-if="meetingAction(row).visible">
                <el-tooltip :content="meetingAction(row).reason" placement="top" :show-after="300">
                  <span>
                    <el-button
                      :type="meetingAction(row).tone"
                      link
                      :disabled="meetingAction(row).disabled"
                      @click="joinMeeting(row)"
                    >{{ meetingAction(row).label }}</el-button>
                  </span>
                </el-tooltip>
              </template>
              <span v-else class="mp__meeting-hint" :title="meetingAction(row).reason">
                {{ meetingAction(row).placeholder }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-alert v-if="error" :title="error" type="warning" show-icon :closable="false" />

    <!-- 员工侧闸门：评估流程（含人工审核）全部结束前不展示能力与报告。
         三种「看不到」必须分开说（后端 assessmentState 给出真实状态）：
         从未评估 → 引导去发起；仍在进行 → 说明还差什么；已走完 → 才是「等审核」。
         历史 bug：三者共用「已提交，HR 正在审核」，对从未评估的人是凭空捏造的进度。 -->
    <el-alert
      v-if="assessmentState === 'NOT_STARTED'"
      class="mp__pending"
      title="尚未开始能力评估"
      type="info"
      show-icon
      :closable="false"
      description="你还没有发起过能力评估。完成「上传简历 → AI 能力测试 → AI 面试」三步后，经 HR 审核定稿，这里会展示你的能力画像与评估报告。"
    />
    <el-alert
      v-else-if="assessmentState === 'IN_PROGRESS'"
      class="mp__pending"
      title="能力评估进行中"
      type="info"
      show-icon
      :closable="false"
      description="你的能力评估尚未全部完成。走完全部阶段并经 HR 审核定稿后，这里会展示本次确立的能力画像与评估报告，在此之前暂不展示结果。"
    />
    <el-alert
      v-else-if="assessmentState === 'PENDING_REVIEW' || (assessmentPending && !assessmentState)"
      class="mp__pending"
      title="等待结果：人工审核中"
      type="info"
      show-icon
      :closable="false"
      description="你的能力评估已提交，HR 正在完成人工审核（或报告内容仍在生成）。定稿完成后这里会展示本次确立的能力画像与评估报告，在此之前暂不展示结果。"
    />

    <section class="mp__stats">
      <article class="mp__stat">
        <span>已确立能力</span>
        <!-- 明确口径：结果未定稿（含从未评估）统一显示「--」。
             不按 NOT_STARTED 显示 0 —— 0 会被读成「系统认定你没有能力」，
             而实际只是「还没有可展示的定稿结果」。 -->
        <strong>{{ assessmentPending ? '--' : abilityDetails.length }}</strong>
        <small>来自审核完成的评估</small>
      </article>
      <article class="mp__stat">
        <span>覆盖能力分类</span>
        <strong>{{ hasProfile ? categoryList.length : '--' }}</strong>
        <small>画像覆盖的能力领域数</small>
      </article>
      <article class="mp__stat">
        <span>平均掌握等级</span>
        <strong>{{ hasProfile && avgMastery != null ? avgMastery : '--' }}</strong>
        <small>已确立能力的平均等级</small>
      </article>
      <article class="mp__stat">
        <span>评估报告</span>
        <strong>{{ reports.length }}</strong>
        <small>历史评估记录</small>
      </article>
    </section>

    <!-- 画像图表区：雷达 / 分类分布 / 等级分布。
         常驻容器，未放行或暂无数据时用空态说明原因，避免打开页面只看到表格。 -->
    <section class="mp__charts">
      <article class="glass-card mp__card">
        <header class="mp__head">
          <div>
            <h2>能力雷达图</h2>
            <p>已确立能力的掌握等级分布（最多展示等级最高的 {{ RADAR_AXIS_LIMIT }} 项）</p>
          </div>
        </header>
        <div v-if="hasProfile && radarData.length >= 3" class="mp__radar">
          <AbilityRadarChart
            :data="radarData"
            :width="420"
            :height="400"
            :max-value="masteryScale"
            :levels="masteryScale"
          />
        </div>
        <el-empty v-else :image-size="80" :description="chartEmptyText('雷达图')" />
      </article>

      <article class="glass-card mp__card">
        <header class="mp__head">
          <div>
            <h2>能力分类分布</h2>
            <p>已确立能力在各类别上的占比</p>
          </div>
        </header>
        <EChartsWrapper v-if="hasProfile && hasCategoryData" :option="categoryOption" height="260px" />
        <el-empty
          v-else
          :image-size="80"
          :description="hasProfile && !hasCategoryData
            ? '暂无可用的能力分类：这些能力项尚未关联到标签分类'
            : chartEmptyText('分类分布')"
        />
      </article>

      <article class="glass-card mp__card">
        <header class="mp__head">
          <div>
            <h2>掌握等级分布</h2>
            <p>各掌握等级上的能力项数量</p>
          </div>
        </header>
        <EChartsWrapper v-if="hasProfile" :option="masteryOption" height="260px" />
        <el-empty v-else :image-size="80" :description="chartEmptyText('等级分布')" />
      </article>

      <article class="glass-card mp__card mp__card--wide">
        <header class="mp__head">
          <div>
            <h2>能力关系图谱</h2>
            <p>本人 → 能力分类 → 能力项 的三层关系视图</p>
          </div>
          <el-icon class="mp__head-icon"><Grid /></el-icon>
        </header>
        <div v-if="hasProfile && forceNodes.length > 1" class="mp__graph">
          <AbilityForceGraph
            :nodes="forceNodes"
            :edges="forceEdges"
            :width="graphWidth"
            :height="520"
            theme="tech-light"
          />
        </div>
        <el-empty v-else :image-size="90" :description="chartEmptyText('关系图谱')" />
      </article>
    </section>

    <!-- 明细表：图表看趋势，表格看逐项结果 -->
    <section v-if="!assessmentPending" class="glass-card mp__card">
      <header class="mp__head">
        <div>
          <h2>已确立能力明细</h2>
          <p>评估通过并进入正式画像的能力项</p>
        </div>
      </header>
      <el-table v-loading="loading" :data="abilityDetails" stripe size="default" empty-text="暂无已确立能力">
        <el-table-column prop="tagName" label="能力项" min-width="200" />
        <el-table-column label="能力分类" width="180">
          <template #default="{ row }">{{ categoryLabel(row.tagCategory) }}</template>
        </el-table-column>
        <el-table-column label="掌握等级" width="160">
          <template #default="{ row }">{{ row.masteryLevelName || `L${row.masteryLevel}` }}</template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 评估报告：入口保持常驻，员工能看到每次评估的进度；
         报告内容仍在全部审核完成后才可打开（后端返回 available=false + 原因）。 -->
    <section class="glass-card mp__card">
      <header class="mp__head">
        <div>
          <h2>评估报告</h2>
          <p>每次评估流程的完成情况与报告状态</p>
        </div>
        <el-icon class="mp__head-icon"><Document /></el-icon>
      </header>
      <el-table :data="reports" stripe size="default" empty-text="暂无评估报告">
        <el-table-column prop="startedAt" label="开始时间" width="180" />
        <el-table-column prop="completedAt" label="完成时间" width="180" />
        <el-table-column label="报告状态" width="150">
          <template #default="{ row }">
            <!-- 必须是唯一一份口径（reportStatusMeta）：本页原先自带一份
                 「READY→已生成、FAILED→失败、其余→审核中」，于是刚上传简历的员工
                 在这里也会看到「审核中」；同时内容仍在生成（GENERATING）的报告
                 会被标成「已生成」却打不开。 -->
            <el-tag size="small" :type="reportStatusMeta(row.reportStatus, row.workflowStatus).tagType">
              {{ reportStatusMeta(row.reportStatus, row.workflowStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default>
            <el-button type="primary" link size="small" @click="openReport">查看报告</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="reportDialogVisible" title="全方位评估报告" width="960px" top="6vh">
      <div v-loading="reportLoading" style="min-height: 160px;">
        <ReportContent v-if="reportDetail && reportDetail.available" :report="reportDetail" />
        <el-empty
          v-else-if="!reportLoading"
          :image-size="100"
          :description="reportDetail?.unavailableReason || '报告尚未生成：需 HR 完成全部能力项审核后才会产出'"
        />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.mp__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.mp__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.mp__stat {
  padding: 18px 20px;
  border: 1px solid #e8ecf5;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 4px 14px rgba(44, 58, 100, 0.04);
}
.mp__stat span {
  display: block;
  color: #8b95ab;
  font-size: 12px;
}
.mp__stat strong {
  display: block;
  margin: 8px 0 6px;
  color: #17213b;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.02em;
}
.mp__stat small {
  color: #9aa3b8;
  font-size: 11px;
}

/* 图表区：雷达图与两个 ee 图表并排，关系图谱整行 */
.mp__charts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.mp__card {
  padding: 18px 20px;
}
.mp__card--wide {
  grid-column: 1 / -1;
}

/* 图谱画布：宽度由脚本按视口计算，这里只负责居中与窄屏横向滚动 */
.mp__graph {
  display: flex;
  justify-content: center;
  overflow-x: auto;
  padding: 4px 0;
}
.mp__radar {
  display: flex;
  justify-content: center;
  overflow-x: auto;
}
.mp__pending {
  align-items: flex-start;
}
/* 表格单元格内的次级说明（如放弃原因），跟着标签占满剩余宽度 */
.mp__cell-sub {
  margin-top: 4px;
  color: #8b95ab;
  font-size: 11px;
  line-height: 1.5;
  white-space: normal;
  word-break: break-word;
}
/*
 * 终面状态变化后操作列的占位说明（会议按钮收起时显示，替代原来的「进入会议」）。
 * 直接让按钮消失而不留痕迹，员工会以为自己那场会不见了，所以必须有一句话兜住。
 */
.mp__meeting-hint {
  color: var(--app-text-muted, #94a3b8);
  font-size: 12px;
  line-height: 1.5;
  white-space: normal;
  word-break: break-word;
}
.mp__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}
.mp__head h2 {
  margin: 0;
  color: #1a2440;
  font-size: 15px;
  font-weight: 700;
}
.mp__head p {
  margin: 5px 0 0;
  color: #8b95ab;
  font-size: 11px;
}
.mp__head-icon {
  color: #9aa3b8;
}
@media (max-width: 1200px) {
  .mp__charts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 1024px) {
  .mp__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .mp__stats,
  .mp__charts {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
