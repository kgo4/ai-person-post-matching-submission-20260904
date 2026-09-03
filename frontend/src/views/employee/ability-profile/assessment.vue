<template>
  <div class="assessment-page">
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
          <el-tag v-if="riskCount > 0" type="danger" effect="plain">风险能力 {{ riskCount }} 项</el-tag>
          <el-button v-if="showReportEntry" size="small" :type="currentReport ? 'primary' : 'info'" @click="openHistoryReports">
            {{ reportEntryLabel }}
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
          <el-button v-if="interviewStatuses.includes(workflow?.status ?? '')" size="small" @click="goInterviewRecords">
            查看面试记录
          </el-button>
          <el-button v-if="workflow?.availableActions?.includes('RETRY_FAILED_STAGE')" type="warning" size="small" :loading="actionLoading" @click="handleRetry">
            重新尝试
          </el-button>
        </div>
      </template>

      <div v-if="currentReport" class="report-summary">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="综合评分">{{ currentReport.overallScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="结论">{{ currentReport.conclusion || '—' }}</el-descriptions-item>
          </el-descriptions>
        <el-button size="small" type="primary" link @click="openReportDetail(currentReport)">查看完整报告</el-button>
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
        <el-alert type="warning" :closable="false" title="等待 Harness 人工审核" description="审核通过后系统将自动融合最终等级并更新能力画像；拦截项仅保留审计记录。" />
      </div>
      <div v-else class="stage-hint">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="当前阶段">{{ workspaceTitle }}</el-descriptions-item>
          <el-descriptions-item label="流程状态">{{ statusLabel }}</el-descriptions-item>
          <el-descriptions-item label="下一步提示" :span="2">{{ workflow.nextStepHint || '—' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>

    <!-- 右侧：证据与能力摘要 -->
    <el-card shadow="never" class="evidence-card">
      <template #header>
        <div class="card-title"><span>证据与能力摘要</span></div>
      </template>
      <el-tabs v-model="profileTab">
        <el-tab-pane label="已确立能力" name="confirmed">
          <el-empty v-if="!profile.confirmed?.length" description="暂无已确立能力" :image-size="60" />
          <el-table v-else :data="profile.confirmed" size="small" border>
            <el-table-column prop="abilityName" label="能力" show-overflow-tooltip />
            <el-table-column prop="finalLevel" label="等级" width="60" />
            <el-table-column prop="confidenceScore" label="置信度" width="80" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="待确立能力" name="provisional">
          <el-empty v-if="!profile.provisional?.length" description="暂无待确立能力" :image-size="60" />
          <el-table v-else :data="profile.provisional" size="small" border>
            <el-table-column prop="abilityName" label="能力" show-overflow-tooltip />
            <el-table-column prop="claimedLevel" label="声明等级" width="90" />
            <el-table-column prop="evidenceCount" label="来源数" width="70" />
            <el-table-column prop="riskLabel" label="风险标签" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="riskTagType(row.riskLabel)">{{ row.riskLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="nextAction" label="下一步" width="100" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
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

    <!-- 每行对应一次人员能力评估，不混入普通标签或数据治理记录。 -->
    <el-drawer v-model="historyDrawerVisible" title="评估流程历史" size="560px">
      <el-table :data="historyReports" v-loading="reportLoading" border size="small">
        <el-table-column label="评估时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="流程状态" width="130">
          <template #default="{ row }">{{ row.workflowStatus }}</template>
        </el-table-column>
        <el-table-column label="报告状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.reportStatus === 'READY'" type="success" size="small">已生成</el-tag>
            <el-tag v-else-if="row.reportStatus === 'FAILED'" type="danger" size="small">生成失败</el-tag>
            <el-tag v-else type="info" size="small">未生成</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="综合评分" width="90">
          <template #default="{ row }">{{ row.overallScore ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="row.reportStatus === 'READY'" size="small" link type="primary" @click="viewReport(row.workflowId)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 报告详情弹窗 -->
    <el-dialog v-model="reportDetailVisible" title="评估报告" width="760px" top="6vh">
      <report-content v-if="reportDetail" :report="reportDetail" />
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
  getAssessmentReport,
  listAssessmentReports,
  type AssessmentReportDetail,
  type AssessmentReportListItem,
} from '@/api/assessment'
import { listConfiguredPostIds, listEnabledPosts } from '@/api'
import { getEmployee } from '@/api/employee'
import type { PostPost } from '@/api'
import { shouldPollByRunStatus, shouldPollWorkflowStatus } from './assessment-logic'
import ReportContent from './assessment-report-content.vue'

const route = useRoute()
const router = useRouter()
const empId = computed(() => Number(route.query.empId ?? 0))
const employeeName = ref('')

const workflow = ref<WorkflowView | null>(null)
const profile = ref<{ confirmed: any[]; provisional: any[] }>({ confirmed: [], provisional: [] })
const harnessResults = ref<any[]>([])
const profileTab = ref('confirmed')
const actionLoading = ref(false)

const generateTestDialog = ref(false)
const testPostId = ref<number>()
const postOptions = ref<PostPost[]>([])
let workflowPollTimer: number | null = null

const currentReport = ref<AssessmentReportDetail | null>(null)
const historyReports = ref<AssessmentReportListItem[]>([])
const historyDrawerVisible = ref(false)
const reportLoading = ref(false)
const reportDetailVisible = ref(false)
const reportDetail = ref<AssessmentReportDetail | null>(null)

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

const riskCount = computed(() => profile.value.provisional?.length ?? 0)

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

/** 面试结束分析中起展示报告入口；报告已生成时按钮为主色 */
const showReportEntry = computed(() => {
  const s = workflow.value?.status
  return ['INTERVIEW_ANALYZING', 'AGGREGATE_HARNESS_RUNNING', 'LEVEL_CONFIRMING', 'COMPLETED', 'REVIEW_REQUIRED'].includes(s ?? '')
})
const reportEntryLabel = computed(() => {
  if (!workflow.value) return '评估报告'
  if (workflow.value.status === 'INTERVIEW_ANALYZING' && !currentReport.value) return '评估报告生成中…'
  return '评估报告'
})

function riskTagType(label?: string): 'warning' | 'danger' | 'info' {
  if (label?.includes('Harness') || label?.includes('冲突')) return 'danger'
  if (label?.includes('待')) return 'warning'
  return 'info'
}

function formatTime(t?: string): string {
  if (!t) return '—'
  return t.replace('T', ' ').slice(0, 19)
}

async function loadWorkflow(createIfMissing = false) {
  if (!empId.value) return
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
  if (workflow.value) {
    loadProfile()
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
  } catch { /* ignore */ }
}

async function loadCurrentReport() {
  if (!workflow.value?.workflowId) return
  try {
    const res = await getAssessmentReport(workflow.value.workflowId)
    currentReport.value = res.data ?? null
  } catch { /* 未生成或失败时保持 null */ }
}

async function openHistoryReports() {
  historyDrawerVisible.value = true
  reportLoading.value = true
  try {
    const res = await listAssessmentReports(empId.value)
    historyReports.value = res.data ?? []
  } catch { /* ignore */ } finally {
    reportLoading.value = false
  }
}

function openReportDetail(report: AssessmentReportDetail) {
  reportDetail.value = report
  reportDetailVisible.value = true
}

async function viewReport(workflowId: number) {
  try {
    const res = await getAssessmentReport(workflowId)
    openReportDetail(res.data ?? (null as any))
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
  if (route.query.tab === 'provisional') {
    profileTab.value = 'provisional'
  }
  await loadWorkflow()
  if (route.query.history === '1') {
    await openHistoryReports()
  }
})

onActivated(async () => {
  await loadWorkflow()
})

onUnmounted(() => {
  stopWorkflowPolling()
})
</script>

<style scoped>
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
</style>
