<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Briefcase, DataLine, Refresh, Search, Setting, User } from '@element-plus/icons-vue'
import PageGuide from '@/components/common/PageGuide.vue'
import EvidenceFlowGraph from './EvidenceFlowGraph.vue'
import { useEvidenceCenter } from '@/composables/useEvidenceCenter'
import {
  getEvidenceStrength, getStrengthLabel, getStrengthType,
  getSourceTypeText, getTargetTypeText, getEvaluationSourceText,
  getStatusText, getStatusType, scoreText, formatDate,
  getSubjectPlaceholder, getSubjectLabel, getVerifiedEvidenceCount,
  CONFIDENCE_HINT, CREDIBILITY_HINT,
} from './utils'
import type { ContestEvidenceItem, EvidenceChainAbility, EvidenceChainEvidence } from '@/api/contest'

const router = useRouter()

const {
  loading, backfillLoading, evidenceData, total, currentPage, pageSize, summary,
  chainMode, subjectLoading, selectedSubjectId, subjectOptions,
  chainLoading, chainData, selectedAbility,
  remoteSearchSubjects, loadEvidenceChain, fetchEvidence: apiFetchEvidence,
  fetchSummary, submitReview: apiSubmitReview, handleBackfill: apiHandleBackfill,
} = useEvidenceCenter()

const activeTab = ref<'trace' | 'manage'>('trace')
const abilitySortBy = ref<'confidence' | 'credibility' | 'evidence'>('credibility')
const abilityKeyword = ref('')

const guideItems = [
  { label: '选择人员', detail: '查看该人员全部能力、能力来源渠道、置信度和可信度。' },
  { label: '选择岗位', detail: '查看岗位能力需求、JD/RAG/模型来源和可信证据。' },
  { label: '证据质量', detail: '按可信度排序，快速识别强证据、弱证据和缺证据能力。' },
]

const filters = reactive({ sourceType: '', targetType: '', evidenceStatus: '', abilityName: '' })

const drawerVisible = ref(false)
const currentEvidence = ref<ContestEvidenceItem | EvidenceChainEvidence | null>(null)
const reviewForm = reactive({ evidenceStatus: 'VERIFIED', reviewComment: '' })

// ===================== 计算属性 =====================

const sortedAbilities = computed(() => {
  if (!chainData.value) return []
  const keyword = abilityKeyword.value.trim().toLowerCase()
  const abilities = [...chainData.value.abilities]
    .filter(a => !keyword || a.abilityName.toLowerCase().includes(keyword))
  abilities.sort((a, b) => {
    switch (abilitySortBy.value) {
      case 'confidence': return (b.averageConfidence || 0) - (a.averageConfidence || 0)
      case 'credibility': return (b.averageCredibility || 0) - (a.averageCredibility || 0)
      case 'evidence': return (b.evidenceCount || 0) - (a.evidenceCount || 0)
      default: return 0
    }
  })
  return abilities
})

const sourceDistribution = computed(() => Object.entries(chainData.value?.sourceTypeDistribution || {}))

// ===================== 统计摘要（证据管理） =====================

interface EvidenceSummaryData {
  totalCount?: number
  averageCredibility?: number | string
  statusDistribution?: Record<string, number>
  sourceTypeDistribution?: Record<string, number>
}

const summaryStats = computed<EvidenceSummaryData>(() => {
  const s = summary.value as EvidenceSummaryData | null
  return {
    totalCount: s?.totalCount ?? 0,
    averageCredibility: Number(s?.averageCredibility ?? 0).toFixed(1),
    statusDistribution: s?.statusDistribution || {},
    sourceTypeDistribution: s?.sourceTypeDistribution || {},
  }
})

const STATUS_ORDER = ['PENDING', 'VERIFIED', 'REJECTED']
const summaryStatusList = computed(() => {
  const dist = summaryStats.value.statusDistribution || {}
  return STATUS_ORDER
    .filter(status => dist[status] != null)
    .map(status => [status, dist[status] as number] as [string, number])
})

const summarySourceList = computed(() => {
  const dist = summaryStats.value.sourceTypeDistribution || {}
  return Object.entries(dist).slice(0, 6)
})

function statusColor(status: string): string {
  const map: Record<string, string> = { PENDING: '#e6a23c', VERIFIED: '#67c23a', REJECTED: '#f56c6c' }
  return map[status] || 'var(--app-text-strong)'
}

// ===================== 数据加载 =====================

function resetChainState() {
  selectedSubjectId.value = undefined
  subjectOptions.value = []
  chainData.value = null
  selectedAbility.value = null
  abilityKeyword.value = ''
  remoteSearchSubjects('', chainMode.value)
}

function openReview(evidence: ContestEvidenceItem | EvidenceChainEvidence) {
  currentEvidence.value = evidence
  reviewForm.evidenceStatus = 'VERIFIED'
  reviewForm.reviewComment = ''
  drawerVisible.value = true
}

/** 点击证据流图中的能力节点，联动左侧选中能力 */
function onGraphSelectAbility(abilityId: number) {
  const ability = chainData.value?.abilities.find(a => a.abilityId === abilityId)
  if (ability) selectedAbility.value = ability
}

async function doSubmitReview() {
  if (!currentEvidence.value) return
  const ok = await apiSubmitReview(currentEvidence.value.id, reviewForm.evidenceStatus, reviewForm.reviewComment)
  if (ok) {
    drawerVisible.value = false
    await apiFetchEvidence(filters)
    await fetchSummary()
    if (selectedSubjectId.value) await loadEvidenceChain(chainMode.value, selectedSubjectId.value)
  }
}

async function doBackfill(sourceType: string) {
  const ok = await apiHandleBackfill(sourceType)
  if (ok) {
    await apiFetchEvidence(filters)
    await fetchSummary()
    if (selectedSubjectId.value) await loadEvidenceChain(chainMode.value, selectedSubjectId.value)
  }
}

// Wrappers for template compatibility
function remoteSearch(keyword: string) { remoteSearchSubjects(keyword, chainMode.value) }
function doFetchEvidence() { apiFetchEvidence(filters) }
function doLoadChain() { if (selectedSubjectId.value) loadEvidenceChain(chainMode.value, selectedSubjectId.value) }

watch(chainMode, resetChainState)

onMounted(() => {
  doFetchEvidence()
  fetchSummary()
  remoteSearchSubjects('', chainMode.value)
})
</script>

<template>
  <div class="evidence-reader">
    <!-- 顶部导航 -->
    <div class="brain-navbar">
      <el-page-header @back="router.push('/dashboard')">
        <template #content>
          <span class="page-title">能力证据中心</span>
        </template>
        <template #extra>
          <el-button type="primary" size="small" :icon="ArrowRight" @click="router.push('/rag/knowledge')">
            AI 知识资产
          </el-button>
        </template>
      </el-page-header>
    </div>

    <!-- 主功能区：证据链追溯 / 证据管理 -->
    <el-tabs v-model="activeTab" class="evidence-tabs">
      <el-tab-pane label="证据链追溯" name="trace">
        <!-- 页面引导 -->
        <PageGuide
          title="证据链阅读器"
          description="选择人员或岗位，追溯每项能力的来源渠道、置信度、可信度与支撑证据，确认每条能力信息均有据可查。"
          :items="guideItems"
        />

        <!-- 主体选择区 -->
    <section class="subject-selector">
      <div class="selector-toolbar">
        <el-radio-group v-model="chainMode">
          <el-radio-button label="employee">
            <el-icon><User /></el-icon>
            选择人员查看能力来源
          </el-radio-button>
          <el-radio-button label="post">
            <el-icon><Briefcase /></el-icon>
            选择岗位查看需求依据
          </el-radio-button>
        </el-radio-group>

        <el-select
          v-model="selectedSubjectId"
          filterable
          remote
          clearable
          reserve-keyword
          :remote-method="remoteSearch"
          :loading="subjectLoading"
          :placeholder="getSubjectPlaceholder(chainMode)"
          class="subject-search"
          @change="doLoadChain"
        >
          <el-option
            v-for="item in subjectOptions"
            :key="item.id"
            :label="getSubjectLabel(item, chainMode)"
            :value="item.id"
          />
        </el-select>

        <el-button :icon="Search" :loading="chainLoading" type="primary" @click="doLoadChain">查看证据链</el-button>
        <el-button :icon="Refresh" @click="resetChainState">重置</el-button>
      </div>
    </section>

    <!-- 证据链主工作区 -->
    <section v-if="chainData" v-loading="chainLoading" class="chain-workspace">
      <!-- 概览卡片 -->
      <div class="overview-bar">
        <div class="overview-card subject-card">
          <span class="overview-label">{{ chainData.subjectType === 'EMPLOYEE' ? '人员' : '岗位' }}</span>
          <strong class="overview-value">{{ chainData.subjectName }}</strong>
          <small class="overview-code">{{ chainData.subjectCode }}</small>
        </div>
        <div class="overview-card">
          <span class="overview-label">能力项</span>
          <strong class="overview-value">{{ chainData.abilityCount }}</strong>
        </div>
        <div class="overview-card">
          <span class="overview-label">证据总数</span>
          <strong class="overview-value">{{ chainData.evidenceCount }}</strong>
        </div>
        <div class="overview-card">
          <el-tooltip :content="CONFIDENCE_HINT" placement="top">
            <span class="overview-label">平均置信度</span>
          </el-tooltip>
          <strong class="overview-value">{{ scoreText(chainData.averageConfidence) }}</strong>
        </div>
        <div class="overview-card">
          <el-tooltip :content="CREDIBILITY_HINT" placement="top">
            <span class="overview-label">平均可信度</span>
          </el-tooltip>
          <strong class="overview-value">{{ scoreText(chainData.averageCredibility) }}</strong>
        </div>
      </div>

      <!-- 主内容区：左侧能力清单 + 右侧详情 -->
      <div class="chain-content">
        <!-- 左侧：能力清单 -->
        <div class="ability-list-panel">
          <div class="panel-header">
            <h3 class="panel-title">
              <el-icon><DataLine /></el-icon>
              能力清单
            </h3>
            <div class="sort-controls">
              <el-input
                v-model="abilityKeyword"
                placeholder="搜索能力"
                clearable
                size="small"
                class="ability-search"
              />
              <span class="sort-label">排序：</span>
              <el-radio-group v-model="abilitySortBy" size="small">
                <el-radio-button label="credibility">可信度</el-radio-button>
                <el-radio-button label="confidence">置信度</el-radio-button>
                <el-radio-button label="evidence">证据数</el-radio-button>
              </el-radio-group>
            </div>
          </div>

          <div class="ability-list">
            <button
              v-for="ability in sortedAbilities"
              :key="ability.abilityId"
              class="ability-item"
              :class="{
                'ability-item--active': selectedAbility?.abilityId === ability.abilityId,
                'ability-item--strong': getEvidenceStrength(ability) === 'strong',
                'ability-item--weak': getEvidenceStrength(ability) === 'weak',
                'ability-item--none': getEvidenceStrength(ability) === 'none',
              }"
              @click="selectedAbility = ability"
            >
              <div class="ability-item-header">
                <strong class="ability-name">{{ ability.abilityName }}</strong>
                <el-tag :type="getStrengthType(getEvidenceStrength(ability))" size="small">
                  {{ getStrengthLabel(getEvidenceStrength(ability)) }}
                </el-tag>
              </div>

              <div class="ability-item-meta">
                <span class="meta-item">
                  <span class="meta-label">等级</span>
                  <span class="meta-value">{{ ability.level || '-' }}</span>
                </span>
                <span class="meta-item">
                  <span class="meta-label">来源渠道</span>
                  <span class="meta-value source-tag">{{ getEvaluationSourceText(ability.source) }}</span>
                </span>
                <span class="meta-item">
                  <span class="meta-label">证据数</span>
                  <span class="meta-value">
                    {{ ability.evidenceCount }}
                    <template v-if="ability.evidences.length">/ 已验证 {{ getVerifiedEvidenceCount(ability) }}</template>
                  </span>
                </span>
              </div>

              <div class="ability-item-scores">
                <span class="score-item">
                  <span class="score-label">置信</span>
                  <span class="score-value">{{ scoreText(ability.averageConfidence) }}</span>
                </span>
                <span class="score-item">
                  <span class="score-label">可信</span>
                  <span class="score-value">{{ scoreText(ability.averageCredibility) }}</span>
                </span>
              </div>
            </button>

            <el-empty v-if="sortedAbilities.length === 0" description="暂无能力数据" :image-size="48" />
          </div>
        </div>

        <!-- 右侧：选中能力详情 -->
        <div class="ability-detail-panel">
          <template v-if="selectedAbility">
            <div class="detail-header">
              <h3 class="detail-title">{{ selectedAbility.abilityName }}</h3>
              <el-tag :type="getStrengthType(getEvidenceStrength(selectedAbility))" size="default">
                {{ getStrengthLabel(getEvidenceStrength(selectedAbility)) }}
              </el-tag>
            </div>

            <!-- 来源渠道信息 -->
            <div class="source-channel-section">
              <h4 class="section-title">来源渠道</h4>
              <div class="channel-grid">
                <div class="channel-item">
                  <span class="channel-label">评价来源</span>
                  <span class="channel-value">{{ getEvaluationSourceText(selectedAbility.source) }}</span>
                </div>
                <div class="channel-item">
                  <span class="channel-label">来源权重</span>
                  <span class="channel-value">{{ selectedAbility.sourceWeight != null ? selectedAbility.sourceWeight : '-' }}</span>
                </div>
                <div class="channel-item">
                  <span class="channel-label">评估日期</span>
                  <span class="channel-value">{{ formatDate(selectedAbility.evaluationDate) }}</span>
                </div>
                <div class="channel-item">
                  <span class="channel-label">掌握等级</span>
                  <span class="channel-value">{{ selectedAbility.level || '-' }}</span>
                </div>
              </div>
              <div v-if="selectedAbility.remark" class="remark-section">
                <span class="remark-label">备注说明</span>
                <p class="remark-text">{{ selectedAbility.remark }}</p>
              </div>
            </div>

            <!-- 岗位特有属性 -->
            <div v-if="chainMode === 'post'" class="post-attributes">
              <h4 class="section-title">岗位要求</h4>
              <div class="attr-tags">
                <el-tag v-if="selectedAbility.required" type="danger">必需能力</el-tag>
                <el-tag v-if="selectedAbility.core" type="warning">核心能力</el-tag>
                <el-tag v-if="selectedAbility.weight != null" type="info">权重 {{ selectedAbility.weight }}</el-tag>
                <el-tag v-if="selectedAbility.modelVersion" type="info">模型版本 {{ selectedAbility.modelVersion }}</el-tag>
              </div>
            </div>

            <!-- 证据质量统计 -->
            <div class="evidence-stats">
              <h4 class="section-title">证据质量</h4>
              <div class="stats-grid">
                <div class="stat-item">
                  <span class="stat-label">置信度</span>
                  <el-progress
                    :percentage="Number(scoreText(selectedAbility.averageConfidence))"
                    :color="selectedAbility.averageConfidence >= 70 ? '#67c23a' : selectedAbility.averageConfidence >= 50 ? '#e6a23c' : '#f56c6c'"
                  />
                </div>
                <div class="stat-item">
                  <span class="stat-label">可信度</span>
                  <el-progress
                    :percentage="Number(scoreText(selectedAbility.averageCredibility))"
                    :color="selectedAbility.averageCredibility >= 70 ? '#67c23a' : selectedAbility.averageCredibility >= 50 ? '#e6a23c' : '#f56c6c'"
                  />
                </div>
              </div>
            </div>

            <!-- 证据链路视图 -->
            <div class="evidence-chain-view">
              <h4 class="section-title">证据链路</h4>
              <div class="chain-path">
                <div class="chain-node chain-node--subject">
                  <strong>{{ chainData?.subjectName }}</strong>
                  <small>{{ chainData?.subjectType === 'EMPLOYEE' ? '人员' : '岗位' }}</small>
                </div>
                <div class="chain-arrow">→</div>
                <div class="chain-node chain-node--ability">
                  <strong>{{ selectedAbility.abilityName }}</strong>
                  <small>等级 {{ selectedAbility.level || '-' }}</small>
                </div>
                <div class="chain-arrow">→</div>
                <div class="chain-node chain-node--source">
                  <strong>{{ getEvaluationSourceText(selectedAbility.source) }}</strong>
                  <small>来源渠道</small>
                </div>
                <div class="chain-arrow">→</div>
                <div class="chain-node chain-node--evidence">
                  <strong>{{ selectedAbility.evidenceCount }} 条证据</strong>
                  <small>{{ getStrengthLabel(getEvidenceStrength(selectedAbility)) }}</small>
                </div>
              </div>
            </div>

            <!-- 支撑证据列表 -->
            <div class="evidence-list-section">
              <h4 class="section-title">支撑证据</h4>
              <div class="evidence-cards">
                <div v-for="evidence in selectedAbility.evidences" :key="evidence.id" class="evidence-card">
                  <div class="evidence-card-header">
                    <span class="evidence-source-type">{{ getSourceTypeText(evidence.sourceType) }}</span>
                    <span class="evidence-card-actions">
                      <el-tag :type="getStatusType(evidence.evidenceStatus)" size="small">
                        {{ getStatusText(evidence.evidenceStatus) }}
                      </el-tag>
                      <el-button link type="primary" size="small" @click="openReview(evidence)">审核</el-button>
                    </span>
                  </div>
                  <div class="evidence-card-title">{{ evidence.sourceTitle || evidence.evidenceCode }}</div>
                  <div class="evidence-card-scores">
                    <span>置信度: {{ scoreText(evidence.confidenceScore) }}</span>
                    <span>可信度: {{ scoreText(evidence.credibilityScore) }}</span>
                  </div>
                  <div class="evidence-card-time">{{ formatDate(evidence.createdTime) }}</div>
                </div>
                <el-empty v-if="selectedAbility.evidences.length === 0" description="暂无支撑证据" :image-size="48" />
              </div>
            </div>
          </template>

          <el-empty v-else description="请从左侧选择一项能力查看详情" :image-size="64" />
        </div>
      </div>

      <!-- 证据流可视化：主体 → 能力 → 证据 -->
      <EvidenceFlowGraph
        :chain-data="chainData"
        :selected-ability-id="selectedAbility?.abilityId ?? null"
        @select-ability="onGraphSelectAbility"
      />

      <!-- 来源分布 -->
      <div class="distribution-bar">
        <h4 class="section-title">来源渠道分布</h4>
        <div class="distribution-grid">
          <div v-for="[type, count] in sourceDistribution" :key="type" class="distribution-item">
            <span class="distribution-label">{{ getSourceTypeText(type) }}</span>
            <strong class="distribution-value">{{ count }}</strong>
          </div>
          <el-empty v-if="sourceDistribution.length === 0" description="暂无来源统计" :image-size="32" />
        </div>
      </div>
    </section>

    <!-- 未选择主体时的空状态 -->
    <section v-else class="empty-state">
      <el-empty description="请先搜索并选择人员或岗位，查看能力来源和证据质量">
        <template #image>
          <div class="empty-icon">
            <el-icon :size="48"><DataLine /></el-icon>
          </div>
        </template>
      </el-empty>
    </section>
      </el-tab-pane>

      <el-tab-pane label="证据管理" name="manage">
        <!-- 全局统计摘要 -->
        <section class="summary-section">
          <div class="summary-cards">
            <div class="summary-card">
              <span class="summary-label">证据总数</span>
              <strong class="summary-value">{{ summaryStats.totalCount }}</strong>
            </div>
            <div v-for="[status, count] in summaryStatusList" :key="status" class="summary-card">
              <span class="summary-label">{{ getStatusText(status) }}</span>
              <strong class="summary-value" :style="{ color: statusColor(status) }">{{ count }}</strong>
            </div>
            <div class="summary-card">
              <el-tooltip :content="CREDIBILITY_HINT" placement="top">
                <span class="summary-label">平均可信度</span>
              </el-tooltip>
              <strong class="summary-value">{{ summaryStats.averageCredibility }}</strong>
            </div>
          </div>
          <div v-if="summarySourceList.length" class="summary-sources">
            <span class="summary-sources-label">来源分布：</span>
            <el-tag v-for="[type, count] in summarySourceList" :key="type" size="small" class="summary-source-tag">
              {{ getSourceTypeText(type) }} {{ count }}
            </el-tag>
          </div>
        </section>

        <!-- 管理工具 -->
        <section class="management-section">
          <div class="management-content">
            <div class="management-heading">
              <el-icon><Setting /></el-icon>
              <span>数据治理工具</span>
              <span class="management-hint">从历史数据回填证据、浏览全量证据列表并执行人工审核，确保每条能力信息都有据可查。</span>
            </div>
        <!-- 数据回填 -->
        <el-card class="section-card">
          <template #header>
            <span>数据回填</span>
            <span class="management-hint">系统每小时自动回填一次（启动 1 分钟后首次），无需手动操作；如需立即补充可点击下方按钮。</span>
          </template>
          <el-space wrap>
            <el-button :loading="backfillLoading" @click="doBackfill('JD_IMPORT')">回填 JD 导入</el-button>
            <el-button :loading="backfillLoading" @click="doBackfill('RESUME_PARSE')">回填简历解析</el-button>
            <el-button :loading="backfillLoading" @click="doBackfill('MATCHING_FEEDBACK')">回填匹配反馈</el-button>
            <el-button :loading="backfillLoading" @click="doBackfill('EMP_ABILITY')">回填员工能力</el-button>
            <el-button :loading="backfillLoading" @click="doBackfill('POST_ABILITY_MODEL')">回填岗位能力</el-button>
          </el-space>
        </el-card>

        <!-- 全量证据列表 -->
        <el-card class="section-card">
          <template #header>
            <span>全量证据列表</span>
          </template>
          <div class="filter-bar">
            <el-select v-model="filters.sourceType" placeholder="来源类型" clearable @change="doFetchEvidence">
              <el-option label="JD导入" value="JD_IMPORT" />
              <el-option label="简历解析" value="RESUME_PARSE" />
              <el-option label="匹配反馈" value="MATCHING_FEEDBACK" />
              <el-option label="员工能力" value="EMP_ABILITY" />
              <el-option label="岗位能力模型" value="POST_ABILITY_MODEL" />
              <el-option label="人工录入" value="MANUAL" />
            </el-select>
            <el-select v-model="filters.targetType" placeholder="目标类型" clearable @change="doFetchEvidence">
              <el-option label="员工能力" value="EMP_ABILITY" />
              <el-option label="岗位模型" value="POST_ABILITY_MODEL" />
              <el-option label="匹配记录" value="MATCHING_RECORD" />
            </el-select>
            <el-select v-model="filters.evidenceStatus" placeholder="状态" clearable @change="doFetchEvidence">
              <el-option label="待审核" value="PENDING" />
              <el-option label="已验证" value="VERIFIED" />
              <el-option label="已拒绝" value="REJECTED" />
            </el-select>
            <el-input v-model="filters.abilityName" placeholder="能力名称" clearable @change="doFetchEvidence" />
          </div>

          <el-table :data="evidenceData" v-loading="loading" stripe>
            <el-table-column prop="sourceType" label="来源类型" width="130">
              <template #default="{ row }">{{ getSourceTypeText(row.sourceType) }}</template>
            </el-table-column>
            <el-table-column prop="sourceTitle" label="来源标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="targetType" label="目标类型" width="150">
              <template #default="{ row }">{{ getTargetTypeText(row.targetType) }}</template>
            </el-table-column>
            <el-table-column prop="abilityName" label="能力名称" width="140" />
            <el-table-column prop="confidenceScore" label="置信度" width="90" />
            <el-table-column prop="credibilityScore" label="可信度" width="90" />
            <el-table-column prop="evidenceStatus" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.evidenceStatus)">{{ getStatusText(row.evidenceStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="创建时间" width="170" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openReview(row)">审核</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="total"
            layout="total, sizes, prev, pager, next"
            class="pagination"
            @current-change="doFetchEvidence"
            @size-change="doFetchEvidence"
          />
        </el-card>
          </div>
        </section>
      </el-tab-pane>
    </el-tabs>

    <!-- 审核抽屉 -->
    <el-drawer v-model="drawerVisible" title="证据审核" size="520px">
      <template v-if="currentEvidence">
        <el-descriptions :column="1" border class="drawer-descriptions">
          <el-descriptions-item label="证据编码">{{ currentEvidence.evidenceCode }}</el-descriptions-item>
          <el-descriptions-item label="来源类型">{{ getSourceTypeText(currentEvidence.sourceType) }}</el-descriptions-item>
          <el-descriptions-item label="来源标题">{{ currentEvidence.sourceTitle || '-' }}</el-descriptions-item>
          <el-descriptions-item label="能力名称">{{ currentEvidence.abilityName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="置信度">{{ currentEvidence.confidenceScore }}</el-descriptions-item>
          <el-descriptions-item label="可信度">{{ currentEvidence.credibilityScore }}</el-descriptions-item>
        </el-descriptions>

        <el-input
          v-if="currentEvidence.sourceText"
          type="textarea"
          :model-value="currentEvidence.sourceText"
          :rows="7"
          readonly
          class="source-text"
        />

        <el-form label-width="84px">
          <el-form-item label="审核结果">
            <el-radio-group v-model="reviewForm.evidenceStatus">
              <el-radio value="VERIFIED">验证通过</el-radio>
              <el-radio value="REJECTED">拒绝</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="审核意见">
            <el-input v-model="reviewForm.reviewComment" type="textarea" :rows="3" placeholder="请输入审核意见" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="doSubmitReview">提交审核</el-button>
          </el-form-item>
        </el-form>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
/* ===================== Tab 布局 ===================== */
.evidence-tabs :deep(.el-tab-pane) {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ability-search {
  width: 120px;
}

.evidence-card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ===================== 统计摘要 ===================== */
.summary-section {
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.summary-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.48);
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.summary-label {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 600;
}

.summary-value {
  color: var(--app-text-strong);
  font-size: 24px;
  font-weight: 800;
  line-height: 1.2;
}

.summary-sources {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.summary-sources-label {
  color: var(--app-text-muted);
  font-size: 12px;
}

/* ===================== 基础布局 ===================== */
.evidence-reader {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 24px;
}

.page-title {
  font-size: 16px;
  font-weight: 700;
}

/* ===================== 主体选择区 ===================== */
.subject-selector {
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  padding: 16px 20px;
}

.selector-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.subject-search {
  width: min(420px, 100%);
}

/* ===================== 概览栏 ===================== */
.overview-bar {
  display: grid;
  grid-template-columns: minmax(180px, 1.4fr) repeat(4, minmax(96px, 1fr));
  gap: 10px;
}

.overview-card {
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(8px);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.overview-label {
  display: block;
  color: var(--app-text-muted);
  font-size: 11px;
  font-weight: 600;
}

.overview-value {
  display: block;
  color: var(--app-text-strong);
  font-size: 22px;
  font-weight: 800;
  line-height: 1.2;
}

.overview-code {
  display: block;
  color: var(--app-text-muted);
  font-size: 11px;
  margin-top: 0;
}

.subject-card .overview-value {
  font-size: 16px;
}

/* ===================== 证据链主工作区 ===================== */
.chain-workspace {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chain-content {
  display: grid;
  grid-template-columns: 380px minmax(0, 1fr);
  gap: 16px;
  min-height: 600px;
}

/* ===================== 左侧：能力清单 ===================== */
.ability-list-panel {
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
  flex-wrap: wrap;
  gap: 8px;
}

.panel-title {
  display: flex;
  gap: 6px;
  align-items: center;
  color: var(--app-text-secondary);
  font-weight: 700;
  font-size: 14px;
  margin: 0;
}

.sort-controls {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sort-label {
  color: var(--app-text-muted);
  font-size: 12px;
}

.ability-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ability-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;
}

.ability-item:hover {
  border-color: var(--app-primary);
  background: rgba(255, 255, 255, 0.48);
}

.ability-item--active {
  border-color: var(--app-primary);
  background: rgba(59, 130, 246, 0.06);
  box-shadow: 0 0 0 1px var(--app-primary);
}

.ability-item--strong {
  border-left: 3px solid #67c23a;
}

.ability-item--weak {
  border-left: 3px solid #e6a23c;
}

.ability-item--none {
  border-left: 3px solid #f56c6c;
}

.ability-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ability-name {
  color: var(--app-text-strong);
  font-size: 14px;
}

.ability-item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.meta-item {
  display: flex;
  gap: 4px;
  align-items: center;
}

.meta-label {
  color: var(--app-text-muted);
  font-size: 12px;
}

.meta-value {
  color: var(--app-text-secondary);
  font-size: 12px;
  font-weight: 500;
}

.source-tag {
  color: var(--app-primary);
  background: rgba(59, 130, 246, 0.08);
  padding: 1px 6px;
  border-radius: 4px;
}

.ability-item-scores {
  display: flex;
  gap: 16px;
}

.score-item {
  display: flex;
  gap: 4px;
  align-items: center;
}

.score-label {
  color: var(--app-text-muted);
  font-size: 11px;
}

.score-value {
  color: var(--app-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

/* ===================== 右侧：能力详情 ===================== */
.ability-detail-panel {
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  padding: 20px;
  overflow-y: auto;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
}

.detail-title {
  color: var(--app-text-strong);
  font-size: 18px;
  font-weight: 700;
  margin: 0;
}

.section-title {
  color: var(--app-text-secondary);
  font-size: 13px;
  font-weight: 600;
  margin: 0 0 12px 0;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
}

/* 来源渠道 */
.source-channel-section {
  margin-bottom: 20px;
}

.channel-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.channel-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.48);
  border-radius: 6px;
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.channel-label {
  color: var(--app-text-muted);
  font-size: 12px;
}

.channel-value {
  color: var(--app-text-strong);
  font-size: 14px;
  font-weight: 500;
}

.remark-section {
  margin-top: 12px;
  padding: 10px;
  background: rgba(245, 158, 11, 0.06);
  border-radius: 6px;
  border: 1px solid rgba(245, 158, 11, 0.2);
}

.remark-label {
  color: var(--app-text-muted);
  font-size: 12px;
  display: block;
  margin-bottom: 4px;
}

.remark-text {
  color: var(--app-text-secondary);
  font-size: 13px;
  margin: 0;
  line-height: 1.5;
}

/* 岗位属性 */
.post-attributes {
  margin-bottom: 20px;
}

.attr-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* 证据质量 */
.evidence-stats {
  margin-bottom: 20px;
}

.stats-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-label {
  color: var(--app-text-muted);
  font-size: 12px;
}

/* 证据链路视图 */
.evidence-chain-view {
  margin-bottom: 20px;
}

.chain-path {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.48);
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  overflow-x: auto;
}

.chain-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 16px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(148, 163, 184, 0.12);
  min-width: 100px;
  text-align: center;
}

.chain-node strong {
  color: var(--app-text-strong);
  font-size: 13px;
}

.chain-node small {
  color: var(--app-text-muted);
  font-size: 11px;
}

.chain-node--subject {
  border-color: var(--app-primary);
  background: rgba(59, 130, 246, 0.06);
}

.chain-node--ability {
  border-color: #8b5cf6;
  background: rgba(139, 92, 246, 0.06);
}

.chain-node--source {
  border-color: #f59e0b;
  background: rgba(245, 158, 11, 0.06);
}

.chain-node--evidence {
  border-color: #10b981;
  background: rgba(16, 185, 129, 0.06);
}

.chain-arrow {
  color: var(--app-text-muted);
  font-size: 18px;
  font-weight: 700;
  flex-shrink: 0;
}

/* 支撑证据列表 */
.evidence-list-section {
  margin-bottom: 20px;
}

.evidence-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 400px;
  overflow-y: auto;
}

.evidence-card {
  padding: 12px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.evidence-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.evidence-source-type {
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 500;
}

.evidence-card-title {
  color: var(--app-text-secondary);
  font-size: 13px;
}

.evidence-card-scores {
  display: flex;
  gap: 16px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.evidence-card-time {
  color: var(--app-text-muted);
  font-size: 11px;
}

/* ===================== 来源分布 ===================== */
.distribution-bar {
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  padding: 16px;
}

.distribution-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.distribution-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 20px;
  background: rgba(255, 255, 255, 0.48);
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  min-width: 100px;
}

.distribution-label {
  color: var(--app-text-muted);
  font-size: 12px;
}

.distribution-value {
  color: var(--app-text-strong);
  font-size: 20px;
  font-weight: 700;
}

/* ===================== 空状态 ===================== */
.empty-state {
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  padding: 80px 16px;
  text-align: center;
}

.empty-icon {
  color: var(--app-text-muted);
}

/* ===================== 管理工具 ===================== */
.management-section {
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  overflow: hidden;
}

.management-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 0 2px;
  color: var(--app-text-secondary);
  font-weight: 600;
  font-size: 14px;
}

.management-hint {
  color: var(--app-text-muted);
  font-weight: 400;
  font-size: 12px;
  margin-left: 8px;
}

.management-content {
  padding: 0 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-card {
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 8px;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
}

.filter-bar > * {
  width: 160px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 12px;
}

.drawer-descriptions,
.source-text {
  margin-bottom: 16px;
}

/* ===================== 响应式 ===================== */
@media (max-width: 1200px) {
  .chain-content {
    grid-template-columns: 1fr;
  }

  .overview-bar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .channel-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .selector-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .subject-search {
    width: 100%;
  }

  .overview-bar {
    grid-template-columns: 1fr;
  }

  .chain-path {
    flex-direction: column;
  }

  .chain-arrow {
    transform: rotate(90deg);
  }
}
</style>
