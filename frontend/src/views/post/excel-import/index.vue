<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CircleCheckFilled,
  Download,
  Document,
  Loading,
  Plus,
  Refresh,
  UploadFilled,
  View,
} from '@element-plus/icons-vue'
import {
  uploadAndAnalyze,
  analyzeBatch,
  getImportPreview,
  confirmImport,
  includeBatchInMarketDiscovery,
  cancelBatch,
  pageImportBatches,
  retryBatch,
  deleteImportBatch,
  downloadPostImportTemplate,
} from '@/api/post-import'
import type {
  PostImportPreview,
  PostImportItemPreview,
  PostImportBatchVO,
} from '@/api/post-import'

// ==================== 视图模式 ====================
type ViewMode = 'list' | 'detail'
const viewMode = ref<ViewMode>('list')

// ==================== 列表相关 ====================
const listLoading = ref(false)
const batchList = ref<PostImportBatchVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const statusFilter = ref<number | undefined>(undefined)

const completedCount = computed(() => batchList.value.filter((b) => b.importStatus === 4).length)
const totalImported = computed(() =>
  batchList.value.reduce((sum, b) => sum + (b.successCount || 0), 0),
)

async function loadBatchList() {
  listLoading.value = true
  try {
    const params: any = { current: currentPage.value, size: pageSize.value }
    if (statusFilter.value !== undefined) params.importStatus = statusFilter.value
    const res = await pageImportBatches(params)
    batchList.value = res.data.records
    total.value = res.data.total
  } finally {
    listLoading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadBatchList()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadBatchList()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadBatchList()
}

// ==================== 上传对话框 ====================
const uploadDialogVisible = ref(false)
const selectedFile = ref<File | null>(null)

function openUploadDialog() {
  selectedFile.value = null
  uploadDialogVisible.value = true
}

function handleFileChange(file: any) {
  selectedFile.value = file.raw
}

async function handleDownloadTemplate() {
  try {
    const blob = await downloadPostImportTemplate()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '岗位JD批量导入模板.xlsx'
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('模板下载失败，请稍后重试')
  }
}

async function handleUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择Excel文件')
    return
  }
  uploading.value = true
  try {
    const res = await uploadAndAnalyze(selectedFile.value)
    preview.value = res.data
    currentBatchId = preview.value.batchId
    includeMarketJd.value = false
    uploadDialogVisible.value = false
    viewMode.value = 'detail'
    await analyzeBatch(preview.value.batchId)
    analyzing.value = true
    startPolling(preview.value.batchId)
  } catch (e: any) {
    ElMessage.error('上传解析失败: ' + (e.message || '未知错误'))
  } finally {
    uploading.value = false
  }
}

// ==================== 详情相关 ====================
const uploading = ref(false)
const confirming = ref(false)
const analyzing = ref(false)
const preview = ref<PostImportPreview | null>(null)
const detailVisible = ref(false)
const selectedItem = ref<PostImportItemPreview | null>(null)
const importResult = ref({ success: 0, fail: 0 })
const importDone = ref(false)
const importing = ref(false)
const importFailed = ref(false)
const includeMarketJd = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null
let currentBatchId: number | null = null

const allAnalyzed = () => {
  if (!preview.value) return false
  return preview.value.items.every(
    (i) => i.analysisStatus === 2 || i.analysisStatus === 3,
  )
}

const hasAnySuccess = () => {
  if (!preview.value) return false
  return preview.value.items.some((i) => i.analysisStatus === 2)
}

// 进入详情
async function enterDetail(batch: PostImportBatchVO) {
  viewMode.value = 'detail'
  importDone.value = false
  importFailed.value = false
  importing.value = false
  currentBatchId = batch.id
  try {
    const res = await getImportPreview(batch.id)
    preview.value = res.data
    const serverStatus = res.data.importStatus
    if ((serverStatus === 1 || serverStatus === 3) && batch.cancelFlag !== 1) {
      analyzing.value = serverStatus === 1
      importing.value = serverStatus === 3
      startPolling(batch.id)
    } else if (serverStatus === 4) {
      importDone.value = true
      importResult.value = { success: res.data.successCount || 0, fail: res.data.failCount || 0 }
    } else if (serverStatus === 5) {
      importFailed.value = true
    } else {
      analyzing.value = false
    }
  } catch (e: any) {
    ElMessage.error('获取详情失败: ' + (e.message || '未知错误'))
    viewMode.value = 'list'
  }
}

// 返回列表
function backToList() {
  stopPolling()
  viewMode.value = 'list'
  preview.value = null
  importDone.value = false
  importing.value = false
  importFailed.value = false
  loadBatchList()
}

// 轮询
function startPolling(batchId: number) {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const res = await getImportPreview(batchId)
      preview.value = res.data
      const status = res.data.importStatus
      if (status === 1 && allAnalyzed()) {
        stopPolling()
        analyzing.value = false
      } else if (status === 3) {
        analyzing.value = false
        importing.value = true
      } else if (status === 4) {
        stopPolling()
        analyzing.value = false
        importing.value = false
        importFailed.value = false
        importDone.value = true
        importResult.value = {
          success: res.data.successCount || 0,
          fail: res.data.failCount || 0,
        }
        ElMessage.success(
          res.data.failCount
            ? `批量导入完成，成功 ${res.data.successCount || 0} 个，失败 ${res.data.failCount} 个`
            : `批量导入完成，共导入 ${res.data.successCount || 0} 个岗位`,
        )
      } else if (status === 5) {
        stopPolling()
        analyzing.value = false
        importing.value = false
        importFailed.value = true
        ElMessage.error(`批量导入失败：${res.data.errorMessage || '请查看批次详情后重试'}`)
      }
    } catch {
      // ignore
    }
  }, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onBeforeUnmount(() => {
  stopPolling()
})

async function handleCancel() {
  if (!currentBatchId) return
  try {
    await cancelBatch(currentBatchId)
    stopPolling()
    analyzing.value = false
    ElMessage.success('已取消分析')
    if (currentBatchId) {
      const res = await getImportPreview(currentBatchId)
      preview.value = res.data
    }
  } catch (e: any) {
    ElMessage.error('取消失败: ' + (e.message || '未知错误'))
  }
}

async function handleRetry() {
  if (!currentBatchId) return
  try {
    await retryBatch(currentBatchId)
    analyzing.value = true
    ElMessage.success('已重新触发分析')
    startPolling(currentBatchId)
    const res = await getImportPreview(currentBatchId)
    preview.value = res.data
  } catch (e: any) {
    ElMessage.error('重试失败: ' + (e.message || '未知错误'))
  }
}

async function handleRetryFromList(batch: PostImportBatchVO) {
  try {
    await ElMessageBox.confirm('确定要重新触发该批次的AI分析吗？', '重试确认', { type: 'warning' })
    await retryBatch(batch.id)
    ElMessage.success('已重新触发分析')
    loadBatchList()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('重试失败: ' + (e.message || '未知错误'))
    }
  }
}

async function handleDeleteFromList(batch: PostImportBatchVO) {
  try {
    await ElMessageBox.confirm(
      '删除后仅移除该导入批次及临时明细，已经导入的岗位不会被删除。',
      '删除导入记录',
      { type: 'warning' },
    )
    await deleteImportBatch(batch.id)
    ElMessage.success('导入记录已删除')
    await loadBatchList()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败: ' + (e.message || '未知错误'))
  }
}

async function handleIncludeBatchInMarketDiscovery(batch: PostImportBatchVO) {
  try {
    await ElMessageBox.confirm(
      '将复用该批次已保存的岗位能力模型作为市场样本，不会重新调用 AI 分析。',
      '纳入市场 JD',
      { type: 'warning' },
    )
    const result = await includeBatchInMarketDiscovery(batch.id)
    ElMessage.success(result.data > 0 ? `已纳入 ${result.data} 条市场 JD` : '该批次没有满足条件的市场 JD 样本')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '纳入市场 JD 失败')
  }
}

async function handleConfirm() {
  if (!preview.value) return
  confirming.value = true
  try {
    await confirmImport({
      batchId: preview.value.batchId,
      includeMarketJd: includeMarketJd.value,
      items: preview.value.items.map((item) => ({
        itemId: item.itemId,
        postName: item.postName,
        postDescription: item.postDescription,
        confirmed: item.analysisStatus === 2,
        abilities: item.abilities,
      })),
    })
    importDone.value = false
    importFailed.value = false
    importing.value = true
    preview.value.importStatus = 3
    ElMessage.info('已提交后台导入，完成后会通知你')
    startPolling(preview.value.batchId)
  } catch (e: any) {
    ElMessage.error('导入失败: ' + (e.message || '未知错误'))
  } finally {
    confirming.value = false
  }
}

function showDetail(item: PostImportItemPreview) {
  selectedItem.value = item
  detailVisible.value = true
}

// ==================== 工具函数 ====================
const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '待解析', type: 'info' },
  1: { label: 'AI解析中', type: 'warning' },
  2: { label: '待确认', type: 'primary' },
  3: { label: '导入中', type: 'warning' },
  4: { label: '导入完成', type: 'success' },
  5: { label: '导入失败', type: 'danger' },
}

function getStatusInfo(status: number, cancelFlag?: number) {
  if (cancelFlag === 1 && status === 1) return { label: '已取消', type: 'info' }
  return statusMap[status] || { label: '未知', type: 'info' }
}

const analysisStatusMap: Record<number, { label: string; type: string }> = {
  0: { label: '待分析', type: 'info' },
  1: { label: '分析中', type: 'warning' },
  2: { label: '成功', type: 'success' },
  3: { label: '失败', type: 'danger' },
}

function formatTime(time: string | null | undefined) {
  if (!time) return '--'
  return time.replace('T', ' ').substring(0, 19)
}

// ==================== 初始化 ====================
onMounted(() => {
  loadBatchList()
})
</script>

<template>
  <div class="page-shell">
    <!-- ========== 列表视图 ========== -->
    <template v-if="viewMode === 'list'">
      <section class="page-hero">
        <div>
          <div class="page-hero__eyebrow">AI Import Center</div>
          <h1 class="page-hero__title">Excel 批量导入</h1>
          <p class="page-hero__desc">
            通过AI智能解析Excel文件，批量导入岗位并自动生成能力模型。支持查看历史导入记录、恢复中断任务。
          </p>
          <div class="page-hero__meta">
            <span class="hero-chip">记录总数 {{ total }}</span>
            <span class="hero-chip">已完成 {{ completedCount }}</span>
            <span class="hero-chip">累计导入 {{ totalImported }} 个岗位</span>
          </div>
        </div>
        <div class="toolbar-group">
          <el-button @click="handleDownloadTemplate">
            <el-icon><Download /></el-icon>
            下载模板
          </el-button>
          <el-button type="primary" @click="openUploadDialog">
            <el-icon><Plus /></el-icon>
            新建导入
          </el-button>
        </div>
      </section>

      <section class="metric-grid">
        <article class="metric-card" style="grid-column: span 4;">
          <div class="metric-card__icon" style="background: rgba(37,99,235,0.12); color: #2563eb;">
            <el-icon :size="22"><Document /></el-icon>
          </div>
          <div>
            <div class="metric-card__label">导入记录</div>
            <div class="metric-card__value">{{ total }}</div>
            <div class="metric-card__hint">所有导入批次</div>
          </div>
        </article>
        <article class="metric-card" style="grid-column: span 4;">
          <div class="metric-card__icon" style="background: rgba(5,150,105,0.12); color: #059669;">
            <el-icon :size="22"><CircleCheckFilled /></el-icon>
          </div>
          <div>
            <div class="metric-card__label">已完成</div>
            <div class="metric-card__value">{{ completedCount }}</div>
            <div class="metric-card__hint">成功导入的批次</div>
          </div>
        </article>
        <article class="metric-card" style="grid-column: span 4;">
          <div class="metric-card__icon" style="background: rgba(6,182,212,0.12); color: #0891b2;">
            <el-icon :size="22"><UploadFilled /></el-icon>
          </div>
          <div>
            <div class="metric-card__label">累计导入</div>
            <div class="metric-card__value">{{ totalImported }}</div>
            <div class="metric-card__hint">岗位总数</div>
          </div>
        </article>
      </section>

      <section class="glass-card">
        <div class="toolbar-panel">
          <div>
            <div class="section-title">导入记录</div>
            <div class="section-desc">查看所有导入批次，支持恢复中断的任务和查看分析详情。</div>
          </div>
          <div class="toolbar-group">
            <el-select v-model="statusFilter" placeholder="状态筛选" clearable class="!w-36" @change="handleSearch">
              <el-option label="待解析" :value="0" />
              <el-option label="AI解析中" :value="1" />
            <el-option label="待确认" :value="2" />
              <el-option label="导入中" :value="3" />
              <el-option label="导入完成" :value="4" />
              <el-option label="导入失败" :value="5" />
            </el-select>
            <el-button :icon="Refresh" @click="handleSearch">刷新</el-button>
          </div>
        </div>

        <div class="panel-body">
          <el-table :data="batchList" v-loading="listLoading" style="width: 100%">
            <el-table-column prop="id" label="批次ID" width="80" />
            <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
            <el-table-column prop="totalRows" label="岗位数" width="80" align="center" />
            <el-table-column label="分析进度" min-width="160">
              <template #default="{ row }">
                <div v-if="row.importStatus <= 1" class="progress-cell">
                  <el-progress
                    :percentage="
                      row.totalRows > 0
                        ? Math.round(((row.successAnalyzedCount + row.failedAnalyzedCount) / row.totalRows) * 100)
                        : 0
                    "
                    :stroke-width="6"
                    style="width: 100px"
                  />
                  <span class="progress-text">
                    {{ row.successAnalyzedCount + row.failedAnalyzedCount }}/{{ row.totalRows }}
                  </span>
                </div>
                <span v-else-if="row.importStatus === 4" class="result-text">
                  <span class="result-success">{{ row.successCount || 0 }} 成功</span>
                  <span v-if="row.failCount" class="result-fail">{{ row.failCount }} 失败</span>
                </span>
                <span v-else-if="row.importStatus === 3" class="text-muted">后台导入中...</span>
                <span v-else class="text-muted">--</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="getStatusInfo(row.importStatus, row.cancelFlag).type as any" size="small">
                  {{ getStatusInfo(row.importStatus, row.cancelFlag).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">
                <span class="text-muted">{{ formatTime(row.createdTime) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="250" fixed="right">
              <template #default="{ row }">
                <div class="table-link-cluster">
                  <el-button type="primary" link @click="enterDetail(row)">
                    <el-icon><View /></el-icon>
                    查看
                  </el-button>
                  <el-button
                    v-if="row.importStatus === 2 || (row.importStatus === 1 && row.cancelFlag !== 1)"
                    type="warning"
                    link
                    @click="enterDetail(row)"
                  >
                    继续处理
                  </el-button>
                  <el-button
                    v-if="row.importStatus === 5 || (row.importStatus === 1 && row.cancelFlag === 1) || row.importStatus === 0"
                    type="success"
                    link
                    @click="handleRetryFromList(row)"
                  >
                    重试
                  </el-button>
                  <el-button
                    v-if="row.importStatus === 4"
                    type="success"
                    link
                    @click="handleIncludeBatchInMarketDiscovery(row)"
                  >
                    纳入市场 JD
                  </el-button>
                  <el-button type="danger" link @click="handleDeleteFromList(row)">
                    删除
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="panel-footer">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            :current-page="currentPage"
            :page-size="pageSize"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </section>
    </template>

    <!-- ========== 详情视图 ========== -->
    <template v-if="viewMode === 'detail'">
      <section class="page-hero">
        <div>
          <div class="page-hero__eyebrow">Import Detail</div>
          <h1 class="page-hero__title">
            {{ importDone ? '导入完成' : importFailed ? '导入失败' : importing ? '后台导入中' : analyzing ? 'AI 分析中' : '导入详情' }}
          </h1>
          <p class="page-hero__desc">
            {{ preview?.fileName || '加载中...' }}
            <template v-if="preview"> &mdash; 共 {{ preview.totalRows }} 个岗位 </template>
          </p>
          <div class="page-hero__meta">
            <span class="hero-chip">批次 #{{ currentBatchId }}</span>
            <span v-if="preview" class="hero-chip">
              分析成功 {{ preview.items.filter((i) => i.analysisStatus === 2).length }}
            </span>
            <span v-if="preview" class="hero-chip">
              分析失败 {{ preview.items.filter((i) => i.analysisStatus === 3).length }}
            </span>
          </div>
        </div>
        <div class="toolbar-group">
          <el-button @click="backToList">返回列表</el-button>
        </div>
      </section>

      <!-- 分析中状态 -->
      <section v-if="analyzing && !importDone" class="glass-card status-banner">
        <div class="status-banner__content">
          <el-icon class="loading-icon" :size="32"><Loading /></el-icon>
          <div>
            <div class="status-banner__title">AI 正在分析岗位能力要求...</div>
            <div class="status-banner__desc">
              已完成 {{ preview?.items.filter((i) => i.analysisStatus === 2 || i.analysisStatus === 3).length || 0 }}/{{
                preview?.totalRows || 0
              }}
              条，可随时取消
            </div>
          </div>
        </div>
        <el-button type="danger" @click="handleCancel">取消分析</el-button>
      </section>

      <!-- 导入完成状态 -->
      <section v-if="importDone" class="glass-card status-banner status-banner--success">
        <div class="status-banner__content">
          <el-icon :size="32" color="#059669"><CircleCheckFilled /></el-icon>
          <div>
            <div class="status-banner__title">导入完成</div>
            <div class="status-banner__desc">
              成功导入 {{ importResult.success }} 个岗位，失败 {{ importResult.fail }} 个
            </div>
          </div>
        </div>
        <el-button type="primary" @click="backToList">返回列表</el-button>
      </section>

      <section v-if="importing && !importDone" class="glass-card status-banner">
        <div class="status-banner__content">
          <el-icon class="loading-icon" :size="32"><Loading /></el-icon>
          <div>
            <div class="status-banner__title">岗位正在后台导入...</div>
            <div class="status-banner__desc">页面可以停留或离开，导入完成后重新进入本批次即可查看结果。</div>
          </div>
        </div>
      </section>

      <section v-if="importFailed" class="glass-card status-banner status-banner--error">
        <div class="status-banner__content">
          <el-icon :size="32" color="#dc2626"><CircleCheckFilled /></el-icon>
          <div>
            <div class="status-banner__title">导入失败</div>
            <div class="status-banner__desc">{{ preview?.errorMessage || '后台导入失败，请重试或查看批次错误信息。' }}</div>
          </div>
        </div>
      </section>

      <!-- 岗位明细表格 -->
      <section class="glass-card">
        <div class="toolbar-panel">
          <div>
            <div class="section-title">岗位明细</div>
            <div class="section-desc">
              AI 分析结果预览，确认后将批量创建岗位和能力模型。
            </div>
          </div>
          <div class="toolbar-group">
            <template v-if="!analyzing && !importDone && !importing && !importFailed">
              <el-button
                v-if="!allAnalyzed()"
                type="warning"
                @click="handleRetry"
              >
                重新分析未完成项
              </el-button>
              <el-button
                type="primary"
                :loading="confirming"
                :disabled="!hasAnySuccess()"
                @click="handleConfirm"
              >
                确认导入
              </el-button>
              <el-checkbox v-model="includeMarketJd" class="market-jd-option">
                纳入市场 JD（复用本次已确认能力）
              </el-checkbox>
            </template>
          </div>
        </div>

        <div class="panel-body">
          <el-table :data="preview?.items || []" border max-height="500">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column prop="rowIndex" label="行号" width="70" align="center" />
            <el-table-column prop="postName" label="岗位名称" width="160" show-overflow-tooltip />
            <el-table-column prop="postDescription" label="岗位描述" min-width="220" show-overflow-tooltip />
            <el-table-column label="能力项" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.analysisStatus === 2" type="success" size="small">
                  {{ row.abilities?.length || 0 }}项
                </el-tag>
                <el-tag v-else-if="row.analysisStatus === 3" type="danger" size="small">失败</el-tag>
                <el-tag v-else-if="row.analysisStatus === 1" type="warning" size="small">分析中</el-tag>
                <el-tag v-else type="info" size="small">待分析</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="(analysisStatusMap[row.analysisStatus]?.type || 'info') as any" size="small">
                  {{ analysisStatusMap[row.analysisStatus]?.label || '未知' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="showDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </template>

    <!-- ========== 上传对话框 ========== -->
    <el-dialog v-model="uploadDialogVisible" title="新建导入" width="520px" :close-on-click-modal="false">
      <el-upload
        drag
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="handleFileChange"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将Excel文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">
            请使用标准模板：岗位名称、岗位职责、任职要求、所属行业。
            <el-button link type="primary" @click.stop="handleDownloadTemplate">下载模板</el-button>
          </div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">开始AI解析</el-button>
      </template>
    </el-dialog>

    <!-- ========== 能力详情弹窗 ========== -->
    <el-dialog v-model="detailVisible" title="岗位能力分析详情" width="800px">
      <div v-if="selectedItem">
        <h4 style="margin: 0 0 8px">{{ selectedItem.postName }}</h4>
        <p style="color: var(--app-text-muted); margin-bottom: 16px">
          {{ selectedItem.postDescription }}
        </p>
        <el-table :data="selectedItem.abilities || []" border>
          <el-table-column prop="suggestedName" label="能力标签" min-width="120" />
          <el-table-column prop="tagCategory" label="分类" width="100" />
          <el-table-column prop="minRequiredLevel" label="要求等级" width="80" align="center" />
          <el-table-column prop="weight" label="权重" width="70" align="center" />
          <el-table-column label="匹配状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.matchStatus === 'MATCHED'" type="success" size="small">已匹配</el-tag>
              <el-tag v-else-if="row.matchStatus === 'SIMILAR'" type="warning" size="small">相似</el-tag>
              <el-tag v-else type="info" size="small">新建</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reasoning" label="推理依据" min-width="160" show-overflow-tooltip />
        </el-table>
        <div v-if="selectedItem.errorMessage" class="error-message">
          <strong>错误信息：</strong>{{ selectedItem.errorMessage }}
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.progress-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress-text {
  font-size: 12px;
  color: var(--app-text-muted);
  white-space: nowrap;
}

.result-text {
  display: flex;
  gap: 8px;
  font-size: 13px;
}

.result-success {
  color: var(--app-success);
  font-weight: 600;
}

.result-fail {
  color: var(--app-danger);
  font-weight: 600;
}

.text-muted {
  color: var(--app-text-muted);
  font-size: 13px;
}

.status-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
}

.status-banner__content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.status-banner__title {
  font-size: 16px;
  font-weight: 700;
  color: var(--app-text-strong);
}

.status-banner__desc {
  margin-top: 4px;
  font-size: 13px;
  color: var(--app-text-muted);
}

.status-banner--success {
  border-color: rgba(5, 150, 105, 0.2);
}

.loading-icon {
  animation: rotate 1.5s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.error-message {
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: 12px;
  background: rgba(220, 38, 38, 0.08);
  color: var(--app-danger);
  font-size: 13px;
}

@media (max-width: 720px) {
  .metric-grid > article {
    grid-column: span 12 !important;
  }

  .status-banner {
    flex-direction: column;
    gap: 16px;
    text-align: center;
  }

  .status-banner__content {
    flex-direction: column;
  }
}
</style>
