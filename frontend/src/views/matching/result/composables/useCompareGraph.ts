import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getReport } from '@/api'
import type { MatchingRecord } from '@/api'
import { parseMatchingReportPayload } from '../gap-diagnosis'

export function useCompareGraph() {
  const graphDialogVisible = ref(false)
  const graphData = ref<any>(null)
  const graphLoading = ref(false)

  async function openCompareGraph(row: MatchingRecord) {
    graphDialogVisible.value = true
    graphLoading.value = true
    try {
      const res = await getReport(row.id)
      const report = parseMatchingReportPayload(res.data)
      if (report.abilityDetails.length) {
        graphData.value = report.abilityDetails.map((item: any) => ({
          dimension: item.tagName || item.abilityName || '',
          employeeScore: Number(item.actualLevel || 0) * 20,
          postRequirement: Number(item.requiredLevel || 0) * 20,
        }))
      } else {
        graphData.value = []
      }
    } catch (error: any) {
      ElMessage.error(error.message || '加载对比图失败')
      graphData.value = []
    } finally {
      graphLoading.value = false
    }
  }

  return { graphDialogVisible, graphData, graphLoading, openCompareGraph }
}
