/**
 * @vitest-environment happy-dom
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { nextTick } from 'vue'

const api = vi.hoisted(() => ({
  getMatchingTaskStatus: vi.fn(),
  pageMatchingTasks: vi.fn(),
  cancelMatchingTask: vi.fn(),
}))

vi.mock('@/api/matching', () => ({
  getMatchingTaskStatus: api.getMatchingTaskStatus,
  pageMatchingTasks: api.pageMatchingTasks,
  cancelMatchingTask: api.cancelMatchingTask,
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), info: vi.fn(), warning: vi.fn() },
}))

import { useMatchingTaskStore } from './matching-tasks'
import { useTaskStore } from './task'

function makeTask(taskId: string, status: number, progress = 0, extra: Record<string, unknown> = {}) {
  return {
    id: 1,
    taskId,
    status,
    progress,
    totalCount: 2,
    processedCount: progress >= 100 ? 2 : 0,
    createdTime: '2026-08-09T00:00:00',
    updatedTime: '2026-08-09T00:00:00',
    ...extra,
  }
}

describe('matching-tasks store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('track 注册任务并同步到通用 taskStore', async () => {
    api.getMatchingTaskStatus.mockResolvedValue({ data: makeTask('t1', 1, 40) })
    const store = useMatchingTaskStore()
    const taskStore = useTaskStore()

    await store.track('t1')

    expect(store.tasks.length).toBe(1)
    expect(store.tasks[0].progress).toBe(40)
    expect(taskStore.tasks.find(t => t.id === 't1')?.status).toBe('running')
  })

  it('pollAll 并行刷新多个 running 任务，终态停止再轮询', async () => {
    api.getMatchingTaskStatus
      .mockResolvedValueOnce({ data: makeTask('t1', 2, 100, { totalCount: 5, resultMessage: '完成' }) })
      .mockResolvedValueOnce({ data: makeTask('t2', 1, 10) })
    const store = useMatchingTaskStore()
    store.tasks.push(makeTask('t1', 1, 0) as any)
    store.tasks.push(makeTask('t2', 1, 0) as any)

    await store.pollAll()

    expect(api.getMatchingTaskStatus).toHaveBeenCalledTimes(2)
    expect(store.byTaskId('t1')?.status).toBe(2)   // 终态
    expect(store.byTaskId('t2')?.status).toBe(1)   // 仍 running

    // 再次 pollAll：t1 终态不再轮询
    await store.pollAll()
    expect(api.getMatchingTaskStatus).toHaveBeenCalledTimes(3) // 仅 t2
  })

  it('cancel 调用 API 并置本地状态为 CANCELLED(4)', async () => {
    api.cancelMatchingTask.mockResolvedValue({ data: null })
    const store = useMatchingTaskStore()
    store.tasks.push(makeTask('t1', 1, 50) as any)

    await store.cancel('t1')

    expect(api.cancelMatchingTask).toHaveBeenCalledWith('t1')
    expect(store.byTaskId('t1')?.status).toBe(4)
    expect(useTaskStore().tasks.find(t => t.id === 't1')?.status).toBe('failed')
  })

  it('refresh 从后端拉取 running 任务合并，不覆盖本地终态', async () => {
    api.pageMatchingTasks.mockResolvedValue({
      data: { records: [makeTask('t1', 1, 20), makeTask('t2', 2, 100)], total: 2 },
    })
    const store = useMatchingTaskStore()
    store.tasks.push(makeTask('t1', 2, 100) as any) // 本地已是终态

    await store.refresh()

    // t1 本地终态保留（后端 running 不覆盖），t2 终态不入 store（只合并 running）
    expect(store.byTaskId('t1')?.status).toBe(2)
    expect(store.byTaskId('t2')).toBeUndefined()
  })

  it('startWatcher 仅在存在 running 任务时轮询', async () => {
    api.getMatchingTaskStatus.mockResolvedValue({ data: makeTask('t1', 1, 30) })
    const store = useMatchingTaskStore()
    store.tasks.push(makeTask('t1', 1, 10) as any)

    store.startWatcher()
    await vi.advanceTimersByTimeAsync(3000)
    await nextTick()
    expect(api.getMatchingTaskStatus).toHaveBeenCalledTimes(1)

    // 任务完成后 watcher 不再调用
    store.tasks[0].status = 2
    await vi.advanceTimersByTimeAsync(6000)
    expect(api.getMatchingTaskStatus).toHaveBeenCalledTimes(1)

    store.stopWatcher()
  })
})
