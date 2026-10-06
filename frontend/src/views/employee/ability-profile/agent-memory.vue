<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search, View, Edit, Check, Close, Clock } from '@element-plus/icons-vue'
import {
  pageAgentMemories,
  getAgentMemoryById,
  updateAgentMemory,
  enableAgentMemory,
  disableAgentMemory,
  expireAgentMemory,
  pageGovernanceEvents,
  getGovernanceEventById,
} from '@/api/ability-governance'
import type { AgentMemory, PersonAbilityGovernanceEvent, PageParams } from '@/api/ability-governance'

// 记忆列表
const memories = ref<AgentMemory[]>([])
const memoryTotal = ref(0)
const memoryLoading = ref(false)
const memoryQuery = reactive<PageParams>({
  pageNum: 1,
  pageSize: 10,
  status: '',
  memoryType: '',
  scope: '',
  keyword: '',
})

// 事件列表
const events = ref<PersonAbilityGovernanceEvent[]>([])
const eventTotal = ref(0)
const eventLoading = ref(false)
const eventQuery = reactive<PageParams>({
  pageNum: 1,
  pageSize: 10,
  modifyType: '',
  empId: undefined,
  tagId: undefined,
})

// 当前激活的标签页
const activeTab = ref('memories')

// 记忆详情对话框
const memoryDetailVisible = ref(false)
const currentMemory = ref<AgentMemory | null>(null)

// 事件详情对话框
const eventDetailVisible = ref(false)
const currentEvent = ref<PersonAbilityGovernanceEvent | null>(null)

// 编辑对话框
const editDialogVisible = ref(false)
const editForm = ref({
  id: 0,
  title: '',
  content: '',
  priority: 0,
  applicableScope: '',
})

// 记忆类型选项
const memoryTypeOptions = [
  { label: '标签归一', value: 'TAG_NORMALIZE' },
  { label: '标签拒绝', value: 'TAG_REJECT' },
  { label: '等级规则', value: 'LEVEL_RULE' },
  { label: '来源权重', value: 'SOURCE_WEIGHT' },
  { label: '边界定义', value: 'BOUNDARY_DEFINE' },
  { label: '正例标签', value: 'MANUAL_POSITIVE' },
]

// 状态选项
const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '启用', value: 'ACTIVE' },
  { label: '禁用', value: 'INACTIVE' },
  { label: '过期', value: 'EXPIRED' },
]

// 适用范围选项
const scopeOptions = [
  { label: '全部', value: 'ALL' },
  { label: '简历解析', value: 'RESUME_PARSE' },
  { label: 'AI测试', value: 'AI_TEST' },
  { label: 'AI面试', value: 'AI_INTERVIEW' },
  { label: 'PMS分析', value: 'AI_PROJECT' },
  { label: '最终等级确认', value: 'FINAL_LEVEL_CONFIRMATION' },
]

// 修改类型选项
const modifyTypeOptions = [
  { label: '人工新增', value: 'MANUAL_ADD' },
  { label: '标签替换', value: 'TAG_REPLACE' },
  { label: '等级上调', value: 'LEVEL_UP' },
  { label: '等级下调', value: 'LEVEL_DOWN' },
  { label: '删除能力', value: 'DELETE_ABILITY' },
  { label: '证据更新', value: 'EVIDENCE_UPDATE' },
  { label: '标签重命名', value: 'TAG_RENAME' },
  { label: '移除标签', value: 'REMOVE_TAG' },
]

// 记忆类型标签颜色
const memoryTypeColors: Record<string, string> = {
  TAG_NORMALIZE: 'primary',
  TAG_REJECT: 'danger',
  LEVEL_RULE: 'success',
  SOURCE_WEIGHT: 'warning',
  BOUNDARY_DEFINE: 'info',
  MANUAL_POSITIVE: '',
}

onMounted(() => {
  loadMemories()
  loadEvents()
})

// 加载记忆列表
async function loadMemories() {
  memoryLoading.value = true
  try {
    const res = await pageAgentMemories(memoryQuery)
    memories.value = res.data.records
    memoryTotal.value = res.data.total
  } catch {
    // handled by interceptor
  } finally {
    memoryLoading.value = false
  }
}

// 加载事件列表
async function loadEvents() {
  eventLoading.value = true
  try {
    const res = await pageGovernanceEvents(eventQuery)
    events.value = res.data.records
    eventTotal.value = res.data.total
  } catch {
    // handled by interceptor
  } finally {
    eventLoading.value = false
  }
}

// 查看记忆详情
async function viewMemoryDetail(id: number) {
  try {
    const res = await getAgentMemoryById(id)
    currentMemory.value = res.data
    memoryDetailVisible.value = true
  } catch {
    // handled by interceptor
  }
}

// 查看事件详情
async function viewEventDetail(id: number) {
  try {
    const res = await getGovernanceEventById(id)
    currentEvent.value = res.data
    eventDetailVisible.value = true
  } catch {
    // handled by interceptor
  }
}

// 启用记忆
async function handleEnableMemory(id: number) {
  try {
    await ElMessageBox.confirm('确定要启用这条记忆吗？', '确认操作', { type: 'warning' })
    await enableAgentMemory(id)
    ElMessage.success('启用成功')
    loadMemories()
  } catch {
    // cancelled or error
  }
}

// 禁用记忆
async function handleDisableMemory(id: number) {
  try {
    await ElMessageBox.confirm('确定要禁用这条记忆吗？', '确认操作', { type: 'warning' })
    await disableAgentMemory(id)
    ElMessage.success('禁用成功')
    loadMemories()
  } catch {
    // cancelled or error
  }
}

// 过期记忆
async function handleExpireMemory(id: number) {
  try {
    await ElMessageBox.confirm('确定要将这条记忆设为过期吗？', '确认操作', { type: 'warning' })
    await expireAgentMemory(id)
    ElMessage.success('已过期')
    loadMemories()
  } catch {
    // cancelled or error
  }
}

// 打开编辑对话框
function openEditDialog(memory: AgentMemory) {
  editForm.value = {
    id: memory.id,
    title: memory.title,
    content: memory.content,
    priority: memory.priority,
    applicableScope: memory.applicableScope,
  }
  editDialogVisible.value = true
}

// 保存编辑
async function handleSaveEdit() {
  try {
    await updateAgentMemory(editForm.value.id, {
      title: editForm.value.title,
      content: editForm.value.content,
      priority: editForm.value.priority,
      applicableScope: editForm.value.applicableScope,
    })
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    loadMemories()
  } catch {
    // handled by interceptor
  }
}

// 获取标签文本
function getMemoryTypeLabel(type: string): string {
  return memoryTypeOptions.find((o) => o.value === type)?.label || type
}
function getStatusLabel(status: string): string {
  return statusOptions.find((o) => o.value === status)?.label || status
}
function getModifyTypeLabel(type: string): string {
  return modifyTypeOptions.find((o) => o.value === type)?.label || type
}
function getScopeLabel(scope: string): string {
  return scopeOptions.find((o) => o.value === scope)?.label || scope
}

// 状态标签类型
function getStatusTagType(status: string): string {
  const map: Record<string, string> = { DRAFT: 'info', ACTIVE: 'success', INACTIVE: 'warning', EXPIRED: 'danger' }
  return map[status] || 'info'
}

// 分页
function handleMemoryPageChange(page: number) { memoryQuery.pageNum = page; loadMemories() }
function handleMemorySizeChange(size: number) { memoryQuery.pageSize = size; memoryQuery.pageNum = 1; loadMemories() }
function handleEventPageChange(page: number) { eventQuery.pageNum = page; loadEvents() }
function handleEventSizeChange(size: number) { eventQuery.pageSize = size; eventQuery.pageNum = 1; loadEvents() }

// 重置查询
function resetMemoryQuery() {
  memoryQuery.status = ''; memoryQuery.memoryType = ''; memoryQuery.scope = ''; memoryQuery.keyword = ''; memoryQuery.pageNum = 1
  loadMemories()
}
function resetEventQuery() {
  eventQuery.modifyType = ''; eventQuery.empId = undefined; eventQuery.tagId = undefined; eventQuery.pageNum = 1
  loadEvents()
}
</script>

<template>
  <div class="page-shell motion-page">
    <section class="page-hero motion-scan">
      <div>
        <div class="page-hero__eyebrow">Agent Memory Governance</div>
        <h1 class="page-hero__title">Agent 记忆治理中心</h1>
        <p class="page-hero__desc">管理人工治理沉淀的记忆规则，启用/禁用/编辑记忆，查看治理事件历史。</p>
        <div class="page-hero__meta">
          <span class="hero-chip">标签归一</span>
          <span class="hero-chip">标签拒绝</span>
          <span class="hero-chip">等级规则</span>
          <span class="hero-chip">正例标签</span>
        </div>
      </div>
    </section>

    <section class="glass-card motion-rise">
      <el-tabs v-model="activeTab" class="px-4 pt-4">
        <!-- 记忆管理标签页 -->
        <el-tab-pane label="记忆管理" name="memories">
          <div class="toolbar-panel">
            <div>
              <div class="section-title">记忆规则列表</div>
              <div class="section-desc">共 {{ memoryTotal }} 条记忆规则</div>
            </div>
            <div class="toolbar-group">
              <el-input v-model="memoryQuery.keyword" placeholder="关键词搜索" clearable class="!w-48" :prefix-icon="Search" />
              <el-select v-model="memoryQuery.status" placeholder="状态" clearable class="!w-28">
                <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
              <el-select v-model="memoryQuery.memoryType" placeholder="记忆类型" clearable class="!w-32">
                <el-option v-for="opt in memoryTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
              <el-select v-model="memoryQuery.scope" placeholder="适用范围" clearable class="!w-32">
                <el-option v-for="opt in scopeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
              <el-button type="primary" @click="loadMemories">查询</el-button>
              <el-button @click="resetMemoryQuery">重置</el-button>
              <el-button :icon="Refresh" @click="loadMemories">刷新</el-button>
            </div>
          </div>

          <el-table :data="memories" v-loading="memoryLoading" border stripe style="width: 100%">
            <el-table-column prop="title" label="记忆内容" min-width="200" show-overflow-tooltip />
            <el-table-column prop="memoryType" label="记忆类型" width="120">
              <template #default="{ row }">
                <el-tag :type="memoryTypeColors[row.memoryType] || 'info'" size="small">
                  {{ getMemoryTypeLabel(row.memoryType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="applicableScope" label="适用范围" width="120">
              <template #default="{ row }">{{ getScopeLabel(row.applicableScope) }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusTagType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" width="80" align="center" />
            <el-table-column prop="useCount" label="命中次数" width="100" align="center" />
            <el-table-column prop="createdTime" label="创建时间" width="170" />
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="viewMemoryDetail(row.id)"><el-icon><View /></el-icon> 详情</el-button>
                <el-button type="primary" link @click="openEditDialog(row)"><el-icon><Edit /></el-icon> 编辑</el-button>
                <el-button v-if="row.status !== 'ACTIVE'" type="success" link @click="handleEnableMemory(row.id)">
                  <el-icon><Check /></el-icon> 启用
                </el-button>
                <el-button v-if="row.status === 'ACTIVE'" type="warning" link @click="handleDisableMemory(row.id)">
                  <el-icon><Close /></el-icon> 禁用
                </el-button>
                <el-button v-if="row.status !== 'EXPIRED'" type="danger" link @click="handleExpireMemory(row.id)">
                  <el-icon><Clock /></el-icon> 过期
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="memoryQuery.pageNum"
              v-model:page-size="memoryQuery.pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="memoryTotal"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="handleMemoryPageChange"
              @size-change="handleMemorySizeChange"
            />
          </div>
        </el-tab-pane>

        <!-- 治理事件标签页 -->
        <el-tab-pane label="治理事件" name="events">
          <div class="toolbar-panel">
            <div>
              <div class="section-title">治理事件列表</div>
              <div class="section-desc">共 {{ eventTotal }} 条治理事件</div>
            </div>
            <div class="toolbar-group">
              <el-select v-model="eventQuery.modifyType" placeholder="修改类型" clearable class="!w-32">
                <el-option v-for="opt in modifyTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
              <el-input v-model="eventQuery.empId" placeholder="员工ID" clearable class="!w-28" />
              <el-input v-model="eventQuery.tagId" placeholder="标签ID" clearable class="!w-28" />
              <el-button type="primary" @click="loadEvents">查询</el-button>
              <el-button @click="resetEventQuery">重置</el-button>
            </div>
          </div>

          <el-table :data="events" v-loading="eventLoading" border stripe style="width: 100%">
            <el-table-column prop="modifyType" label="修改类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ getModifyTypeLabel(row.modifyType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="oldTagName" label="原标签" width="150" show-overflow-tooltip />
            <el-table-column prop="newTagName" label="新标签" width="150" show-overflow-tooltip />
            <el-table-column prop="oldLevel" label="原等级" width="80" align="center" />
            <el-table-column prop="newLevel" label="新等级" width="80" align="center" />
            <el-table-column prop="modifyReason" label="修改原因" min-width="200" show-overflow-tooltip />
            <el-table-column prop="createdTime" label="创建时间" width="170" />
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="viewEventDetail(row.id)"><el-icon><View /></el-icon> 详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="eventQuery.pageNum"
              v-model:page-size="eventQuery.pageSize"
              :page-sizes="[10, 20, 50, 100]"
              :total="eventTotal"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="handleEventPageChange"
              @size-change="handleEventSizeChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <!-- 记忆详情对话框 -->
    <el-dialog v-model="memoryDetailVisible" title="记忆详情" width="700px">
      <div v-if="currentMemory">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="记忆ID">{{ currentMemory.id }}</el-descriptions-item>
          <el-descriptions-item label="记忆类型">
            <el-tag>{{ getMemoryTypeLabel(currentMemory.memoryType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="适用范围">{{ getScopeLabel(currentMemory.applicableScope) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusTagType(currentMemory.status)">{{ getStatusLabel(currentMemory.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="优先级">{{ currentMemory.priority }}</el-descriptions-item>
          <el-descriptions-item label="命中次数">{{ currentMemory.useCount }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ currentMemory.title }}</el-descriptions-item>
          <el-descriptions-item label="内容" :span="2">
            <div class="whitespace-pre-wrap max-h-48 overflow-y-auto">{{ currentMemory.content }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="触发表达" :span="2">{{ currentMemory.triggerExpressionsJson || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源事件ID">{{ currentMemory.sourceEventId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最后使用时间">{{ currentMemory.lastUsedTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ currentMemory.createdTime }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ currentMemory.updatedTime }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <!-- 事件详情对话框 -->
    <el-dialog v-model="eventDetailVisible" title="治理事件详情" width="700px">
      <div v-if="currentEvent">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="事件ID">{{ currentEvent.id }}</el-descriptions-item>
          <el-descriptions-item label="员工ID">{{ currentEvent.empId }}</el-descriptions-item>
          <el-descriptions-item label="修改类型">
            <el-tag>{{ getModifyTypeLabel(currentEvent.modifyType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="关联记忆ID">{{ currentEvent.memoryId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="原标签">{{ currentEvent.oldTagName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="新标签">{{ currentEvent.newTagName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="原等级">{{ currentEvent.oldLevel || '-' }}</el-descriptions-item>
          <el-descriptions-item label="新等级">{{ currentEvent.newLevel || '-' }}</el-descriptions-item>
          <el-descriptions-item label="修改原因" :span="2">
            <div class="whitespace-pre-wrap max-h-32 overflow-y-auto">{{ currentEvent.modifyReason || '-' }}</div>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ currentEvent.createdTime }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑记忆" width="600px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" placeholder="记忆标题" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="editForm.content" type="textarea" :rows="4" placeholder="记忆内容" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="editForm.priority" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="适用范围">
          <el-select v-model="editForm.applicableScope" placeholder="选择适用范围">
            <el-option v-for="opt in scopeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar-panel {
  @apply flex items-center justify-between mb-4 flex-wrap gap-2;
}
.toolbar-group {
  @apply flex items-center gap-2 flex-wrap;
}
.section-title {
  @apply text-lg font-semibold text-gray-800;
}
.section-desc {
  @apply text-sm text-gray-500 mt-1;
}
.pagination-bar {
  @apply flex justify-end mt-4;
}
</style>
