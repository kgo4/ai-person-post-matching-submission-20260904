import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() },
}))

const mockGetUsageStats = vi.fn()
const mockComputeUsageStats = vi.fn()
const mockPageRelations = vi.fn()
const mockDiscoverRelations = vi.fn()
const mockCreateRelation = vi.fn()
const mockApproveRelation = vi.fn()
const mockRejectRelation = vi.fn()

vi.mock('@/api/tag-governance', () => ({
  getUsageStats: (...args: any[]) => mockGetUsageStats(...args),
  computeUsageStats: (...args: any[]) => mockComputeUsageStats(...args),
  pageRelations: (...args: any[]) => mockPageRelations(...args),
  discoverRelations: (...args: any[]) => mockDiscoverRelations(...args),
  createRelation: (...args: any[]) => mockCreateRelation(...args),
  approveRelation: (...args: any[]) => mockApproveRelation(...args),
  rejectRelation: (...args: any[]) => mockRejectRelation(...args),
}))

import { useTagRelations } from './useTagRelations'

const mockUsageStats = [
  {
    id: 1,
    tagId: 1,
    tagName: 'Java',
    tagCategory: 'TECHNICAL',
    usedByPostCount: 20,
    usedByEmpCount: 15,
    heatScore: 85.5,
    statDate: '2026-01-01',
  },
]

const mockRelations = [
  {
    id: 1,
    sourceTagId: 1,
    targetTagId: 2,
    relationType: 'SIMILAR',
    similarityScore: 0.75,
    status: 'PENDING',
    evidenceSource: '',
    remark: '',
    createdTime: '2026-01-01',
    sourceTagName: 'Java',
    targetTagName: 'Spring',
  },
]

describe('useTagRelations', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  describe('loadStats - success', () => {
    it('should load usage stats', async () => {
      mockGetUsageStats.mockResolvedValue({ data: mockUsageStats })
      const { usageStats, statsLoading, loadStats } = useTagRelations()

      await loadStats()

      expect(mockGetUsageStats).toHaveBeenCalledWith(50)
      expect(usageStats.value).toEqual(mockUsageStats)
      expect(statsLoading.value).toBe(false)
    })
  })

  describe('loadStats - empty snapshot', () => {
    it('should initialize statistics and reload when no snapshot exists', async () => {
      mockGetUsageStats.mockResolvedValueOnce({ data: [] })
      mockComputeUsageStats.mockResolvedValue({ data: undefined })
      mockGetUsageStats.mockResolvedValueOnce({ data: mockUsageStats })
      const { usageStats, loadStats } = useTagRelations()

      await loadStats()

      expect(mockComputeUsageStats).toHaveBeenCalledTimes(1)
      expect(mockGetUsageStats).toHaveBeenNthCalledWith(1, 50)
      expect(mockGetUsageStats).toHaveBeenNthCalledWith(2, 50)
      expect(usageStats.value).toEqual(mockUsageStats)
    })
  })

  describe('loadStats - failure', () => {
    it('should handle error', async () => {
      mockGetUsageStats.mockRejectedValue(new Error('Server error'))
      const { usageStats, loadStats } = useTagRelations()

      await loadStats()

      expect(usageStats.value).toEqual([])
    })
  })

  describe('loadRelations - success', () => {
    it('should load relations', async () => {
      mockPageRelations.mockResolvedValue({ data: { records: mockRelations } })
      const { relations, relationsLoading, loadRelations } = useTagRelations()

      await loadRelations()

      expect(mockPageRelations).toHaveBeenCalledWith({ pageNum: 1, pageSize: 100 })
      expect(relations.value).toEqual(mockRelations)
      expect(relationsLoading.value).toBe(false)
    })
  })

  describe('loadRelations - failure', () => {
    it('should handle error', async () => {
      mockPageRelations.mockRejectedValue(new Error('Server error'))
      const { relations, loadRelations } = useTagRelations()

      await loadRelations()

      expect(relations.value).toEqual([])
    })
  })

  describe('refresh', () => {
    it('should reload relations on refresh', async () => {
      mockPageRelations.mockResolvedValueOnce({ data: { records: mockRelations } })
      mockPageRelations.mockResolvedValueOnce({ data: { records: [] } })

      const { relations, loadRelations } = useTagRelations()

      await loadRelations()
      expect(relations.value).toEqual(mockRelations)

      await loadRelations()
      expect(relations.value).toEqual([])
      expect(mockPageRelations).toHaveBeenCalledTimes(2)
    })
  })
})
