import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type TaskType = 'matching' | 'video-analysis' | 'pms-analysis'
export type TaskStatus = 'running' | 'completed' | 'failed'

export interface TrackedTask {
  id: string
  type: TaskType
  refId: number
  refName?: string
  status: TaskStatus
  progress?: number
  message?: string
  startTime: number
  endTime?: number
  notified?: boolean
}

export const useTaskStore = defineStore(
  'task',
  () => {
    const tasks = ref<TrackedTask[]>([])

    const runningTasks = computed(() => tasks.value.filter(t => t.status === 'running'))
    const completedTasks = computed(() => tasks.value.filter(t => t.status === 'completed' && !t.notified))
    const hasRunningTasks = computed(() => runningTasks.value.length > 0)

    function addTask(task: Omit<TrackedTask, 'status' | 'startTime'>) {
      // 避免重复添加
      const existing = tasks.value.find(t => t.id === task.id)
      if (existing) return

      tasks.value.push({
        ...task,
        status: 'running',
        startTime: Date.now(),
      })
    }

    function updateTask(id: string, update: Partial<Pick<TrackedTask, 'status' | 'progress' | 'message' | 'refName'>>) {
      const task = tasks.value.find(t => t.id === id)
      if (!task) return

      Object.assign(task, update)
      if (update.status === 'completed' || update.status === 'failed') {
        task.endTime = Date.now()
      }
    }

    function markNotified(id: string) {
      const task = tasks.value.find(t => t.id === id)
      if (task) {
        task.notified = true
      }
    }

    function markAllNotified() {
      tasks.value.forEach(t => {
        if (t.status !== 'running') {
          t.notified = true
        }
      })
    }

    function removeTask(id: string) {
      tasks.value = tasks.value.filter(t => t.id !== id)
    }

    function clearFinished() {
      tasks.value = tasks.value.filter(t => t.status === 'running')
    }

    function getTasksByType(type: TaskType) {
      return tasks.value.filter(t => t.type === type)
    }

    function getRunningTaskByRef(type: TaskType, refId: number) {
      return tasks.value.find(t => t.type === type && t.refId === refId && t.status === 'running')
    }

    // 清理超过24小时的已完成任务
    function cleanup() {
      const now = Date.now()
      const maxAge = 24 * 60 * 60 * 1000
      tasks.value = tasks.value.filter(t => {
        if (t.status === 'running') return true
        if (t.endTime && now - t.endTime > maxAge) return false
        return true
      })
    }

    return {
      tasks,
      runningTasks,
      completedTasks,
      hasRunningTasks,
      addTask,
      updateTask,
      markNotified,
      markAllNotified,
      removeTask,
      clearFinished,
      getTasksByType,
      getRunningTaskByRef,
      cleanup,
    }
  },
  {
    persist: {
      key: 'tasks',
      storage: localStorage,
    },
  }
)
