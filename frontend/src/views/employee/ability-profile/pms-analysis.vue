<script setup lang="ts">
/**
 * PMS项目数据分析页面
 * 功能：映射PMS用户、分析项目工作数据、展示结果、导入能力
 */
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { MagicStick, View, Refresh } from '@element-plus/icons-vue'
import {
  autoMapPmsUser,
  manualMapPmsUser,
  getPmsMapping,
  analyzePmsAbilities,
  getPmsAnalysisHistory,
  getPmsAnalysisDetail,
  importPmsAbilities,
  listPmsUsers,
  testPmsConnection,
} from '@/api/ability-source'
import type { PmsUserMapping, PmsAnalysisTask } from '@/api/ability-source'
import { getEmployee } from '@/api/employee'
import { useTaskStore } from '@/store/modules/task'

const router = useRouter()
const route = useRoute()
const taskStore = useTaskStore()
const empId = ref(Number(route.query.empId) || 0)
const empName = ref('')
const loading = ref(false)

// PMS映射
const mapping = ref<PmsUserMapping | null>(null)
const mappingLoading = ref(false)

// 手动映射弹窗
const manualMapDialogVisible = ref(false)
const pmsUsers = ref<any[]>([])
const selectedPmsUserId = ref<number | undefined>(undefined)
const pmsUsersLoading = ref(false)

// 分析配置
const analysisMonths = ref(6)

// 分析任务
const analyzing = ref(false)
const analysisHistory = ref<PmsAnalysisTask[]>([])

// 结果详情弹窗
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailSummary = ref('')
const detailAbilities = ref<any[]>([])
const detailTask = ref<PmsAnalysisTask | null>(null)
const selectedAbilityIndexes = ref<number[]>([])
const importing = ref(false)

// PMS连接状态
const pmsConnected = ref<boolean | null>(null)

// 状态映射
const statusMap: Record<number, { text: string; type: string }> = {
  0: { text: '待分析', type: 'info' },
  1: { text: '分析中', type: 'warning' },
  2: { text: '待导入', type: 'success' },
  3: { text: '失败', type: 'danger' },
  6: { text: '已导入', type: 'success' },
}

const categoryLabel: Record<string, string> = {
  TECHNICAL: '技术能力',
  SOFT: '软技能',
  BUSINESS: '业务能力',
}

const levelLabel: Record<number, string> = {
  1: '入门',
  2: '熟悉',
  3: '掌握',
  4: '精通',
  5: '专家',
}

onMounted(async () => {
  if (!empId.value) {
    ElMessage.warning('请先选择人员')
    router.back()
    return
  }
  await loadEmployeeInfo()
  await loadMapping()
  await loadHistory()
  await testConnection()

  // 检查是否有进行中的PMS分析任务
  checkRunningTasks()
})

function checkRunningTasks() {
  const runningTasks = taskStore.getTasksByType('pms-analysis').filter(t => t.status === 'running')
  if (runningTasks.length > 0) {
    ElNotification({
      title: 'PMS分析进行中',
      message: '上次的PMS分析可能已完成，请刷新历史记录查看结果',
      type: 'info',
      duration: 8000,
    })
  }
}

async function loadEmployeeInfo() {
  try {
    const res = await getEmployee(empId.value)
    empName.value = res.data?.realName || ''
  } catch {
    // handled by interceptor
  }
}

async function testConnection() {
  try {
    const res = await testPmsConnection()
    pmsConnected.value = res.data
  } catch {
    pmsConnected.value = false
  }
}

async function loadMapping() {
  try {
    const res = await getPmsMapping(empId.value)
    mapping.value = res.data || null
  } catch {
    mapping.value = null
  }

  if (!mapping.value) {
    try {
      const autoRes = await autoMapPmsUser(empId.value)
      if (autoRes.data) {
        mapping.value = autoRes.data
        ElMessage.success(`已自动匹配PMS用户：${autoRes.data.pmsNickname}`)
      }
    } catch {
      // 自动匹配失败
    }
  }
}

async function handleAutoMap() {
  mappingLoading.value = true
  try {
    const res = await autoMapPmsUser(empId.value)
    if (res.data) {
      mapping.value = res.data
      ElMessage.success(`自动映射成功：${res.data.pmsNickname}`)
    } else {
      ElMessage.warning('未找到匹配的PMS用户，请手动映射')
    }
  } catch {
    // handled by interceptor
  } finally {
    mappingLoading.value = false
  }
}

async function openManualMap() {
  pmsUsersLoading.value = true
  manualMapDialogVisible.value = true
  try {
    const res = await listPmsUsers()
    pmsUsers.value = res.data || []
  } catch {
    // handled by interceptor
  } finally {
    pmsUsersLoading.value = false
  }
}

async function handleManualMap() {
  if (!selectedPmsUserId.value) {
    ElMessage.warning('请选择PMS用户')
    return
  }
  mappingLoading.value = true
  try {
    const res = await manualMapPmsUser(empId.value, selectedPmsUserId.value)
    mapping.value = res.data
    manualMapDialogVisible.value = false
    ElMessage.success('映射成功')
  } catch {
    // handled by interceptor
  } finally {
    mappingLoading.value = false
  }
}

async function handleAnalyze() {
  if (!mapping.value) {
    ElMessage.warning('请先映射PMS用户')
    return
  }

  analyzing.value = true
  const taskId = `pms-${empId.value}-${Date.now()}`
  taskStore.addTask({
    id: taskId,
    type: 'pms-analysis',
    refId: empId.value,
    refName: empName.value,
  })

  try {
    const res = await analyzePmsAbilities(empId.value, analysisMonths.value)
    taskStore.updateTask(taskId, { status: 'completed', message: '分析完成' })
    ElMessage.success(`分析完成，提取了 ${res.data.extractedAbilityCount} 项能力`)
    await loadHistory()
    // 自动打开最新分析结果
    await openDetail(res.data)
  } catch {
    taskStore.updateTask(taskId, { status: 'failed', message: '分析失败' })
    // handled by interceptor
  } finally {
    analyzing.value = false
  }
}

async function loadHistory() {
  try {
    const res = await getPmsAnalysisHistory(empId.value)
    analysisHistory.value = res.data || []
  } catch {
    // handled by interceptor
  }
}

async function openDetail(task: PmsAnalysisTask) {
  detailVisible.value = true
  detailLoading.value = true
  detailTask.value = task
  detailAbilities.value = []
  detailSummary.value = ''
  selectedAbilityIndexes.value = []

  try {
    const res = await getPmsAnalysisDetail(task.id)
    detailSummary.value = res.data.summary || ''
    detailAbilities.value = res.data.abilities || []
    // 默认全选
    selectedAbilityIndexes.value = detailAbilities.value.map((_: any, i: number) => i)
  } catch {
    // handled by interceptor
  } finally {
    detailLoading.value = false
  }
}

async function handleImport() {
  if (!detailTask.value || selectedAbilityIndexes.value.length === 0) {
    ElMessage.warning('请至少选择一项能力')
    return
  }

  importing.value = true
  try {
    const res = await importPmsAbilities(empId.value, detailTask.value.id, selectedAbilityIndexes.value)
    ElMessage.success(`导入成功，共导入 ${res.data.importedCount} 项能力`)
    detailVisible.value = false
    await loadHistory()
  } catch {
    // handled by interceptor
  } finally {
    importing.value = false
  }
}

function handleSelectionChange(selection: any[]) {
  selectedAbilityIndexes.value = selection.map(row => {
    return detailAbilities.value.findIndex(a => a === row)
  }).filter(i => i >= 0)
}

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="hover">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>PMS项目数据分析 - {{ empName || '人员#' + empId }}</span>
          <el-button @click="router.back()">返回</el-button>
        </div>
      </template>

      <!-- PMS连接状态 -->
      <el-alert
        v-if="pmsConnected === false"
        title="PMS数据库连接失败，请检查网络和配置"
        type="error"
        :closable="false"
        show-icon
        style="margin-bottom: 16px;"
      />

      <!-- 用户映射区域 -->
      <div class="section">
        <h4>用户映射</h4>
        <div v-if="mapping" class="mapping-info">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="PMS用户名">{{ mapping.pmsUsername }}</el-descriptions-item>
            <el-descriptions-item label="PMS昵称">{{ mapping.pmsNickname }}</el-descriptions-item>
            <el-descriptions-item label="PMS工号">{{ mapping.pmsEmployeeId || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div style="margin-top: 8px;">
            <el-button size="small" @click="openManualMap">重新映射</el-button>
          </div>
        </div>
        <div v-else class="no-mapping">
          <el-empty description="未映射PMS用户" :image-size="60">
            <el-button type="primary" :loading="mappingLoading" @click="handleAutoMap">
              自动映射（通过工号匹配）
            </el-button>
            <el-button @click="openManualMap">手动映射</el-button>
          </el-empty>
        </div>
      </div>

      <el-divider />

      <!-- 分析配置 -->
      <div class="section">
        <h4>数据分析</h4>
        <el-form inline>
          <el-form-item label="分析时间范围">
            <el-select v-model="analysisMonths" style="width: 160px;">
              <el-option :value="3" label="近3个月" />
              <el-option :value="6" label="近6个月" />
              <el-option :value="12" label="近12个月" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :icon="MagicStick"
              :loading="analyzing"
              :disabled="!mapping"
              @click="handleAnalyze"
            >
              {{ analyzing ? 'AI分析中...' : '开始分析' }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-divider />

      <!-- 分析历史 -->
      <div class="section">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
          <h4 style="margin: 0;">分析历史</h4>
          <el-button :icon="Refresh" size="small" @click="loadHistory">刷新</el-button>
        </div>
        <el-table :data="analysisHistory" border size="small" v-loading="loading">
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="statusMap[row.analysisStatus]?.type as any" size="small">
                {{ statusMap[row.analysisStatus]?.text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="dateRangeMonths" label="时间范围" width="90" align="center">
            <template #default="{ row }">{{ row.dateRangeMonths }}个月</template>
          </el-table-column>
          <el-table-column prop="workOrderCount" label="工单" width="70" align="center" />
          <el-table-column prop="bugCount" label="Bug" width="70" align="center" />
          <el-table-column prop="testCaseCount" label="用例" width="70" align="center" />
          <el-table-column prop="projectCount" label="项目" width="70" align="center" />
          <el-table-column prop="extractedAbilityCount" label="提取能力" width="90" align="center">
            <template #default="{ row }">
              <span style="font-weight: bold; color: #409eff;">{{ row.extractedAbilityCount }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdTime" label="分析时间" width="160">
            <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.analysisStatus === 2"
                type="primary"
                link
                size="small"
                @click="openDetail(row)"
              >
                查看并导入
              </el-button>
              <el-button
                v-else-if="row.analysisStatus === 6"
                type="success"
                link
                size="small"
                @click="openDetail(row)"
              >
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 分析结果详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="PMS分析结果"
      width="900px"
      :close-on-click-modal="false"
    >
      <div v-loading="detailLoading">
        <template v-if="detailTask">
          <!-- 任务概览 -->
          <el-descriptions :column="4" border size="small" style="margin-bottom: 16px;">
            <el-descriptions-item label="状态">
              <el-tag :type="statusMap[detailTask.analysisStatus]?.type as any" size="small">
                {{ statusMap[detailTask.analysisStatus]?.text }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="工单数">{{ detailTask.workOrderCount }}</el-descriptions-item>
            <el-descriptions-item label="Bug数">{{ detailTask.bugCount }}</el-descriptions-item>
            <el-descriptions-item label="项目数">{{ detailTask.projectCount }}</el-descriptions-item>
          </el-descriptions>

          <!-- AI分析摘要 -->
          <div v-if="detailSummary" style="margin-bottom: 16px;">
            <h4>分析摘要</h4>
            <el-input type="textarea" :model-value="detailSummary" :rows="3" readonly />
          </div>

          <!-- 能力列表 -->
          <div v-if="detailAbilities.length > 0">
            <h4>提取的能力（{{ detailAbilities.length }}项）
              <span v-if="detailTask.analysisStatus === 2" style="font-size: 12px; color: #909399; font-weight: normal; margin-left: 8px;">
                勾选后写入人员能力画像
              </span>
            </h4>
            <el-table
              :data="detailAbilities"
              border
              size="small"
              max-height="400"
              @selection-change="handleSelectionChange"
            >
              <el-table-column
                v-if="detailTask.analysisStatus === 2"
                type="selection"
                width="45"
                :selectable="() => true"
              />
              <el-table-column prop="tagName" label="能力标签" width="150" />
              <el-table-column label="分类" width="90">
                <template #default="{ row }">
                  {{ categoryLabel[row.tagCategory] || row.tagCategory }}
                </template>
              </el-table-column>
              <el-table-column label="等级" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.level >= 4 ? 'success' : row.level >= 3 ? 'primary' : 'warning'" size="small">
                    {{ levelLabel[row.level] || row.level }}({{ row.level }})
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="置信度" width="80" align="center">
                <template #default="{ row }">
                  {{ row.confidence ? (row.confidence * 100).toFixed(0) + '%' : '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="evidence" label="依据" min-width="200" show-overflow-tooltip />
            </el-table>
          </div>

          <el-empty v-else-if="!detailLoading" description="暂无分析结果" />
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detailTask?.analysisStatus === 2"
          type="primary"
          :loading="importing"
          :disabled="selectedAbilityIndexes.length === 0"
          @click="handleImport"
        >
          写入人员能力画像 ({{ selectedAbilityIndexes.length }})
        </el-button>
      </template>
    </el-dialog>

    <!-- 手动映射弹窗 -->
    <el-dialog
      v-model="manualMapDialogVisible"
      title="手动映射PMS用户"
      width="600px"
      :close-on-click-modal="false"
    >
      <div v-loading="pmsUsersLoading">
        <p style="margin-bottom: 12px; color: #909399;">请选择对应的PMS用户：</p>
        <el-radio-group v-model="selectedPmsUserId" style="width: 100%;">
          <el-table :data="pmsUsers" border size="small" max-height="400">
            <el-table-column label="选择" width="60" align="center">
              <template #default="{ row }">
                <el-radio :value="row.id">&nbsp;</el-radio>
              </template>
            </el-table-column>
            <el-table-column prop="username" label="用户名" width="120" />
            <el-table-column prop="nickname" label="昵称" width="120" />
            <el-table-column prop="employee_id" label="工号" width="100" />
            <el-table-column prop="role" label="角色" width="80" />
          </el-table>
        </el-radio-group>
      </div>
      <template #footer>
        <el-button @click="manualMapDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="mappingLoading" @click="handleManualMap">确认映射</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-container {
  padding: 20px;
}

.section {
  margin-bottom: 16px;
}

.section h4 {
  margin-bottom: 12px;
  color: #303133;
  font-size: 15px;
}

.mapping-info {
  padding: 12px;
  background: #f0f9eb;
  border-radius: 8px;
}

.no-mapping {
  padding: 20px;
}
</style>
