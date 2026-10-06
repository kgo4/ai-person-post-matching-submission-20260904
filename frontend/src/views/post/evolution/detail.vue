<template>
  <div class="evolution-detail-page">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px">
      <template #content>演化任务详情</template>
      <template #extra>
        <el-button v-if="task && task.taskStatus !== 'RUNNING'" type="danger" plain @click="handleDeleteTask">删除记录</el-button>
      </template>
    </el-page-header>

    <!-- 任务信息 -->
    <el-card shadow="never" v-if="task">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="任务编码">{{ task.taskCode }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ postName || task.taskName }}</el-descriptions-item>
        <el-descriptions-item label="岗位ID">{{ task.postId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(task.taskStatus)">{{ statusLabel(task.taskStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ task.createdTime }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ task.updatedTime }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="task.errorMessage" style="margin-top: 12px">
        <el-alert :title="task.errorMessage" type="error" show-icon />
      </div>
      <div v-if="taskSummary" class="task-summary-note">
        本次分析：{{ taskSummary.signalCount || 0 }} 条信号，{{ taskSummary.evidenceCount || 0 }} 条证据，
        {{ taskSummary.savedChangeItems || 0 }} 条可审核变更；Harness 阻断 {{ taskSummary.harnessBlock || 0 }} 条。
      </div>
    </el-card>

    <el-card v-if="evidences.length" shadow="never" style="margin-top:12px">
      <template #header><span>证据摘要（{{ evidences.length }} 条）</span></template>
      <div class="evidence-list">
        <div v-for="evidence in evidences" :key="evidence.id" class="evidence-list__item">
          <div class="evidence-list__title">{{ evidence.sourceTitle || evidence.sourceType || '外部资料' }}</div>
          <div class="evidence-list__text">{{ evidence.evidenceText }}</div>
          <a v-if="evidence.sourceUrl" :href="evidence.sourceUrl" target="_blank" rel="noreferrer">查看来源</a>
        </div>
      </div>
    </el-card>

    <!-- 变更统计摘要 -->
    <el-card v-if="changeSummary" shadow="never" style="margin-top:12px">
      <template #header>
        <span>变更统计摘要</span>
      </template>
      <div class="summary-grid">
        <div class="summary-item">
          <span class="summary-item__label">新增能力</span>
          <span class="summary-item__value is-add">{{ changeSummary.added }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-item__label">删除能力</span>
          <span class="summary-item__value is-remove">{{ changeSummary.removed }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-item__label">修改能力</span>
          <span class="summary-item__value is-update">{{ changeSummary.updated }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-item__label">高置信度</span>
          <span class="summary-item__value is-high">{{ changeSummary.highConfidence }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-item__label">低置信度</span>
          <span class="summary-item__value is-low">{{ changeSummary.lowConfidence }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-item__label">幻觉风险</span>
          <span class="summary-item__value is-risk">{{ changeSummary.hallucinationRisk }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-item__label">待审核</span>
          <span class="summary-item__value is-pending">{{ changeSummary.pending }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-item__label">已通过</span>
          <span class="summary-item__value is-approved">{{ changeSummary.approved }}</span>
        </div>
      </div>
    </el-card>

    <!-- JD 对比视图 -->
    <el-card v-if="task?.newJdText" shadow="never" style="margin-top:12px">
      <template #header>
        <div class="card-header">
          <span>JD 能力对比</span>
          <el-button text size="small" @click="showJdCompare = !showJdCompare">
            {{ showJdCompare ? '收起' : '展开' }}
          </el-button>
        </div>
      </template>
      <div v-if="showJdCompare" class="jd-compare">
        <div class="jd-compare__col">
          <div class="jd-compare__title">原 JD 能力基线</div>
          <pre class="jd-compare__text">{{ task.summaryJson || '(无基线数据)' }}</pre>
        </div>
        <div class="jd-compare__arrow">→</div>
        <div class="jd-compare__col">
          <div class="jd-compare__title">新 JD 能力要求</div>
          <pre class="jd-compare__text">{{ task.newJdText }}</pre>
        </div>
      </div>
    </el-card>

    <!-- 变更项列表 -->
    <el-card shadow="never" style="margin-top: 12px">
      <template #header>
        <div class="card-header">
          <span>变更项列表（{{ changeItems.length }} 项）</span>
          <div v-if="task?.taskStatus === 'WAIT_CONFIRM'">
            <el-button
              v-if="selectedIds.size > 0"
              type="success"
              size="small"
              @click="handleBatchReview('APPROVED')"
              style="margin-right:8px"
            >
              批量通过 ({{ selectedIds.size }})
            </el-button>
            <el-button
              v-if="selectedIds.size > 0"
              type="danger"
              size="small"
              @click="handleBatchReview('REJECTED')"
            >
              批量拒绝 ({{ selectedIds.size }})
            </el-button>
          </div>
        </div>
      </template>
      <el-alert v-if="taskSummary?.harnessBlock && !changeItems.length" type="warning" :closable="false" show-icon title="本次提议均未进入审核列表" description="请根据任务摘要和证据检查阻断原因后重新运行或调整资料来源。" style="margin-bottom: 12px" />
      <el-table :data="changeItems" v-loading="loading" stripe @selection-change="onSelectionChange" ref="tableRef">
        <el-table-column
          v-if="task?.taskStatus === 'WAIT_CONFIRM'"
          type="selection"
          width="45"
          :selectable="(row: PostEvolutionChangeItem) => row.confirmStatus === 'PENDING'"
        />
        <el-table-column prop="abilityName" label="能力名称" min-width="130">
          <template #default="{ row }">
            <div class="ability-name-cell">
              <span>{{ row.abilityName }}</span>
              <el-tag v-if="row.hallucinationRisk" type="danger" size="small" effect="dark">幻觉风险</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="160">
          <template #default="{ row }">
            <ConfidenceGauge
              :score="row.confidenceScore ?? row.supportScore ?? 50"
              :evidence-count="row.evidenceSources?.length ?? 0"
              :show-evidence-count="true"
              :show-hallucination-risk="true"
              size="small"
            />
          </template>
        </el-table-column>
        <el-table-column prop="changeType" label="变更类型" width="100">
          <template #default="{ row }">
            <el-tag :type="changeTypeColor(row.changeType)" size="small">{{ changeTypeLabel(row.changeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Harness建议" width="110">
          <template #default="{ row }">
            <el-tag :type="row.harnessDecision === 'PASS' ? 'success' : row.harnessDecision === 'BLOCK' ? 'danger' : 'warning'" size="small">
              {{ row.harnessDecision === 'PASS' ? '通过' : row.harnessDecision === 'BLOCK' ? '阻断' : row.harnessDecision || '待校验' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="旧值" min-width="130">
          <template #default="{ row }">
            <span v-if="row.oldLevel">L{{ row.oldLevel }}</span>
            <span v-if="row.oldWeight"> W{{ row.oldWeight }}</span>
            <span v-if="row.oldIsCore !== null && row.oldIsCore !== undefined">
              {{ row.oldIsCore ? ' 核心' : ' 非核心' }}
            </span>
            <span v-if="!row.oldLevel && !row.oldWeight && (row.oldIsCore === null || row.oldIsCore === undefined)" class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="新值" min-width="130">
          <template #default="{ row }">
            <span v-if="row.newLevel">L{{ row.newLevel }}</span>
            <span v-if="row.newWeight"> W{{ row.newWeight }}</span>
            <span v-if="row.newIsCore !== null && row.newIsCore !== undefined">
              {{ row.newIsCore ? ' 核心' : ' 非核心' }}
            </span>
            <span v-if="!row.newLevel && !row.newWeight && (row.newIsCore === null || row.newIsCore === undefined)" class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="证据片段" min-width="240">
          <template #default="{ row }">
            <div class="evidence-stack">
              <el-button
                v-if="row.evidenceItems?.length"
                type="primary"
                link
                size="small"
                @click="openEvidenceDialog(row)"
              >
                查看 {{ row.evidenceItems.length }} 条关联证据
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="证据可信度" min-width="180">
          <template #default="{ row }">
            <template v-if="row.evidenceSummary">
              <div class="evidence-metrics">
                <span>{{ row.evidenceSummary.sourceCount }} 个来源</span>
                <span>最高 {{ formatScore(row.evidenceSummary.maxTrustScore) }}</span>
                <span>平均 {{ formatScore(row.evidenceSummary.averageTrustScore) }}</span>
                <el-tag size="small" :type="row.evidenceSummary.crossSourceVerified ? 'success' : 'info'">
                  {{ row.evidenceSummary.crossSourceVerified ? '跨源佐证' : '单源佐证' }}
                </el-tag>
              </div>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="数据来源" width="120">
          <template #default="{ row }">
            <el-tooltip v-if="row.sourceDetail" :content="row.sourceDetail" placement="top">
              <el-tag size="small" type="info">{{ sourceTypeLabel(row.sourceType) }}</el-tag>
            </el-tooltip>
            <el-tag v-else-if="row.sourceType" size="small" type="info">{{ sourceTypeLabel(row.sourceType) }}</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="confirmStatus" label="人工审核" width="100">
          <template #default="{ row }">
            <el-tag :type="row.confirmStatus === 'APPROVED' ? 'success' : row.confirmStatus === 'REJECTED' ? 'danger' : 'warning'" size="small">
              {{ row.confirmStatus === 'APPROVED' ? '通过' : row.confirmStatus === 'REJECTED' ? '拒绝' : '待审核' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <template v-if="row.confirmStatus === 'PENDING' && task?.taskStatus === 'WAIT_CONFIRM'">
              <el-button type="success" link size="small" @click="handleReview(row, 'APPROVED')">通过</el-button>
              <el-button type="danger" link size="small" @click="handleReview(row, 'REJECTED')">拒绝</el-button>
            </template>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="evidenceDialogVisible"
      :title="`${selectedEvidenceChange?.abilityName || ''}：关联证据`"
      width="880px"
      destroy-on-close
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="以下证据共同支撑当前能力变更；通过或拒绝操作只审核该能力变更一次。"
        style="margin-bottom: 12px"
      />
      <el-table :data="selectedEvidenceChange?.evidenceItems || []" border max-height="460">
        <el-table-column label="来源" min-width="150">
          <template #default="{ row }">
            <div>{{ row.sourceTitle || sourceTypeLabel(row.sourceType) }}</div>
            <div class="text-muted">{{ row.sourceRef }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="evidenceText" label="原文片段" min-width="250" show-overflow-tooltip />
        <el-table-column prop="collectedTime" label="采集时间" width="165" />
        <el-table-column label="相关度" width="84">
          <template #default="{ row }">{{ formatScore(row.similarityScore) }}</template>
        </el-table-column>
        <el-table-column label="可信度" width="84">
          <template #default="{ row }">{{ formatScore(row.trustScore) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getEvolutionTask, pageEvolutionChangeItems, reviewEvolutionChangeItem, batchReviewEvolutionChangeItems, deleteEvolutionTask, getTaskEvidence } from '@/api/evolution'
import { getPost } from '@/api/post'
import type { PostEvolutionTask, PostEvolutionChangeItem } from '@/api/evolution'
import ConfidenceGauge from '@/components/common/ConfidenceGauge.vue'

const route = useRoute()
const taskId = Number(route.params.id)

const loading = ref(false)
const task = ref<PostEvolutionTask | null>(null)
const postName = ref('')
const changeItems = ref<PostEvolutionChangeItem[]>([])
const evidences = ref<import('@/api/evolution').PostEvolutionEvidence[]>([])
const selectedEvidenceChange = ref<PostEvolutionChangeItem | null>(null)
const evidenceDialogVisible = ref(false)
const selectedIds = ref<Set<number>>(new Set())
const tableRef = ref()
const showJdCompare = ref(false)
const reviewComment = ref('')
const taskSummary = computed(() => {
  if (!task.value?.summaryJson) return null
  try { return JSON.parse(task.value.summaryJson) } catch { return null }
})

const changeSummary = computed(() => {
  const items = changeItems.value
  if (!items.length) return null
  return {
    added: items.filter(i => i.changeType === 'ADDED').length,
    removed: items.filter(i => i.changeType === 'REMOVED').length,
    updated: items.filter(i => i.changeType.startsWith('UPDATED_')).length,
    highConfidence: items.filter(i => (i.confidenceScore ?? i.supportScore ?? 0) >= 80).length,
    lowConfidence: items.filter(i => (i.confidenceScore ?? i.supportScore ?? 0) < 50).length,
    hallucinationRisk: items.filter(i => i.hallucinationRisk).length,
    pending: items.filter(i => i.confirmStatus === 'PENDING').length,
    approved: items.filter(i => i.confirmStatus === 'APPROVED').length,
  }
})

const statusType = (status: string) => {
  const map: Record<string, string> = { PENDING: 'info', RUNNING: 'warning', WAIT_CONFIRM: 'primary', APPLIED: 'success', FAILED: 'danger' }
  return map[status] || 'info'
}

const statusLabel = (status: string) => {
  const map: Record<string, string> = { PENDING: '待处理', RUNNING: '运行中', WAIT_CONFIRM: '待确认', APPLIED: '已应用', FAILED: '失败' }
  return map[status] || status
}

const changeTypeColor = (type: string) => {
  if (type === 'ADDED') return 'success'
  if (type === 'REMOVED') return 'danger'
  if (type.startsWith('UPDATED_')) return 'warning'
  return 'info'
}

const changeTypeLabel = (type: string) => {
  const map: Record<string, string> = { ADDED: '新增', REMOVED: '移除', UPDATED_LEVEL: '更新等级', UPDATED_WEIGHT: '更新权重', UPDATED_CORE: '更新核心', NO_CHANGE: '无变化' }
  return map[type] || type
}

const sourceTypeLabel = (type?: string) => {
  if (!type) return '-'
  const map: Record<string, string> = {
    RAG_CHUNK: '知识检索', JD_TEXT: 'JD分析', PROTOTYPE: '岗位原型', MANUAL: '人工录入',
    CLOUD_KNOWLEDGE_INTERNAL: '企业云知识库', INDUSTRY_WHITEPAPER: '行业白皮书',
    INTERNAL_POST_INFO: '内部岗位资料', INTERNAL_BUSINESS_UPDATE: '内部业务资料', INTERNAL_POLICY: '内部制度资料'
  }
  return map[type] || type
}

function onSelectionChange(rows: PostEvolutionChangeItem[]) {
  selectedIds.value = new Set(rows.map(r => r.id))
}

const formatScore = (score: number) => {
  const percent = score <= 1 ? score * 100 : score
  return `${percent.toFixed(0)}%`
}

const openEvidenceDialog = (item: PostEvolutionChangeItem) => {
  selectedEvidenceChange.value = item
  evidenceDialogVisible.value = true
}

/** 截断证据片段用于列表展示 */
function truncateEvidence(text: string, maxLen: number) {
  if (!text) return ''
  const clean = text.replace(/\s+/g, ' ').trim()
  return clean.length > maxLen ? clean.substring(0, maxLen) + '…' : clean
}

const loadData = async () => {
  loading.value = true
  try {
    const taskRes = await getEvolutionTask(taskId)
    task.value = taskRes.data
    try { const postRes = await getPost(task.value.postId); postName.value = postRes.data?.postName || '' } catch { postName.value = '' }
    const itemsRes = await pageEvolutionChangeItems(taskId, { current: 1, size: 100 })
    changeItems.value = itemsRes.data.records || []
    try {
      const evidenceRes = await getTaskEvidence(taskId)
      evidences.value = evidenceRes.data || []
    } catch {
      evidences.value = []
    }
  } finally {
    loading.value = false
  }
}

const handleDeleteTask = async () => {
  try {
    await ElMessageBox.confirm('确认删除这条演化记录？关联证据和变更项也会删除，但不会影响岗位能力模型。', '删除演化记录', { type: 'warning' })
    await deleteEvolutionTask(taskId)
    ElMessage.success('演化记录已删除')
    history.back()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('删除演化记录失败')
  }
}

const handleReview = async (item: PostEvolutionChangeItem, status: string) => {
  await reviewEvolutionChangeItem(taskId, item.id, { confirmStatus: status })
  ElMessage.success('审核成功')
  loadData()
}

const handleBatchReview = async (status: string) => {
  if (selectedIds.value.size === 0) {
    ElMessage.warning('请先勾选待审核的变更项')
    return
  }
  try {
    await ElMessageBox.confirm(`确认批量${status === 'APPROVED' ? '通过' : '拒绝'} ${selectedIds.value.size} 项变更？`, '批量审核')
    await batchReviewEvolutionChangeItems(taskId, {
      itemIds: [...selectedIds.value],
      confirmStatus: status,
    })
    ElMessage.success(`已批量${status === 'APPROVED' ? '通过' : '拒绝'} ${selectedIds.value.size} 项`)
    selectedIds.value = new Set()
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('批量审核失败: ' + (e.message || '未知错误'))
    }
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.evolution-detail-page {
  padding: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.task-summary-note {
  margin-top: 12px;
  padding: 9px 12px;
  border-left: 3px solid var(--app-primary);
  color: var(--app-text-secondary);
  background: rgba(59, 130, 246, 0.05);
  font-size: 13px;
}
.evidence-list { display: grid; gap: 10px; }
.evidence-list__item { padding: 10px 12px; border: 1px solid rgba(148, 163, 184, 0.16); border-radius: 8px; }
.evidence-list__title { color: var(--app-text-strong); font-weight: 600; }
.evidence-list__text { margin: 5px 0; color: var(--app-text-secondary); font-size: 13px; line-height: 1.6; }
.evidence-list__item a { color: var(--app-primary); font-size: 12px; }
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.summary-item {
  display: flex;
  flex-direction: column;
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(148, 163, 184, 0.06);
}
.summary-item__label {
  font-size: 12px;
  color: var(--app-text-secondary);
  margin-bottom: 4px;
}
.summary-item__value {
  font-size: 22px;
  font-weight: 800;
}
.summary-item__value.is-add { color: var(--app-success); }
.summary-item__value.is-remove { color: var(--app-danger); }
.summary-item__value.is-update { color: var(--app-warning); }
.summary-item__value.is-high { color: var(--app-success); }
.summary-item__value.is-low { color: var(--app-danger); }
.summary-item__value.is-risk { color: #dc2626; }
.summary-item__value.is-pending { color: var(--app-warning); }
.summary-item__value.is-approved { color: var(--app-success); }
.evidence-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.evidence-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  color: var(--app-text-secondary);
  font-size: 12px;
}
.ability-name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.text-muted {
  color: var(--app-text-muted);
  font-size: 12px;
}
.reason-text {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.jd-compare {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 16px;
  align-items: start;
}
.jd-compare__col {
  min-width: 0;
}
.jd-compare__title {
  font-size: 13px;
  font-weight: 700;
  color: var(--app-text-secondary);
  margin-bottom: 8px;
}
.jd-compare__text {
  font-size: 12px;
  color: var(--app-text);
  background: rgba(148, 163, 184, 0.06);
  border-radius: 10px;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow: auto;
  margin: 0;
}
.jd-compare__arrow {
  display: flex;
  align-items: center;
  font-size: 22px;
  color: var(--app-primary);
  font-weight: 800;
  padding-top: 28px;
}
</style>
