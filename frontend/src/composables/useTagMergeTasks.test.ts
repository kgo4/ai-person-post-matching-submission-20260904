import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElNotification: vi.fn(),
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() },
}))

const mockExecuteMerge = vi.fn()
const mockScheduleMerge = vi.fn()
const mockCancelMerge = vi.fn()
const mockListPendingMerges = vi.fn()
const mockListMergeNotifications = vi.fn()

vi.mock('@/api/tag-governance', () => ({
  executeMerge: (...args: any[]) => mockExecuteMerge(...args),
  scheduleMerge: (...args: any[]) => mockScheduleMerge(...args),
  cancelMerge: (...args: any[]) => mockCancelMerge(...args),
  listPendingMerges: (...args: any[]) => mockListPendingMerges(...args),
  listMergeNotifications: (...args: any[]) => mockListMergeNotifications(...args),
}))

import { useTagMergeTasks } from './useTagMergeTasks'

const mockPendingTasks = [
  { taskId: 'task-1', scheduledTime: '2026-07-20T10:00:00', threshold: 0.9 },
  { taskId: 'task-2', scheduledTime: '2026-07-21T14:00:00', threshold: 0.85 },
]

describe('useTagMergeTasks', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-13T10:00:00+08:00'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('loadPendingMerges - success', () => {
    it('should load pending merge tasks', async () => {
      mockListPendingMerges.mockResolvedValue({ data: mockPendingTasks })
      const { pendingMerges, loadPendingMerges } = useTagMergeTasks()

      await loadPendingMerges()

      expect(mockListPendingMerges).toHaveBeenCalledOnce()
      expect(pendingMerges.value).toEqual(mockPendingTasks)
    })
  })

  describe('handleScheduleMerge', () => {
    it('rejects a time at or before now without scheduling a merge', async () => {
      const { mergeForm, handleScheduleMerge } = useTagMergeTasks()
      mergeForm.scheduledTime = new Date('2026-08-13T10:00:00+08:00')

      await handleScheduleMerge()

      expect(mockScheduleMerge).not.toHaveBeenCalled()
    })

    it('submits the selected local time without converting it to UTC', async () => {
      mockScheduleMerge.mockResolvedValue({ data: { taskId: 'task-1' } })
      mockListPendingMerges.mockResolvedValue({ data: [] })
      const { mergeForm, handleScheduleMerge } = useTagMergeTasks()
      mergeForm.scheduledTime = new Date('2026-08-13T17:30:00+08:00')

      await handleScheduleMerge()

      expect(mockScheduleMerge).toHaveBeenCalledWith(0.9, '2026-08-13T17:30:00')
    })
  })

  describe('loadPendingMerges - failure', () => {
    it('should silently handle error', async () => {
      mockListPendingMerges.mockRejectedValue(new Error('Network error'))
      const { pendingMerges, loadPendingMerges } = useTagMergeTasks()

      await loadPendingMerges()

      expect(pendingMerges.value).toEqual([])
    })
  })

  describe('loadMergeNotifications', () => {
    it('keeps the current users completed and failed merge results available to the page', async () => {
      const notifications = [{ taskId: 'TAG_MERGE_1', status: 'COMPLETED', threshold: 0.9, scheduledTime: '2026-08-13T10:05:00' }]
      mockListMergeNotifications.mockResolvedValue({ data: notifications })
      const { recentMergeNotifications, loadMergeNotifications } = useTagMergeTasks()

      await loadMergeNotifications(false)

      expect(recentMergeNotifications.value).toEqual(notifications)
    })
  })

  describe('refresh', () => {
    it('should reload pending merges on refresh', async () => {
      mockListPendingMerges.mockResolvedValueOnce({ data: mockPendingTasks })
      mockListPendingMerges.mockResolvedValueOnce({ data: [mockPendingTasks[0]] })

      const { pendingMerges, loadPendingMerges } = useTagMergeTasks()

      await loadPendingMerges()
      expect(pendingMerges.value).toEqual(mockPendingTasks)

      await loadPendingMerges()
      expect(pendingMerges.value).toEqual([mockPendingTasks[0]])
      expect(mockListPendingMerges).toHaveBeenCalledTimes(2)
    })
  })
})
