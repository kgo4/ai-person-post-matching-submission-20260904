import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageRecords } from '@/api'
import type { MatchingRecord, PageResultVO } from '@/api'
import { getScoreColor, getMatchStatusText, getApprovalStatusText } from '@/views/matching/detail/utils'

export { getScoreColor, getMatchStatusText, getApprovalStatusText }

export function getScreeningLevelText(level: number) {
  const map: Record<number, { text: string }> = {
    1: { text: 'L1 淘汰' },
    2: { text: 'L2 通过' },
    3: { text: 'L3 AI' },
  }
  return map[level] || { text: '-' }
}

export function parseHardConditions(json: string) {
  try {
    const result = JSON.parse(json)
    return result.details || []
  } catch {
    return []
  }
}

export function useMatchingResult() {
  const loading = ref(false)
  const tableData = ref<MatchingRecord[]>([])
  const total = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(10)

  const filters = reactive({
    postId: '',
    empId: '',
    matchStatus: '',
  })

  const approvedCount = computed(() => tableData.value.filter((item) => item.approvalStatus === 2).length)
  const pendingCount = computed(() => tableData.value.filter((item) => item.approvalStatus === 1 || item.approvalStatus === 0).length)
  const strongMatchCount = computed(() => tableData.value.filter((item) => item.matchStatus === 1).length)

  async function loadData() {
    loading.value = true
    try {
      const params: any = {
        current: currentPage.value,
        size: pageSize.value,
      }
      if (filters.postId) params.postId = filters.postId
      if (filters.empId) params.empId = filters.empId
      if (filters.matchStatus) params.matchStatus = filters.matchStatus

      const res = await pageRecords(params)
      const pageResult: PageResultVO<MatchingRecord> = res.data as any
      tableData.value = pageResult.records || []
      total.value = pageResult.total || 0
    } catch (error: any) {
      ElMessage.error(error.message || '加载匹配结果失败')
    } finally {
      loading.value = false
    }
  }

  function handleSearch() {
    currentPage.value = 1
    loadData()
  }

  function handleSizeChange(size: number) {
    pageSize.value = size
    currentPage.value = 1
    loadData()
  }

  function handleCurrentChange(page: number) {
    currentPage.value = page
    loadData()
  }

  function resetFilters() {
    filters.postId = ''
    filters.empId = ''
    filters.matchStatus = ''
    handleSearch()
  }

  return {
    loading,
    tableData,
    total,
    currentPage,
    pageSize,
    filters,
    approvedCount,
    pendingCount,
    strongMatchCount,
    loadData,
    handleSearch,
    handleSizeChange,
    handleCurrentChange,
    resetFilters,
  }
}
