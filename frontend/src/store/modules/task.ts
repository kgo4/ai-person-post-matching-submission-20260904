import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { canViewTaskType } from './task-visibility'

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

    /**
     * 只保留当前身份有权查看的任务。
     *
     * 为什么需要：本 store 持久化在 `localStorage['tasks']`。上一个账号（如 HR）
     * 的任务会在下一个身份（如岗位体系管理员）登录时被 pinia 持久化插件**原样恢复**，
     * 表现为「岗位体系管理员收到匹配完成信息、顶栏铃铛亮红点、点进去 403」——
     * 匹配任务接口要求 MATCHING:READ，而 JOB_ARCHITECT 只有 POST:*。
     *
     * 进入系统（layout 挂载）时调用一次即可对齐身份；写入侧另有同源收口
     * （`matching-tasks` 的 syncToTaskStore 只对有权角色同步）。
     * 裁剪后 pinia 会把新值写回 localStorage，残留数据一并清除。
     */
    function retainVisible(permissions: readonly string[]) {
      tasks.value = tasks.value.filter(task => canViewTaskType(task.type, permissions))
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
      retainVisible,
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
