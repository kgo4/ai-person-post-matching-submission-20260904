<script setup lang="ts">
/**
 * AI反馈数据管理页面
 * 查看和管理AI匹配反馈数据
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, DataAnalysis, Download, Refresh, Search } from '@element-plus/icons-vue'
import {
  pageFeedback,
  exportFeedback,
  batchUpdateExportStatus,
  getFeedbackSummary,
  getFeedbackTrend,
  getDeviationDistribution
} from '@/api'
import type { MatchingFeedbackDataset, PageResultVO } from '@/api'

const router = useRouter()
const loading = ref(false)
const tableData = ref<MatchingFeedbackDataset[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 筛选
const filterExport = ref<number | undefined>(undefined)

// 多选
const selectedIds = ref<number[]>([])

// 统计数据
const summaryData = ref<any>({})
const trendData = ref<any>({})
const deviationData = ref<any>({})
const showStats = ref(false)

function getAdoptionStatusText(status: number) {
  const map: Record<number, string> = {
    1: '完全采纳',
    2: '部分采纳',
    3: '未采纳',
  }
  return map[status] || '未知'
}

function getAdoptionStatusType(status: number) {
  const map: Record<number, string> = {
    1: 'success',
    2: 'warning',
    3: 'info',
  }
  return map[status] || 'info'
}
function getExportStatusText(status: number) {
  const map: Record<number, string> = {
    0: '未允许导出',
    1: '允许导出',
  }
  return map[status] || '未知'
}


function getExportStatusType(status: number) {
  return status === 1 ? 'success' : 'warning'
}

async function loadData() {
  loading.value = true
  try {
    const params: any = {
      current: currentPage.value,
      size: pageSize.value,
    }
    if (filterExport.value !== undefined) {
      params.exportEnabled = filterExport.value
    }

    const res = await pageFeedback(params)
    const pageResult: PageResultVO<MatchingFeedbackDataset> = res.data as any
    tableData.value = pageResult.records || []
    total.value = pageResult.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '加载反馈数据失败')
  } finally {
    loading.value = false
  }
}

async function loadStatistics() {
  try {
    const [summaryRes, trendRes, deviationRes] = await Promise.all([
      getFeedbackSummary(100),
      getFeedbackTrend(30),
      getDeviationDistribution(100)
    ])
    summaryData.value = summaryRes.data || {}
    trendData.value = trendRes.data || {}
    deviationData.value = deviationRes.data || {}
  } catch (e: any) {
    console.error('加载统计数据失败', e)
  }
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadData()
}

function handleCurrentChange(page: number) {
  currentPage.value = page
  loadData()
}

function viewMatchingDetail(recordId: number) {
  router.push(`/matching/detail/${recordId}`)
}

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

// 多选处理
function handleSelectionChange(selection: MatchingFeedbackDataset[]) {
  selectedIds.value = selection.map(item => item.id!)
}

// 导出反馈数据
async function handleExport() {
  try {
    const res = await exportFeedback(filterExport.value)
    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `AI反馈数据_${new Date().toLocaleDateString('zh-CN')}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e: any) {
    ElMessage.error(e.message || '导出失败')
  }
}

// 批量标记为已使用
async function handleBatchMarkUsed() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先选择要操作的记录')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要将选中的 ${selectedIds.value.length} 条记录标记为已使用吗？`, '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await batchUpdateExportStatus(selectedIds.value, 1)
    ElMessage.success('批量标记成功')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '批量标记失败')
    }
  }
}

// 批量标记为未使用
async function handleBatchMarkUnused() {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先选择要操作的记录')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要将选中的 ${selectedIds.value.length} 条记录标记为未使用吗？`, '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await batchUpdateExportStatus(selectedIds.value, 0)
    ElMessage.success('批量标记成功')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '批量标记失败')
    }
  }
}

// 切换统计面板
function toggleStats() {
  showStats.value = !showStats.value
  if (showStats.value) {
    loadStatistics()
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="page-container">
    <!-- 统计面板 -->
    <el-card v-if="showStats" shadow="hover" style="margin-bottom: 16px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>反馈数据统计</span>
          <el-button type="primary" link @click="loadStatistics">
            <el-icon><Refresh /></el-icon> 刷新统计
          </el-button>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="6">
          <el-statistic title="总反馈数" :value="summaryData.totalFeedback || 0" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="完全采纳" :value="summaryData.fullAdoption || 0" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="部分采纳" :value="summaryData.partialAdoption || 0" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="平均偏差" :value="(summaryData.averageDeviation || 0).toFixed(1)" suffix="分" />
        </el-col>
      </el-row>

      <el-divider />

      <el-row :gutter="20">
        <el-col :span="12">
          <h4>偏差分布</h4>
          <div v-if="deviationData.labels" style="margin-top: 12px;">
            <div v-for="(label, index) in deviationData.labels" :key="label" style="margin-bottom: 8px;">
              <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
                <span>{{ label }}</span>
                <span>{{ deviationData.distribution?.[index] || 0 }} 条</span>
              </div>
              <el-progress
                :percentage="deviationData.totalSamples ? ((deviationData.distribution?.[index] || 0) / deviationData.totalSamples * 100) : 0"
                :stroke-width="10"
              />
            </div>
          </div>
        </el-col>
        <el-col :span="12">
          <h4>最近30天趋势</h4>
          <div v-if="trendData.dates" style="margin-top: 12px;">
            <div style="height: 200px; overflow: auto;">
              <div v-for="(date, index) in trendData.dates.slice(-7)" :key="date" style="margin-bottom: 8px;">
                <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
                  <span>{{ date }}</span>
                  <span>{{ trendData.counts?.[index] || 0 }} 条反馈</span>
                </div>
                <el-progress
                  :percentage="Math.min(100, (trendData.counts?.[index] || 0) * 10)"
                  :stroke-width="8"
                  :show-text="false"
                />
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="hover">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>AI反馈数据管理</span>
          <div style="display: flex; gap: 8px;">
            <el-button type="primary" @click="toggleStats">
              <el-icon><DataAnalysis /></el-icon> {{ showStats ? '隐藏统计' : '显示统计' }}
            </el-button>
            <el-button type="success" @click="handleExport">
              <el-icon><Download /></el-icon> 导出Excel
            </el-button>
            <el-button type="primary" @click="loadData">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        title="反馈数据说明"
        type="info"
        :closable="false"
        description="反馈数据记录人工对 AI 匹配结果的校准，可按授权导出为标准化的校准数据集（不含训练或模型效果指标）。"
        style="margin-bottom: 16px;"
      />

      <!-- 筛选和批量操作 -->
      <div class="search-bar">
        <el-select v-model="filterExport" placeholder="导出标记" clearable style="width: 140px;" @change="handleSearch">
          <el-option label="不允许导出" :value="0" />
          <el-option label="允许导出" :value="1" />
        </el-select>
        <el-button type="primary" @click="handleSearch">
          <el-icon><Search /></el-icon> 搜索
        </el-button>
        <el-button @click="filterExport = undefined; handleSearch()">重置</el-button>

        <div style="flex: 1;"></div>

        <el-button-group v-if="selectedIds.length > 0">
          <el-button type="warning" @click="handleBatchMarkUsed">
            <el-icon><Check /></el-icon> 批量允许导出 ({{ selectedIds.length }})
          </el-button>
          <el-button type="info" @click="handleBatchMarkUnused">
            <el-icon><Close /></el-icon> 批量禁止导出 ({{ selectedIds.length }})
          </el-button>
        </el-button-group>
      </div>

      <el-table
        :data="tableData"
        v-loading="loading"
        border
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="80px" />
        <el-table-column prop="matchingRecordId" label="匹配记录ID" width="120px">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewMatchingDetail(row.matchingRecordId)">
              #{{ row.matchingRecordId }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="empId" label="人员ID" width="100px" />
        <el-table-column prop="postId" label="岗位ID" width="100px" />
        <el-table-column label="AI匹配分" width="100px">
          <template #default="{ row }">
            <span :style="{ fontWeight: 'bold' }">{{ row.aiMatchScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最终分数" width="100px">
          <template #default="{ row }">
            <span :style="{ fontWeight: 'bold' }">{{ row.finalMatchScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="采纳状态" width="100px">
          <template #default="{ row }">
            <el-tag :type="getAdoptionStatusType(row.adoptionStatus)" size="small">
              {{ getAdoptionStatusText(row.adoptionStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="导出授权" width="100px">
          <template #default="{ row }">
            <el-tag :type="getExportStatusType(row.exportEnabled)" size="small">
              {{ getExportStatusText(row.exportEnabled) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="feedbackTime" label="反馈时间" width="180px">
          <template #default="{ row }">
            {{ formatTime(row.feedbackTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120px" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="viewMatchingDetail(row.matchingRecordId)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :current-page="currentPage"
          :page-size="pageSize"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  padding: 20px;
}
.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}
.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
