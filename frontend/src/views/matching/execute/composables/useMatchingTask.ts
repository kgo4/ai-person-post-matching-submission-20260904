import { getCurrentInstance, onUnmounted, ref } from 'vue'
import type { MatchingTask } from '@/api/matching/types'

export interface UseMatchingTaskOptions {
  /** 任务状态查询函数（注入以便测试） */
  getStatus: (taskId: string) => Promise<{ data: MatchingTask }>
  onDone?: (task: MatchingTask) => void
  onError?: (task: MatchingTask) => void
  onTimeout?: () => void
  /** 最大轮询次数，默认 120 */
  maxAttempts?: number
}

/**
 * 异步匹配任务轮询。
 * 由调用方在提交任务后 start(taskId)；status 变化时自动调度下一次轮询；
 * 间隔按指数退避（10 次翻倍，上限 10s），直到完成(2)/失败(3)/超时。
 */
export function useMatchingTask(options: UseMatchingTaskOptions) {
  const { getStatus, onDone, onError, onTimeout, maxAttempts = 120 } = options
  const taskId = ref<string | null>(null)
  const status = ref<MatchingTask | null>(null)
  const running = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null
  let attempts = 0
  // 代际号：start/stop/reset 都会递增，使在途 poll 的过期响应失效（防异步复活/竞态）
  let epoch = 0

  function clearTimer() {
    if (timer !== null) { clearTimeout(timer); timer = null }
  }
  function schedule(delay: number) { timer = setTimeout(poll, delay) }

  async function poll() {
    if (!taskId.value) return
    const myEpoch = epoch
    try {
      const res = await getStatus(taskId.value)
      if (myEpoch !== epoch) return // 已 stop/reset/重新 start，丢弃过期响应
      status.value = res.data
      if (res.data.status === 2) { stop(); onDone?.(res.data); return }
      if (res.data.status === 3) { stop(); onError?.(res.data); return }
      scheduleNext()
    } catch {
      if (myEpoch !== epoch) return
      scheduleNext()
    }
  }

  function scheduleNext() {
    attempts += 1
    if (attempts >= maxAttempts) {
      stop()
      onTimeout?.()
      return
    }
    schedule(Math.min(1000 * 2 ** Math.floor(attempts / 10), 10_000))
  }

  function start(id: string) {
    clearTimer() // 清掉旧 timer，避免重复 start 并发轮询
    epoch += 1
    taskId.value = id
    status.value = null
    attempts = 0
    running.value = true
    schedule(1000)
  }
  function stop() {
    clearTimer()
    epoch += 1
    running.value = false
  }
  function reset() { stop(); taskId.value = null; status.value = null; attempts = 0 }

  // 仅在组件 setup 上下文中注册自动清理；非组件环境（如单元测试直接调用）跳过
  if (getCurrentInstance()) {
    onUnmounted(stop)
  }

  return { taskId, status, running, start, stop, reset }
}
