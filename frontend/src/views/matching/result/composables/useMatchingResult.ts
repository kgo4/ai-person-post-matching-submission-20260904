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

/** 终面结果标签（表格「终面」列；与指标卡同一口径：只看 interviewResult） */
export function interviewResultText(result?: number | null): string {
  if (result === 1) return '已通过'
  if (result === 2) return '未通过'
  if (result === 3) return '待定'
  return '未完成'
}

/** 终面结果标签配色（与文字一一对应，避免两处各写一套判断） */
export function interviewTagType(result?: number | null): 'success' | 'danger' | 'warning' | 'info' {
  if (result === 1) return 'success'
  if (result === 2) return 'danger'
  if (result === 3) return 'warning'
  return 'info'
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

  /**
   * 「已通过」= **视频终面通过**的人数。
   *
   * 【2026-09-04 口径修正】原先统计的是 `approvalStatus === 2`（**审批**通过），
   * 而 HR 在匹配结果页关心的是"面过了几个人"。审批通过只代表 HR 认可匹配结论，
   * 与候选人是否通过视频终面是两件独立的事 —— 混在一起会把"审批过了"读成"面试过了"。
   *
   * 后端在分页结果里回填 `interviewResult`（取最近一次**已完成**的终面）：
   * 1 通过 / 2 未通过 / 3 待定 / null 未完成。这里只数 1。
   */
  const interviewPassedCount = computed(
    () => tableData.value.filter((item) => item.interviewResult === 1).length,
  )
  /**
   * 「终面待处理」= 尚未完成终面的条数（未约面 / 待定）。
   *
   * 【2026-09-04 口径修正】原先是 `approvalStatus === 1 || === 0`（审批中/未发起）。
   * 但匹配结果不需要审批，审批字段长期停在 0 → 该卡片会恒等于全部条数，没有信息量。
   * 改为终面口径后与同排的「终面通过」构成有意义的一对：待处理 + 已通过。
   */
  const pendingCount = computed(
    () => tableData.value.filter((item) => item.interviewResult !== 1).length,
  )
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
    interviewPassedCount,
    pendingCount,
    strongMatchCount,
    loadData,
    handleSearch,
    handleSizeChange,
    handleCurrentChange,
    resetFilters,
  }
}
