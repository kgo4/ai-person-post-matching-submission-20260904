import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getRecord, getReport, getAiReport, getMatchDiagnosis, listApprovalFlows } from '@/api'
import type { MatchingRecord, MatchingApprovalFlow, MatchDiagnosisResult, QuantitativeReportData, AiReportData } from '@/api'
import { parseReport } from '@/views/matching/detail/utils'

export function useMatchDetail(id: number) {
  const loading = ref(false)
  const matchResult = ref<MatchingRecord | null>(null)
  const quantitativeReport = ref<QuantitativeReportData | null>(null)
  const aiReport = ref<AiReportData | null>(null)
  const approvalFlows = ref<MatchingApprovalFlow[]>([])
  const diagnosisLoading = ref(false)
  const diagnosisResult = ref<MatchDiagnosisResult | null>(null)

  async function loadRecord() {
    loading.value = true
    try {
      const res = await getRecord(id)
      matchResult.value = res.data
    } catch (error: unknown) {
      const msg = error instanceof Error ? error.message : '加载匹配详情失败'
      ElMessage.error(msg)
    } finally {
      loading.value = false
    }
  }

  async function loadReports() {
    try {
      const [reportRes, aiRes] = await Promise.allSettled([getReport(id), getAiReport(id)])
      if (reportRes.status === 'fulfilled' && reportRes.value.data)
        quantitativeReport.value = parseReport(reportRes.value.data)
      if (aiRes.status === 'fulfilled' && aiRes.value.data)
        aiReport.value = parseReport(aiRes.value.data)
    } catch {
      quantitativeReport.value = null
      aiReport.value = null
    }
  }

  async function loadApprovals() {
    try {
      const res = await listApprovalFlows(id)
      approvalFlows.value = res.data || []
    } catch {
      approvalFlows.value = []
    }
  }

  async function loadDiagnosis() {
    diagnosisLoading.value = true
    try {
      const res = await getMatchDiagnosis(id)
      diagnosisResult.value = res.data || null
    } catch (error: unknown) {
      diagnosisResult.value = null
      const msg = error instanceof Error ? error.message : '闭环诊断加载失败'
      ElMessage.warning(msg)
    } finally {
      diagnosisLoading.value = false
    }
  }

  async function loadAll() {
    await Promise.all([loadRecord(), loadReports(), loadApprovals(), loadDiagnosis()])
  }

  return {
    loading,
    matchResult,
    quantitativeReport,
    aiReport,
    approvalFlows,
    diagnosisLoading,
    diagnosisResult,
    loadRecord,
    loadReports,
    loadApprovals,
    loadDiagnosis,
    loadAll,
  }
}
