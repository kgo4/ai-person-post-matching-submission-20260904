import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getRecord, getReport, getAiReport, pageRecords, listApprovalFlows } from '@/api'
import type { MatchingRecord, MatchingApprovalFlow, QuantitativeReportData, AiReportData } from '@/api'

export const useMatchingStore = defineStore('matching', () => {
  const currentRecord = ref<MatchingRecord | null>(null)
  const currentRecordLoading = ref(false)

  const quantitativeReport = ref<QuantitativeReportData | null>(null)
  const aiReport = ref<AiReportData | null>(null)
  const approvalFlows = ref<MatchingApprovalFlow[]>([])

  const resultList = ref<MatchingRecord[]>([])
  const resultListTotal = ref(0)
  const resultListLoading = ref(false)

  // Detail
  async function fetchRecord(id: number) {
    currentRecordLoading.value = true
    try {
      const res = await getRecord(id)
      currentRecord.value = res.data
      return res.data
    } finally {
      currentRecordLoading.value = false
    }
  }

  async function fetchReport(id: number) {
    const res = await getReport(id)
    const data = res.data
    quantitativeReport.value = typeof data === 'string' ? JSON.parse(data) : data
    return quantitativeReport.value
  }

  async function fetchAiReport(id: number) {
    const res = await getAiReport(id)
    const data = res.data
    aiReport.value = typeof data === 'string' ? JSON.parse(data) : data
    return aiReport.value
  }

  async function fetchApprovalFlows(recordId: number) {
    const res = await listApprovalFlows(recordId)
    approvalFlows.value = res.data || []
    return approvalFlows.value
  }

  function clearDetail() {
    currentRecord.value = null
    quantitativeReport.value = null
    aiReport.value = null
    approvalFlows.value = []
  }

  // List
  async function fetchResultList(params: { current?: number; size?: number; postId?: number; empId?: number; matchStatus?: number }) {
    resultListLoading.value = true
    try {
      const res = await pageRecords(params)
      resultList.value = res.data?.records || []
      resultListTotal.value = res.data?.total || 0
      return res.data
    } finally {
      resultListLoading.value = false
    }
  }

  return {
    currentRecord,
    currentRecordLoading,
    quantitativeReport,
    aiReport,
    approvalFlows,
    resultList,
    resultListTotal,
    resultListLoading,
    fetchRecord,
    fetchReport,
    fetchAiReport,
    fetchApprovalFlows,
    clearDetail,
    fetchResultList,
  }
})
