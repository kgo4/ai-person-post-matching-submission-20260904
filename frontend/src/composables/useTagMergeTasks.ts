import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import {
  cancelMerge,
  executeMerge,
  listPendingMerges,
  listMergeNotifications,
  scheduleMerge,
} from '@/api/tag-governance'
import type { TagMergeNotification } from '@/api/tag-governance'

export function useTagMergeTasks() {
  const mergingTags = ref(false)
  const schedulingMerge = ref(false)
  const mergeForm = reactive({
    threshold: 0.9,
    scheduledTime: '' as string | Date,
  })
  const pendingMerges = ref<Array<{ taskId: string; scheduledTime: string; threshold: number }>>([])
  const recentMergeNotifications = ref<TagMergeNotification[]>([])
  const notifiedTaskIds = new Set<string>()
  const lastMergeResult = ref<{
    foundPairs: number
    mergedCount: number
    totalTags: number
    tagsWithVector: number
    details: Array<{ mergeTag: string; keepTag: string; similarity: number }>
  } | null>(null)
  const mergeResultDialogVisible = ref(false)

  async function handleExecuteMerge() {
    if (mergeForm.threshold < 0.5 || mergeForm.threshold > 1) {
      ElMessage.warning('阈值应在 0.5 ~ 1 之间')
      return
    }
    try {
      await ElMessageBox.confirm(
        `将立即归并相似度 ≥ ${Math.round(mergeForm.threshold * 100)}% 的标签，是否继续？`,
        '确认立即归并',
        { confirmButtonText: '立即执行', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
    mergingTags.value = true
    try {
      const res = await executeMerge(mergeForm.threshold)
      lastMergeResult.value = res.data
      mergeResultDialogVisible.value = true
      ElMessage.success(`归并完成：发现 ${res.data.foundPairs} 对，成功归并 ${res.data.mergedCount} 对`)
    } catch {
      ElMessage.error('归并执行失败')
    } finally {
      mergingTags.value = false
    }
  }

  async function handleScheduleMerge() {
    if (!mergeForm.scheduledTime) {
      ElMessage.warning('请选择执行时间')
      return
    }
    if (mergeForm.threshold < 0.5 || mergeForm.threshold > 1) {
      ElMessage.warning('阈值应在 0.5 ~ 1 之间')
      return
    }
    const selectedTime = new Date(mergeForm.scheduledTime)
    if (Number.isNaN(selectedTime.getTime()) || selectedTime.getTime() <= Date.now()) {
      ElMessage.warning('执行时间必须晚于当前时间')
      return
    }
    schedulingMerge.value = true
    try {
      const scheduledTime = toLocalDateTime(mergeForm.scheduledTime)
      await scheduleMerge(mergeForm.threshold, scheduledTime)
      ElMessage.success(`已设定定时归并：${scheduledTime.replace('T', ' ')}`)
      mergeForm.scheduledTime = ''
      await loadPendingMerges()
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || '定时设定失败')
    } finally {
      schedulingMerge.value = false
    }
  }

  async function handleCancelMerge(taskId: string) {
    try {
      await cancelMerge(taskId)
      ElMessage.success('已取消定时任务')
      await loadPendingMerges()
    } catch {
      ElMessage.error('取消失败')
    }
  }

  async function loadPendingMerges() {
    try {
      const res = await listPendingMerges()
      pendingMerges.value = res.data || []
    } catch {
      // ignore
    }
  }

  async function loadMergeNotifications(notify = true) {
    try {
      const res = await listMergeNotifications()
      recentMergeNotifications.value = res.data || []
      if (!notify) return
      for (const task of recentMergeNotifications.value) {
        if (notifiedTaskIds.has(task.taskId)) continue
        notifiedTaskIds.add(task.taskId)
        const succeeded = task.status === 'COMPLETED'
        ElNotification({
          title: succeeded ? '标签定时合并完成' : '标签定时合并失败',
          message: succeeded
            ? `任务 ${task.taskId} 已完成，请在标签健康页查看结果。`
            : `任务 ${task.taskId} 执行失败：${task.errorMessage || '请查看任务结果。'}`,
          type: succeeded ? 'success' : 'error',
          duration: 6000,
        })
      }
    } catch {
      // Notification polling must not interrupt governance operations.
    }
  }

  return {
    mergingTags,
    schedulingMerge,
    mergeForm,
    pendingMerges,
    recentMergeNotifications,
    lastMergeResult,
    mergeResultDialogVisible,
    handleExecuteMerge,
    handleScheduleMerge,
    handleCancelMerge,
    loadPendingMerges,
    loadMergeNotifications,
  }
}

function toLocalDateTime(value: string | Date): string {
  if (typeof value === 'string') {
    return value.slice(0, 19)
  }

  const pad = (part: number) => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`
    + `T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
}
