import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { nextTick } from 'vue'
import { useMatchingTask } from './useMatchingTask'
import type { MatchingTask } from '@/api/matching/types'

function makeTask(status: number, progress = 0): MatchingTask {
  return {
    id: 1, taskId: 't1', status, progress, totalCount: 2, processedCount: 1,
    createdTime: '', updatedTime: '',
  }
}

describe('useMatchingTask', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('轮询直到完成并触发 onDone', async () => {
    const getStatus = vi.fn()
      .mockResolvedValueOnce({ data: makeTask(1, 30) })
      .mockResolvedValueOnce({ data: makeTask(1, 70) })
      .mockResolvedValueOnce({ data: makeTask(2, 100) })
    const onDone = vi.fn()
    const { start, running, status } = useMatchingTask({ getStatus, onDone })

    start('t1')
    expect(running.value).toBe(true)
    await vi.advanceTimersByTimeAsync(1000)   // 第1次轮询
    await nextTick()
    expect(status.value?.progress).toBe(30)
    await vi.advanceTimersByTimeAsync(1000)   // 第2次轮询
    await nextTick()
    expect(status.value?.progress).toBe(70)
    await vi.advanceTimersByTimeAsync(1000)   // 第3次轮询 → 完成
    await nextTick()
    expect(onDone).toHaveBeenCalledWith(expect.objectContaining({ status: 2 }))
    expect(running.value).toBe(false)
    expect(getStatus).toHaveBeenCalledTimes(3)
  })

  it('失败时触发 onError 并停止', async () => {
    const getStatus = vi.fn().mockResolvedValue({ data: makeTask(3, 40) })
    const onError = vi.fn()
    const { start, running } = useMatchingTask({ getStatus, onError })
    start('t1')
    await vi.advanceTimersByTimeAsync(1000)
    await nextTick()
    expect(onError).toHaveBeenCalledWith(expect.objectContaining({ status: 3 }))
    expect(running.value).toBe(false)
    expect(getStatus).toHaveBeenCalledTimes(1)
  })

  it('超过 maxAttempts 触发 onTimeout', async () => {
    const getStatus = vi.fn().mockResolvedValue({ data: makeTask(1, 10) })
    const onTimeout = vi.fn()
    const { start, running } = useMatchingTask({ getStatus, onTimeout, maxAttempts: 3 })
    start('t1')
    await vi.advanceTimersByTimeAsync(1000 * 60)
    await nextTick()
    expect(onTimeout).toHaveBeenCalledTimes(1)
    expect(getStatus).toHaveBeenCalledTimes(3)
    expect(running.value).toBe(false)
  })

  it('轮询间隔按指数退避（每10次翻倍）', async () => {
    const getStatus = vi.fn().mockResolvedValue({ data: makeTask(1, 10) })
    const { start } = useMatchingTask({ getStatus, maxAttempts: 30 })
    start('t1')
    // 前 10 次轮询间隔 1000ms：第 10 次在 10000ms 触发；第 11 次应在 2000ms 后（12000ms）
    await vi.advanceTimersByTimeAsync(11_999)
    expect(getStatus).toHaveBeenCalledTimes(10)
    await vi.advanceTimersByTimeAsync(1)
    expect(getStatus).toHaveBeenCalledTimes(11)
  })

  it('getStatus 抛错时继续轮询，不触发 onError/onTimeout', async () => {
    const getStatus = vi.fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce({ data: makeTask(2, 100) })
    const onDone = vi.fn()
    const onError = vi.fn()
    const onTimeout = vi.fn()
    const { start, running } = useMatchingTask({ getStatus, onDone, onError, onTimeout })
    start('t1')
    await vi.advanceTimersByTimeAsync(1000)   // 第1次：抛错 → scheduleNext
    await nextTick()
    await vi.advanceTimersByTimeAsync(1000)   // 第2次：完成
    await nextTick()
    expect(onDone).toHaveBeenCalledTimes(1)
    expect(onError).not.toHaveBeenCalled()
    expect(onTimeout).not.toHaveBeenCalled()
    expect(running.value).toBe(false)
  })

  it('重复 start 不会并发轮询（旧 timer 被清）', async () => {
    const getStatus = vi.fn().mockResolvedValue({ data: makeTask(1, 10) })
    const { start, running } = useMatchingTask({ getStatus })
    start('t1')
    start('t1')
    await vi.advanceTimersByTimeAsync(1000)
    await nextTick()
    expect(getStatus).toHaveBeenCalledTimes(1)
    expect(running.value).toBe(true)
  })

  it('stop 后旧响应返回不会覆盖新任务（epoch 守卫）', async () => {
    let resolveOld!: (v: { data: MatchingTask }) => void
    const getStatus = vi.fn()
      .mockImplementationOnce(() => new Promise((r) => { resolveOld = r })) // 旧任务：挂起
      .mockResolvedValue({ data: makeTask(1, 10) })                          // 新任务及后续
    const onDone = vi.fn()
    const { start, stop, status } = useMatchingTask({ getStatus, onDone })
    start('t1')                    // 发出旧 poll（挂起）
    await vi.advanceTimersByTimeAsync(1000)
    stop()                         // 停止（epoch 递增）
    start('t1')                    // 重新开始（epoch 递增）
    await vi.advanceTimersByTimeAsync(1000)
    expect(getStatus).toHaveBeenCalledTimes(2)
    resolveOld({ data: makeTask(2, 100) })   // 旧响应此时才返回
    await nextTick()
    expect(onDone).not.toHaveBeenCalled()    // 过期响应被丢弃
    await vi.advanceTimersByTimeAsync(1000)
    await nextTick()
    expect(getStatus).toHaveBeenCalledTimes(3) // 新任务轮询链未被打断
    expect(status.value).not.toBeNull()
  })

  it('reset 清空状态', async () => {
    const getStatus = vi.fn().mockResolvedValue({ data: makeTask(1, 10) })
    const { start, reset, taskId, status, running } = useMatchingTask({ getStatus })
    start('t1')
    expect(taskId.value).toBe('t1')
    reset()
    expect(taskId.value).toBeNull()
    expect(status.value).toBeNull()
    expect(running.value).toBe(false)
    await vi.advanceTimersByTimeAsync(5000)
    await nextTick()
    expect(getStatus).toHaveBeenCalledTimes(0) // reset 后不再轮询
  })
})
