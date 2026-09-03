<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Download, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MatchingResultMetrics from './components/MatchingResultMetrics.vue'
import MatchingResultTable from './components/MatchingResultTable.vue'
import ApprovalDialog from './components/ApprovalDialog.vue'
import AbilityGapDrawer from './components/AbilityGapDrawer.vue'
import PostMatchCompareGraph from '@/components/graph/PostMatchCompareGraph.vue'
import { useMatchingResult } from './composables/useMatchingResult'
import { useResultLock } from './composables/useResultLock'
import { useResultModify } from './composables/useResultModify'
import { useResultApproval } from './composables/useResultApproval'
import { useAbilityGap } from './composables/useAbilityGap'
import { useCompareGraph } from './composables/useCompareGraph'
import { exportMatchResults } from '@/api/matching'

const router = useRouter()
const route = useRoute()

const {
  loading,
  tableData,
  total,
  currentPage,
  pageSize,
  filters,
  approvedCount,
  pendingCount,
  strongMatchCount,
  loadData,
  handleSearch,
  handleSizeChange,
  handleCurrentChange,
  resetFilters,
} = useMatchingResult()

const { handleLock, handleUnlock, handleDelete } = useResultLock(loadData)
const { modifyDialogVisible, modifyLoading, currentModifyRecord, modifyForm, openModifyDialog, handleModify } = useResultModify(loadData)
const {
  approvalDialogVisible,
  approvalLoading,
  currentApprovalRecord,
  approvalForm,
  userList,
  userLoading,
  openApprovalDialog,
  handleInitiateApproval,
} = useResultApproval(loadData)
const {
  gapDrawerVisible,
  gapLoading,
  currentGapRecord,
  gapAbilities,
  gapLearningPath,
  gapGraphData,
  gapEvidenceResults,
  gapWarnings,
  gapDimensionScores,
  gapImprovementPlan,
  gapExportLoading,
  gapGraphSummary,
  openGapWorkbench,
  handlePrintGapReport,
  handleExportGapReport,
  goToResumeParse,
} = useAbilityGap()
const { graphDialogVisible, graphData, graphLoading, openCompareGraph } = useCompareGraph()

function viewDetail(id: number) {
  router.push(`/matching/detail/${id}`)
}

async function handleExport() {
  try {
    const response = await exportMatchResults(filters.postId ? Number(filters.postId) : undefined)
    const url = URL.createObjectURL(response.data as Blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'matching-results.xlsx'
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(() => {
  // 支持从任务页/URL 深链带入岗位过滤（task.postId query）
  const qPostId = route.query.postId
  if (qPostId) {
    filters.postId = String(qPostId)
  }
  loadData()
})
</script>

<template>
  <div class="page-shell motion-page">
    <section class="page-hero motion-scan">
      <div>
        <div class="page-hero__eyebrow">Matching Ledger</div>
        <h1 class="page-hero__title">匹配结果总览</h1>
        <p class="page-hero__desc">集中查看图谱匹配结果、审批状态和硬性条件淘汰原因，并在同一工作台里进行复核与修正。</p>
        <div class="page-hero__meta">
          <span class="hero-chip">当前页 {{ total }} 条</span>
          <span class="hero-chip">强匹配 {{ strongMatchCount }}</span>
          <span class="hero-chip">待处理 {{ pendingCount }}</span>
          <span class="hero-chip">已通过 {{ approvedCount }}</span>
        </div>
      </div>

      <div class="toolbar-group">
        <button class="glass-btn" @click="handleExport">
          <el-icon><Download /></el-icon>
          导出 Excel
        </button>
        <el-button type="primary" @click="router.push('/matching/execute')">
          <el-icon><VideoPlay /></el-icon>
          发起匹配
        </el-button>
      </div>
    </section>

    <MatchingResultMetrics
      :total="total"
      :strong-match-count="strongMatchCount"
      :pending-count="pendingCount"
      :approved-count="approvedCount"
    />

    <MatchingResultTable
      :data="tableData"
      :loading="loading"
      :total="total"
      :current-page="currentPage"
      :page-size="pageSize"
      :filters="filters"
      :approved-count="approvedCount"
      :pending-count="pendingCount"
      :strong-match-count="strongMatchCount"
      @search="handleSearch"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
      @reset="resetFilters"
      @lock="handleLock"
      @unlock="handleUnlock"
      @delete="handleDelete"
      @modify="openModifyDialog"
      @detail="viewDetail"
      @approval="openApprovalDialog"
      @gap="openGapWorkbench"
      @compare="openCompareGraph"
    />

    <el-dialog v-model="modifyDialogVisible" title="修改匹配结果" width="520px" :close-on-click-modal="false">
      <el-form :model="modifyForm" label-width="110px">
        <el-form-item label="最终匹配分">
          <el-input-number v-model="modifyForm.finalMatchScore" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="匹配状态">
          <el-select v-model="modifyForm.matchStatus" style="width: 100%;">
            <el-option label="待审核" :value="0" />
            <el-option label="强匹配" :value="1" />
            <el-option label="匹配" :value="2" />
            <el-option label="待观察" :value="3" />
            <el-option label="不匹配" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="modifyForm.manualRemark" type="textarea" :rows="3" placeholder="输入备注信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modifyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="modifyLoading" @click="handleModify">确认修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="graphDialogVisible" title="能力匹配对比图谱" width="820px" :close-on-click-modal="false">
      <div v-loading="graphLoading" class="compare-dialog">
        <PostMatchCompareGraph v-if="graphData?.length" :data="graphData" :width="750" :height="400" />
        <div v-else-if="!graphLoading" class="compare-empty">暂无对比数据</div>
      </div>
    </el-dialog>

    <AbilityGapDrawer
      :visible="gapDrawerVisible"
      :loading="gapLoading"
      :record="currentGapRecord"
      :gaps="gapAbilities"
      :learning-path="gapLearningPath"
      :graph-data="gapGraphData"
      :evidence-results="gapEvidenceResults"
      :warnings="gapWarnings"
      :dimension-scores="gapDimensionScores"
      :improvement-plan="gapImprovementPlan"
      :export-loading="gapExportLoading"
      :graph-summary="gapGraphSummary"
      @update:visible="gapDrawerVisible = $event"
      @print-report="handlePrintGapReport"
      @export-gap="handleExportGapReport"
      @go-resume="goToResumeParse"
    />

    <ApprovalDialog
      :visible="approvalDialogVisible"
      :loading="approvalLoading"
      :current-record="currentApprovalRecord"
      :form="approvalForm"
      :user-list="userList"
      :user-loading="userLoading"
      @update:visible="approvalDialogVisible = $event"
      @submit="handleInitiateApproval"
    />
  </div>
</template>

<style scoped>
.compare-dialog {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px;
}

.compare-empty {
  color: var(--app-text-muted);
}
</style>
