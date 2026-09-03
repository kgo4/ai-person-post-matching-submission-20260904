import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() },
}))

const mockGetTagTree = vi.fn()
const mockGetTagTreeByCategory = vi.fn()
const mockGetTagById = vi.fn()
const mockSaveTag = vi.fn()
const mockUpdateTag = vi.fn()
const mockDeleteTag = vi.fn()
const mockBatchGenerateTagVectors = vi.fn()

vi.mock('@/api', () => ({
  getTagTree: (...args: any[]) => mockGetTagTree(...args),
  getTagTreeByCategory: (...args: any[]) => mockGetTagTreeByCategory(...args),
  getTagById: (...args: any[]) => mockGetTagById(...args),
  saveTag: (...args: any[]) => mockSaveTag(...args),
  updateTag: (...args: any[]) => mockUpdateTag(...args),
  deleteTag: (...args: any[]) => mockDeleteTag(...args),
  batchGenerateTagVectors: (...args: any[]) => mockBatchGenerateTagVectors(...args),
}))

import { useTagDirectory } from './useTagDirectory'

const mockTreeData = [
  {
    id: 1,
    tagCode: 'TECH_ROOT',
    tagName: '技术能力',
    tagCategory: 'TECHNICAL',
    tagLevel: 0,
    children: [
      {
        id: 2,
        tagCode: 'JAVA',
        tagName: 'Java',
        tagCategory: 'TECHNICAL',
        tagLevel: 1,
        children: [],
      },
    ],
  },
  {
    id: 3,
    tagCode: 'SOFT_ROOT',
    tagName: '软技能',
    tagCategory: 'SOFT',
    tagLevel: 0,
    children: [],
  },
]

describe('useTagDirectory', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('loadTree - success', () => {
    it('should load tree data and compute category stats', async () => {
      mockGetTagTree.mockResolvedValue({ data: mockTreeData })
      const { treeData, categoryStats, totalTagCount, loadTree, loading } = useTagDirectory()

      expect(loading.value).toBe(false)
      await loadTree()

      expect(mockGetTagTree).toHaveBeenCalledOnce()
      expect(treeData.value).toEqual(mockTreeData)
      expect(categoryStats.value).toEqual({ TECHNICAL: 1, SOFT: 0, BUSINESS: 0 })
      expect(totalTagCount.value).toBe(3)
      expect(loading.value).toBe(false)
    })

    it('should load tree by category', async () => {
      const categoryTree = [mockTreeData[1]]
      mockGetTagTreeByCategory.mockResolvedValue({ data: categoryTree })
      const { treeData, loadTree } = useTagDirectory()

      await loadTree('SOFT')

      expect(mockGetTagTreeByCategory).toHaveBeenCalledWith('SOFT')
      expect(treeData.value).toEqual(categoryTree)
    })
  })

  describe('loadTree - failure', () => {
    it('should set empty treeData on failure', async () => {
      mockGetTagTree.mockRejectedValue(new Error('Network error'))
      const { treeData, loadTree } = useTagDirectory()

      await loadTree()

      expect(treeData.value).toEqual([])
    })
  })

  describe('refresh', () => {
    it('should reload tree data on subsequent calls', async () => {
      mockGetTagTree.mockResolvedValueOnce({ data: mockTreeData })
      const updatedTree = [mockTreeData[1]]
      mockGetTagTree.mockResolvedValueOnce({ data: updatedTree })

      const { treeData, loadTree } = useTagDirectory()

      await loadTree()
      expect(treeData.value).toEqual(mockTreeData)

      await loadTree()
      expect(treeData.value).toEqual(updatedTree)
      expect(mockGetTagTree).toHaveBeenCalledTimes(2)
    })
  })
})
