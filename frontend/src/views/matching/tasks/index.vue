<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageMatchingTasks, deleteMatchingTask, listEnabledPosts } from '@/api'
import type { MatchingTask } from '@/api/matching/types'
import { useMatchingTaskStore } from '@/store/modules/matching-tasks'
import { useTaskStore } from '@/store/modules/task'

const router = useRouter()
const store = useMatchingTaskStore()

/** 岗位ID → 岗位名 字典（历史任务可能关联已停用岗位，查不到回退 ID） */
const postNameMap = ref<Record<number, string>>({})

const activeStatus = ref<number | undefined>(undefined)
const tabValue = ref('undefined')
const list = ref<MatchingTask[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(10)
const loading = ref(false)

const statusTabs = [
  { label: '全部', value: undefined },
  { label: '进行中', value: 1 },
  { label: '已完成', value: 2 },
  { label: '失败', value: 3 },
  { label: '已取消', value: 4 },
]

const statusMeta: Record<number, { label: string; type: 'primary' | 'success' | 'danger' | 'info' | 'warning' }> = {
  0: { label: '待执行', type: 'info' },
  1: { label: '执行中', type: 'primary' },
  2: { label: '已完成', type: 'success' },
  3: { label: '失败', type: 'danger' },
  4: { label: '已取消', type: 'warning' },
}

/** 本地实时任务覆盖后端分页中的同 taskId 项（进度实时性） */
const merged = computed(() => {
  const map = new Map(list.value.map(t => [t.taskId, t]))
  for (const t of store.tasks) {
    if (t.status === 0 || t.status === 1) map.set(t.taskId, { ...t })
  }
  return [...map.values()]
})

async function load() {
  loading.value = true
  try {
    const [pageRes] = await Promise.all([pageMatchingTasks({ current: current.value, size: size.value, status: activeStatus.value })])
    const data = pageRes.data as any
    list.value = data?.records || []
    total.value = data?.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
  // 岗位名字典独立加载：失败不阻塞任务列表（taskInfo 回退显示岗位 ID）
  try {
    const postsRes = await listEnabledPosts()
    const posts = (postsRes.data as any) || []
    const map: Record<number, string> = {}
    for (const p of posts) {
      if (p?.id != null && p?.postName) map[p.id] = p.postName
    }
    postNameMap.value = map
  } catch {
    postNameMap.value = {}
  }
}

function onTabChange() {
  current.value = 1
  activeStatus.value = tabValue.value === 'undefined' ? undefined : Number(tabValue.value)
  load()
}

async function cancelTask(task: MatchingTask) {
  try {
    await ElMessageBox.confirm(`确定取消任务 ${task.taskId}？进行中的匹配将停止接收结果。`, '取消任务', { type: 'warning' })
  } catch {
    return
  }
  await store.cancel(task.taskId)
  load()
}

async function deleteTask(task: MatchingTask) {
  try {
    await ElMessageBox.confirm(
      `确定删除任务 ${task.taskId}？将连同删除该任务产生的全部匹配记录（不可恢复）。`,
      '删除任务',
      { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' },
    )
  } catch {
    return
  }
  try {
    await deleteMatchingTask(task.taskId)
    // 同步清理：通用铃铛 taskStore + 匹配 store 本地条目（防 3s 轮询 in-flight 幽灵复活）
    useTaskStore().removeTask(task.taskId)
    const idx = store.tasks.findIndex(t => t.taskId === task.taskId)
    if (idx >= 0) store.tasks.splice(idx, 1)
    ElMessage.success('任务已删除')
    load()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}

function viewResult(task: MatchingTask) {
  router.push({ path: '/matching/result', query: task.postId ? { postId: task.postId } : {} })
}

function fmtTime(s?: string) {
  return s ? new Date(s).toLocaleString() : '—'
}

function taskInfo(task: MatchingTask) {
  if (task.postId) {
    const name = postNameMap.value[task.postId]
    return name ? `${name}（#${task.postId}）` : `岗位 #${task.postId}`
  }
  if (task.empIds) {
    try {
      const ids = JSON.parse(task.empIds)
      return Array.isArray(ids) ? `员工 ${ids.join(', ')}` : task.empIds
    } catch {
      return task.empIds
    }
  }
  return '—'
}

onMounted(() => {
  load()
})
</script>

<template>
  <div class="page-shell motion-page">
    <!-- Hero -->
    <section class="page-hero motion-scan">
      <div>
        <div class="page-hero__eyebrow">Matching Tasks</div>
        <h1 class="page-hero__title">匹配任务</h1>
        <p class="page-hero__desc">全部匹配任务实时状态，进行中任务可取消，完成后可查看结果</p>
      </div>
    </section>

    <section class="glass-card motion-rise">
      <div class="toolbar-panel">
        <el-tabs v-model="tabValue" class="tasks-tabs" @tab-change="onTabChange">
          <el-tab-pane v-for="tab in statusTabs" :key="String(tab.value)" :label="tab.label" :name="String(tab.value)" />
        </el-tabs>
      </div>
      <div class="panel-body">
        <el-table v-loading="loading" :data="merged" size="small">
          <el-table-column prop="taskId" label="任务ID" min-width="200" show-overflow-tooltip />
          <el-table-column label="对象" min-width="140">
            <template #default="{ row }">{{ taskInfo(row) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusMeta[row.status]?.type || 'info'" size="small" effect="light">
                {{ statusMeta[row.status]?.label || `状态 ${row.status}` }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="进度" min-width="170">
            <template #default="{ row }">
              <el-progress v-if="row.status === 0 || row.status === 1" :percentage="row.progress || 0" :stroke-width="8" />
              <span v-else class="task-count">{{ row.totalCount || 0 }} 条记录</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="180">
            <template #default="{ row }">{{ fmtTime(row.createdTime) }}</template>
          </el-table-column>
          <el-table-column label="信息" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">
              <span :class="{ 'task-error': row.status === 3 }">
                {{ row.status === 3 ? (row.errorMessage || '匹配失败') : row.status === 2 ? (row.resultMessage || '完成') : row.status === 4 ? '已取消' : '执行中...' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 0 || row.status === 1" size="small" type="danger" text @click="cancelTask(row)">取消</el-button>
              <el-button v-else-if="row.status === 2" size="small" type="primary" text @click="viewResult(row)">查看结果</el-button>
              <el-button size="small" type="danger" text @click="deleteTask(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="tasks-pager">
          <el-pagination
            v-model:current-page="current"
            v-model:page-size="size"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="load"
            @size-change="load"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.tasks-tabs { padding: 0 20px; }
.tasks-pager { margin-top: 16px; display: flex; justify-content: flex-end; }
.task-count { font-size: 13px; color: var(--app-text-secondary, #475569); }
.task-error { color: var(--app-danger, #dc2626); }
</style>
