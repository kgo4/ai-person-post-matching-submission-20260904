<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  CircleCheck, Link, MagicStick, Refresh, Search, Warning,
  Collection, Clock, Loading, Open, Medal,
} from '@element-plus/icons-vue'
import { pageRecords } from '@/api/matching'
import type { MatchingRecord } from '@/api/types'
import { getLearningPath } from '@/api/learning'
import { confirmLearningOutcome, getMatchDiagnosis } from '@/api/capability-closure'
import type { MatchDiagnosisResult } from '@/api/capability-closure'
import { generateAiLearningSuggestions, getCachedAiLearningSuggestions } from '@/api/ai-learning'
import type { AiLearningSuggestionResponse, AbilitySuggestion, LearningStep } from '@/api/ai-learning'
import {
  buildLearningOutcomePayload,
  normalizeLearningPathDiagnosis,
  platformLabel,
  resourceTypeLabel,
  type LearningPathPlanItem,
  type NormalizedLearningGap,
} from './learning-path'
import { selectCachedAiLearningSuggestion } from './learning-path-cache'
import LearningRadarChart from '@/components/learning/LearningRadarChart.vue'
import LearningStepBar from '@/components/learning/LearningStepBar.vue'
import type { LearningPhase } from '@/components/learning/LearningStepBar.vue'
import { generateLearningPath, type LearningPathGenerateRequest } from '@/api/learning-path-refactor'

// ===================== Constants =====================

const PHASES: LearningPhase[] = [
  { key: 'record', label: '匹配记录', description: '选择人岗匹配' },
  { key: 'diagnosis', label: '差距诊断', description: '能力差距分析' },
  { key: 'ai', label: 'AI 增强', description: '智能学习建议' },
  { key: 'plan', label: '生成路径', description: '生成学习计划' },
]

const COLLECTED_KEY = 'lp_collected_resources'

// ===================== State =====================

const route = useRoute()
const router = useRouter()

const activePhase = ref<string>('record')
const recordLoading = ref(false)
const diagnosisLoading = ref(false)
const manualLoading = ref(false)
const aiSuggestionLoading = ref(false)
const confirmLoadingKey = ref('')
const planGenerating = ref(false)
const generatedPlanId = ref<number | null>(null)
const recentRecords = ref<MatchingRecord[]>([])
const selectedRecordId = ref<number>()
const activeRecord = ref<MatchingRecord | null>(null)
const activeDiagnosis = ref<MatchDiagnosisResult | null>(null)
const aiSuggestionResult = ref<AiLearningSuggestionResponse | null>(null)
const collectedIds = ref<Set<string>>(loadCollected())
const expandedGaps = ref<Set<string>>(new Set())

const manualForm = reactive({
  abilityNames: [] as string[],
  currentLevel: 1,
  targetLevel: 3,
})

// ===================== Computed =====================

const normalizedDiagnosis = computed(() => normalizeLearningPathDiagnosis(activeDiagnosis.value))
const totalResourceCount = computed(() =>
  Object.values(normalizedDiagnosis.value.learningByAbility).reduce((sum, items) => sum + items.length, 0),
)
const canConfirmOutcome = computed(() => Boolean(activeDiagnosis.value?.empId))
const hasAiSuggestions = computed(() => (aiSuggestionResult.value?.suggestions?.length || 0) > 0)
const aiValidation = computed(() => aiSuggestionResult.value?.validation)
const hasActiveRecord = computed(() => Boolean(activeRecord.value))
const hasDiagnosis = computed(() => normalizedDiagnosis.value.gaps.length > 0)
const canEnterAiPhase = computed(() => hasDiagnosis.value && activeDiagnosis.value?.matchingRecordId != null)
const canEnterPlanPhase = computed(() => hasDiagnosis.value)

// All resources flattened for timeline display
const allResources = computed(() => {
  const result: { gap: NormalizedLearningGap; resource: LearningPathPlanItem }[] = []
  for (const gap of normalizedDiagnosis.value.gaps) {
    const items = normalizedDiagnosis.value.learningByAbility[gap.abilityName] || []
    for (const resource of items) {
      result.push({ gap, resource })
    }
  }
  return result
})

// AI suggestions as resource-like items for embedding
const aiResources = computed(() => {
  if (!aiSuggestionResult.value) return [] as { suggestion: AbilitySuggestion; step: LearningStep }[]
  const result: { suggestion: AbilitySuggestion; step: LearningStep }[] = []
  for (const suggestion of aiSuggestionResult.value.suggestions) {
    for (const step of suggestion.steps) {
      result.push({ suggestion, step })
    }
  }
  return result
})

// Collected resources count
const collectedCount = computed(() => collectedIds.value.size)

// ===================== Lifecycle =====================

onMounted(async () => {
  await loadRecentRecords()
  const queryRecordId = Number(route.query.recordId)
  if (Number.isFinite(queryRecordId) && queryRecordId > 0) {
    selectedRecordId.value = queryRecordId
    await generateFromRecord(queryRecordId)
  }
})

// ===================== Record phase =====================

async function loadRecentRecords() {
  recordLoading.value = true
  try {
    const res = await pageRecords({ current: 1, size: 30 })
    recentRecords.value = res.data?.records || []
  } catch (error: any) {
    ElMessage.error(error.message || '匹配记录加载失败')
  } finally {
    recordLoading.value = false
  }
}

async function handleRecordGenerate() {
  if (!selectedRecordId.value) {
    ElMessage.warning('请先选择一条人员与岗位匹配记录')
    return
  }
  await generateFromRecord(selectedRecordId.value)
}

async function generateFromRecord(recordId: number) {
  diagnosisLoading.value = true
  aiSuggestionResult.value = null
  try {
    activeRecord.value = recentRecords.value.find((r) => r.id === recordId) || activeRecord.value
    const res = await getMatchDiagnosis(recordId)
    activeDiagnosis.value = res.data
    activeRecord.value = recentRecords.value.find((r) => r.id === recordId) || {
      id: recordId, batchNo: '', empId: res.data.empId, postId: res.data.postId,
      aiMatchScore: 0, matchStatus: 0, quantitativeReport: '', aiAnalysisReport: '',
      manualRemark: '', approvalStatus: 0, isLocked: 0, lockedBy: 0, lockedTime: '', createdTime: '',
    }
    router.replace({ query: { ...route.query, recordId: String(recordId) } })
    await restoreCachedAiSuggestions(recordId)

    if (normalizedDiagnosis.value.gaps.length > 0) {
      activePhase.value = 'diagnosis'
      expandedGaps.value = new Set([normalizedDiagnosis.value.gaps[0].abilityName])
    } else {
      ElMessage.info('该匹配记录暂未识别到明显能力差距')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '匹配差距诊断失败')
  } finally {
    diagnosisLoading.value = false
  }
}

async function restoreCachedAiSuggestions(recordId: number) {
  try {
    const res = await getCachedAiLearningSuggestions(recordId)
    const cached = selectCachedAiLearningSuggestion(res.data || [])
    if (cached) aiSuggestionResult.value = cached
  } catch {
    // silent
  }
}

// ===================== Manual form =====================

async function handleManualGenerate() {
  const names = manualForm.abilityNames.map((n) => n.trim()).filter(Boolean)
  if (names.length === 0) {
    ElMessage.warning('请输入至少一个缺少的能力')
    return
  }
  manualLoading.value = true
  try {
    const res = await getLearningPath({
      abilityNames: names,
      currentLevel: manualForm.currentLevel,
      targetLevel: manualForm.targetLevel,
    })
    activeRecord.value = null
    activeDiagnosis.value = {
      matchingRecordId: 0, empId: 0, postId: 0,
      gaps: names.map((abilityName) => ({
        abilityName, currentLevel: manualForm.currentLevel,
        requiredLevel: manualForm.targetLevel, weakEvidence: false,
        reason: '手动补录的能力差距',
      })),
      learningPath: res.data,
    }
    activePhase.value = 'diagnosis'
    if (normalizedDiagnosis.value.gaps.length > 0) {
      expandedGaps.value = new Set([normalizedDiagnosis.value.gaps[0].abilityName])
    }
  } catch (error: any) {
    ElMessage.error(error.message || '学习路径生成失败')
  } finally {
    manualLoading.value = false
  }
}

// ===================== AI phase =====================

async function handleGenerateAiSuggestions() {
  if (!activeDiagnosis.value?.matchingRecordId || !activeDiagnosis.value?.empId) {
    ElMessage.warning('请先选择匹配记录生成差距诊断')
    return
  }
  aiSuggestionLoading.value = true
  aiSuggestionResult.value = null
  activePhase.value = 'ai'
  try {
    const res = await generateAiLearningSuggestions({
      matchingRecordId: activeDiagnosis.value.matchingRecordId,
      empId: activeDiagnosis.value.empId,
      postId: activeDiagnosis.value.postId,
    })
    aiSuggestionResult.value = res.data
    if (res.data.hasInsufficientEvidence) {
      ElMessage.warning('部分能力证据不足，AI建议仅供参考')
    } else {
      ElMessage.success('AI学习建议生成完成')
    }
  } catch (error: any) {
    ElMessage.error(error.message || 'AI学习建议生成失败')
  } finally {
    aiSuggestionLoading.value = false
  }
}

// ===================== Collection =====================

function loadCollected(): Set<string> {
  try {
    const raw = localStorage.getItem(COLLECTED_KEY)
    return raw ? new Set(JSON.parse(raw)) : new Set()
  } catch {
    return new Set()
  }
}

function saveCollected() {
  localStorage.setItem(COLLECTED_KEY, JSON.stringify([...collectedIds.value]))
}

function isCollected(key: string) {
  return collectedIds.value.has(key)
}

function toggleCollect(key: string) {
  if (collectedIds.value.has(key)) {
    collectedIds.value.delete(key)
  } else {
    collectedIds.value.add(key)
  }
  collectedIds.value = new Set(collectedIds.value)
  saveCollected()
}

// ===================== Plan Generation =====================

async function handleGeneratePlan() {
  const recordId = selectedRecordId.value
  if (!recordId) {
    ElMessage.warning('请先选择匹配记录')
    return
  }
  planGenerating.value = true
  try {
    const req: LearningPathGenerateRequest = {
      matchingRecordId: recordId,
      includeProjectTasks: true,
      useAi: true,
    }
    const res = await generateLearningPath(req)
    const plan = res.data
    if (plan?.id) {
      generatedPlanId.value = plan.id
      ElMessage.success('学习路径计划已生成！')
      router.push(`/learning/path/${plan.id}`)
    } else {
      ElMessage.error('计划生成失败，未返回有效计划ID')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '计划生成失败')
  } finally {
    planGenerating.value = false
  }
}

// ===================== Confirm / Actions =====================

function toggleGap(abilityName: string) {
  if (expandedGaps.value.has(abilityName)) {
    expandedGaps.value.delete(abilityName)
  } else {
    expandedGaps.value.add(abilityName)
  }
}

async function handleConfirmOutcome(gap: NormalizedLearningGap, resource: LearningPathPlanItem) {
  if (!activeDiagnosis.value?.empId) return
  const key = `${gap.abilityName}-${resource.resourceId || resource.title}`
  confirmLoadingKey.value = key
  try {
    const payload = buildLearningOutcomePayload(activeDiagnosis.value.empId, gap, resource)
    if (aiSuggestionResult.value) {
      payload.confirmationSource = 'LEARNING_PATH_AI_ASSISTED'
      payload.ragChunkIds = JSON.stringify(aiSuggestionResult.value.ragChunkIds || [])
    }
    await confirmLearningOutcome(payload)
    ElMessage.success('学习结果已写入人员能力证据')
  } catch (error: any) {
    ElMessage.error(error.message || '学习结果确认失败')
  } finally {
    confirmLoadingKey.value = ''
  }
}

async function handleConfirmAiStep(suggestion: AbilitySuggestion, step: LearningStep) {
  if (!activeDiagnosis.value?.empId) return
  const key = `ai-${suggestion.abilityName}-${step.resourceId}`
  confirmLoadingKey.value = key
  try {
    const gap: NormalizedLearningGap = {
      tagId: suggestion.tagId, abilityName: suggestion.abilityName,
      currentLevel: suggestion.currentLevel || 0, requiredLevel: suggestion.requiredLevel || 0,
      weakEvidence: suggestion.insufficientEvidence, reason: suggestion.reason || '',
      gapLevel: Math.max((suggestion.requiredLevel || 0) - (suggestion.currentLevel || 0), 0),
      severity: suggestion.riskLevel === 'HIGH' ? 'danger' : suggestion.riskLevel === 'MEDIUM' ? 'warning' : 'info',
    }
    const resource: LearningPathPlanItem = {
      abilityName: suggestion.abilityName, resourceId: step.resourceId,
      title: step.title || '', resourceType: step.resourceType,
      difficultyLevel: step.difficultyLevel, url: step.url, description: step.action,
      learningMethod: step.resourceType || '学习资源', accessPath: step.url || '学习资源库',
      hasResource: Boolean(step.resourceId),
    }
    const payload = buildLearningOutcomePayload(activeDiagnosis.value.empId, gap, resource)
    payload.confirmationSource = 'LEARNING_PATH_AI_ASSISTED'
    payload.ragChunkIds = JSON.stringify(aiSuggestionResult.value?.ragChunkIds || [])
    await confirmLearningOutcome(payload)
    ElMessage.success('学习结果已写入人员能力证据')
  } catch (error: any) {
    ElMessage.error(error.message || '学习结果确认失败')
  } finally {
    confirmLoadingKey.value = ''
  }
}

// ===================== Helpers =====================

function recordLabel(record: MatchingRecord) {
  const emp = record.empName || `人员#${record.empId}`
  const post = record.postName || `岗位#${record.postId}`
  const score = record.finalMatchScore ?? record.aiMatchScore
  return `${emp} → ${post}${score != null ? ` / ${Number(score).toFixed(1)}分` : ''}`
}

function levelText(gap: NormalizedLearningGap) {
  return `L${gap.currentLevel || 0} → L${gap.requiredLevel || '-'}`
}

function resourceKey(gap: NormalizedLearningGap, item: LearningPathPlanItem) {
  return `${gap.abilityName}-${item.resourceId || item.title}`
}

function severityColor(severity: string) {
  if (severity === 'danger') return '#dc2626'
  if (severity === 'warning') return '#d97706'
  return '#2563eb'
}

function severityBg(severity: string) {
  if (severity === 'danger') return '#fee2e2'
  if (severity === 'warning') return '#fef3c7'
  return '#dbeafe'
}

function riskLevelTag(riskLevel?: string) {
  if (riskLevel === 'HIGH') return 'danger'
  if (riskLevel === 'MEDIUM') return 'warning'
  return 'info'
}

function riskLevelText(riskLevel?: string) {
  if (riskLevel === 'HIGH') return '高风险'
  if (riskLevel === 'MEDIUM') return '中风险'
  return '低风险'
}

function riskColor(riskLevel?: string) {
  if (riskLevel === 'HIGH') return '#dc2626'
  if (riskLevel === 'MEDIUM') return '#d97706'
  return '#6b7280'
}

function difficultyStars(level?: number) {
  const l = Math.min(Math.max(level || 1, 1), 5)
  return '⭐'.repeat(l) + '☆'.repeat(5 - l)
}

function phaseIndex(key: string) {
  return PHASES.findIndex((p) => p.key === key)
}
</script>

<template>
  <div class="pg-layout">
    <!-- ===== STEP BAR ===== -->
    <LearningStepBar
      :phases="PHASES"
      :active-phase="activePhase"
      @select-phase="(key) => { if (phaseIndex(key) <= phaseIndex(activePhase) || (key === 'ai' && canEnterAiPhase) || (key === 'plan' && canEnterPlanPhase) || key === 'record') activePhase = key }"
    />

    <!-- ===== BODY ===== -->
    <div class="pg-body">
      <!-- MAIN CONTENT -->
      <div class="pg-main">

        <!-- PHASE 1: Record selector -->
        <section v-if="activePhase === 'record'" class="pg-panel">
          <h2 class="pg-panel__heading">
            <span class="pg-panel__step-tag">01</span> 选择匹配记录
          </h2>

          <div class="pg-record-select">
            <el-select
              v-model="selectedRecordId"
              filterable
              clearable
              placeholder="搜索人员姓名、岗位名称或匹配记录..."
              style="width: 100%; max-width: 520px"
              size="large"
              @change="handleRecordGenerate"
            >
              <el-option
                v-for="record in recentRecords"
                :key="record.id"
                :label="recordLabel(record)"
                :value="record.id"
              />
            </el-select>
            <el-button
              type="primary"
              size="large"
              :loading="diagnosisLoading"
              :disabled="!selectedRecordId"
              @click="handleRecordGenerate"
            >
              <el-icon><Search /></el-icon> 生成诊断
            </el-button>
          </div>

          <!-- Manual input -->
          <div class="pg-manual">
            <div class="pg-manual__header">
              <span class="pg-manual__title">手动补录能力差距</span>
              <span class="pg-manual__hint">无需匹配记录，直接输入待提升的能力</span>
            </div>
            <el-form :model="manualForm" label-position="top" size="default">
              <el-form-item label="缺少的能力">
                <el-select
                  v-model="manualForm.abilityNames"
                  multiple filterable allow-create default-first-option
                  placeholder="输入能力名称后回车，可添加多个"
                  style="width: 100%"
                />
              </el-form-item>
              <div class="pg-level-row">
                <el-form-item label="当前等级" style="flex:1">
                  <el-input-number v-model="manualForm.currentLevel" :min="1" :max="5" />
                </el-form-item>
                <el-form-item label="目标等级" style="flex:1">
                  <el-input-number v-model="manualForm.targetLevel" :min="1" :max="5" />
                </el-form-item>
                <el-form-item label="&nbsp;" style="flex:0 0 auto">
                  <el-button type="primary" :loading="manualLoading" @click="handleManualGenerate">
                    生成路径
                  </el-button>
                </el-form-item>
              </div>
            </el-form>
          </div>

          <!-- Quick record info if already selected -->
          <div v-if="activeRecord" class="pg-quick-info">
            <div class="pg-quick-info__row">
              <span>人员</span><strong>{{ activeRecord.empName || `#${activeRecord.empId}` }}</strong>
            </div>
            <div class="pg-quick-info__row">
              <span>岗位</span><strong>{{ activeRecord.postName || `#${activeRecord.postId}` }}</strong>
            </div>
            <div class="pg-quick-info__row">
              <span>匹配分</span>
              <strong class="pg-score">{{ Number((activeRecord.finalMatchScore ?? activeRecord.aiMatchScore) || 0).toFixed(1) }}</strong>
            </div>
          </div>
        </section>

        <!-- PHASE 2: Gap Diagnosis -->
        <section v-if="activePhase === 'diagnosis' && hasDiagnosis" class="pg-panel">
          <h2 class="pg-panel__heading">
            <span class="pg-panel__step-tag">02</span> 能力差距诊断
            <span class="pg-panel__badge">{{ normalizedDiagnosis.gaps.length }} 项差距</span>
          </h2>

          <!-- Radar Chart -->
          <div class="pg-radar-wrap">
            <LearningRadarChart
              :gaps="normalizedDiagnosis.gaps"
              :emp-name="activeRecord?.empName"
              :post-name="activeRecord?.postName"
            />
          </div>

          <!-- Gap List -->
          <div class="pg-gap-grid">
            <div
              v-for="gap in normalizedDiagnosis.gaps"
              :key="gap.abilityName"
              class="pg-gap-card"
              :class="{ 'is-expanded': expandedGaps.has(gap.abilityName) }"
            >
              <div class="pg-gap-card__header" @click="toggleGap(gap.abilityName)">
                <div class="pg-gap-card__dot" :style="{ background: severityColor(gap.severity) }" />
                <div class="pg-gap-card__info">
                  <span class="pg-gap-card__name">{{ gap.abilityName }}</span>
                  <span class="pg-gap-card__levels">
                    L{{ gap.currentLevel }} → L{{ gap.requiredLevel }}
                    <span
                      class="pg-gap-card__badge"
                      :style="{ color: severityColor(gap.severity), background: severityBg(gap.severity) }"
                    >
                      {{ gap.severity === 'danger' ? '差距大' : gap.severity === 'warning' ? '需关注' : '小幅提升' }}
                    </span>
                  </span>
                </div>
                <span
                  v-if="gap.weakEvidence"
                  class="pg-gap-card__flag"
                  style="color:#d97706;background:#fef3c7"
                >证据不足</span>
              </div>

              <div v-if="gap.reason" class="pg-gap-card__reason">
                {{ gap.reason }}
              </div>

              <!-- Expanded: Resource list -->
              <div v-if="expandedGaps.has(gap.abilityName)" class="pg-gap-card__resources">
                <div
                  v-for="item in normalizedDiagnosis.learningByAbility[gap.abilityName] || []"
                  :key="resourceKey(gap, item)"
                  class="pg-resource-card"
                >
                  <div class="pg-resource-card__top">
                    <span v-if="item.resourceType" class="pg-resource-type">
                      {{ resourceTypeLabel(item.resourceType) }}
                    </span>
                    <span v-if="item.platform" class="pg-resource-platform">
                      {{ platformLabel(item.platform) }}
                    </span>
                    <span v-if="item.duration" class="pg-resource-duration">
                      <el-icon><Clock /></el-icon> {{ item.duration }}
                    </span>
                    <span class="pg-resource-difficulty" :title="`难度 Lv${item.difficultyLevel || 1}`">
                      {{ difficultyStars(item.difficultyLevel) }}
                    </span>
                    <el-button
                      class="pg-collect-btn"
                      :type="isCollected(resourceKey(gap, item)) ? 'warning' : 'default'"
                      size="small"
                      text
                      @click.stop="toggleCollect(resourceKey(gap, item))"
                    >
                      <el-icon><Collection /></el-icon>
                    </el-button>
                  </div>

                  <h4 class="pg-resource-card__title">
                    <a v-if="item.url" :href="item.url" target="_blank" class="pg-resource-link">
                      {{ item.title }}
                      <el-icon><Open /></el-icon>
                    </a>
                    <span v-else>{{ item.title }}</span>
                  </h4>

                  <p v-if="item.description" class="pg-resource-card__desc">
                    {{ item.description }}
                  </p>

                  <div class="pg-resource-card__actions">
                    <el-button
                      v-if="item.url"
                      :icon="Link"
                      tag="a"
                      :href="item.url"
                      target="_blank"
                      size="small"
                    >
                      打开资源
                    </el-button>
                    <el-button
                      type="primary"
                      :icon="CircleCheck"
                      size="small"
                      :disabled="!canConfirmOutcome"
                      :loading="confirmLoadingKey === resourceKey(gap, item)"
                      @click="handleConfirmOutcome(gap, item)"
                    >
                      已掌握
                    </el-button>
                  </div>
                </div>

                <div v-if="!(normalizedDiagnosis.learningByAbility[gap.abilityName] || []).length" class="pg-empty-mini">
                  暂无匹配资源，请在学习资源管理中添加
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- Empty diagnosis -->
        <section v-if="activePhase === 'diagnosis' && !hasDiagnosis && !diagnosisLoading" class="pg-panel">
          <div class="pg-empty-big">
            <p>请先在左侧选择一个匹配记录来生成能力差距诊断</p>
          </div>
        </section>

        <!-- PHASE 3: AI Enhanced -->
        <section v-if="activePhase === 'ai'" class="pg-panel">
          <h2 class="pg-panel__heading">
            <span class="pg-panel__step-tag">03</span> AI 智能增强
            <span v-if="aiValidation" class="pg-panel__badge">
              {{ aiValidation.validatedSteps }}/{{ aiValidation.totalSteps }} 通过验证
            </span>
          </h2>

          <!-- Validation summary -->
          <div v-if="aiValidation" class="pg-validation">
            <div class="pg-validation__item">
              <span class="pg-validation__num">{{ aiValidation.totalSteps }}</span>
              <span class="pg-validation__label">总步骤</span>
            </div>
            <div class="pg-validation__item">
              <span class="pg-validation__num pg-validation__num--ok">{{ aiValidation.validatedSteps }}</span>
              <span class="pg-validation__label">已验证</span>
            </div>
            <div class="pg-validation__item">
              <span class="pg-validation__num pg-validation__num--warn">{{ aiValidation.filteredSteps }}</span>
              <span class="pg-validation__label">已过滤</span>
            </div>
          </div>

          <!-- AI Suggestion cards (embedded into gap context) -->
          <div v-if="hasAiSuggestions" class="pg-ai-list">
            <article
              v-for="suggestion in aiSuggestionResult!.suggestions"
              :key="suggestion.abilityName"
              class="pg-ai-card"
            >
              <header class="pg-ai-card__header">
                <div>
                  <h4>{{ suggestion.abilityName }}</h4>
                  <p>
                    L{{ suggestion.currentLevel || 0 }} → L{{ suggestion.requiredLevel || '-' }}
                    · {{ suggestion.reason || '' }}
                  </p>
                </div>
                <div class="pg-ai-card__tags">
                  <el-tag :type="riskLevelTag(suggestion.riskLevel)" size="small">
                    {{ riskLevelText(suggestion.riskLevel) }}
                  </el-tag>
                  <el-tag v-if="suggestion.insufficientEvidence" type="warning" size="small">
                    证据不足
                  </el-tag>
                </div>
              </header>

              <div v-if="suggestion.insufficientEvidence" class="pg-ai-card__warn">
                <el-icon><Warning /></el-icon> 该能力暂无足够系统资源，AI建议仅供参考。
              </div>

              <div v-if="suggestion.steps?.length" class="pg-ai-card__steps">
                <div v-for="(step, idx) in suggestion.steps" :key="idx" class="pg-ai-step">
                  <div class="pg-ai-step__num">{{ idx + 1 }}</div>
                  <div class="pg-ai-step__body">
                    <div class="pg-ai-step__title">
                      <strong>{{ step.title }}</strong>
                      <span v-if="step.resourceType" class="pg-ai-step__type">{{ step.resourceType }}</span>
                      <span v-if="step.difficultyLevel" class="pg-ai-step__stars">
                        {{ difficultyStars(step.difficultyLevel) }}
                      </span>
                      <el-button
                        class="pg-collect-btn"
                        :type="isCollected(`ai-${suggestion.abilityName}-${step.resourceId || idx}`) ? 'warning' : 'default'"
                        size="small"
                        text
                        @click.stop="toggleCollect(`ai-${suggestion.abilityName}-${step.resourceId || idx}`)"
                      >
                        <el-icon><Collection /></el-icon>
                      </el-button>
                    </div>
                    <p v-if="step.why" class="pg-ai-step__text"><b>为什么：</b>{{ step.why }}</p>
                    <p v-if="step.action" class="pg-ai-step__text"><b>怎么做：</b>{{ step.action }}</p>
                  </div>
                  <div class="pg-ai-step__actions">
                    <el-button v-if="step.url" :icon="Link" tag="a" :href="step.url" target="_blank" size="small">
                      打开
                    </el-button>
                    <el-button
                      v-if="step.validated !== false && step.resourceId"
                      type="primary"
                      :icon="CircleCheck"
                      size="small"
                      :disabled="!canConfirmOutcome"
                      :loading="confirmLoadingKey === `ai-${suggestion.abilityName}-${step.resourceId}`"
                      @click="handleConfirmAiStep(suggestion, step)"
                    >
                      已掌握
                    </el-button>
                  </div>
                </div>
              </div>
            </article>
          </div>

          <!-- AI loading skeleton -->
          <div v-if="aiSuggestionLoading" class="pg-ai-skeleton">
            <div v-for="n in 3" :key="n" class="pg-ai-skeleton__card">
              <div class="pg-ai-skeleton__line pg-ai-skeleton__line--title" />
              <div class="pg-ai-skeleton__line" />
              <div class="pg-ai-skeleton__line pg-ai-skeleton__line--short" />
            </div>
          </div>

          <!-- No AI yet -->
          <div v-if="!hasAiSuggestions && !aiSuggestionLoading" class="pg-empty-big">
            <el-icon :size="40" color="#d1d5db"><MagicStick /></el-icon>
            <p>点击「生成AI建议」按钮，AI 将基于系统资源库为您生成个性化学习建议</p>
            <el-button type="warning" :loading="aiSuggestionLoading" @click="handleGenerateAiSuggestions">
              <el-icon><MagicStick /></el-icon> 生成 AI 建议
            </el-button>
          </div>
        </section>

        <!-- PHASE 4: Learning Path Timeline -->
        <section v-if="activePhase === 'plan'" class="pg-panel">
          <h2 class="pg-panel__heading">
            <span class="pg-panel__step-tag">04</span> 学习路径
            <span class="pg-panel__badge">{{ totalResourceCount }} 项资源</span>
            <span style="flex:1" />
            <el-button
              type="primary"
              :icon="generatedPlanId ? Open : MagicStick"
              :loading="planGenerating"
              :disabled="!selectedRecordId"
              @click="generatedPlanId ? router.push(`/learning/path/${generatedPlanId}`) : handleGeneratePlan()"
            >
              {{ generatedPlanId ? '查看已生成计划' : 'AI 生成学习计划' }}
            </el-button>
          </h2>

          <div v-if="generatedPlanId" class="pg-plan-generated-tip">
            <el-icon style="color:#059669"><CircleCheck /></el-icon>
            学习计划已生成（含 AI 增强的步骤、项目任务和评估题目），
            <el-button type="primary" link size="small" @click="router.push(`/learning/path/${generatedPlanId}`)">
              前往计划详情 →
            </el-button>
          </div>

          <!-- Timeline view -->
          <div class="pg-timeline">
            <div class="pg-timeline__line" />
            <div
              v-for="(item, idx) in allResources"
              :key="resourceKey(item.gap, item.resource)"
              class="pg-timeline__node"
              :class="{ 'is-expanded': expandedGaps.has(item.gap.abilityName) }"
            >
              <div
                class="pg-timeline__dot"
                :style="{ background: severityColor(item.gap.severity) }"
              >
                {{ idx + 1 }}
              </div>

              <div class="pg-timeline__card" @click="toggleGap(item.gap.abilityName)">
                <div class="pg-timeline__card-header">
                  <h3>{{ item.resource.title }}</h3>
                  <div class="pg-timeline__card-tags">
                    <span
                      class="pg-timeline__badge"
                      :style="{ color: severityColor(item.gap.severity), background: severityBg(item.gap.severity) }"
                    >
                      {{ item.gap.abilityName }}
                    </span>
                    <span v-if="item.resource.resourceType" class="pg-timeline__type">
                      {{ resourceTypeLabel(item.resource.resourceType) }}
                    </span>
                    <span v-if="item.resource.platform" class="pg-timeline__platform">
                      {{ platformLabel(item.resource.platform) }}
                    </span>
                    <span v-if="item.resource.duration" class="pg-timeline__duration">
                      <el-icon><Clock /></el-icon> {{ item.resource.duration }}
                    </span>
                  </div>
                </div>

                <div class="pg-timeline__card-level">
                  <span>L{{ item.gap.currentLevel }} → L{{ item.gap.requiredLevel }}</span>
                  <span class="pg-timeline__stars">{{ difficultyStars(item.resource.difficultyLevel) }}</span>
                </div>

                <!-- Resource detail (shown when expanded) -->
                <div v-if="expandedGaps.has(item.gap.abilityName)" class="pg-timeline__card-detail">
                  <p v-if="item.resource.description">{{ item.resource.description }}</p>
                  <div class="pg-timeline__card-actions">
                    <el-button
                      v-if="item.resource.url"
                      :icon="Link"
                      tag="a"
                      :href="item.resource.url"
                      target="_blank"
                      size="small"
                    >
                      打开资源
                    </el-button>
                    <el-button
                      :icon="isCollected(resourceKey(item.gap, item.resource)) ? Collection : Collection"
                      size="small"
                      @click.stop="toggleCollect(resourceKey(item.gap, item.resource))"
                    >
                      {{ isCollected(resourceKey(item.gap, item.resource)) ? '已收藏' : '收藏' }}
                    </el-button>
                    <el-button
                      type="primary"
                      :icon="CircleCheck"
                      size="small"
                      :disabled="!canConfirmOutcome"
                      :loading="confirmLoadingKey === resourceKey(item.gap, item.resource)"
                      @click.stop="handleConfirmOutcome(item.gap, item.resource)"
                    >
                      已掌握
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>

      <!-- RIGHT PANEL -->
      <aside class="pg-sidebar">
        <!-- Progress ring -->
        <div class="pg-sidebar-panel">
          <div class="pg-sidebar-panel__title">快捷操作</div>
          <div class="pg-sidebar-actions">
            <el-button
              :type="activePhase === 'record' ? 'primary' : 'default'"
              size="small"
              :icon="Search"
              @click="activePhase = 'record'"
            >
              选择记录
            </el-button>
            <el-button
              :type="activePhase === 'diagnosis' ? 'primary' : 'default'"
              size="small"
              :disabled="!hasDiagnosis"
              @click="activePhase = 'diagnosis'"
            >
              查看诊断
            </el-button>
            <el-button
              :type="activePhase === 'ai' ? 'warning' : 'default'"
              size="small"
              :icon="MagicStick"
              :disabled="!canEnterAiPhase"
              :loading="aiSuggestionLoading"
              @click="handleGenerateAiSuggestions"
            >
              AI 增强
            </el-button>
            <el-button
              :type="activePhase === 'plan' ? 'primary' : 'default'"
              size="small"
              :disabled="!canEnterPlanPhase"
              @click="activePhase = 'plan'"
            >
              学习路径
            </el-button>
          </div>
        </div>

        <!-- Stats -->
        <div v-if="hasDiagnosis" class="pg-sidebar-panel">
          <div class="pg-sidebar-panel__title">统计数据</div>
          <div class="pg-sidebar-stats">
            <div class="pg-sidebar-stat">
              <span class="pg-sidebar-stat__num">{{ normalizedDiagnosis.gaps.length }}</span>
              <span class="pg-sidebar-stat__label">能力差距</span>
            </div>
            <div class="pg-sidebar-stat">
              <span class="pg-sidebar-stat__num">{{ totalResourceCount }}</span>
              <span class="pg-sidebar-stat__label">学习资源</span>
            </div>
            <div class="pg-sidebar-stat">
              <span class="pg-sidebar-stat__num">{{ hasAiSuggestions ? aiValidation?.totalSteps || 0 : '-' }}</span>
              <span class="pg-sidebar-stat__label">AI 步骤</span>
            </div>
            <div class="pg-sidebar-stat">
              <span class="pg-sidebar-stat__num" :class="{ 'pg-sidebar-stat__num--active': collectedCount > 0 }">
                {{ collectedCount }}
              </span>
              <span class="pg-sidebar-stat__label">已收藏</span>
            </div>
          </div>
        </div>

        <!-- Collected resources -->
        <div v-if="collectedCount > 0" class="pg-sidebar-panel">
          <div class="pg-sidebar-panel__title">
            <el-icon><Collection /></el-icon> 我的收藏 ({{ collectedCount }})
          </div>
          <div class="pg-sidebar-collected-hint">
            已收藏的资源可在「我的学习库」中统一查看
          </div>
        </div>

        <!-- Actions -->
        <div class="pg-sidebar-panel">
          <div class="pg-sidebar-panel__title">其他操作</div>
          <el-button size="small" :loading="recordLoading" @click="loadRecentRecords" style="width:100%">
            <el-icon><Refresh /></el-icon> 刷新记录
          </el-button>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
/* ===== Layout ===== */
.pg-layout {
  padding: 16px;
  min-height: 100%;
}

.pg-body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.pg-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pg-sidebar {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ===== Panels ===== */
.pg-panel {
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 12px;
  padding: 20px 24px;
}

.pg-panel__heading {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 700;
  color: #111827;
  display: flex;
  align-items: center;
  gap: 10px;
}

.pg-panel__step-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: #2563eb;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.pg-panel__badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 10px;
  background: #f3f4f6;
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
}

/* ===== Sidebar Panels ===== */
.pg-sidebar-panel {
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 12px;
  padding: 14px;
}

.pg-sidebar-panel__title {
  font-size: 13px;
  font-weight: 700;
  color: #374151;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.pg-sidebar-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pg-sidebar-actions .el-button {
  justify-content: flex-start;
}

.pg-sidebar-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.pg-sidebar-stat {
  text-align: center;
  padding: 10px 8px;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(148, 163, 184, 0.08);
  border-radius: 8px;
}

.pg-sidebar-stat__num {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #111827;
}

.pg-sidebar-stat__num--active {
  color: #d97706;
}

.pg-sidebar-stat__label {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 2px;
  display: block;
}

.pg-sidebar-collected-hint {
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.5;
}

/* ===== Record Select ===== */
.pg-record-select {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.pg-manual {
  margin-top: 20px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.45);
  border: 1px solid rgba(148, 163, 184, 0.08);
  border-radius: 10px;
}

.pg-manual__header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.pg-manual__title {
  font-size: 14px;
  font-weight: 700;
  color: #374151;
}

.pg-manual__hint {
  font-size: 12px;
  color: #9ca3af;
}

.pg-level-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.pg-quick-info {
  margin-top: 16px;
  padding: 12px 16px;
  background: rgba(37, 99, 235, 0.04);
  border: 1px solid rgba(37, 99, 235, 0.1);
  border-radius: 8px;
  display: flex;
  gap: 24px;
}

.pg-quick-info__row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.pg-quick-info__row span {
  color: #9ca3af;
}

.pg-quick-info__row strong {
  color: #111827;
}

.pg-score {
  color: #2563eb !important;
  font-size: 16px;
}

/* ===== Radar ===== */
.pg-radar-wrap {
  margin-bottom: 16px;
  padding: 8px;
  background: rgba(255, 255, 255, 0.4);
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.06);
}

/* ===== Gap Grid ===== */
.pg-gap-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pg-gap-card {
  border: 1px solid rgba(148, 163, 184, 0.1);
  border-radius: 10px;
  overflow: hidden;
  transition: border-color 0.15s;
}

.pg-gap-card:hover {
  border-color: rgba(37, 99, 235, 0.2);
}

.pg-gap-card.is-expanded {
  border-color: #2563eb;
  box-shadow: 0 2px 12px rgba(37, 99, 235, 0.06);
}

.pg-gap-card__header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
}

.pg-gap-card__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.pg-gap-card__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pg-gap-card__name {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.pg-gap-card__levels {
  font-size: 12px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 6px;
}

.pg-gap-card__badge {
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.pg-gap-card__flag {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.pg-gap-card__reason {
  padding: 0 16px 10px 36px;
  font-size: 12px;
  color: #9ca3af;
}

.pg-gap-card__resources {
  border-top: 1px solid rgba(148, 163, 184, 0.08);
  padding: 12px 16px 16px 36px;
  display: grid;
  gap: 10px;
}

/* ===== Resource Card ===== */
.pg-resource-card {
  padding: 14px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(148, 163, 184, 0.08);
  border-radius: 8px;
  transition: border-color 0.15s;
}

.pg-resource-card:hover {
  border-color: #2563eb;
}

.pg-resource-card__top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.pg-resource-type {
  font-size: 11px;
  font-weight: 600;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
  padding: 2px 8px;
  border-radius: 4px;
}

.pg-resource-platform {
  font-size: 11px;
  color: #6b7280;
  background: rgba(148, 163, 184, 0.1);
  padding: 2px 8px;
  border-radius: 4px;
}

.pg-resource-duration {
  font-size: 11px;
  color: #9ca3af;
  display: flex;
  align-items: center;
  gap: 3px;
}

.pg-resource-difficulty {
  font-size: 11px;
  color: #d97706;
  margin-left: auto;
}

.pg-collect-btn {
  margin-left: 4px;
}

.pg-resource-card__title {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.pg-resource-link {
  color: #111827;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.pg-resource-link:hover {
  color: #2563eb;
}

.pg-resource-link .el-icon {
  font-size: 12px;
}

.pg-resource-card__desc {
  margin: 0 0 10px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
}

.pg-resource-card__actions {
  display: flex;
  gap: 8px;
}

/* ===== AI Cards ===== */
.pg-ai-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pg-ai-card {
  padding: 16px;
  border: 1px solid rgba(245, 158, 11, 0.15);
  border-radius: 10px;
  background: rgba(245, 158, 11, 0.03);
}

.pg-ai-card__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
}

.pg-ai-card__header h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}

.pg-ai-card__header p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #9ca3af;
}

.pg-ai-card__tags {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.pg-ai-card__warn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: #fef3c7;
  border: 1px solid #fde68a;
  border-radius: 6px;
  color: #92400e;
  font-size: 12px;
  margin-bottom: 10px;
}

.pg-ai-card__steps {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pg-ai-step {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, 0.1);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.6);
}

.pg-ai-step__num {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #2563eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}

.pg-ai-step__body {
  flex: 1;
  min-width: 0;
}

.pg-ai-step__title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.pg-ai-step__title strong {
  font-size: 13px;
  color: #111827;
}

.pg-ai-step__type {
  font-size: 11px;
  color: #6b7280;
  background: rgba(148, 163, 184, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
}

.pg-ai-step__stars {
  font-size: 10px;
  color: #d97706;
}

.pg-ai-step__text {
  margin: 4px 0 0;
  font-size: 12px;
  color: #6b7280;
}

.pg-ai-step__text b {
  color: #374151;
}

.pg-ai-step__actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

/* AI skeleton loading */
.pg-ai-skeleton {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pg-ai-skeleton__card {
  padding: 16px;
  border: 1px solid rgba(148, 163, 184, 0.08);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.4);
}

.pg-ai-skeleton__line {
  height: 12px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f3f4f6 25%, #e5e7eb 50%, #f3f4f6 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
  margin-bottom: 8px;
}

.pg-ai-skeleton__line--title {
  width: 45%;
  height: 16px;
}

.pg-ai-skeleton__line--short {
  width: 65%;
}

@keyframes skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ===== Validation summary ===== */
.pg-validation {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.45);
  border: 1px solid rgba(148, 163, 184, 0.1);
  border-radius: 8px;
}

.pg-validation__item {
  text-align: center;
}

.pg-validation__num {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #111827;
}

.pg-validation__num--ok {
  color: #059669;
}

.pg-validation__num--warn {
  color: #d97706;
}

.pg-validation__label {
  font-size: 11px;
  color: #9ca3af;
}

/* ===== Timeline ===== */
.pg-timeline {
  position: relative;
  padding-left: 44px;
}

.pg-timeline__line {
  position: absolute;
  left: 18px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: rgba(148, 163, 184, 0.16);
  border-radius: 1px;
}

.pg-timeline__node {
  position: relative;
  margin-bottom: 16px;
}

.pg-timeline__dot {
  position: absolute;
  left: -44px;
  top: 16px;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
  color: #fff;
  z-index: 1;
  box-shadow: 0 2px 6px rgba(0,0,0,0.1);
}

.pg-timeline__card {
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 10px;
  padding: 16px 20px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.pg-timeline__card:hover {
  border-color: #2563eb;
}

.pg-timeline__node.is-expanded .pg-timeline__card {
  border-color: #2563eb;
  box-shadow: 0 2px 12px rgba(37, 99, 235, 0.08);
}

.pg-timeline__card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.pg-timeline__card-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}

.pg-timeline__card-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.pg-timeline__badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.pg-timeline__type {
  font-size: 11px;
  color: #6b7280;
  background: rgba(148, 163, 184, 0.1);
  padding: 2px 8px;
  border-radius: 4px;
}

.pg-timeline__platform {
  font-size: 11px;
  font-weight: 600;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
  padding: 2px 8px;
  border-radius: 4px;
}

.pg-timeline__duration {
  font-size: 11px;
  color: #9ca3af;
  display: flex;
  align-items: center;
  gap: 3px;
}

.pg-timeline__card-level {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #6b7280;
}

.pg-timeline__stars {
  font-size: 11px;
  color: #d97706;
}

.pg-timeline__card-detail {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid rgba(148, 163, 184, 0.1);
}

.pg-timeline__card-detail p {
  margin: 0 0 12px;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
}

.pg-timeline__card-actions {
  display: flex;
  gap: 8px;
}

/* ===== Empty states ===== */
.pg-empty-mini {
  padding: 16px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
}

.pg-empty-big {
  padding: 40px 24px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.pg-empty-big p {
  font-size: 14px;
  color: #9ca3af;
  margin: 0;
  max-width: 400px;
}

/* Plan generated tip */
.pg-plan-generated-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: rgba(5, 150, 105, 0.06);
  border: 1px solid rgba(5, 150, 105, 0.15);
  border-radius: 8px;
  font-size: 13px;
  color: #374151;
}

/* ===== Responsive ===== */
@media (max-width: 1200px) {
  .pg-body {
    flex-wrap: wrap;
  }
  .pg-sidebar {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .pg-body {
    flex-direction: column;
  }
  .pg-sidebar {
    width: 100%;
    order: -1;
  }
  .pg-record-select {
    flex-direction: column;
  }
  .pg-level-row {
    flex-direction: column;
  }
  .pg-quick-info {
    flex-direction: column;
    gap: 4px;
  }
  .pg-sidebar-stats {
    grid-template-columns: repeat(4, 1fr);
  }
}
</style>
