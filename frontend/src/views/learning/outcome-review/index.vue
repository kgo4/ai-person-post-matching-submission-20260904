<script setup lang="ts">
/**
 * HR 学习成果复核（人岗匹配闭环设计 P4）。
 *
 * 核心口径：
 *   员工提交学习成果后进入「待复核」，**此时不回写能力证据**；
 *   HR 通过 → 走既有能力证据回写链路，能力画像更新；
 *   HR 驳回 → 必填理由退回，员工可修改后重新提交（新单据，旧单留痕）。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approveLearningOutcome,
  getLearningOutcomeContext,
  getPendingLearningOutcomeCount,
  learningOutcomeStatusTagType,
  learningOutcomeStatusText,
  pageLearningOutcomeReviews,
  rejectLearningOutcome,
  LEARNING_OUTCOME_STATUS,
} from '@/api/learning-outcome'
import type {
  LearningOutcomeContext,
  LearningOutcomeSubmission,
} from '@/api/learning-outcome'
import { getStatusMeta } from '@/components/learning/types'
// 学习记录摘要与员工侧「提交前自检」共用同一份实现：两边各写一份必然漂移，
// 会出现「员工提交时看到记录完整、HR 复核时看到记录不完整」这种自相矛盾。
import {
  summarizeLearningRecord,
  type LearningRecordInput,
} from '@/views/learning/my-path/my-learning-path'
// 材料地址由员工自由填写，渲染成可点链接前必须过协议白名单校验
import { buildMaterialLinks, formatSubmittedAt, materialLinkHint } from './material-links'

const loading = ref(false)
const error = ref('')
const rows = ref<LearningOutcomeSubmission[]>([])
const total = ref(0)
const pendingCount = ref(0)

const query = reactive({
  current: 1,
  size: 10,
  reviewStatus: '' as number | '',
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await pageLearningOutcomeReviews({
      current: query.current,
      size: query.size,
      reviewStatus: query.reviewStatus === '' ? undefined : query.reviewStatus,
    })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e: any) {
    rows.value = []
    total.value = 0
    error.value = e?.message || '学习成果提交列表加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loadPendingCount() {
  try {
    const res = await getPendingLearningOutcomeCount()
    pendingCount.value = res.data ?? 0
  } catch {
    pendingCount.value = 0
  }
}

function handleSearch() {
  query.current = 1
  load()
  loadPendingCount()
}

/** 复核通过：提示中明确「通过后立即回写」，避免 HR 误以为是仅标记 */
async function handleApprove(row: LearningOutcomeSubmission) {
  try {
    const { value } = await ElMessageBox.prompt(
      `确认通过「${empLabel(row)}」的「${abilityLabel(row)}」学习成果吗？通过后能力证据立即回写，能力画像将更新。`,
      '复核通过',
      {
        confirmButtonText: '确认通过',
        cancelButtonText: '取消',
        type: 'success',
        inputPlaceholder: '复核意见（可选）',
      },
    )
    await approveLearningOutcome(row.id, value?.trim() || undefined)
    ElMessage.success('已通过复核，能力证据已回写')
    handleSearch()
  } catch {
    // 用户取消
  }
}

/** 复核驳回：理由必填，员工侧可见 */
async function handleReject(row: LearningOutcomeSubmission) {
  try {
    const { value } = await ElMessageBox.prompt(
      `驳回「${abilityLabel(row)}」的学习成果，请填写理由（员工可见）。`,
      '复核驳回',
      {
        confirmButtonText: '确认驳回',
        cancelButtonText: '取消',
        type: 'warning',
        inputPlaceholder: '驳回理由（必填）',
        inputValidator: (v: string) => (v && v.trim().length > 0) || '驳回理由不能为空',
      },
    )
    await rejectLearningOutcome(row.id, value.trim())
    ElMessage.success('已驳回，员工可修改后重新提交')
    handleSearch()
  } catch {
    // 用户取消
  }
}

function empLabel(row: LearningOutcomeSubmission) {
  return row.empName || `员工#${row.empId}`
}

function abilityLabel(row: LearningOutcomeSubmission) {
  return row.abilityName || (row.tagId != null ? `能力#${row.tagId}` : '未知能力')
}

function levelText(level: number | null | undefined) {
  return level == null ? '--' : `L${level}`
}

/* ===================== 学习情况（提交单是唯一入口） ===================== */

const contextVisible = ref(false)
const contextLoading = ref(false)
const contextError = ref('')
const context = ref<LearningOutcomeContext | null>(null)
const contextAbility = ref('')

/**
 * 学习记录入参 —— 与员工侧「提交前自检」用同一套字段，
 * 保证「员工看到的记录」与「HR 看到的记录」是同一个结论。
 */
const contextRecordInput = computed<LearningRecordInput>(() => {
  const data = context.value
  if (!data) return {}
  const step = data.steps.find(item => item.stepId === data.appliedStepId) ?? null
  const scored = data.assessments.filter(
    item => item.assessmentStatus === 'PASSED' || item.assessmentStatus === 'NOT_PASSED',
  )
  // 多条评估题以最近一条已判分的为准，与员工侧口径一致
  const latest = scored.length > 0 ? scored[scored.length - 1] : null
  return {
    stepCompleted: step?.status === 'COMPLETED',
    assessmentStatus: latest?.assessmentStatus ?? data.assessments[0]?.assessmentStatus ?? null,
    assessmentScore: latest?.score ?? null,
    resourceCount: step?.resourceCount ?? null,
    completedResourceCount: step?.status === 'COMPLETED' ? (step?.resourceCount ?? 0) : 0,
  }
})

const contextRecordSummary = computed(() => summarizeLearningRecord(contextRecordInput.value))

const contextProgressText = computed(() => {
  const data = context.value
  if (!data || data.totalStepCount == null) return '尚无学习计划'
  return `${data.completedStepCount ?? 0} / ${data.totalStepCount} 步已完成`
})

/**
 * 项目材料 + 链接预处理。
 *
 * 后端的材料已把「属于本次申请步骤的」排在前面，这里不再重排；
 * 并把每条材料的三个地址转成「可点 / 只能看原文」两种形态。
 */
const contextMaterials = computed(() => {
  return (context.value?.projectMaterials ?? []).map(material => {
    const links = buildMaterialLinks(material)
    return {
      material,
      links,
      hint: materialLinkHint(links),
      submittedText: formatSubmittedAt(material.submittedTime),
    }
  })
})

/** 打开某条提交单对应的学习情况。入口是提交单，HR 无法绕过它直接翻员工的学习路径 */
async function loadContext(row: LearningOutcomeSubmission) {
  contextVisible.value = true
  contextAbility.value = abilityLabel(row)
  contextLoading.value = true
  contextError.value = ''
  context.value = null
  try {
    const res = await getLearningOutcomeContext(row.id)
    context.value = res.data ?? null
  } catch (e: any) {
    contextError.value = e?.message || '加载学习情况失败，请稍后重试'
  } finally {
    contextLoading.value = false
  }
}

function assessmentTone(status?: string | null) {
  if (status === 'PASSED') return 'success'
  if (status === 'NOT_PASSED') return 'danger'
  return 'info'
}

function assessmentText(status?: string | null) {
  if (status === 'PASSED') return '已通过'
  if (status === 'NOT_PASSED') return '未通过'
  if (status === 'PENDING') return '待判分'
  return '未作答'
}

onMounted(() => {
  load()
  loadPendingCount()
})
</script>

<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <span class="page-hero__eyebrow">Learning Review</span>
        <h1 class="page-hero__title">学习成果复核</h1>
        <p class="page-hero__desc">
          员工提交能力提升申请后进入待复核，此时不会回写能力证据；通过后才走能力证据回写链路，
          能力画像随之更新；驳回需填写理由，员工可修改后重新提交。
          点「学习情况」可查看他的学习路径、评估题记录与项目材料 —— 项目材料由员工自行提交，
          <strong>不单独复核</strong>，是判断能力提升的主要佐证。
          HR 默认看不到员工的学习路径，<strong>申请单是唯一入口</strong>。
        </p>
        <div class="page-hero__meta">
          <span class="hero-chip">待复核：{{ pendingCount }} 条</span>
          <span class="hero-chip">当前筛选：{{ total }} 条</span>
        </div>
      </div>
      <div class="mr__actions">
        <el-select v-model="query.reviewStatus" placeholder="复核状态" clearable style="width: 140px;">
          <el-option label="待复核" :value="LEARNING_OUTCOME_STATUS.PENDING" />
          <el-option label="已通过" :value="LEARNING_OUTCOME_STATUS.APPROVED" />
          <el-option label="已驳回" :value="LEARNING_OUTCOME_STATUS.REJECTED" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </div>
    </section>

    <el-alert v-if="error" :title="error" type="warning" show-icon :closable="false" />

    <section class="glass-card">
      <div class="panel-body">
        <el-table :data="rows" v-loading="loading" style="width: 100%">
          <el-table-column label="员工" min-width="140">
            <template #default="{ row }">{{ empLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="能力项" min-width="180">
            <template #default="{ row }">{{ abilityLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="等级变化" width="120">
            <template #default="{ row }">
              {{ levelText(row.beforeLevel) }} → {{ levelText(row.confirmedLevel) }}
            </template>
          </el-table-column>
          <el-table-column prop="note" label="提交说明" min-width="200" show-overflow-tooltip />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="learningOutcomeStatusTagType(row.reviewStatus)" effect="plain" round>
                {{ learningOutcomeStatusText(row.reviewStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="复核意见" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.reviewComment || '--' }}</template>
          </el-table-column>
          <el-table-column prop="createdTime" label="提交时间" width="170" />
          <el-table-column label="操作" width="215" fixed="right">
            <template #default="{ row }">
              <!-- 学习情况对所有状态可见：复核完也可能回看当时是依据什么通过的 -->
              <el-button type="primary" link @click="loadContext(row)">学习情况</el-button>
              <template v-if="row.reviewStatus === LEARNING_OUTCOME_STATUS.PENDING">
                <el-button type="success" link @click="handleApprove(row)">通过</el-button>
                <el-button type="danger" link @click="handleReject(row)">驳回</el-button>
              </template>
              <span v-else class="outcome-review__done">已处理</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && rows.length === 0" description="暂无学习成果提交" />
      </div>

      <div class="panel-footer">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="query.current"
          :page-size="query.size"
          :page-sizes="[10, 20, 50]"
          @current-change="(p: number) => { query.current = p; load() }"
          @size-change="(s: number) => { query.size = s; query.current = 1; load() }"
        />
      </div>
    </section>

    <!--
      学习情况抽屉。
      🔴 可见性闸门：HR 默认看不到员工的学习路径，**提交单是唯一入口** ——
      这条申请是员工主动发起的，所以此时展示他的路径进展与验证记录是合理的；
      不存在「按员工直接查学习路径」的入口。
    -->
    <el-drawer
      v-model="contextVisible"
      :title="`学习情况 · ${contextAbility}`"
      size="720px"
      destroy-on-close
    >
      <div v-loading="contextLoading" class="or-context">
        <el-alert
          v-if="contextError"
          type="error"
          show-icon
          :closable="false"
          :title="contextError"
        />

        <template v-if="context">
          <div class="or-context__head">
            <span class="or-context__name">{{ context.empName || `员工#${context.empId}` }}</span>
            <span class="or-context__muted">目标岗位：{{ context.postName || '—' }}</span>
            <span class="or-context__muted">{{ contextProgressText }}</span>
          </div>

          <el-alert type="info" show-icon :closable="false" :title="contextRecordSummary.text">
            <template #default>
              <div class="or-context__record">
                <div v-for="(line, index) in contextRecordSummary.items" :key="index">{{ line }}</div>
              </div>
            </template>
          </el-alert>

          <div class="or-context__title">学习路径</div>
          <p v-if="context.steps.length === 0" class="or-context__muted">
            该员工在此匹配下还没有生成学习计划。
          </p>
          <div v-else class="or-context__steps">
            <div
              v-for="step in context.steps"
              :key="step.stepId"
              class="or-context__step"
              :class="{ 'is-applied': step.applied }"
            >
              <div class="or-context__step-main">
                <span class="or-context__step-name">{{ step.abilityName || '未命名能力' }}</span>
                <el-tag size="small" effect="plain" round>{{ getStatusMeta(step.status || '').label }}</el-tag>
                <el-tag v-if="step.applied" size="small" type="warning" effect="plain" round>本次申请</el-tag>
              </div>
              <div class="or-context__step-sub">
                L{{ step.currentLevel ?? '--' }} → L{{ step.targetLevel ?? '--' }}
                <template v-if="step.resourceCount != null"> · 资源 {{ step.resourceCount }} 个</template>
              </div>
            </div>
          </div>

          <div class="or-context__title">验证记录（本次申请的能力）</div>
          <p v-if="context.appliedStepId === null" class="or-context__muted">
            该能力在当前学习计划里没有对应步骤，无法关联验证记录。
          </p>
          <p v-else-if="context.assessments.length === 0" class="or-context__muted">
            该步骤还没有评估题记录。
          </p>
          <div v-else class="or-context__assessments">
            <div v-for="item in context.assessments" :key="item.id" class="or-context__assessment">
              <div class="or-context__assessment-head">
                <el-tag size="small" :type="assessmentTone(item.assessmentStatus)" effect="plain" round>
                  {{ assessmentText(item.assessmentStatus) }}
                </el-tag>
                <span class="or-context__muted">{{ item.questionType || '题目' }}</span>
                <span v-if="item.score != null" class="or-context__score">{{ item.score }} 分</span>
              </div>
              <div v-if="item.answerText" class="or-context__answer">{{ item.answerText }}</div>
              <div v-if="item.scoringFeedback" class="or-context__feedback">
                点评：{{ item.scoringFeedback }}
              </div>
            </div>
          </div>

          <div class="or-context__title">项目材料（员工自提，未经独立复核）</div>
          <p v-if="contextMaterials.length === 0" class="or-context__muted">
            该员工在这份学习计划下还没有提交项目材料。项目材料不单独复核 ——
            它只说明「员工做了练习并交了东西」，<strong>能力等级由你在本次复核中判断</strong>。
          </p>
          <div v-else class="or-context__materials">
            <div
              v-for="entry in contextMaterials"
              :key="entry.material.submissionId"
              class="or-context__material"
              :class="{ 'is-applied': entry.material.belongsToAppliedStep }"
            >
              <div class="or-context__material-head">
                <span class="or-context__material-name">{{ entry.material.taskTitle || '项目任务' }}</span>
                <el-tag
                  v-if="entry.material.belongsToAppliedStep"
                  size="small"
                  type="warning"
                  effect="plain"
                  round
                >
                  本次申请能力
                </el-tag>
                <span v-if="entry.submittedText" class="or-context__muted">{{ entry.submittedText }}</span>
              </div>
              <div v-if="entry.material.stepAbilityName" class="or-context__material-sub">
                对应能力：{{ entry.material.stepAbilityName }}
              </div>
              <div v-if="entry.links.length" class="or-context__material-links">
                <template v-for="link in entry.links" :key="link.key">
                  <a
                    v-if="link.href"
                    :href="link.href"
                    target="_blank"
                    rel="noopener noreferrer"
                  >{{ link.label }}</a>
                  <span v-else class="or-context__material-raw">{{ link.label }}：{{ link.raw }}</span>
                </template>
              </div>
              <div v-if="entry.hint" class="or-context__material-hint">{{ entry.hint }}</div>
              <div v-if="entry.material.submissionText" class="or-context__material-text">
                {{ entry.material.submissionText }}
              </div>
            </div>
          </div>

          <div class="or-context__title">本次申请</div>
          <div class="or-context__muted">
            等级变化：L{{ context.beforeLevel ?? '--' }} → L{{ context.confirmedLevel ?? '--' }}
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.outcome-review__done {
  opacity: 0.6;
  font-size: 12px;
}

.or-context {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.or-context__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 12px;
}

.or-context__name {
  font-size: 14px;
  font-weight: 500;
  color: var(--app-text-strong, #1f2329);
}

.or-context__muted {
  font-size: 12px;
  color: var(--app-text-muted, #94a3b8);
}

.or-context__record {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.8;
}

.or-context__title {
  margin-top: 2px;
  font-size: 13px;
  font-weight: 500;
  color: var(--app-text-strong, #1f2329);
}

.or-context__steps,
.or-context__assessments,
.or-context__materials {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.or-context__material {
  padding: 8px 10px;
  border: 1px solid var(--app-border, #e5e6eb);
  border-radius: var(--app-radius-md, 8px);
}

/* 直接对应本次申请能力的材料排前面并高亮 */
.or-context__material.is-applied {
  border-color: var(--app-warning, #d97706);
  background: var(--app-warning-soft, rgba(217, 119, 6, 0.08));
}

.or-context__material-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.or-context__material-name {
  font-size: 13px;
  color: var(--app-text-strong, #1f2329);
}

.or-context__material-sub {
  margin-top: 3px;
  font-size: 12px;
  color: var(--app-text-muted, #94a3b8);
}

.or-context__material-links {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 6px;
  font-size: 12px;
}

.or-context__material-links a {
  color: var(--app-primary, #2563eb);
  text-decoration: none;
}

.or-context__material-links a:hover {
  text-decoration: underline;
}

/* 地址不规范时按纯文本展示：藏起来会让 HR 以为员工没交材料 */
.or-context__material-raw {
  color: var(--app-text-muted, #94a3b8);
  word-break: break-all;
}

.or-context__material-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--app-warning, #d97706);
}

.or-context__material-text {
  margin-top: 6px;
  font-size: 12px;
  color: var(--app-text, #4e5969);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.or-context__step {
  padding: 8px 10px;
  border: 1px solid var(--app-border, #e5e6eb);
  border-radius: var(--app-radius-md, 8px);
}

/* 本次申请对应那一步高亮：HR 不必在一列步骤里自己找 */
.or-context__step.is-applied {
  border-color: var(--app-warning, #d97706);
  background: var(--app-warning-soft, rgba(217, 119, 6, 0.08));
}

.or-context__step-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.or-context__step-name {
  font-size: 13px;
  color: var(--app-text-strong, #1f2329);
}

.or-context__step-sub {
  margin-top: 3px;
  font-size: 12px;
  color: var(--app-text-muted, #94a3b8);
}

.or-context__assessment {
  padding: 8px 10px;
  border: 1px solid var(--app-border, #e5e6eb);
  border-radius: var(--app-radius-md, 8px);
  background: var(--app-bg-secondary, #f8fafc);
}

.or-context__assessment-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.or-context__score {
  font-size: 12px;
  font-weight: 500;
  color: var(--app-text-strong, #1f2329);
}

.or-context__answer {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-strong, #1f2329);
  white-space: pre-wrap;
  word-break: break-word;
}

.or-context__feedback {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-muted, #94a3b8);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
