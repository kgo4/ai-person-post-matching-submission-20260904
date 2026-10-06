<script setup lang="ts">
/**
 * 员工侧「我的学习路径」。
 *
 * 为什么与 HR 的 `/learning/path` 分成两个页面：
 * 那个页面是**工作台**——第一步是「选择人岗匹配记录」（还能搜索人员姓名），
 * 员工打开自己的学习路径时看到的是一堆与自己无关的选人控件。
 * 本页把人员范围固定为登录人本人，员工只需要「选自己的哪个目标岗位」。
 *
 * 页面结构（计划与差距同屏，互为索引）：
 * - 顶部：目标岗位切换（只列本人的匹配记录）+ 计划进度；
 * - 左栏：学习计划步骤（复用 `LearningStepCard`）；
 * - 右栏：能力差距（复用 `LearningGapPanel`，数据由左栏步骤派生，保证两栏同源）；
 * - 底部：能力提升申请（随时可提交，但如实展示 HR 会看到的学习记录）。
 *
 * 决策逻辑全部在 `./my-learning-path.ts`（可被 .mjs 直测），本文件只负责取数与交互。
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import LearningPathHeader from '@/components/learning/LearningPathHeader.vue'
import LearningStepCard from '@/components/learning/LearningStepCard.vue'
import LearningGapPanel from '@/components/learning/LearningGapPanel.vue'
import { pageRecords } from '@/api/matching'
import {
  generateLearningPath,
  getAssessmentsByPlan,
  getLearningPathByMatch,
  submitProjectTask,
  updateStepStatus,
} from '@/api/learning-path-refactor'
import LearningProjectSubmitDialog from '@/components/learning/LearningProjectSubmitDialog.vue'
import { pageMyLearningOutcomes, submitLearningOutcome } from '@/api/learning-outcome'
import {
  generateAiLearningSuggestions,
  getCachedAiLearningSuggestions,
} from '@/api/ai-learning'
import type { AbilitySuggestion, ValidationSummary } from '@/api/ai-learning'
import { resolveApiErrorMessage } from '@/utils/request-error-message'
import { toSafeExternalUrl } from '@/utils/external-url'
import {
  buildGapRows,
  buildTargetCandidates,
  emptyPlanHint,
  emptyTargetHint,
  locateStepIndex,
  pickDefaultTarget,
  resolveImprovementEligibility,
  summarizeLearningRecord,
  type LearningRecordInput,
} from './my-learning-path'
import type {
  LearningAssessmentItem,
  LearningOutcomeSubmission,
  LearningPathPlan,
  LearningPathStep,
  LearningProjectSubmitDTO,
  LearningProjectTask,
} from '@/api'

/* ===================== 目标岗位 ===================== */

const targetsLoading = ref(false)
const targetError = ref('')
const targets = ref<ReturnType<typeof buildTargetCandidates>>([])
const activeRecordId = ref<number | null>(null)

const activeTarget = computed(
  () => targets.value.find(item => item.matchingRecordId === activeRecordId.value) ?? null,
)

/**
 * 取本人的可作目标的匹配记录。
 *
 * 后端 `/matching/record/page` 对只有员工权限的调用者会**忽略 empId 入参**并按本人收口
 * （`MatchingRecordApiFacade.page` → `pagePublishedRecords`），所以这里不需要也不能传 empId；
 * 返回的也一定是已发布的记录。
 */
async function loadTargets() {
  targetsLoading.value = true
  targetError.value = ''
  try {
    const res = await pageRecords({ current: 1, size: 50 })
    const records = (res.data?.records ?? []) as Array<Record<string, any>>
    targets.value = buildTargetCandidates(
      records.map(record => ({
        matchingRecordId: Number(record.id),
        postId: record.postId ?? null,
        postName: record.postName ?? null,
        matchScore: record.finalMatchScore ?? record.aiMatchScore ?? null,
        createdTime: record.createdTime ?? null,
      })),
    )
    const next = pickDefaultTarget(
      targets.value,
      targets.value
        .filter(item => plannedRecordIds.value.has(item.matchingRecordId))
        .map(item => item.matchingRecordId),
    )
    // 切换岗位后重新加载计划；没换岗位就不重复请求
    if (next !== null && next !== activeRecordId.value) {
      activeRecordId.value = next
      await loadPlan()
    }
  } catch (error) {
    targets.value = []
    targetError.value = resolveApiErrorMessage(error, '加载我的匹配记录失败')
  } finally {
    targetsLoading.value = false
  }
}

/* ===================== 学习计划 ===================== */

const planLoading = ref(false)
const planError = ref('')
const plan = ref<LearningPathPlan | null>(null)
const generating = ref(false)

/** 已有计划的匹配记录 id —— 用于「再次进入时接着上次那条继续」 */
const plannedRecordIds = ref<Set<number>>(new Set())

const steps = computed<LearningPathStep[]>(() => plan.value?.steps ?? [])
const gapRows = computed(() => buildGapRows(steps.value))
const planEmptyHint = computed(() => emptyPlanHint(!!plan.value))
const targetEmptyHint = computed(() => emptyTargetHint(targetsLoading.value === false && targets.value.length === 0))

async function loadPlan() {
  if (activeRecordId.value === null) {
    plan.value = null
    return
  }
  planLoading.value = true
  planError.value = ''
  plan.value = null
  try {
    const res = await getLearningPathByMatch(activeRecordId.value)
    plan.value = res.data ?? null
    if (plan.value) {
      plannedRecordIds.value = new Set([...plannedRecordIds.value, activeRecordId.value])
      await loadAssessments()
      await loadMyOutcomes()
      await loadCachedAi()
    }
  } catch (error) {
    // 未生成计划时后端回 404：这不是故障，页面上给「生成学习计划」入口即可，
    // 因此只在确实不是 404 时显示错误条。
    const message = resolveApiErrorMessage(error, '')
    plan.value = null
    if (message && !/不存在|未找到|404/.test(message)) {
      planError.value = message
    }
  } finally {
    planLoading.value = false
  }
}

const submitDialogRef = ref<InstanceType<typeof LearningProjectSubmitDialog> | null>(null)

/** 打开项目材料提交对话框（材料是能力提升申请的佐证，需要员工自己交） */
function openProjectSubmit(task: LearningProjectTask) {
  submitDialogRef.value?.open(task)
}

/**
 * 提交项目材料。
 *
 * 提交即视为该步骤学完 —— 后端同步生成能力证据。能力等级**不在这里变**，
 * 由 HR 在复核「能力提升申请」时判定；所以成功后只提示「本步骤已学完」，
 * 不提「等待审核」，否则员工会以为还有一道独立复核在等。
 */
async function handleSubmitProjectMaterial(taskId: number, data: LearningProjectSubmitDTO) {
  try {
    await submitProjectTask(taskId, data)
    ElMessage.success('材料已提交，本步骤标记为已学完')
    await loadPlan()
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '材料提交失败，请稍后重试'))
  }
}

async function handleGeneratePlan() {
  if (activeRecordId.value === null || generating.value) return
  generating.value = true
  planError.value = ''
  try {
    const res = await generateLearningPath({
      matchingRecordId: activeRecordId.value,
      includeProjectTasks: true,
      useAi: true,
    })
    plan.value = res.data ?? null
    if (plan.value) {
      plannedRecordIds.value = new Set([...plannedRecordIds.value, activeRecordId.value])
      ElMessage.success('学习计划已生成')
      await loadAssessments()
    } else {
      planError.value = '计划生成失败：未返回有效计划'
    }
  } catch (error) {
    planError.value = resolveApiErrorMessage(error, '生成学习计划失败')
  } finally {
    generating.value = false
  }
}

/* ===================== 步骤状态与评估题 ===================== */

const activeStepId = ref<number | null>(null)
const highlightStepId = ref<number | null>(null)
const updatingStepId = ref<number | null>(null)

/** 计划内全部评估题，按 stepId 聚合 —— 「这条能力验证过没有」的唯一依据 */
const assessments = ref<LearningAssessmentItem[]>([])

async function loadAssessments() {
  if (!plan.value?.id) {
    assessments.value = []
    return
  }
  try {
    const res = await getAssessmentsByPlan(plan.value.id)
    assessments.value = (res.data ?? []) as LearningAssessmentItem[]
  } catch {
    // 评估题取不到不影响看计划：验证状态退化为「未作答」，不阻断主流程
    assessments.value = []
  }
}

async function handleStepStatusChange(step: LearningPathStep, status: string) {
  if (updatingStepId.value !== null) return
  updatingStepId.value = step.id
  try {
    await updateStepStatus(step.id, status)
    step.status = status
    activeStepId.value = step.id
    if (status === 'COMPLETED') {
      ElMessage.success('已标记为学完，可在下方提交能力提升申请')
    }
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '更新学习状态失败'))
  } finally {
    updatingStepId.value = null
  }
}

/** 点右侧差距 → 左侧滚动并高亮；找不到对应步骤时明确提示，不静默不动 */
function focusStep(stepId: number) {
  const index = locateStepIndex(steps.value, stepId)
  if (index < 0) {
    ElMessage.warning('该差距在当前学习计划里没有对应步骤，可尝试重新生成计划')
    return
  }
  highlightStepId.value = stepId
  activeStepId.value = stepId
  void nextTick(() => {
    document.getElementById(`mlp-step-${stepId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}

/* ===================== 能力提升申请 ===================== */

const myOutcomes = ref<LearningOutcomeSubmission[]>([])
const applyVisible = ref(false)
const applying = ref(false)
const applyForm = ref<{ abilityName: string; confirmedLevel: number; note: string }>({
  abilityName: '',
  confirmedLevel: 3,
  note: '',
})

const planOutcomes = computed(() => {
  const names = new Set(steps.value.map(step => (step.abilityName ?? '').trim()).filter(Boolean))
  if (names.size === 0) return []
  return myOutcomes.value.filter(item => names.has((item.abilityName ?? '').trim()))
})

const activeApplyStep = computed(
  () => steps.value.find(step => (step.abilityName ?? '') === applyForm.value.abilityName) ?? null,
)

/** 当前选中能力的学习记录，喂给资格判定与摘要（同一份入参，两处不会漂移） */
const activeRecord = computed<LearningRecordInput>(() => {
  const step = activeApplyStep.value
  if (!step) {
    return {}
  }
  const items = assessments.value.filter(item => item.stepId === step.id)
  const scored = items.filter(item => item.assessmentStatus === 'PASSED' || item.assessmentStatus === 'NOT_PASSED')
  // 多条评估题时取最近一条已判分的：多题场景下应当以「最后一次作答」为准
  const latest = scored.length > 0 ? scored[scored.length - 1] : null
  return {
    stepCompleted: step.status === 'COMPLETED',
    assessmentStatus: latest?.assessmentStatus ?? items[0]?.assessmentStatus ?? null,
    assessmentScore: latest?.score ?? null,
    resourceCount: step.resourceCount ?? null,
    completedResourceCount: step.status === 'COMPLETED' ? (step.resourceCount ?? 0) : 0,
  }
})

const applyEligibility = computed(() => resolveImprovementEligibility(activeRecord.value))
const applyRecordSummary = computed(() => summarizeLearningRecord(activeRecord.value))

async function loadMyOutcomes() {
  try {
    const res = await pageMyLearningOutcomes({ current: 1, size: 50 })
    myOutcomes.value = (res.data?.records ?? []) as LearningOutcomeSubmission[]
  } catch {
    myOutcomes.value = []
  }
}

function openApply(step?: LearningPathStep) {
  const first = step ?? steps.value[0]
  applyForm.value = {
    abilityName: (first?.abilityName ?? '').trim(),
    confirmedLevel: Math.min(Math.max(first?.targetLevel ?? 3, 1), 5),
    note: '',
  }
  applyVisible.value = true
}

async function submitApply() {
  if (applying.value) return
  const name = applyForm.value.abilityName.trim()
  if (!name) {
    ElMessage.warning('请选择要申请提升的能力')
    return
  }
  applying.value = true
  try {
    await submitLearningOutcome({
      matchingRecordId: activeRecordId.value ?? undefined,
      abilityName: name,
      beforeLevel: activeApplyStep.value?.currentLevel ?? undefined,
      confirmedLevel: applyForm.value.confirmedLevel,
      note: applyForm.value.note.trim() || undefined,
    })
    ElMessage.success('能力提升申请已提交，等待 HR 复核')
    applyVisible.value = false
    await loadMyOutcomes()
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '提交失败，请稍后重试'))
  } finally {
    applying.value = false
  }
}

/* ===================== AI 学习建议 ===================== */

const aiSuggestions = ref<AbilitySuggestion[]>([])
const aiValidation = ref<ValidationSummary | null>(null)
const aiLoading = ref(false)
const aiError = ref('')

/**
 * 读取已缓存的建议。
 *
 * 进页面与切换目标岗位时调用：AI 建议是一次 LLM 调用，不能每次进页面都重新生成 ——
 * 有缓存就先展示，用户明确点「重新生成」时才再调一次。
 */
async function loadCachedAi() {
  aiSuggestions.value = []
  aiValidation.value = null
  aiError.value = ''
  if (activeRecordId.value === null) return
  try {
    const res = await getCachedAiLearningSuggestions(activeRecordId.value)
    const list = res.data ?? []
    // 后端按时间返回多条生成记录，取最近一条；没有缓存是正常状态，不报错
    const latest = list.length > 0 ? list[list.length - 1] : null
    aiSuggestions.value = latest?.suggestions ?? []
    aiValidation.value = latest?.validation ?? null
  } catch {
    aiSuggestions.value = []
  }
}

async function generateAi() {
  if (activeRecordId.value === null || aiLoading.value) return
  aiLoading.value = true
  aiError.value = ''
  try {
    const res = await generateAiLearningSuggestions({
      matchingRecordId: activeRecordId.value,
      // 差距直接取当前计划的步骤：避免另算一份差距导致「计划说有 5 项、建议只针对 3 项」
      gaps: steps.value.map(step => ({
        tagId: step.abilityTagId,
        abilityName: step.abilityName,
        currentLevel: step.currentLevel,
        requiredLevel: step.targetLevel,
        weakEvidence: step.evidenceStatus !== 'VERIFIED',
      })),
    })
    aiSuggestions.value = res.data?.suggestions ?? []
    aiValidation.value = res.data?.validation ?? null
    if (aiSuggestions.value.length === 0) {
      aiError.value = 'AI 未生成有效建议：通常是系统资源库里还没有与这些能力匹配的学习资源。'
    }
  } catch (error) {
    aiError.value = resolveApiErrorMessage(error, 'AI 建议生成失败，请稍后重试')
  } finally {
    aiLoading.value = false
  }
}

/* ===================== 初始化 ===================== */

async function refreshAll() {
  await loadTargets()
  await loadMyOutcomes()
}

onMounted(refreshAll)
</script>

<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <span class="page-hero__eyebrow">My Learning Path</span>
        <h1 class="page-hero__title">我的学习路径</h1>
        <p class="page-hero__desc">
          按你与目标岗位的能力差距排出学习顺序：左侧是学习计划，右侧是差距清单，两边互相点击即可定位。
          学完并完成验证后，可提交「能力提升申请」，HR 复核通过后能力画像才会更新。
        </p>
      </div>
      <div class="mlp__actions">
        <el-button :icon="Refresh" :loading="targetsLoading || planLoading" @click="refreshAll">
          刷新
        </el-button>
      </div>
    </section>

    <!-- 目标岗位切换：只列本人的匹配记录，员工不搜人不选人 -->
    <section v-if="targets.length" class="glass-card mlp__target">
      <div class="mlp__target-row">
        <span class="mlp__target-label">学习目标</span>
        <el-select
          v-model="activeRecordId"
          placeholder="选择目标岗位"
          style="width: 320px;"
          @change="loadPlan"
        >
          <el-option
            v-for="item in targets"
            :key="item.matchingRecordId"
            :label="`${item.postName}${item.matchScore != null ? ` · 匹配分 ${item.matchScore}` : ''}`"
            :value="item.matchingRecordId"
          />
        </el-select>
        <span class="mlp__target-hint">
          只列出你已发布的匹配结果；换一个目标岗位会切换到对应的学习计划。
        </span>
      </div>
    </section>

    <el-alert
      v-if="targetError"
      type="error"
      show-icon
      :closable="false"
      :title="targetError"
      style="margin-bottom: 16px;"
    />

    <!-- 没有任何可用目标：说清原因与下一步，不留空白 -->
    <section v-if="!targetsLoading && targets.length === 0 && !targetError" class="glass-card">
      <div class="panel-body mlp__empty">
        <p class="mlp__empty-title">暂无学习目标</p>
        <p class="mlp__empty-desc">{{ targetEmptyHint }}</p>
      </div>
    </section>

    <template v-if="targets.length">
      <LearningPathHeader
        v-if="plan"
        :plan-title="plan.planTitle"
        :emp-name="plan.empName"
        :post-name="plan.postName"
        :plan-status="plan.planStatus"
        :current-score="plan.currentScore"
        :target-score="plan.targetScore"
        :completed-step-count="plan.completedStepCount"
        :total-step-count="plan.totalStepCount"
        :pending-submission-count="plan.pendingSubmissionCount"
        :loading="planLoading"
        style="margin-bottom: 16px;"
      />

      <el-alert
        v-if="planError"
        type="error"
        show-icon
        :closable="false"
        :title="planError"
        style="margin-bottom: 16px;"
      />

      <!-- 没有计划时给生成入口；有计划则不给「重新生成」的显眼按钮，避免误触清掉进度 -->
      <section v-if="!plan && !planLoading" class="glass-card" style="margin-bottom: 16px;">
        <div class="panel-body mlp__empty">
          <p class="mlp__empty-title">{{ planEmptyHint }}</p>
          <p class="mlp__empty-desc">
            计划会按你与「{{ activeTarget?.postName }}」的能力差距自动排出学习顺序，可随时重新生成。
          </p>
          <el-button type="primary" :loading="generating" @click="handleGeneratePlan">
            生成学习计划
          </el-button>
        </div>
      </section>

      <div v-if="plan" class="mlp__grid">
        <section class="glass-card">
          <div class="panel-body">
            <div class="mlp__section-title">
              学习计划
              <span class="mlp__section-sub">按差距大小排序，一步对应一项能力</span>
            </div>
            <p v-if="steps.length === 0" class="mlp__empty-desc">该计划暂无学习步骤。</p>
            <div
              v-for="step in steps"
              :id="`mlp-step-${step.id}`"
              :key="step.id"
              class="mlp__step-wrap"
            >
              <LearningStepCard
                :step="step"
                :active="activeStepId === step.id"
                :highlight="highlightStepId === step.id"
                @select-step="activeStepId = step.id"
                @start-learning="(s: LearningPathStep) => handleStepStatusChange(s, 'IN_PROGRESS')"
                @mark-completed="(s: LearningPathStep) => handleStepStatusChange(s, 'COMPLETED')"
                @submit-task="openProjectSubmit"
              />
            </div>
          </div>
        </section>

        <section class="glass-card">
          <div class="panel-body">
            <div class="mlp__section-title">
              能力差距
              <span class="mlp__section-sub">取自同一份计划，与左侧永远一致</span>
            </div>
            <LearningGapPanel
              :steps="steps"
              :current-score="plan.currentScore"
              :target-score="plan.targetScore"
              :post-name="plan.postName"
              @select-step="focusStep"
            />
            <p class="mlp__gap-note">共 {{ gapRows.length }} 项差距</p>
          </div>
        </section>
      </div>

      <!--
        AI 学习建议。
        与「学习计划」的分工：计划回答「按什么顺序学」（由后端按差距生成），
        这里回答「这一项为什么学、具体怎么做」，且只推荐资源库里真实存在的资源。
        按需生成而不是进页面就调 LLM：一次建议 = 一次模型调用，不该因为切页面被反复触发。
      -->
      <section v-if="plan" class="glass-card" style="margin-top: 16px;">
        <div class="panel-body">
          <div class="mlp__apply-head">
            <div>
              <div class="mlp__section-title" style="margin-bottom: 2px;">AI 学习建议</div>
              <div class="mlp__section-sub">
                只基于系统资源库里已收录的资源生成，不会编造不存在的资源
              </div>
            </div>
            <el-button :loading="aiLoading" @click="generateAi">
              {{ aiSuggestions.length > 0 ? '重新生成' : '生成 AI 建议' }}
            </el-button>
          </div>

          <el-alert
            v-if="aiError"
            type="warning"
            show-icon
            :closable="false"
            :title="aiError"
            style="margin-bottom: 12px;"
          />

          <p
            v-if="!aiLoading && aiSuggestions.length === 0 && !aiError"
            class="mlp__empty-desc"
          >
            还没有建议。点「生成 AI 建议」，AI 会按你当前的差距列出「为什么学、怎么学」。
          </p>

          <el-collapse v-if="aiSuggestions.length > 0" class="mlp__ai">
            <el-collapse-item
              v-for="(item, index) in aiSuggestions"
              :key="`${item.abilityName}-${index}`"
              :name="index"
            >
              <template #title>
                <span class="mlp__ai-title">{{ item.abilityName }}</span>
                <span class="mlp__ai-meta">L{{ item.currentLevel ?? '--' }} → L{{ item.requiredLevel ?? '--' }}</span>
                <el-tag
                  v-if="item.insufficientEvidence"
                  size="small"
                  type="warning"
                  effect="plain"
                  round
                >
                  证据不足，仅供参考
                </el-tag>
                <el-tag v-else size="small" effect="plain" round>{{ item.steps.length }} 条建议</el-tag>
              </template>

              <p v-if="item.reason" class="mlp__ai-reason">{{ item.reason }}</p>
              <div v-for="(step, si) in item.steps" :key="si" class="mlp__ai-step">
                <div class="mlp__ai-step-head">
                  <span class="mlp__ai-step-title">{{ step.title || '学习资源' }}</span>
                  <el-tag v-if="step.validated" size="small" type="success" effect="plain" round>
                    资源已校验
                  </el-tag>
                  <el-tag v-else size="small" type="info" effect="plain" round>未校验</el-tag>
                </div>
                <div v-if="step.why" class="mlp__ai-line"><span>为什么：</span>{{ step.why }}</div>
                <div v-if="step.action" class="mlp__ai-line"><span>怎么做：</span>{{ step.action }}</div>
                <div v-if="step.url" class="mlp__ai-line">
                  <!-- AI 建议的链接同样要过协议白名单：它未经资源库校验时完全由模型生成 -->
                  <a
                    v-if="toSafeExternalUrl(step.url)"
                    :href="toSafeExternalUrl(step.url) ?? undefined"
                    target="_blank"
                    rel="noopener noreferrer"
                  >{{ step.url }}</a>
                  <span v-else class="mlp__ai-url-raw">{{ step.url }}</span>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>

          <div v-if="aiValidation" class="mlp__ai-summary">
            共 {{ aiValidation.totalSteps }} 条建议，{{ aiValidation.validatedSteps }} 条通过资源库校验<template
              v-if="aiValidation.filteredSteps > 0"
            >，{{ aiValidation.filteredSteps }} 条因资源不存在被过滤</template>
          </div>
        </div>
      </section>

      <!-- 能力提升申请：随时可提交，但如实展示 HR 会看到的记录 -->
      <section v-if="plan" class="glass-card" style="margin-top: 16px;">
        <div class="panel-body">
          <div class="mlp__apply-head">
            <div>
              <div class="mlp__section-title" style="margin-bottom: 2px;">我的能力提升申请</div>
              <div class="mlp__section-sub">
                学完并通过验证后提交，HR 复核通过才会更新能力画像
              </div>
            </div>
            <el-button type="primary" @click="openApply()">申请能力提升</el-button>
          </div>

          <p v-if="planOutcomes.length === 0" class="mlp__empty-desc">本计划还没有提交过申请。</p>
          <div v-else class="mlp__apply-list">
            <div v-for="item in planOutcomes" :key="item.id" class="mlp__apply-item">
              <div class="mlp__apply-item-main">
                <span class="mlp__apply-ability">{{ item.abilityName || '未命名能力' }}</span>
                <el-tag size="small" effect="plain" round>
                  {{ item.beforeLevel ?? '--' }} → {{ item.confirmedLevel ?? '--' }}
                </el-tag>
              </div>
              <div class="mlp__apply-item-sub">
                {{ item.createdTime || '' }}
                <template v-if="item.reviewComment"> · 复核意见：{{ item.reviewComment }}</template>
              </div>
            </div>
          </div>
        </div>
      </section>
    </template>

    <el-dialog v-model="applyVisible" title="申请能力提升" width="560px" :close-on-click-modal="false">
      <el-form label-width="88px">
        <el-form-item label="能力" required>
          <el-select v-model="applyForm.abilityName" placeholder="选择要申请的能力" style="width: 100%;">
            <el-option
              v-for="step in steps"
              :key="step.id"
              :label="step.abilityName || '未命名能力'"
              :value="step.abilityName"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="当前等级">
          <span>{{ activeApplyStep?.currentLevel ?? '--' }}</span>
        </el-form-item>
        <el-form-item label="自评等级" required>
          <el-slider v-model="applyForm.confirmedLevel" :min="1" :max="5" :step="1" show-stops />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="applyForm.note"
            type="textarea"
            :rows="3"
            placeholder="写清你做了什么、验证结果如何，便于 HR 复核"
          />
        </el-form-item>
      </el-form>

      <!-- 提交前如实告知：HR 会看到什么。按需求申请随时可提交，但不能让人误以为附带的是完整证据 -->
      <el-alert
        type="info"
        show-icon
        :closable="false"
        :title="applyRecordSummary.text"
      >
        <template #default>
          <div class="mlp__record">
            <div v-for="(line, index) in applyRecordSummary.items" :key="index">{{ line }}</div>
            <div class="mlp__record-hint">{{ applyEligibility.hint }}</div>
          </div>
        </template>
      </el-alert>

      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 项目材料提交：材料随能力提升申请一起给 HR 看，所以要在同一页面就能交 -->
    <LearningProjectSubmitDialog ref="submitDialogRef" @submit="handleSubmitProjectMaterial" />
  </div>
</template>

<style scoped>
.mlp__actions {
  display: flex;
  gap: 8px;
}

.mlp__target {
  margin-bottom: 16px;
}

.mlp__target-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
}

.mlp__target-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--app-text-strong, #1f2329);
}

.mlp__target-hint {
  font-size: 12px;
  color: var(--app-text-muted, #94a3b8);
}

.mlp__empty {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.mlp__empty-title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--app-text-strong, #1f2329);
}

.mlp__empty-desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-muted, #94a3b8);
}

/* 两栏：左计划宽一些，右差距窄一些；窄屏自动堆叠 */
.mlp__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

@media (max-width: 1100px) {
  .mlp__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.mlp__section-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 500;
  color: var(--app-text-strong, #1f2329);
}

.mlp__section-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--app-text-muted, #94a3b8);
}

.mlp__step-wrap {
  scroll-margin-top: 80px;
}

.mlp__step-wrap + .mlp__step-wrap {
  margin-top: 10px;
}

.mlp__gap-note {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--app-text-muted, #94a3b8);
}

.mlp__apply-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.mlp__apply-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 10px;
}

.mlp__apply-item {
  padding: 10px 12px;
  border: 1px solid var(--app-border, #e5e6eb);
  border-radius: var(--app-radius-md, 8px);
  background: var(--app-bg-secondary, #f8fafc);
}

.mlp__apply-item-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mlp__apply-ability {
  font-size: 13px;
  color: var(--app-text-strong, #1f2329);
}

.mlp__apply-item-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-text-muted, #94a3b8);
  word-break: break-word;
}

.mlp__record {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.8;
}

.mlp__record-hint {
  margin-top: 4px;
  color: var(--app-text-muted, #94a3b8);
}

/* AI 学习建议：用折叠面板，避免一次铺开十几条建议把页面撑得很长 */
.mlp__ai-title {
  margin-right: 10px;
  font-size: 13px;
  font-weight: 500;
  color: var(--app-text-strong, #1f2329);
}

.mlp__ai-meta {
  margin-right: 10px;
  font-size: 12px;
  color: var(--app-text-muted, #94a3b8);
}

.mlp__ai-reason {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--app-text-muted, #94a3b8);
}

.mlp__ai-step {
  padding: 8px 10px;
  margin-bottom: 8px;
  border: 1px solid var(--app-border, #e5e6eb);
  border-radius: var(--app-radius-md, 8px);
  background: var(--app-bg-secondary, #f8fafc);
}

.mlp__ai-step-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.mlp__ai-step-title {
  font-size: 13px;
  color: var(--app-text-strong, #1f2329);
}

.mlp__ai-line {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--app-text-strong, #1f2329);
  white-space: pre-wrap;
  word-break: break-word;
}

.mlp__ai-line > span {
  color: var(--app-text-muted, #94a3b8);
}

/* 地址不合法时按纯文本展示：用警示色提示「这条打不开」，
   否则它和普通说明文字长得一样，员工会以为是系统的问题 */
.mlp__ai-url-raw {
  color: var(--app-warning, #d97706);
}

.mlp__ai-summary {
  margin-top: 10px;
  font-size: 12px;
  color: var(--app-text-muted, #94a3b8);
}
</style>
