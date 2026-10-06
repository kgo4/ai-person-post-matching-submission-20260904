/**
 * @vitest-environment happy-dom
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { nextTick } from 'vue'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stripSfcComments } from '@/utils/sfc-source'

const currentDir = dirname(fileURLToPath(import.meta.url))

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
import { useUserStore } from './user'

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
    // 2026-09-04：匹配任务已按角色收口为「仅 MATCHING:READ（HR）」——
    // 其他角色既不拉取也不入铃铛。测试以 HR 身份运行，验证正常链路。
    const userStore = useUserStore()
    userStore.permissions = ['MATCHING:READ']
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

  it('非匹配运营角色（无 MATCHING:READ）不会把匹配任务同步进铃铛', async () => {
    // 回归：此前每个登录角色都会拉取全量匹配任务并同步到顶栏，
    // 表现为「匹配完成通知所有角色都收到」。
    useUserStore().permissions = []
    api.getMatchingTaskStatus.mockResolvedValue({ data: makeTask('t1', 1, 40) })

    const store = useMatchingTaskStore()
    const taskStore = useTaskStore()
    await store.track('t1')

    expect(store.tasks.length).toBe(1)
    expect(taskStore.tasks).toHaveLength(0)
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

  /* ============ 跨身份残留（2026-09-04 二次收口） ============
   * task store 持久化在 localStorage['tasks']，登出不清 ——
   * 上一个账号（HR）的匹配任务会在下一个身份（岗位体系管理员）登录时被恢复。
   * layout 挂载时调用 retainVisible 裁掉，这里锁住 store 侧的行为。 */
  it('retainVisible 裁掉当前身份无权查看的任务（跨身份残留兜底）', () => {
    const taskStore = useTaskStore()
    taskStore.addTask({ id: 'mt-1', type: 'matching', refId: 0, refName: '匹配任务 9f3a2c' })
    taskStore.updateTask('mt-1', { status: 'completed', message: '匹配完成，共处理 12 条记录' })
    taskStore.addTask({ id: 'pms-1', type: 'pms-analysis', refId: 7 })
    // 未登记类型 = 本人任务，任何身份都应保留，用于对照「登记类型才裁」
    taskStore.addTask({ id: 'va-1', type: 'video-analysis', refId: 9 })

    // 岗位体系管理员：只有 POST:*
    taskStore.retainVisible(['POST:READ', 'POST:MANAGE'])

    // matching 需 MATCHING:READ、pms-analysis 需 ASSESSMENT:MANAGE，两者都该被裁掉；
    // video-analysis 未登记（本人任务）必须保留。
    expect(taskStore.tasks.map(t => t.id)).toEqual(['va-1'])
    // 本人任务不受影响，且匹配任务不再计入角标
    expect(taskStore.completedTasks).toHaveLength(0)
  })

  it('retainVisible 对匹配运营（HR）不做裁剪', () => {
    const taskStore = useTaskStore()
    taskStore.addTask({ id: 'mt-1', type: 'matching', refId: 0 })

    taskStore.retainVisible(['MATCHING:READ'])

    expect(taskStore.tasks.map(t => t.id)).toEqual(['mt-1'])
  })

  /* 权限码只能有一处来源（task-visibility.ts）；本文件若再写死一次，
   * 就会出现「后端接口一个码、前端写入侧一个码、装载侧又一个码」的漂移。
   * 断言前必须剥注释 —— 否则注释里那句「不再写死 hasPermission(...)」
   * 会把「不得出现该字符串」的断言自己撞红（本项目踩过三次）。 */
  it('匹配任务可见性规则只有一处来源，不在本文件写死权限码', () => {
    const source = stripSfcComments(readFileSync(join(currentDir, 'matching-tasks.ts'), 'utf8'))
    expect(source).not.toContain("hasPermission('MATCHING:READ')")
    expect(source).not.toContain('hasPermission("MATCHING:READ")')
    expect(source).toContain("from './task-visibility'")
  })
})
