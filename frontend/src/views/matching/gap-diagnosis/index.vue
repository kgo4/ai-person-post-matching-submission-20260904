<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getComprehensiveDiagnosis, pageRecords } from '@/api'
import type {
  AbilityGapFact,
  ComprehensiveDiagnosisFact,
  ComprehensiveDiagnosisResult,
  EvidenceRiskFact,
  HardConditionFact,
  LearningResourceFact,
} from '@/api'
import type { MatchingRecord } from '@/api'

const router = useRouter()
const loading = ref(false)
const drawerVisible = ref(false)
const diagnosisLoading = ref(false)
const records = ref<MatchingRecord[]>([])
const total = ref(0)
const selectedRecord = ref<MatchingRecord | null>(null)
const diagnosisResult = ref<ComprehensiveDiagnosisResult | null>(null)

const query = reactive({
  current: 1,
  size: 10,
  keyword: '',
})

const fact = computed(() => diagnosisResult.value?.factPackage)

onMounted(loadRecords)

async function loadRecords() {
  loading.value = true
  try {
    const res = await pageRecords({
      current: query.current,
      size: query.size,
      keyword: query.keyword || undefined,
    })
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '诊断记录加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.current = 1
  loadRecords()
}

async function openDiagnosis(record: MatchingRecord) {
  selectedRecord.value = record
  diagnosisResult.value = null
  drawerVisible.value = true
  diagnosisLoading.value = true
  try {
    const res = await getComprehensiveDiagnosis(record.id)
    diagnosisResult.value = res.data || null
  } catch {
    diagnosisResult.value = null
    ElMessage.warning('综合诊断数据加载失败')
  } finally {
    diagnosisLoading.value = false
  }
}

function goDetail(record: MatchingRecord) {
  router.push(`/matching/detail/${record.id}`)
}

function formatScore(score?: number | null) {
  return score == null ? '-' : Number(score).toFixed(1)
}

function getScoreColor(score?: number | null) {
  if (score == null) return '#94a3b8'
  if (score >= 85) return '#059669'
  if (score >= 70) return '#2563eb'
  if (score >= 55) return '#d97706'
  return '#dc2626'
}

function getRiskColor(risk?: string) {
  switch (risk) {
    case 'CRITICAL': return '#dc2626'
    case 'HIGH': return '#ea580c'
    case 'MEDIUM': return '#d97706'
    default: return '#059669'
  }
}

function getRiskLabel(risk?: string) {
  switch (risk) {
    case 'CRITICAL': return '严重风险'
    case 'HIGH': return '高风险'
    case 'MEDIUM': return '中等风险'
    default: return '低风险'
  }
}

function getSeverityType(severity?: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  switch (severity) {
    case 'CRITICAL': return 'danger'
    case 'HIGH': return 'danger'
    case 'MEDIUM': return 'warning'
    case 'LOW': return 'success'
    default: return 'info'
  }
}

function getConditionStatusType(passed: boolean): '' | 'success' | 'danger' {
  return passed ? 'success' : 'danger'
}

function getRiskTypeLabel(riskType: string) {
  switch (riskType) {
    case 'WEAK_SOURCE': return '弱证据'
    case 'SINGLE_SOURCE': return '单一来源'
    case 'OUTDATED': return '过期证据'
    case 'LOW_CREDIBILITY': return '低可信度'
    default: return riskType
  }
}

function getMatchStatusLabel(status?: number) {
  switch (status) {
    case 1: return '强适配'
    case 2: return '适配'
    case 3: return '待观察'
    case 4: return '不适配'
    default: return '待审核'
  }
}

function getScreeningLevelLabel(level?: number) {
  switch (level) {
    case 1: return 'L1 硬条件通过'
    case 2: return 'L2 能力标签通过'
    case 3: return 'L3 AI深度匹配'
    default: return '未筛选'
  }
}

// 能力差距排序：核心 > 必备 > 普通；差距大的优先
const sortedAbilityGaps = computed(() => {
  if (!fact.value?.abilityGaps) return []
  return [...fact.value.abilityGaps].sort((a, b) => {
    if (a.core !== b.core) return a.core ? -1 : 1
    if (a.required !== b.required) return a.required ? -1 : 1
    const gapA = (a.requiredLevel || 0) - (a.currentLevel || 0)
    const gapB = (b.requiredLevel || 0) - (b.currentLevel || 0)
    return gapB - gapA
  })
})
</script>

<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <div class="page-hero__eyebrow">Comprehensive Gap Diagnosis</div>
        <h1 class="page-hero__title">综合差距诊断</h1>
        <p class="page-hero__desc">7 维度结构化诊断：硬条件、能力等级、语义匹配、证据可信、岗位任务、反馈校准、成长路径。</p>
      </div>
      <div class="toolbar-group">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="搜索人员或岗位"
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </div>
    </section>

    <section class="glass-card">
      <div class="panel-body">
        <div class="section-header">
          <div>
            <div class="section-title">匹配记录</div>
            <div class="section-desc">点击"综合诊断"查看多维度差距分析。</div>
          </div>
        </div>

        <el-table v-loading="loading" :data="records" style="width: 100%">
          <el-table-column label="人员" min-width="140">
            <template #default="{ row }">{{ row.empName || `人员#${row.empId}` }}</template>
          </el-table-column>
          <el-table-column label="目标岗位" min-width="160">
            <template #default="{ row }">{{ row.postName || `岗位#${row.postId}` }}</template>
          </el-table-column>
          <el-table-column label="匹配分" width="110" align="center">
            <template #default="{ row }">
              <strong :style="{ color: getScoreColor(row.finalMatchScore ?? row.aiMatchScore) }">
                {{ formatScore(row.finalMatchScore ?? row.aiMatchScore) }}
              </strong>
            </template>
          </el-table-column>
          <el-table-column label="筛选级别" width="140" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.screeningLevel >= 3 ? 'success' : row.screeningLevel >= 2 ? '' : 'info'">
                {{ getScreeningLevelLabel(row.screeningLevel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="openDiagnosis(row)">综合诊断</el-button>
              <el-button size="small" @click="goDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager-row">
          <el-pagination
            v-model:current-page="query.current"
            v-model:page-size="query.size"
            :total="total"
            layout="total, sizes, prev, pager, next"
            @current-change="loadRecords"
            @size-change="handleSearch"
          />
        </div>
      </div>
    </section>

    <!-- 综合诊断抽屉 -->
    <el-drawer v-model="drawerVisible" title="综合差距诊断" size="720px">
      <div v-loading="diagnosisLoading" class="diagnosis-drawer">
        <!-- 人员+岗位摘要 -->
        <div v-if="fact" class="record-summary">
          <div class="summary-main">
            <strong>{{ fact.empName || `人员#${fact.empId}` }}</strong>
            <span class="arrow">&rarr;</span>
            <strong>{{ fact.postName || `岗位#${fact.postId}` }}</strong>
            <el-tag v-if="fact.postLevel" size="small" type="info" style="margin-left: 8px">{{ fact.postLevel }}</el-tag>
          </div>
          <div class="summary-scores">
            <span class="score-chip" :style="{ color: getScoreColor(fact.scores?.finalMatchScore) }">
              {{ formatScore(fact.scores?.finalMatchScore) }}
            </span>
            <el-tag size="small" :type="getSeverityType(fact.scores?.matchStatus === 1 ? 'LOW' : fact.scores?.matchStatus === 4 ? 'HIGH' : 'MEDIUM')">
              {{ getMatchStatusLabel(fact.scores?.matchStatus) }}
            </el-tag>
          </div>
        </div>

        <template v-if="fact">
          <!-- 1. 多维度分数概览 -->
          <section class="diag-section">
            <h3 class="section-label">多维度分数</h3>
            <div class="score-grid">
              <div class="score-cell">
                <div class="score-cell__label">能力模型</div>
                <div class="score-cell__value" :style="{ color: getScoreColor(fact.scores?.abilityScore) }">
                  {{ formatScore(fact.scores?.abilityScore) }}
                </div>
              </div>
              <div class="score-cell">
                <div class="score-cell__label">语义匹配</div>
                <div class="score-cell__value" :style="{ color: getScoreColor(fact.scores?.semanticScore) }">
                  {{ formatScore(fact.scores?.semanticScore) }}
                </div>
              </div>
              <div class="score-cell">
                <div class="score-cell__label">证据可信</div>
                <div class="score-cell__value" :style="{ color: getScoreColor(fact.scores?.evidenceScore) }">
                  {{ formatScore(fact.scores?.evidenceScore) }}
                </div>
              </div>
              <div class="score-cell">
                <div class="score-cell__label">AI深度分</div>
                <div class="score-cell__value" :style="{ color: getScoreColor(fact.scores?.llmScore) }">
                  {{ formatScore(fact.scores?.llmScore) }}
                </div>
              </div>
              <div class="score-cell">
                <div class="score-cell__label">模型质量</div>
                <div class="score-cell__value" :style="{ color: getScoreColor(fact.scores?.modelQualityScore) }">
                  {{ formatScore(fact.scores?.modelQualityScore) }}
                </div>
              </div>
              <div class="score-cell">
                <div class="score-cell__label">反馈校准</div>
                <div class="score-cell__value" :style="{ color: (fact.scores?.feedbackAdjustment || 0) >= 0 ? '#059669' : '#dc2626' }">
                  {{ fact.scores?.feedbackAdjustment != null ? (fact.scores.feedbackAdjustment > 0 ? '+' : '') + formatScore(fact.scores.feedbackAdjustment) : '-' }}
                </div>
              </div>
            </div>
          </section>

          <!-- 2. 硬条件差距 -->
          <section v-if="fact.hardConditions.length" class="diag-section">
            <h3 class="section-label">
              硬条件检查
              <el-tag size="small" :type="fact.hardConditions.every(c => c.passed) ? 'success' : 'danger'">
                {{ fact.hardConditions.filter(c => c.passed).length }}/{{ fact.hardConditions.length }} 通过
              </el-tag>
            </h3>
            <div class="condition-list">
              <div v-for="(cond, idx) in fact.hardConditions" :key="idx" class="condition-item" :class="{ failed: !cond.passed }">
                <el-tag size="small" :type="getConditionStatusType(cond.passed)">{{ cond.passed ? '通过' : '未通过' }}</el-tag>
                <span class="condition-label">{{ cond.label || cond.field }}</span>
                <span class="condition-detail">
                  期望 {{ cond.expectedValue || '-' }}，实际 {{ cond.actualValue || '-' }}
                </span>
              </div>
            </div>
          </section>

          <!-- 3. 能力等级差距 -->
          <section v-if="sortedAbilityGaps.length" class="diag-section">
            <h3 class="section-label">
              能力差距
              <el-tag size="small" type="danger">{{ sortedAbilityGaps.length }} 项</el-tag>
            </h3>
            <div class="gap-list">
              <article v-for="gap in sortedAbilityGaps" :key="gap.tagId || gap.abilityName" class="gap-item">
                <div class="gap-header">
                  <div class="gap-name">
                    <el-tag v-if="gap.core" size="small" type="danger">核心</el-tag>
                    <el-tag v-else-if="gap.required" size="small" type="warning">必备</el-tag>
                    <strong>{{ gap.abilityName }}</strong>
                  </div>
                  <el-tag :type="gap.weakEvidence ? 'warning' : 'danger'" size="small">
                    {{ gap.weakEvidence ? '证据薄弱' : '等级不足' }}
                  </el-tag>
                </div>
                <div class="gap-levels">
                  <span>当前 <strong>L{{ gap.currentLevel ?? 0 }}</strong></span>
                  <span class="level-arrow">&rarr;</span>
                  <span>要求 <strong>L{{ gap.requiredLevel ?? '-' }}</strong></span>
                </div>
                <div v-if="gap.reason" class="gap-reason">{{ gap.reason }}</div>
                <div v-if="gap.evidenceSources?.length" class="gap-evidence">
                  <span class="evidence-label">证据来源：</span>
                  <el-tag v-for="ev in gap.evidenceSources" :key="ev.source" size="small" type="info">
                    {{ ev.source }} (L{{ ev.level ?? '?' }})
                  </el-tag>
                </div>
              </article>
            </div>
          </section>

          <!-- 4. 证据风险 -->
          <section v-if="fact.evidenceRisks.length" class="diag-section">
            <h3 class="section-label">
              证据风险
              <el-tag size="small" type="warning">{{ fact.evidenceRisks.length }} 项</el-tag>
            </h3>
            <div class="risk-list">
              <div v-for="(risk, idx) in fact.evidenceRisks" :key="idx" class="risk-item">
                <el-tag size="small" type="warning">{{ getRiskTypeLabel(risk.riskType) }}</el-tag>
                <span class="risk-desc">{{ risk.description }}</span>
                <span v-if="risk.primarySourceType" class="risk-source">来源: {{ risk.primarySourceType }}</span>
              </div>
            </div>
          </section>

          <!-- 5. 语义匹配信号 -->
          <section class="diag-section">
            <h3 class="section-label">语义匹配</h3>
            <div class="semantic-info">
              <div class="semantic-scores">
                <span>向量分: <strong :style="{ color: getScoreColor(fact.semanticSignals?.vectorScore) }">{{ formatScore(fact.semanticSignals?.vectorScore) }}</strong></span>
                <span>整体语义分: <strong :style="{ color: getScoreColor(fact.semanticSignals?.profileSemanticScore) }">{{ formatScore(fact.semanticSignals?.profileSemanticScore) }}</strong></span>
                <el-tag size="small" :type="fact.semanticSignals?.vectorAvailable ? 'success' : 'info'">
                  {{ fact.semanticSignals?.vectorAvailable ? '向量可用' : '向量缺失' }}
                </el-tag>
              </div>
            </div>
          </section>

          <!-- 6. 反馈信号 -->
          <section v-if="fact.feedbackSignals?.feedbackCalibration || fact.feedbackSignals?.manualRemark || fact.feedbackSignals?.feedbackReasons?.length" class="diag-section">
            <h3 class="section-label">反馈信号</h3>
            <div class="feedback-info">
              <div v-if="fact.feedbackSignals?.feedbackCalibration">
                校准值: <strong>{{ fact.feedbackSignals.feedbackCalibration > 0 ? '+' : '' }}{{ formatScore(fact.feedbackSignals.feedbackCalibration) }}</strong>
              </div>
              <div v-if="fact.feedbackSignals?.manualRemark" class="feedback-remark">
                备注: {{ fact.feedbackSignals.manualRemark }}
              </div>
              <div v-if="fact.feedbackSignals?.feedbackReasons?.length" class="feedback-reasons">
                <el-tag v-for="reason in fact.feedbackSignals.feedbackReasons" :key="reason" size="small">{{ reason }}</el-tag>
              </div>
            </div>
          </section>

          <!-- 7. 学习资源 -->
          <section v-if="fact.availableLearningResources.length" class="diag-section">
            <h3 class="section-label">
              学习资源
              <el-tag size="small" type="info">{{ fact.availableLearningResources.length }} 项</el-tag>
            </h3>
            <div class="resource-list">
              <div v-for="(res, idx) in fact.availableLearningResources" :key="idx" class="resource-item">
                <div class="resource-title">{{ res.title }}</div>
                <div class="resource-meta">
                  <el-tag v-if="res.resourceType" size="small" type="info">{{ res.resourceType }}</el-tag>
                  <span v-if="res.abilityName">关联: {{ res.abilityName }}</span>
                  <span v-if="res.difficultyLevel">难度: L{{ res.difficultyLevel }}</span>
                </div>
              </div>
            </div>
          </section>

          <!-- AI 分析（第二期） -->
          <section v-if="diagnosisResult?.aiAnalysis" class="diag-section ai-section">
            <h3 class="section-label">
              AI 综合分析
              <el-tag size="small" :style="{ backgroundColor: getRiskColor(diagnosisResult.aiAnalysis.riskLevel), color: '#fff' }">
                {{ getRiskLabel(diagnosisResult.aiAnalysis.riskLevel) }}
              </el-tag>
            </h3>
            <div class="ai-conclusion">{{ diagnosisResult.aiAnalysis.overallConclusion }}</div>

            <div v-if="diagnosisResult.aiAnalysis.dimensions?.length" class="ai-dimensions">
              <div v-for="dim in diagnosisResult.aiAnalysis.dimensions" :key="dim.dimension" class="ai-dim">
                <div class="ai-dim-header">
                  <strong>{{ dim.title }}</strong>
                  <el-tag size="small" :type="getSeverityType(dim.severity)">{{ dim.severity }}</el-tag>
                </div>
                <div v-if="dim.analysis" class="ai-dim-analysis">{{ dim.analysis }}</div>
                <ul v-if="dim.suggestions?.length" class="ai-dim-suggestions">
                  <li v-for="s in dim.suggestions" :key="s">{{ s }}</li>
                </ul>
              </div>
            </div>
          </section>
        </template>

        <el-empty v-else-if="!diagnosisLoading" description="暂无诊断数据" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.pager-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.diagnosis-drawer {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.record-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-radius: 10px;
  background: linear-gradient(135deg, #f0f9ff 0%, #f8fafc 100%);
  border: 1px solid #e0f2fe;
}

.summary-main {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
}

.arrow {
  color: #94a3b8;
}

.summary-scores {
  display: flex;
  align-items: center;
  gap: 10px;
}

.score-chip {
  font-size: 28px;
  font-weight: 700;
}

/* 维度区块 */
.diag-section {
  padding: 14px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #f1f5f9;
}

.section-label {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

/* 分数网格 */
.score-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.score-cell {
  padding: 10px;
  border-radius: 8px;
  background: #f8fafc;
  text-align: center;
}

.score-cell__label {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}

.score-cell__value {
  font-size: 20px;
  font-weight: 700;
}

/* 硬条件 */
.condition-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.condition-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
}

.condition-item.failed {
  background: #fef2f2;
  border-color: #fecaca;
}

.condition-label {
  font-weight: 500;
  color: #1e293b;
}

.condition-detail {
  font-size: 12px;
  color: #64748b;
  margin-left: auto;
}

/* 能力差距 */
.gap-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.gap-item {
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.gap-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.gap-name {
  display: flex;
  align-items: center;
  gap: 6px;
}

.gap-levels {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #475569;
}

.level-arrow {
  color: #94a3b8;
}

.gap-reason {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

.gap-evidence {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
}

.evidence-label {
  white-space: nowrap;
}

/* 证据风险 */
.risk-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.risk-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  background: #fffbeb;
  border: 1px solid #fde68a;
}

.risk-desc {
  color: #1e293b;
}

.risk-source {
  font-size: 12px;
  color: #64748b;
  margin-left: auto;
}

/* 语义匹配 */
.semantic-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.semantic-scores {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: #475569;
}

/* 反馈 */
.feedback-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: #475569;
}

.feedback-remark {
  padding: 8px;
  background: #f8fafc;
  border-radius: 6px;
}

.feedback-reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

/* 学习资源 */
.resource-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.resource-item {
  padding: 10px 12px;
  border-radius: 6px;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
}

.resource-title {
  font-weight: 500;
  color: #1e293b;
}

.resource-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

/* AI 分析 */
.ai-section {
  background: linear-gradient(135deg, #faf5ff 0%, #f5f3ff 100%);
  border-color: #e9d5ff;
}

.ai-conclusion {
  padding: 12px;
  background: #fff;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  color: #1e293b;
  margin-bottom: 12px;
}

.ai-dimensions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-dim {
  padding: 10px 12px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #f1f5f9;
}

.ai-dim-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.ai-dim-analysis {
  font-size: 13px;
  color: #475569;
  line-height: 1.5;
  margin-bottom: 6px;
}

.ai-dim-suggestions {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: #64748b;
}

.ai-dim-suggestions li {
  margin-bottom: 2px;
}
</style>
