<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { approve, confirmLearningOutcome, generateLearningPath, getMatchingAiContext } from '@/api'
import type { MatchingApprovalDTO, MatchingApprovalFlow, AiContextPackage } from '@/api'
import { buildLearningOutcomePayload, normalizeDiagnosis } from './closure-diagnosis'
import type { NormalizedClosureGap } from './closure-diagnosis'
import { useMatchDetail } from '@/composables/useMatchDetail'
import SourceRefDrawer from '@/components/common/SourceRefDrawer.vue'
import MatchScorePanel from './components/MatchScorePanel.vue'
import MatchApprovalDialog from './components/MatchApprovalDialog.vue'
import { buildScoreParts, parseHardConditionDetails } from './match-detail-view-model'
import {
  parseJson, formatScore, formatScorePart, getAiScoreEmptyText,
  getScoreColor, formatPercentWeight, getMatchStatusText, getMatchStatusType,
  getApprovalStatusText, getApprovalStatusType, getScreeningLevelText,
  isPassed, getGapText, getRiskLevelType,
} from './utils'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

const {
  loading, matchResult, quantitativeReport, aiReport, approvalFlows,
  diagnosisLoading, diagnosisResult, loadAll, loadRecord, loadApprovals, loadDiagnosis,
} = useMatchDetail(id)

const activeTab = ref('ability')
const diagnosisSubmitting = ref(false)

const approvalDialogVisible = ref(false)
const approvalSubmitting = ref(false)
const currentFlowNode = ref<MatchingApprovalFlow | null>(null)

const learningPathLoading = ref(false)

// AI上下文
const aiContextDrawerVisible = ref(false)
const aiContextLoading = ref(false)
const aiContext = ref<AiContextPackage | null>(null)
const sourceRefDetailVisible = ref(false)
const currentSourceRefRef = ref('')
const aiContextActiveTab = ref('gaps')

const abilityDetails = computed(() => quantitativeReport.value?.abilityDetails || [])
const passedAbilities = computed(() => abilityDetails.value.filter((item) => isPassed(item)).length)
const failedAbilities = computed(() => abilityDetails.value.length - passedAbilities.value)
const normalizedDiagnosis = computed(() => normalizeDiagnosis(diagnosisResult.value))
const learningAbilityNames = computed(() => Object.keys(normalizedDiagnosis.value.learningByAbility))
const aiDimensionScores = computed<any[]>(() => (aiReport.value?.dimensionScores as any[]) || [])
const aiScoreReasons = computed<any[]>(() => (aiReport.value?.scoreReasons as any[]) || [])
const aiEvidenceAnalysis = computed<any[]>(() => (aiReport.value?.evidenceAnalysis as any[]) || [])
const aiRiskSignals = computed<string[]>(() => aiReport.value?.riskSignals || [])
const aiWeakEvidenceFlags = computed<string[]>(() => aiReport.value?.weakEvidenceFlags || [])
const aiAttentionPoints = computed<string[]>(() => aiReport.value?.humanAttentionPoints || [])
const aiHistoricalRefs = computed<string[]>(() => aiReport.value?.historicalReferenceUsed || [])

const hardConditionDetails = computed<any[]>(() => parseHardConditionDetails(matchResult.value?.hardConditionResult))

const scoreParts = computed(() => buildScoreParts(matchResult.value))

onMounted(() => loadAll())

function getPersonName() {
  return quantitativeReport.value?.empName || matchResult.value?.empName || (matchResult.value ? `人员#${matchResult.value.empId}` : '-')
}

function getPostName() {
  return quantitativeReport.value?.postName || matchResult.value?.postName || (matchResult.value ? `岗位#${matchResult.value.postId}` : '-')
}

function openApprovalDialog(flow: MatchingApprovalFlow, approved: boolean) {
  currentFlowNode.value = flow
  approvalDialogVisible.value = true
}

async function handleSubmitApproval(payload: {
  matchingRecordId: number
  approvalStatus: 2 | 3
  approvalRemark: string
}) {
  if (!matchResult.value) return

  approvalSubmitting.value = true
  try {
    const dto: MatchingApprovalDTO = {
      matchingRecordId: payload.matchingRecordId,
      approvalStatus: payload.approvalStatus,
      approvalRemark: payload.approvalRemark,
    }
    await approve(dto)
    ElMessage.success(payload.approvalStatus === 2 ? '审核通过' : '已驳回')
    approvalDialogVisible.value = false
    await Promise.all([loadRecord(), loadApprovals()])
  } catch (error: any) {
    ElMessage.error(error.message || '审核操作失败')
  } finally {
    approvalSubmitting.value = false
  }
}

async function handleConfirmLearningOutcome(gap: NormalizedClosureGap, resource?: any) {
  const record = matchResult.value || diagnosisResult.value
  if (!record?.empId) {
    ElMessage.warning('缺少员工信息，无法确认学习成果')
    return
  }
  diagnosisSubmitting.value = true
  try {
    await confirmLearningOutcome(buildLearningOutcomePayload({ empId: record.empId }, gap, resource))
    ElMessage.success('学习成果已回写为员工能力证据')
    await loadDiagnosis()
  } catch (error: any) {
    ElMessage.error(error.message || '学习成果确认失败')
  } finally {
    diagnosisSubmitting.value = false
  }
}

async function handleGenerateLearningPath() {
  if (!matchResult.value?.id) {
    ElMessage.warning('匹配记录未加载')
    return
  }

  learningPathLoading.value = true
  try {
    const res = await generateLearningPath({
      matchingRecordId: matchResult.value.id,
      targetScore: Math.min(100, Number(matchResult.value.aiMatchScore || 0) + 15),
      includeProjectTasks: true
    })
    const planId = res.data?.id
    if (planId) {
      ElMessage.success('学习路径已生成')
      router.push(`/learning/path/${planId}`)
    }
  } catch (error: any) {
    ElMessage.error(error.message || '生成学习路径失败')
  } finally {
    learningPathLoading.value = false
  }
}

async function handleShowAiContext() {
  if (!matchResult.value?.id) {
    ElMessage.warning('匹配记录未加载')
    return
  }

  aiContextDrawerVisible.value = true
  aiContextLoading.value = true
  try {
    const res = await getMatchingAiContext(matchResult.value.id)
    aiContext.value = res.data
  } catch (error: any) {
    ElMessage.error(error.message || '加载AI上下文失败')
  } finally {
    aiContextLoading.value = false
  }
}

function handleShowSourceRefDetail(ref: string) {
  currentSourceRefRef.value = ref
  sourceRefDetailVisible.value = true
}
</script>

<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <div class="page-hero__eyebrow">Match Analysis</div>
        <h1 class="page-hero__title">匹配深度解析</h1>
        <p class="page-hero__desc">{{ getPersonName() }} 与 {{ getPostName() }} 的完整匹配解释，包括量化评分、硬性条件和审批链路。</p>
        <div class="page-hero__meta">
          <span class="hero-chip">{{ getMatchStatusText(matchResult?.matchStatus) }}</span>
          <span class="hero-chip">{{ getApprovalStatusText(matchResult?.approvalStatus) }}</span>
          <span class="hero-chip">{{ getScreeningLevelText(matchResult?.screeningLevel) }}</span>
        </div>
      </div>
      <div class="toolbar-group">
        <el-button type="primary" :loading="learningPathLoading" @click="handleGenerateLearningPath">
          生成学习路径
        </el-button>
        <el-button @click="handleShowAiContext">
          查看 AI 读取上下文
        </el-button>
        <el-button @click="router.back()">返回</el-button>
      </div>
    </section>

    <section class="glass-card" v-loading="loading">
      <div class="panel-body" v-if="matchResult">
        <MatchScorePanel
          :parts="scoreParts"
          :final-score="matchResult.finalMatchScore ?? matchResult.aiMatchScore"
          :final-color="getScoreColor(matchResult.finalMatchScore ?? matchResult.aiMatchScore)"
          :person-name="getPersonName()"
          :post-name="getPostName()"
          :emp-id="matchResult.empId"
          :post-id="matchResult.postId"
        />

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="系统先基于硬性条件做门槛过滤，再结合能力模型和语义向量生成量化分，最后按需引入 AI 深度分析与审批复核。"
          class="mb-5"
        />

        <el-tabs v-model="activeTab">
          <el-tab-pane label="能力模型解释" name="ability">
            <div class="section-header">
              <div>
                <div class="section-title">L2 能力模型匹配</div>
                <div class="section-desc">共 {{ abilityDetails.length }} 项要求，{{ passedAbilities }} 项达标，{{ failedAbilities }} 项待提升。</div>
              </div>
            </div>
            <el-table :data="abilityDetails" style="width: 100%">
              <el-table-column prop="tagName" label="能力项" min-width="160" />
              <el-table-column prop="requiredLevel" label="岗位要求等级" width="130" align="center" />
              <el-table-column prop="actualLevel" label="员工掌握等级" width="130" align="center" />
              <el-table-column label="结果" width="110" align="center">
                <template #default="{ row }">
                  <el-tag :type="isPassed(row) ? 'success' : 'danger'">{{ isPassed(row) ? '达标' : '不足' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="核心能力" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.isCore === 1 ? 'warning' : 'info'">{{ row.isCore === 1 ? '是' : '否' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="解释" min-width="220">
                <template #default="{ row }">{{ getGapText(row) }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="硬性条件" name="hard">
            <div class="section-header">
              <div>
                <div class="section-title">L1 硬性条件过滤</div>
                <div class="section-desc">用于处理必须满足的门槛，例如学历、证书、地域或自定义扩展字段。</div>
              </div>
            </div>
            <el-empty v-if="hardConditionDetails.length === 0" description="本次匹配没有配置硬性条件，直接进入能力模型评分。" />
            <el-table v-else :data="hardConditionDetails" style="width: 100%">
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.passed ? 'success' : 'danger'">{{ row.passed ? '通过' : '未通过' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="label" label="条件" min-width="180" />
              <el-table-column prop="expectedValue" label="期望值" min-width="140" />
              <el-table-column label="实际值" min-width="140">
                <template #default="{ row }">{{ row.actualValue ?? '未填写' }}</template>
              </el-table-column>
              <el-table-column prop="source" label="数据来源" width="120" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="向量语义" name="vector">
            <div class="glass-panel vector-panel">
              <div class="vector-score">{{ formatScore(matchResult.vectorScore) }}</div>
              <div>
                <div class="section-title">向量语义评分</div>
                <p class="section-desc">
                  用于处理文本描述不完全一致但语义接近的情况，例如简历里写“Java 开发”，岗位里写“Java 后端开发”。它不替代能力模型，而是补充语义相关性判断。
                </p>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="AI 分析" name="ai">
            <el-empty v-if="!aiReport" description="本次匹配未生成 AI 深度分析报告。" />
            <div v-else class="ai-layout">
              <el-alert
                :type="aiReport.fallbackUsed ? 'warning' : 'info'"
                :closable="false"
                show-icon
                :title="aiReport.fallbackUsed
                  ? '当前为服务端事实分析：基于已加载的人员能力、岗位要求、评分分解和来源引用生成。'
                  : '当前为 AI 受控分析：AI 仅解释服务端提供的能力、岗位要求、评分分解和来源引用。'"
                class="ai-evidence-alert"
              />
              <div class="glass-panel stack-card">
                <div class="section-title">AI 建议分</div>
                <div class="ai-score">{{ formatScore(aiReport.aiScore ?? matchResult.aiScore ?? matchResult.llmScore) }}</div>
                <div class="section-desc">{{ aiReport.conclusion || '暂无结论' }}</div>
                <div class="ai-confidence">可信度 {{ formatScore(aiReport.confidence) }}</div>
              </div>
              <div class="glass-panel stack-card">
                <div class="section-title">优势</div>
                <div v-if="aiReport.strengths?.length" class="stack-list">
                  <div v-for="(item, index) in aiReport.strengths" :key="index" class="stack-list__item">{{ item }}</div>
                </div>
                <el-empty v-else description="暂无优势分析" :image-size="60" />
              </div>
              <div class="glass-panel stack-card">
                <div class="section-title">差距</div>
                <div v-if="aiReport.gaps?.length" class="stack-list">
                  <div v-for="(item, index) in aiReport.gaps" :key="index" class="stack-list__item">{{ item }}</div>
                </div>
                <el-empty v-else description="暂无差距分析" :image-size="60" />
              </div>
              <div class="glass-panel stack-card">
                <div class="section-title">建议</div>
                <div v-if="aiReport.suggestions?.length" class="stack-list">
                  <div v-for="(item, index) in aiReport.suggestions" :key="index" class="stack-list__item">{{ item }}</div>
                </div>
                <el-empty v-else description="暂无建议" :image-size="60" />
              </div>
            </div>

            <div class="ai-detail-grid">
              <div class="glass-panel stack-card">
                <div class="section-title">维度评分</div>
                <div v-if="aiDimensionScores.length" class="dimension-list">
                  <div v-for="item in aiDimensionScores" :key="item.dimension" class="dimension-list__row">
                    <div>
                      <div class="dimension-list__title">{{ item.dimension }}</div>
                      <div class="dimension-list__meta">权重 {{ formatPercentWeight(item.weight) }}</div>
                    </div>
                    <div class="dimension-list__score" :style="{ color: getScoreColor(item.score) }">{{ formatScore(item.score) }}</div>
                  </div>
                </div>
                <el-empty v-else description="暂无维度评分" :image-size="60" />
              </div>

              <div class="glass-panel stack-card">
                <div class="section-title">打分依据</div>
                <div v-if="aiScoreReasons.length" class="reason-list">
                  <div v-for="(item, index) in aiScoreReasons" :key="index" class="reason-item">
                    <div class="reason-item__head">
                      <span class="reason-item__factor">{{ item.factor }}</span>
                      <el-tag :type="item.direction === '+' ? 'success' : 'warning'" size="small">
                        {{ item.direction === '+' ? '+' : '-' }}{{ item.impact }}
                      </el-tag>
                    </div>
                    <div class="reason-item__text">{{ item.reason }}</div>
                    <div v-if="item.factRefs?.length" class="reason-item__refs">
                      <span v-for="ref in item.factRefs" :key="ref" class="fact-ref">{{ ref }}</span>
                    </div>
                  </div>
                </div>
                <el-empty v-else description="暂无结构化打分依据" :image-size="60" />
              </div>

              <div class="glass-panel stack-card">
                <div class="section-title">证据分析</div>
                <div v-if="aiEvidenceAnalysis.length" class="evidence-analysis-list">
                  <div v-for="item in aiEvidenceAnalysis" :key="item.ability" class="evidence-analysis-item">
                    <div class="evidence-analysis-item__head">
                      <span class="evidence-analysis-item__title">{{ item.ability }}</span>
                      <el-tag size="small" :type="item.confidence === '高' ? 'success' : item.confidence === '低' ? 'warning' : 'info'">
                        {{ item.confidence || '未知' }}
                      </el-tag>
                    </div>
                    <div class="evidence-analysis-item__meta">融合等级 {{ item.fusedLevel ?? '-' }}</div>
                    <div v-if="item.sources?.length" class="evidence-analysis-item__sources">
                      <span v-for="source in item.sources" :key="source" class="fact-ref">{{ source }}</span>
                    </div>
                    <div v-if="item.conflict" class="evidence-analysis-item__conflict">{{ item.conflict }}</div>
                  </div>
                </div>
                <el-empty v-else description="暂无证据分析" :image-size="60" />
              </div>

              <div class="glass-panel stack-card">
                <div class="section-title">风险与人工关注点</div>
                <div v-if="aiRiskSignals.length || aiWeakEvidenceFlags.length || aiAttentionPoints.length" class="stack-list">
                  <div v-for="(item, index) in aiRiskSignals" :key="`risk-${index}`" class="stack-list__item stack-list__item--warning">{{ item }}</div>
                  <div v-for="(item, index) in aiWeakEvidenceFlags" :key="`weak-${index}`" class="stack-list__item stack-list__item--warning">{{ item }}</div>
                  <div v-for="(item, index) in aiAttentionPoints" :key="`attention-${index}`" class="stack-list__item">{{ item }}</div>
                </div>
                <el-empty v-else description="暂无额外风险提示" :image-size="60" />
              </div>

              <div class="glass-panel stack-card">
                <div class="section-title">历史参照</div>
                <p class="section-desc">{{ aiReport?.modelQualityNote || '模型质量说明暂缺。' }}</p>
                <div v-if="aiHistoricalRefs.length" class="reason-item__refs">
                  <span v-for="ref in aiHistoricalRefs" :key="ref" class="fact-ref">{{ ref }}</span>
                </div>
                <el-empty v-else description="暂无历史引用" :image-size="60" />
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="闭环诊断" name="closure">
            <div v-loading="diagnosisLoading" class="closure-panel">
              <div class="section-header">
                <div>
                  <div class="section-title">差距到学习的闭环</div>
                  <div class="section-desc">从匹配差距生成学习建议，完成后回写员工能力证据并触发图谱刷新。</div>
                </div>
                <el-button size="small" :loading="diagnosisLoading" @click="loadDiagnosis">刷新</el-button>
              </div>

              <el-empty
                v-if="normalizedDiagnosis.gaps.length === 0"
                description="当前匹配未识别出待提升能力。"
              />

              <div v-else class="closure-grid">
                <article v-for="gap in normalizedDiagnosis.gaps" :key="`${gap.tagId || gap.abilityName}`" class="closure-gap">
                  <div class="closure-gap__head">
                    <div>
                      <div class="closure-gap__title">{{ gap.abilityName }}</div>
                      <div class="closure-gap__reason">{{ gap.reason || '岗位要求与当前能力存在差距' }}</div>
                    </div>
                    <el-tag :type="gap.severity">{{ gap.weakEvidence ? '证据薄弱' : '等级差距' }}</el-tag>
                  </div>

                  <div class="closure-levels">
                    <span>当前 L{{ gap.currentLevel }}</span>
                    <span>目标 L{{ gap.requiredLevel }}</span>
                  </div>

                  <div v-if="normalizedDiagnosis.learningByAbility[gap.abilityName]?.length" class="learning-list">
                    <div
                      v-for="resource in normalizedDiagnosis.learningByAbility[gap.abilityName]"
                      :key="resource.resourceId || resource.title"
                      class="learning-item"
                    >
                      <div>
                        <div class="learning-item__title">{{ resource.title }}</div>
                        <div class="learning-item__meta">
                          <span>{{ resource.resourceType || '学习资源' }}</span>
                          <span v-if="resource.difficultyLevel">难度 L{{ resource.difficultyLevel }}</span>
                        </div>
                      </div>
                      <el-button
                        size="small"
                        type="primary"
                        :loading="diagnosisSubmitting"
                        @click="handleConfirmLearningOutcome(gap, resource)"
                      >
                        确认完成
                      </el-button>
                    </div>
                  </div>

                  <div v-else class="learning-empty">
                    <span>暂无绑定学习资源</span>
                    <el-button
                      size="small"
                      type="primary"
                      plain
                      :loading="diagnosisSubmitting"
                      @click="handleConfirmLearningOutcome(gap)"
                    >
                      手动确认提升
                    </el-button>
                  </div>
                </article>
              </div>

              <div v-if="learningAbilityNames.length" class="closure-summary">
                已生成 {{ learningAbilityNames.length }} 类能力学习建议。
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="审批流程" name="approval">
            <el-empty v-if="approvalFlows.length === 0" description="尚未发起审批流程。" />
            <el-table v-else :data="approvalFlows" style="width: 100%">
              <el-table-column prop="nodeOrder" label="顺序" width="80" align="center" />
              <el-table-column prop="nodeName" label="节点" min-width="140" />
              <el-table-column prop="approverId" label="审核人 ID" width="120" />
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.approvalStatus === 1 ? 'success' : row.approvalStatus === 2 ? 'danger' : 'info'">
                    {{ row.approvalStatus === 1 ? '已通过' : row.approvalStatus === 2 ? '已驳回' : '待审核' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="approvalRemark" label="审核意见" />
              <el-table-column prop="approvalTime" label="审核时间" width="170" />
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="{ row }">
                  <template v-if="row.approvalStatus === 0">
                    <el-button type="success" size="small" @click="openApprovalDialog(row, true)">通过</el-button>
                    <el-button type="danger" size="small" @click="openApprovalDialog(row, false)">驳回</el-button>
                  </template>
                  <span v-else class="text-slate-400">已处理</span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </section>

    <MatchApprovalDialog
      v-model:visible="approvalDialogVisible"
      :flow="currentFlowNode"
      :record-id="matchResult?.id"
      :submitting="approvalSubmitting"
      @submit="handleSubmitApproval"
    />

    <!-- AI上下文Drawer -->
    <el-drawer v-model="aiContextDrawerVisible" title="AI 读取上下文" size="70%" :close-on-click-modal="false">
      <div v-loading="aiContextLoading">
        <template v-if="aiContext">
          <!-- 概览 -->
          <div class="ai-context-summary">
            <el-descriptions :column="3" border>
              <el-descriptions-item label="员工">{{ aiContext.empName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="岗位">{{ aiContext.postName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="匹配分">{{ aiContext.matchScore?.toFixed(2) || '-' }}</el-descriptions-item>
              <el-descriptions-item label="员工能力">{{ aiContext.employeeAbilities?.length || 0 }} 条</el-descriptions-item>
              <el-descriptions-item label="岗位要求">{{ aiContext.postRequirements?.length || 0 }} 条</el-descriptions-item>
              <el-descriptions-item label="能力差距">{{ aiContext.gaps?.length || 0 }} 条</el-descriptions-item>
              <el-descriptions-item label="证据来源">{{ aiContext.evidences?.length || 0 }} 条</el-descriptions-item>
              <el-descriptions-item label="风险信号">{{ aiContext.riskSignals?.length || 0 }} 条</el-descriptions-item>
              <el-descriptions-item label="预估Token">{{ aiContext.tokenEstimate || '-' }}</el-descriptions-item>
            </el-descriptions>
          </div>

          <!-- Tabs -->
          <el-tabs v-model="aiContextActiveTab" style="margin-top: 20px;">
            <!-- 能力差距 -->
            <el-tab-pane label="能力差距" name="gaps">
              <el-table :data="aiContext.gaps || []" style="width: 100%">
                <el-table-column prop="abilityName" label="能力" min-width="150" />
                <el-table-column prop="currentLevel" label="当前等级" width="100" align="center">
                  <template #default="{ row }">L{{ row.currentLevel || 0 }}</template>
                </el-table-column>
                <el-table-column prop="requiredLevel" label="要求等级" width="100" align="center">
                  <template #default="{ row }">L{{ row.requiredLevel }}</template>
                </el-table-column>
                <el-table-column prop="gap" label="差距" width="80" align="center" />
                <el-table-column prop="priority" label="优先级" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.priority === 'HIGH' ? 'danger' : row.priority === 'MEDIUM' ? 'warning' : 'info'" size="small">
                      {{ row.priority }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="来源" width="120">
                  <template #default="{ row }">
                    <el-button v-for="ref in (row.sourceRefs || [])" :key="ref" link type="primary" size="small"
                      @click="handleShowSourceRefDetail(ref)">
                      {{ ref.split(':').pop() }}
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 证据来源 -->
            <el-tab-pane label="证据来源" name="evidences">
              <el-table :data="aiContext.evidences || []" style="width: 100%">
                <el-table-column prop="sourceTitle" label="来源标题" min-width="200" />
                <el-table-column prop="sourceType" label="来源类型" width="120" />
                <el-table-column prop="abilityName" label="关联能力" width="150" />
                <el-table-column prop="confidenceScore" label="置信度" width="100" align="center">
                  <template #default="{ row }">{{ row.confidenceScore?.toFixed(0) || '-' }}</template>
                </el-table-column>
                <el-table-column prop="credibilityScore" label="可信度" width="100" align="center">
                  <template #default="{ row }">{{ row.credibilityScore?.toFixed(0) || '-' }}</template>
                </el-table-column>
                <el-table-column prop="evidenceStatus" label="状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.evidenceStatus === 'VERIFIED' ? 'success' : 'info'" size="small">
                      {{ row.evidenceStatus }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="详情" width="80">
                  <template #default="{ row }">
                    <el-button v-if="row.sourceRef" link type="primary" size="small"
                      @click="handleShowSourceRefDetail(row.sourceRef)">
                      查看
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 风险信号 -->
            <el-tab-pane label="风险信号" name="risks">
              <div v-if="(aiContext.riskSignals || []).length === 0" style="text-align: center; padding: 40px; color: #909399;">
                暂无风险信号
              </div>
              <div v-else class="risk-list">
                <el-card v-for="(risk, index) in (aiContext.riskSignals || [])" :key="index" class="risk-card">
                  <div class="risk-header">
                    <el-tag :type="getRiskLevelType(risk.riskLevel)" size="small">{{ risk.riskLevel }}</el-tag>
                    <span class="risk-type">{{ risk.riskType }}</span>
                  </div>
                  <div class="risk-message">{{ risk.message }}</div>
                  <div v-if="risk.sourceRefs?.length" class="risk-refs">
                    <el-button v-for="ref in risk.sourceRefs" :key="ref" link type="primary" size="small"
                      @click="handleShowSourceRefDetail(ref)">
                      {{ ref }}
                    </el-button>
                  </div>
                </el-card>
              </div>
            </el-tab-pane>

            <!-- 匹配分解 -->
            <el-tab-pane label="匹配分解" name="scores">
              <el-table :data="aiContext.scoreBreakdown || []" style="width: 100%">
                <el-table-column prop="dimension" label="维度" min-width="150" />
                <el-table-column prop="score" label="分数" width="100" align="center">
                  <template #default="{ row }">{{ row.score?.toFixed(2) || '-' }}</template>
                </el-table-column>
                <el-table-column prop="description" label="说明" min-width="300" />
              </el-table>
            </el-tab-pane>

            <!-- 原始JSON -->
            <el-tab-pane label="原始JSON" name="json">
              <pre class="ai-context-json">{{ JSON.stringify(aiContext, null, 2) }}</pre>
            </el-tab-pane>
          </el-tabs>
        </template>
        <el-empty v-else-if="!aiContextLoading" description="暂无AI上下文数据" />
      </div>
    </el-drawer>

    <!-- 来源详情Drawer -->
    <SourceRefDrawer
      v-model="sourceRefDetailVisible"
      :ref-value="currentSourceRefRef"
    />
  </div>
</template>

<style scoped>
.vector-panel {
  display: flex;
  gap: 24px;
  align-items: center;
  padding: 24px;
}

.vector-score,
.ai-score {
  color: var(--app-primary);
  font-size: 42px;
  line-height: 1;
  font-weight: 900;
}

.ai-confidence {
  margin-top: 10px;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.ai-layout {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.ai-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.dimension-list,
.reason-list,
.evidence-analysis-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dimension-list__row,
.reason-item,
.evidence-analysis-item {
  padding: 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.62);
}

.dimension-list__title,
.reason-item__factor,
.evidence-analysis-item__title {
  color: var(--app-text-strong);
  font-size: 13px;
  font-weight: 800;
}

.dimension-list__meta,
.reason-item__text,
.evidence-analysis-item__meta,
.evidence-analysis-item__conflict {
  margin-top: 6px;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.dimension-list__score {
  font-size: 18px;
  font-weight: 900;
}

.reason-item__head,
.evidence-analysis-item__head,
.dimension-list__row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.reason-item__refs,
.evidence-analysis-item__sources {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.fact-ref {
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  font-size: 11px;
  font-weight: 700;
}

.stack-list__item--warning {
  border: 1px solid rgba(217, 119, 6, 0.18);
  background: rgba(245, 158, 11, 0.08);
}

.stack-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.stack-list__item {
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.6);
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.closure-panel {
  min-height: 220px;
}

.closure-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.closure-gap {
  padding: 18px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.62);
}

.closure-gap__head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.closure-gap__title {
  color: var(--app-text-strong);
  font-size: 16px;
  font-weight: 800;
}

.closure-gap__reason {
  margin-top: 6px;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.closure-levels {
  display: flex;
  gap: 10px;
  margin: 14px 0;
  color: var(--app-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.closure-levels span {
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(37, 99, 235, 0.08);
}

.learning-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.learning-item,
.learning-empty {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.82);
}

.learning-item__title {
  color: var(--app-text-strong);
  font-size: 13px;
  font-weight: 700;
}

.learning-item__meta {
  display: flex;
  gap: 10px;
  margin-top: 5px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.learning-empty {
  color: var(--app-text-muted);
  font-size: 13px;
}

.closure-summary {
  margin-top: 14px;
  color: var(--app-text-muted);
  font-size: 12px;
}

@media (max-width: 1200px) {
  .score-parts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .closure-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .detail-band {
    grid-template-columns: 1fr;
    text-align: left;
  }

  .detail-band__score {
    text-align: left;
  }

  .ai-layout {
    grid-template-columns: 1fr;
  }

  .ai-detail-grid {
    grid-template-columns: 1fr;
  }
}

/* AI上下文样式 */
.ai-context-summary {
  margin-bottom: 20px;
}

.risk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.risk-card {
  margin: 0;
}

.risk-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.risk-type {
  color: var(--app-text-strong);
  font-size: 14px;
  font-weight: 700;
}

.risk-message {
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.risk-refs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.ai-context-json {
  padding: 16px;
  border-radius: 8px;
  background: #f5f7fa;
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
  max-height: 600px;
  overflow-y: auto;
}
</style>
