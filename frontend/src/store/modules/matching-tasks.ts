import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { cancelMatchingTask, getMatchingTaskStatus, pageMatchingTasks } from '@/api/matching'
import type { MatchingTask } from '@/api/matching/types'
import { useTaskStore } from './task'

/** 终态：完成(2)/失败(3)/已取消(4) */
const TERMINAL = new Set([2, 3, 4])

/**
 * 全局匹配任务状态 store（跨页面存活）。
 *
 * - 发起页提交任务后 track() 注册，由全局 watcher 轮询（layout 挂载 startWatcher）
 * - 切页不丢：组件生命周期无关；刷新后由 refresh() 从后端恢复进行中任务
 * - 并行匹配：多个任务同时轮询（pollAll）
 * - 同步到通用 taskStore（type:'matching'），顶栏铃铛面板零改动复用
 */
export const useMatchingTaskStore = defineStore(
  'matching-tasks',
  () => {
    const tasks = ref<MatchingTask[]>([])
    const runningCount = computed(() => tasks.value.filter(t => t.status === 0 || t.status === 1).length)

    let timer: number | null = null
    /** 进行中请求去重：同一 taskId 同时只允许一个 in-flight 查询 */
    const inFlight = new Set<string>()

    function byTaskId(taskId: string) {
      return tasks.value.find(t => t.taskId === taskId)
    }

    function upsert(item: MatchingTask): MatchingTask {
      const idx = tasks.value.findIndex(t => t.taskId === item.taskId)
      if (idx >= 0) tasks.value[idx] = item
      else tasks.value.push(item)
      return item
    }

    function taskStoreStatus(t: MatchingTask): 'running' | 'completed' | 'failed' {
      if (t.status === 2) return 'completed'
      if (t.status === 3 || t.status === 4) return 'failed'
      return 'running'
    }

    function taskStoreMessage(t: MatchingTask): string {
      if (t.status === 2) return t.resultMessage || '匹配完成'
      if (t.status === 3) return t.errorMessage || '匹配失败'
      if (t.status === 4) return '已取消'
      return '处理中...'
    }

    function notifyDone(t: MatchingTask) {
      if (t.status === 2) ElMessage.success(`匹配任务完成：${t.resultMessage || `${t.totalCount} 条记录`}`)
      else if (t.status === 3) ElMessage.error(`匹配任务失败：${t.errorMessage || '未知错误'}`)
      else ElMessage.info('匹配任务已取消')
    }

    function syncToTaskStore(t: MatchingTask) {
      const taskStore = useTaskStore()
      taskStore.addTask({ id: t.taskId, type: 'matching', refId: 0, refName: `匹配任务 ${t.taskId.slice(-6)}` })
      taskStore.updateTask(t.taskId, {
        status: taskStoreStatus(t),
        progress: t.progress,
        message: taskStoreMessage(t),
      })
    }

    /** 注册任务跟踪（提交任务后调用），并同步到铃铛 taskStore */
    async function track(taskId: string) {
      const created = upsert({ taskId, status: 1, progress: 0, totalCount: 0, processedCount: 0 } as MatchingTask)
      syncToTaskStore(created)
      await refreshOne(taskId)
    }

    /** 查询单个任务并更新本地状态；终态时同步铃铛并通知（in-flight 去重 + 不覆盖本地终态） */
    async function refreshOne(taskId: string): Promise<MatchingTask | null> {
      if (inFlight.has(taskId)) return null
      inFlight.add(taskId)
      try {
        const res = await getMatchingTaskStatus(taskId)
        const t = res.data
        if (!t) return null
        const prev = byTaskId(taskId)
        // 本地已终态（如用户刚取消）不覆盖
        if (prev && TERMINAL.has(prev.status) && prev.status !== t.status) return null
        upsert(t)
        syncToTaskStore(t)
        if (TERMINAL.has(t.status) && prev && (prev.status === 0 || prev.status === 1)) {
          notifyDone(t)
        }
        return t
      } catch {
        return null
      } finally {
        inFlight.delete(taskId)
      }
    }

    /** 轮询所有进行中任务（并行） */
    async function pollAll() {
      const running = tasks.value.filter(t => t.status === 0 || t.status === 1)
      await Promise.all(running.map(t => refreshOne(t.taskId)))
    }

    /** 全局轮询调度：仅当存在进行中任务时每 3s 轮询一次 */
    function startWatcher() {
      if (timer !== null) return
      timer = window.setInterval(() => {
        if (runningCount.value === 0) return
        pollAll()
      }, 3000)
    }

    function stopWatcher() {
      if (timer !== null) {
        clearInterval(timer)
        timer = null
      }
    }

    /** 刷新后恢复：从后端拉取进行中任务合并进本地（与 localStorage 快照互补） */
    async function refresh() {
      try {
        const res = await pageMatchingTasks({ current: 1, size: 50 })
        const records: MatchingTask[] = (res.data as any)?.records || []
        for (const t of records) {
          if (t.status === 0 || t.status === 1) {
            const existing = byTaskId(t.taskId)
            // 不覆盖本地已终态的任务（如用户已取消）
            if (existing && TERMINAL.has(existing.status)) continue
            upsert(t)
            syncToTaskStore(t)
          }
        }
      } catch {
        /* 后端不可用时不阻塞（本地快照仍可用） */
      }
    }

    /** 取消任务：调后端 API + 本地置 CANCELLED */
    async function cancel(taskId: string) {
      try {
        await cancelMatchingTask(taskId)
        const t = byTaskId(taskId)
        if (t) {
          t.status = 4
          syncToTaskStore(t)
        }
        ElMessage.success('任务已取消')
      } catch (e: any) {
        ElMessage.error(e.message || '取消失败')
      }
    }

    return {
      tasks,
      runningCount,
      byTaskId,
      track,
      refreshOne,
      pollAll,
      startWatcher,
      stopWatcher,
      refresh,
      cancel,
    }
  },
  {
    persist: {
      key: 'matching-tasks',
      storage: localStorage,
    },
  }
)
