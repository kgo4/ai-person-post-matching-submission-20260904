<template>
  <div class="assessment-page">
    <!-- 档案/身份解析失败时常驻提示：不让人对着"点了没反应"猜原因 -->
    <el-alert
      v-if="scopeError"
      :title="scopeError"
      type="warning"
      show-icon
      :closable="false"
      class="scope-alert"
    />
    <!-- 顶部：人员身份 / 流程状态 / 更新时间 / 风险状态 -->
    <el-card shadow="never" class="header-card">
      <div class="header-row">
        <div class="header-left">
          <h2>人员能力评估流程</h2>
          <div class="emp-info">
            <el-tag type="info" effect="plain">{{ employeeName || `员工 #${empId}` }}</el-tag>
            <el-tag :type="statusTagType" effect="dark">{{ statusLabel }}</el-tag>
            <span v-if="workflow?.nextStepHint" class="hint">{{ workflow.nextStepHint }}</span>
          </div>
        </div>
        <div class="header-right">
          <el-button v-if="!workflow" type="primary" size="small" :loading="actionLoading" @click="handleStartAssessment">
            开始本次评估
          </el-button>
          <span v-if="workflow?.startedAt" class="time">开始于 {{ formatTime(workflow.startedAt) }}</span>
          <el-button v-if="historyReports.length" size="small" @click="scrollToRecords">
            评估记录（{{ historyReports.length }}）
          </el-button>
        </div>
      </div>

      <!-- 中部：固定五阶段进度条 -->
      <el-steps :active="activeStep" finish-status="success" align-center class="steps">
        <el-step title="简历" description="上传与解析" />
        <el-step title="AI 测试" description="验证覆盖" />
        <el-step title="AI 面试" description="行为确认" />
        <el-step title="聚合审核" description="Harness 批量审核" />
        <el-step title="最终确认" description="等级确认中心" />
      </el-steps>
    </el-card>

    <!-- 主体：当前阶段工作区 -->
    <el-card shadow="never" class="workspace-card">
      <template #header>
        <div class="card-title">
          <span>{{ workspaceTitle }}</span>
          <el-button v-if="workflow?.availableActions?.includes('UPLOAD_RESUME')" type="primary" size="small" @click="goResume">
            上传简历
          </el-button>
          <el-button v-if="workflow?.availableActions?.includes('GENERATE_TEST')" type="primary" size="small" :loading="actionLoading" @click="openGenerateTest">
            生成验证测试
          </el-button>
          <el-button v-if="canEnterTest" type="primary" size="small" @click="goTest">
            进入 AI 测试
          </el-button>
          <el-button v-if="canCreateInterview" type="primary" size="small" :loading="actionLoading" @click="handleCreateInterview">
            {{ workflow?.status === 'INTERVIEW_PREPARING' || workflow?.status === 'INTERVIEW_IN_PROGRESS' ? '继续面试' : '发起 AI 面试' }}
          </el-button>
          <!-- 面试过程记录：员工侧不提供入口（员工只能看面试评价，评价在评估报告内）。
               员工仍需进入 AI 面试页答题 —— 那条链路是「发起/继续 AI 面试」按钮，不受影响。 -->
          <el-button
            v-if="canViewInterviewRecord && interviewStatuses.includes(workflow?.status ?? '')"
            size="small"
            @click="goInterviewRecords"
          >
            查看面试记录
          </el-button>
          <el-button v-if="workflow?.availableActions?.includes('RETRY_FAILED_STAGE')" type="warning" size="small" :loading="actionLoading" @click="handleRetry">
            重新尝试
          </el-button>
        </div>
      </template>

      <div v-if="currentReport" class="report-summary">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="评估完成">{{ (currentReport.completedAt || '--').replace('T', ' ').slice(0, 16) }}</el-descriptions-item>
          <el-descriptions-item label="结论">{{ currentReport.conclusion || '—' }}</el-descriptions-item>
          </el-descriptions>
        <el-button size="small" type="primary" link @click="openReportDetail(currentReport)">查看完整报告</el-button>
      </div>
      <!-- 报告摘要区：未定稿时给出「在等什么」的说明。
           必须区分「从未评估」与「审核中」—— 前者下方就是「暂无评估流程，请先上传简历」，
           若此处再说「等待人工审核」会自相矛盾（没有任何流程在审核）。 -->
      <div v-else-if="profile.assessmentState === 'NOT_STARTED'" class="report-summary">
        <p class="hint">尚未开始能力评估，请先上传简历。</p>
      </div>
      <!-- 流程仍在推进时，报告摘要区不该抢「等待审核」的话，进度由下方阶段区说明 -->
      <div v-else-if="profile.assessmentState === 'IN_PROGRESS'" class="report-summary">
        <p class="hint">评估流程进行中，走完全部阶段后才会生成评估报告。</p>
      </div>
      <!-- 与「能力审核说明」同一口径：只有后端明确判定为「已走完流程、等审核」（PENDING_REVIEW）
           才说「人工审核中」。原先只看兼容布尔 assessmentPending（= 只要未定稿就为真），
           在「流程还在推进但状态字段取不到」时会谎报审核进度。 -->
      <div v-else-if="profile.assessmentState === 'PENDING_REVIEW'" class="report-summary">
        <p class="hint">等待结果：人工审核中，审核完成后可在此查看本次评估报告。</p>
      </div>
      <div v-else-if="workflow?.status === 'INTERVIEW_ANALYZING'" class="report-summary">
        <el-skeleton :rows="3" animated />
        <p class="hint">评估报告正在生成，请稍候…</p>
      </div>

      <el-empty v-if="!workflow" description="暂无评估流程，请先上传简历" />
      <div v-else-if="workflow.status === 'COMPLETED'" class="completed-box">
        <el-result icon="success" title="能力评估已完成" sub-title="可查看能力画像并发起匹配" />
        <div class="completed-actions">
          <el-button type="primary" @click="loadProfile">查看能力画像</el-button>
        </div>
      </div>
      <div v-else-if="workflow.status === 'REVIEW_REQUIRED'" class="review-box">
        <el-alert
          type="warning"
          :closable="false"
          title="等待结果：人工审核中"
          description="你的简历、AI 测试与 AI 面试已全部完成。HR 审核完成前，评估结果与能力信息暂不展示；审核通过后系统会自动融合最终等级并更新能力画像，届时即可查看结果并发起匹配。"
        />
      </div>
      <div v-else class="stage-hint">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="当前阶段">{{ workspaceTitle }}</el-descriptions-item>
          <el-descriptions-item label="流程状态">{{ statusLabel }}</el-descriptions-item>
          <el-descriptions-item label="下一步提示" :span="2">{{ workflow.nextStepHint || '—' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>

    <!-- 我的评估记录：一个人可能被评估多次，每次评估独立成一条记录，
         员工可逐次查看该次评估报告（列表按开始时间倒序，首行即最近一次）。 -->
    <section ref="recordsCardRef" class="records-section">
      <el-card shadow="never" class="records-card">
        <template #header>
          <div class="card-title">
            <span>我的评估记录</span>
            <span class="records-count">
              共 {{ evaluationSummary.total }} 次评估 · 已完成 {{ evaluationSummary.completed }} 次 · 报告可查看 {{ evaluationSummary.reportReady }} 份
            </span>
          </div>
        </template>

        <el-alert
          v-if="recordsLoadFailed"
          type="error"
          :closable="false"
          show-icon
          title="评估记录加载失败"
          description="刷新页面可重试；若持续失败，请联系 HR 或权限管理员检查你的评估记录。"
        />

        <el-table
          v-else
          v-loading="reportLoading"
          :data="historyReports"
          border
          size="small"
          empty-text="暂无评估记录：上传简历后即可开始第一次能力评估"
        >
          <el-table-column label="评估时间" min-width="170">
            <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
          </el-table-column>
          <el-table-column label="流程状态" width="150">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="workflowTagType(row.workflowStatus)">
                {{ evaluationStatusText(row.workflowStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="报告状态" width="110">
            <template #default="{ row }">
              <!--
                必须同时看流程状态：报告未生成时，只有流程确实停在聚合审核/等级确认
                才说「审核中」；简历/测试/面试阶段说「评估中」——
                否则员工刚上传完简历就会看到「HR 审核中」（用户 2026-09-04 反馈）。
              -->
              <el-tag size="small" :type="reportStatusMeta(row.reportStatus, row.workflowStatus).tagType">
                {{ reportStatusMeta(row.reportStatus, row.workflowStatus).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="综合评分" width="100">
            <template #default="{ row }">
              <!--
                分数与报告正文同一闸门：HR 未把全部能力项审核完时后端不下发分数，
                这里显示「审核中 / 评估中」而不是 `--`，避免把「还没审完」读成「考了 0 分」。
              -->
              <span :class="{ 'score-pending': !reportStatusMeta(row.reportStatus, row.workflowStatus).viewable }">
                {{ overallScoreText(row.reportStatus, row.overallScore, row.workflowStatus) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button
                size="small"
                link
                type="primary"
                :disabled="!reportStatusMeta(row.reportStatus, row.workflowStatus).viewable"
                @click="viewReport(row.workflowId)"
              >
                查看报告
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <p class="records-hint">
          每次评估独立成一条记录。报告内容与综合评分需该次评估流程完成、且 HR 完成全部能力项审核后才可查看。
          「报告状态」显示「评估中」表示流程仍在简历解析 / AI 测试 / AI 面试阶段，「审核中」表示已进入
          HR 的聚合审核与等级确认；两种状态下「综合评分」都尚未定稿，不代表得分为零。
        </p>
      </el-card>
    </section>

    <!-- 右侧：审核说明。
         2026-09-04 口径变更：员工侧**不再展示任何审核期中间态** ——
         原先这里的「已确立能力 / 待确立能力」双 tab 会把未定稿的能力声明暴露给员工。
         员工的能力项统一在「我的能力画像」查看，且必须等 HR 把全部能力项审核完毕
         （评估报告已生成）后才可见。 -->
    <el-card shadow="never" class="evidence-card">
      <template #header>
        <div class="card-title"><span>能力审核说明</span></div>
      </template>
      <div class="review-note">
        <p class="review-note__title">{{ reviewNote.title }}</p>
        <p class="review-note__desc">{{ reviewNote.desc }}</p>
        <el-button
          v-if="reviewNote.showProfileButton"
          type="primary"
          plain
          size="small"
          @click="router.push('/employee/ability-profile')"
        >
          前往我的能力画像
        </el-button>
        <el-button
          v-else-if="reviewNote.tone === 'empty'"
          type="primary"
          plain
          size="small"
          :loading="actionLoading"
          @click="handleStartAssessment"
        >
          开始本次评估
        </el-button>
      </div>
    </el-card>

    <!-- 生成验证测试：选择目标岗位 -->
    <el-dialog v-model="generateTestDialog" title="生成验证测试" width="480px">
      <el-form label-width="90px">
        <el-form-item label="目标岗位">
          <el-select v-model="testPostId" placeholder="选择目标岗位" filterable style="width: 100%">
            <el-option v-for="p in postOptions" :key="p.id" :label="p.postName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon
          title="测试将基于简历能力与目标岗位生成，用于验证简历中的能力主张" />
      </el-form>
      <template #footer>
        <el-button @click="generateTestDialog = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="handleGenerateTest">生成</el-button>
      </template>
    </el-dialog>

    <!-- 报告详情弹窗 -->
    <el-dialog v-model="reportDetailVisible" title="全方位评估报告" width="760px" top="6vh">
      <report-content v-if="reportDetail && reportDetail.available" :report="reportDetail" />
      <el-empty
        v-else
        :image-size="100"
        :description="reportDetail?.unavailableReason || '报告暂时不可查看'"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  buildProvisionalSnapshot,
  createInterview,
  getActiveWorkflow,
  generateVerificationTest,
  getAssessmentProfile,
  getHarnessResults,
  getOrCreateActiveWorkflow,
  retryStage,
  type ProvisionalAbilitySnapshot,
  type WorkflowView,
} from '@/api/assessment'
import {
  getComprehensiveReportByWorkflow,
  listAssessmentReports,
  type ComprehensiveAssessmentReportDetail,
  type AssessmentReportListItem,
} from '@/api/assessment'
import { listConfiguredPostIds, listEnabledPosts } from '@/api'
import { getEmployee } from '@/api/employee'
import type { PostPost } from '@/api'
import { shouldPollByRunStatus, shouldPollWorkflowStatus } from './assessment-logic'
import {
  deriveReviewNote,
  evaluationStatusText,
  overallScoreText,
  reportStatusMeta,
  summarizeEvaluationRecords,
} from './assessment-records'
import { hasPermission } from '@/utils/permission'
import ReportContent from './assessment-report-content.vue'
import { useEmployeeScope } from '@/composables/useEmployeeScope'

const route = useRoute()
const router = useRouter()
const { empId, resolveEmployeeScope } = useEmployeeScope()
const employeeName = ref('')
/** 身份/档案解析失败的原因（常驻提示，不用一闪而过的 toast） */
const scopeError = ref('')

const workflow = ref<WorkflowView | null>(null)
const profile = ref<{
  confirmed: any[]
  provisional: any[]
  assessmentPending?: boolean
  assessmentState?: 'NOT_STARTED' | 'IN_PROGRESS' | 'PENDING_REVIEW' | 'READY'
}>({ confirmed: [], provisional: [] })
const harnessResults = ref<any[]>([])
const actionLoading = ref(false)

const generateTestDialog = ref(false)
const testPostId = ref<number>()
const postOptions = ref<PostPost[]>([])
let workflowPollTimer: number | null = null

const currentReport = ref<ComprehensiveAssessmentReportDetail | null>(null)
const historyReports = ref<AssessmentReportListItem[]>([])
const recordsCardRef = ref<HTMLElement | null>(null)
const reportLoading = ref(false)
/** 记录是否已加载完成：未完成前不对外下「有没有评估过」的结论 */
const recordsLoaded = ref(false)
/** 记录是否加载失败：与「没有记录」严格区分，前者不得显示空态结论 */
const recordsLoadFailed = ref(false)
const reportDetailVisible = ref(false)
const reportDetail = ref<ComprehensiveAssessmentReportDetail | null>(null)

/** 评估记录汇总（次数/已完成/报告可查看） */
const evaluationSummary = computed(() => summarizeEvaluationRecords(historyReports.value))

/**
 * 最近一次评估的流程状态：优先当前活跃流程，其次记录列表首行（按开始时间倒序）。
 *
 * 用途：区分「HR 真的在审核」与「流程还在推进」—— 后者不该显示 HR 审核文案。
 * 记录行内各自的报告状态不看这个值（用行自己的 workflowStatus），
 * 这里只服务「能力审核说明」这张汇总卡片。
 */
const latestWorkflowStatus = computed<string | null>(
  () => workflow.value?.workflowStatus
    ?? workflow.value?.status
    ?? historyReports.value[0]?.workflowStatus
    ?? null,
)

/**
 * 「能力审核说明」文案由真实数据推导。
 *
 * 历史 bug：文案只看 `profile.assessmentPending`，而该字段来自「存在进行中流程时」才发出的请求，
 * 因此从未评估过的员工会看到「能力审核已完成…评估报告已生成」。现在先看有没有评估记录。
 * 注意后端闸门把「从未评估」与「审核未清空」都算作 assessmentPending=true，
 * 所以必须与 hasRecords 联合判断，不能单独依赖它。
 */
const reviewNote = computed(() => deriveReviewNote({
  recordsLoaded: recordsLoaded.value,
  recordsLoadFailed: recordsLoadFailed.value,
  hasRecords: historyReports.value.length > 0,
  assessmentPending: Boolean(profile.value.assessmentPending),
  reportReady: currentReport.value != null || historyReports.value[0]?.reportStatus === 'READY',
  // 后端闸门把「从未评估」与「审核未清空」都算 assessmentPending=true，
  // 但后者可能是「流程还在简历/测试/面试」。传流程状态，由纯函数决定说不说「HR 正在审核」。
  latestWorkflowStatus: latestWorkflowStatus.value,
}))

/** 记录行内流程状态的标签颜色 */
function workflowTagType(status?: string | null): 'success' | 'danger' | 'primary' | 'info' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED' || status === 'REVIEW_REQUIRED') return 'danger'
  if (status === 'CANCELLED') return 'info'
  return 'primary'
}

const STAGE_ORDER = ['RESUME', 'TEST', 'INTERVIEW', 'AGGREGATE', 'CONFIRM']

function stageOf(status?: string): number {
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
      return 0
  }
}

const activeStep = computed(() => {
  const s = workflow.value?.status
  if (s === 'COMPLETED' || s === 'REVIEW_REQUIRED') return 5
  return stageOf(s)
})

const statusLabel = computed(() => workflow.value?.displayStatus ?? workflow.value?.status ?? '—')

const statusTagType = computed(() => {
  const s = workflow.value?.status
  if (s === 'COMPLETED') return 'success'
  if (s === 'FAILED' || s === 'RECOVERY_REQUIRED' || s === 'REVIEW_REQUIRED') return 'danger'
  return 'primary'
})

const workspaceTitle = computed(() => {
  const s = workflow.value?.status
  const display = workflow.value?.displayStatus
  const map: Record<string, string> = {
    RESUME_REQUIRED: '阶段 1：简历上传',
    RESUME_PARSING: '阶段 1：简历解析中',
    RESUME_EVIDENCE_READY: '阶段 1：简历证据就绪',
    TEST_GENERATING: '阶段 2：测试生成中',
    TEST_IN_PROGRESS: '阶段 2：测试进行中',
    TEST_EVALUATING: '阶段 2：测试评分中',
    TEST_EVIDENCE_READY: '阶段 2：测试证据就绪',
    INTERVIEW_PREPARING: '阶段 3：面试准备中',
    INTERVIEW_IN_PROGRESS: '阶段 3：面试进行中',
    INTERVIEW_ANALYZING: '阶段 3：面试分析中',
    AGGREGATE_HARNESS_RUNNING: '阶段 4：聚合审核中',
    LEVEL_CONFIRMING: '阶段 5：等级确认中',
    COMPLETED: '评估已完成',
    REVIEW_REQUIRED: '阶段 5：人工复核',
    RECOVERY_REQUIRED: '流程待恢复',
    FAILED: '流程失败',
  }
  // 展示标题以后端 displayStatus 为准（前端不再自行推断业务状态）
  return map[s ?? ''] ?? display ?? '能力评估流程'
})

// Action visibility is guarded by the workflow state as well as the backend
// action list. This prevents stale availableActions from exposing entry points
// while an asynchronous stage is already running.
const canEnterTest = computed(() => workflow.value?.status === 'TEST_IN_PROGRESS')
const canCreateInterview = computed(() => {
  const workflowState = workflow.value?.status
  if (!workflow.value?.availableActions?.includes('CREATE_INTERVIEW')) return false
  return workflowState === 'TEST_EVIDENCE_READY'
    || workflowState === 'INTERVIEW_PREPARING'
    || workflowState === 'INTERVIEW_IN_PROGRESS'
})

function formatTime(t?: string): string {
  if (!t) return '—'
  return t.replace('T', ' ').slice(0, 19)
}

async function loadWorkflow(createIfMissing = false) {
  // 无 empId 时必须显式说明原因：早期版本直接 return，用户点「开始评估」看到的
  // 是"什么都没发生"。后端 /employee/me 已按需建档，走到这里仍为空说明建档失败
  // 或调用方未等待身份解析 —— 两种情况都要让人看得见，而不是静默。
  if (!empId.value) {
    scopeError.value = '当前账号尚未绑定人员档案，无法发起评估，请稍后重试或联系管理员'
    return
  }
  scopeError.value = ''
  if (!employeeName.value) {
    try {
      const employee = await getEmployee(empId.value)
      employeeName.value = employee.data?.realName || ''
    } catch {
      employeeName.value = ''
    }
  }
  const res = createIfMissing
    ? await getOrCreateActiveWorkflow(empId.value)
    : await getActiveWorkflow(empId.value)
  workflow.value = res.data ?? null
  // 画像/闸门状态必须**无条件**拉取：没有活跃流程时（例如已完成待审核、或从未评估）
  // profile 若一直为空，assessmentPending 就会恒为 undefined，说明文案会退化成错误结论。
  loadProfile()
  if (workflow.value) {
    loadCurrentReport()
    if (workflow.value.status === 'AGGREGATE_HARNESS_RUNNING' || workflow.value.status === 'REVIEW_REQUIRED' || workflow.value.status === 'LEVEL_CONFIRMING' || workflow.value.status === 'COMPLETED') {
      getHarnessResults(workflow.value.workflowId).then((r) => (harnessResults.value = r.data ?? [])).catch(() => {})
    }
    if (shouldPoll()) {
      startWorkflowPolling()
    } else {
      stopWorkflowPolling()
    }
  } else {
    stopWorkflowPolling()
  }
}

async function handleStartAssessment() {
  actionLoading.value = true
  try {
    // 先确保身份已解析：超管/平台管理员没有内置档案，需要 /employee/me 按需建档。
    // 不先解析就调用会带着 empId=0 打接口，白白失败一次。
    if (!empId.value) {
      await resolveEmployeeScope()
    }
    await loadWorkflow(true)
    if (workflow.value) {
      ElMessage.success('已创建本次能力评估')
    }
  } catch {
    ElMessage.error('暂时无法开始评估，请稍后再试')
  } finally {
    actionLoading.value = false
  }
}

function shouldPoll(): boolean {
  if (!workflow.value) return false
  // 工作流活跃 + 阶段运行在途（PENDING/RUNNING）才轮询；WAITING_USER 不轮询高频
  if (!shouldPollWorkflowStatus(workflow.value.workflowStatus ?? workflow.value.status)) return false
  const runStatus = workflow.value.currentStageDetail?.runStatus
  if (runStatus && !shouldPollByRunStatus(runStatus)) return false
  return true
}

function startWorkflowPolling() {
  stopWorkflowPolling()
  workflowPollTimer = window.setInterval(async () => {
    if (!shouldPoll()) {
      stopWorkflowPolling()
      return
    }
    try {
      await loadWorkflow()
    } catch {
      // Keep the last visible state; the next tick retries.
    }
  }, 2000)
}

function stopWorkflowPolling() {
  if (workflowPollTimer !== null) {
    window.clearInterval(workflowPollTimer)
    workflowPollTimer = null
  }
}

async function loadProfile() {
  try {
    const res = await getAssessmentProfile(empId.value)
    profile.value = res.data ?? { confirmed: [], provisional: [] }
    // 员工侧闸门由后端统一判定（HR 代看时为 false），前端只用于展示等待态与隐藏报告入口
    if (res.data?.assessmentPending) {
      currentReport.value = null
    }
  } catch { /* ignore */ }
}

async function loadCurrentReport() {
  if (!workflow.value?.workflowId || empId.value == null) return
  try {
    const res = await getComprehensiveReportByWorkflow(empId.value, workflow.value.workflowId)
    // 后端闸门未放行时返回 available=false（流程未完成 / 审核未清空），
    // 对摘要区而言与「还没有报告」等价，统一收敛为 null。
    currentReport.value = res.data?.available ? res.data : null
  } catch { /* 未生成或失败时保持 null */ }
}

async function loadRecords() {
  if (empId.value == null) return
  reportLoading.value = true
  recordsLoadFailed.value = false
  try {
    const res = await listAssessmentReports(empId.value)
    historyReports.value = res.data ?? []
    recordsLoaded.value = true
  } catch {
    // 失败时保持 recordsLoaded=false：让说明卡片走「无法读取」而不是空态结论
    historyReports.value = []
    recordsLoadFailed.value = true
    recordsLoaded.value = true
  } finally {
    reportLoading.value = false
  }
}

/** 跳到「我的评估记录」区块（顶栏入口与 ?history=1 深链共用） */
function scrollToRecords() {
  recordsCardRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function openReportDetail(report: ComprehensiveAssessmentReportDetail) {
  reportDetail.value = report
  reportDetailVisible.value = true
}

async function viewReport(workflowId: number) {
  if (empId.value == null) return
  try {
    const res = await getComprehensiveReportByWorkflow(empId.value, workflowId)
    const detail = res.data
    // 后端用 available=false + 原因表达「还没到时候」（流程未完成 / 审核未清空），
    // 这是正常业务状态，按原因提示而不是报「加载失败」。
    if (!detail || !detail.available) {
      ElMessage.warning(detail?.unavailableReason || '评估结果尚未完成审核，请等待 HR 审核完成后查看')
      return
    }
    openReportDetail(detail)
  } catch {
    ElMessage.error('报告暂时无法加载，请稍后再试')
  }
}

async function openGenerateTest() {
  generateTestDialog.value = true
  if (!postOptions.value.length) {
    try {
      const res = await listEnabledPosts()
      const posts = res.data ?? []
      const configured = await listConfiguredPostIds(posts.map(post => post.id))
      const configuredIds = new Set(configured.data ?? [])
      postOptions.value = posts.filter(post => configuredIds.has(post.id))
      if (!postOptions.value.length) {
        ElMessage.warning('当前没有已配置能力模型的启用岗位')
      }
    } catch { /* ignore */ }
  }
}

async function handleGenerateTest() {
  if (!workflow.value) {
    return
  }
  if (!testPostId.value) {
    ElMessage.warning('请选择已配置能力模型的目标岗位')
    return
  }
  actionLoading.value = true
  try {
    const res = await generateVerificationTest(workflow.value.workflowId, testPostId.value)
    const data = res.data
    if (data?.testId) {
      ElMessage.success('验证测试已生成，即将跳转到测试页面')
      generateTestDialog.value = false
      router.push({ path: '/employee/ability-profile/ai-test', query: {
        empId: empId.value,
        testId: data.testId,
        workflowId: workflow.value.workflowId,
        fromAssessment: '1',
        refresh: String(Date.now()),
      } })
    } else {
      ElMessage.success('验证测试已生成，测试进行中')
      generateTestDialog.value = false
      goTest()
    }
  } catch {
    ElMessage.error('测试暂时无法生成，请稍后再试')
  } finally {
    actionLoading.value = false
  }
}

async function handleCreateInterview() {
  if (!workflow.value) return
  actionLoading.value = true
  try {
    const res = await createInterview(workflow.value.workflowId)
    const data = res.data
    if (data?.sessionId) {
      ElMessage.success('AI 面试已创建，即将跳转到面试页面')
      router.push({ path: '/employee/ability-profile/live-interview', query: {
        empId: empId.value,
        sessionId: data.sessionId,
        postId: data.postId,
        workflowId: workflow.value.workflowId,
        fromAssessment: '1',
        refresh: String(Date.now()),
      } })
    } else {
      ElMessage.success('AI 面试已创建，请前往面试')
      router.push({ path: '/employee/ability-profile/live-interview', query: {
        empId: empId.value,
        workflowId: workflow.value.workflowId,
        fromAssessment: '1',
        refresh: String(Date.now()),
      } })
    }
  } catch {
    ElMessage.error('面试暂时无法创建，请稍后再试')
  } finally {
    actionLoading.value = false
  }
}

/**
 * 是否可查看「面试过程记录」（逐题问答/转写）。
 *
 * 2026-09-04 口径：员工只能看面试**评价**，过程记录不向员工开放 ——
 * 面试报告已并入唯一的「评估报告」，过程记录作为报告内入口且仅 HR 可见。
 * 用 EMPLOYEE:READ 区分：HR 持有，员工不持有。
 */
const canViewInterviewRecord = computed(() => hasPermission('EMPLOYEE:READ'))

/** 面试相关状态：展示"查看面试记录"入口（面试中/分析/聚合/等级确认/完成） */
const interviewStatuses = [
  'INTERVIEW_PREPARING',
  'INTERVIEW_IN_PROGRESS',
  'INTERVIEW_ANALYZING',
  'AGGREGATE_HARNESS_RUNNING',
  'LEVEL_CONFIRMING',
  'COMPLETED',
  'REVIEW_REQUIRED',
]

function goInterviewRecords() {
  if (!empId.value) return
  router.push({ path: '/employee/ability-profile/live-interview', query: { empId: empId.value } })
}

async function handleRetry() {
  if (!workflow.value?.currentStage) return
  try {
    await ElMessageBox.confirm('将从当前阶段继续，不会重复已完成的内容。', '重新尝试', { type: 'warning' })
  } catch {
    return
  }
  actionLoading.value = true
  try {
    await retryStage(workflow.value.workflowId, workflow.value.currentStage)
    ElMessage.success('已投递重试任务')
    await loadWorkflow()
  } catch {
    ElMessage.error('暂时无法继续，请稍后再试')
  } finally {
    actionLoading.value = false
  }
}

function goResume() {
  router.push({ path: '/employee/ability-profile/resume-parse', query: {
    empId: empId.value,
    workflowId: workflow.value?.workflowId,
    fromAssessment: '1',
    refresh: String(Date.now()),
  } })
}

function goTest() {
  if (!workflow.value) return
  router.push({ path: '/employee/ability-profile/ai-test', query: {
    empId: empId.value,
    workflowId: workflow.value.workflowId,
    fromAssessment: '1',
    refresh: String(Date.now()),
  } })
}

onMounted(async () => {
  await resolveEmployeeScope()
  // 记录列表与流程并行拉取：说明卡片依赖记录判定，不能等流程加载完才发起
  await Promise.all([loadWorkflow(), loadRecords()])
  // 深层链接（如能力编辑页「查看评估历史」）直接定位到记录列表
  if (route.query.history === '1') {
    scrollToRecords()
  }
})

onActivated(async () => {
  await Promise.all([loadWorkflow(), loadRecords()])
})

onUnmounted(() => {
  stopWorkflowPolling()
})
</script>

<style scoped>
.scope-alert {
  margin-bottom: 12px;
}
.assessment-page {
  padding: 16px;
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}
.header-left h2 {
  margin: 0 0 8px;
}
.emp-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.time {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.card-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.completed-actions {
  text-align: center;
  margin-top: 8px;
}
.mt {
  margin-top: 12px;
}
.report-summary {
  padding: 8px 0 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 12px;
}
.report-summary .hint { margin: 8px 0 0; }

/* 能力审核说明：替代原先的「已确立 / 待确立能力」双 tab（员工侧不展示审核中间态） */
.review-note {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}
.review-note__title {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--app-text-strong, #1a2440);
}
.review-note__desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.9;
  color: var(--app-text-muted, #8b95ab);
}

/* 我的评估记录：头部左侧标题 + 右侧次数汇总，表格下方一行口径说明 */
.records-section {
  scroll-margin-top: 12px;
}
.records-count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}
.records-hint {
  margin: 10px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.7;
}

/* 综合评分尚未定稿（HR 审核未清空）时的占位样式：明显弱于真实分数，不会被误读成得分 */
.score-pending {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
