<script setup lang="ts">
import { Bell, Close } from '@element-plus/icons-vue'
import { useTaskStore } from '@/store/modules/task'

const taskStore = useTaskStore()

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  navigate: [type: string]
  dismiss: [id: string]
  'clear-finished': []
}>()

const taskRouteMap: Record<string, string> = {
  'matching': '/matching/tasks',
  'video-analysis': '/employee/ability-profile/live-interview',
  'pms-analysis': '/employee/ability-profile/pms-analysis',
}

function getTaskRoute(type: string) {
  return taskRouteMap[type] || '/dashboard'
}

function goToTask(type: string) {
  emit('update:visible', false)
  emit('navigate', getTaskRoute(type))
}

function taskTypeLabel(type: string) {
  if (type === 'matching') return '匹配'
  if (type === 'video-analysis') return '面试分析'
  return 'PMS分析'
}
</script>

<template>
  <div class="layout-task-bell" @click="emit('update:visible', !visible)">
    <el-icon><Bell /></el-icon>
    <span v-if="taskStore.runningTasks.length > 0" class="task-badge running">
      {{ taskStore.runningTasks.length }}
    </span>
    <span v-else-if="taskStore.completedTasks.length > 0" class="task-badge completed">
      {{ taskStore.completedTasks.length }}
    </span>
  </div>

  <div v-if="visible" class="task-panel-overlay" @click.self="emit('update:visible', false)">
    <div class="task-panel">
      <div class="task-panel__header">
        <h3>任务状态</h3>
        <el-button v-if="taskStore.tasks.some(t => t.status !== 'running')" text size="small" @click="emit('clear-finished')">
          全部已读
        </el-button>
      </div>

      <div v-if="taskStore.tasks.length === 0" class="task-panel__empty">
        暂无进行中的任务
      </div>

      <div v-else class="task-panel__list">
        <div
          v-for="task in taskStore.tasks"
          :key="task.id"
          class="task-item"
          :class="task.status"
          @click="goToTask(task.type)"
        >
          <div class="task-item__icon">
            <span v-if="task.status === 'running'" class="task-spinner"></span>
            <span v-else-if="task.status === 'completed'" class="task-icon done">&#10003;</span>
            <span v-else class="task-icon failed">&#10007;</span>
          </div>
          <div class="task-item__info">
            <div class="task-item__name">{{ task.refName || `任务 #${task.refId}` }}</div>
            <div class="task-item__meta">
              <span class="task-type-tag">{{ taskTypeLabel(task.type) }}</span>
              <span class="task-item__message">{{ task.message || '处理中...' }}</span>
            </div>
          </div>
          <button
            v-if="task.status !== 'running'"
            class="task-item__dismiss"
            title="移除"
            @click.stop="emit('dismiss', task.id)"
          >
            <el-icon><Close /></el-icon>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.layout-task-bell { position: relative; display: flex; align-items: center; justify-content: center; width: 44px; height: 44px; border: 1px solid rgba(148, 163, 184, 0.18); border-radius: 14px; background: rgba(255, 255, 255, 0.82); cursor: pointer; transition: background-color 0.2s, transform 0.2s; }
.layout-task-bell:hover { transform: translateY(-1px); background: rgba(255, 255, 255, 0.95); }
.task-badge { position: absolute; top: -4px; right: -4px; display: flex; align-items: center; justify-content: center; min-width: 18px; height: 18px; padding: 0 5px; border-radius: 999px; font-size: 11px; font-weight: 700; }
.task-badge.running { color: #fff; background: #3b82f6; animation: task-pulse 2s infinite; }
.task-badge.completed { color: #fff; background: #10b981; }
@keyframes task-pulse { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.15); } }
.task-panel-overlay { position: fixed; inset: 0; z-index: 100; }
.task-panel { position: absolute; top: 80px; right: 24px; display: flex; flex-direction: column; width: 360px; max-height: 480px; overflow: hidden; border: 1px solid rgba(148, 163, 184, 0.16); border-radius: 20px; background: rgba(255, 255, 255, 0.98); backdrop-filter: blur(16px); box-shadow: 0 20px 50px rgba(15, 23, 42, 0.15); }
.task-panel__header { display: flex; align-items: center; justify-content: space-between; padding: 16px 18px; border-bottom: 1px solid rgba(148, 163, 184, 0.12); }
.task-panel__header h3 { margin: 0; color: var(--app-text-strong); font-size: 15px; font-weight: 700; }
.task-panel__empty { padding: 40px 20px; color: var(--app-text-muted); font-size: 13px; text-align: center; }
.task-panel__list { padding: 8px; overflow-y: auto; }
.task-item { display: flex; align-items: center; gap: 12px; padding: 12px; border-radius: 14px; cursor: pointer; transition: background-color 0.15s; }
.task-item:hover { background: rgba(37, 99, 235, 0.06); }
.task-item__icon { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 36px; height: 36px; }
.task-spinner { width: 24px; height: 24px; border: 3px solid rgba(59, 130, 246, 0.2); border-top-color: #3b82f6; border-radius: 50%; animation: task-spin 0.8s linear infinite; }
@keyframes task-spin { to { transform: rotate(360deg); } }
.task-icon { display: flex; align-items: center; justify-content: center; width: 28px; height: 28px; border-radius: 50%; font-size: 14px; font-weight: 700; }
.task-icon.done { color: #10b981; background: rgba(16, 185, 129, 0.12); }
.task-icon.failed { color: #ef4444; background: rgba(239, 68, 68, 0.12); }
.task-item__info { flex: 1; min-width: 0; }
.task-item__name { overflow: hidden; color: var(--app-text-strong); font-size: 13px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.task-item__meta { display: flex; align-items: center; gap: 8px; margin-top: 4px; }
.task-type-tag { padding: 2px 6px; border-radius: 6px; color: #3b82f6; background: rgba(59, 130, 246, 0.1); font-size: 11px; font-weight: 600; }
.task-item__message { overflow: hidden; color: var(--app-text-muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.task-item__dismiss { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 28px; height: 28px; border: none; border-radius: 8px; color: var(--app-text-muted); background: transparent; cursor: pointer; transition: background-color 0.15s, color 0.15s; }
.task-item__dismiss:hover { color: #ef4444; background: rgba(239, 68, 68, 0.1); }
</style>
