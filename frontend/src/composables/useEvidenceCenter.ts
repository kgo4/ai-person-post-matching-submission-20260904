import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageEmployees, pagePosts } from '@/api'
import {
  backfillContestEvidence, getContestEvidenceSummary,
  getEmployeeEvidenceChain, getPostEvidenceChain,
  pageContestEvidence, reviewContestEvidence,
} from '@/api/contest'
import type { EmpEmployee, PostPost } from '@/api/types'
import type { ContestEvidenceItem, EvidenceChain, EvidenceChainAbility } from '@/api/contest'

type SubjectOption = EmpEmployee | PostPost

export function useEvidenceCenter() {
  const loading = ref(false)
  const backfillLoading = ref(false)
  const evidenceData = ref<ContestEvidenceItem[]>([])
  const total = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(10)
  const summary = ref<unknown>(null)

  const chainMode = ref<'employee' | 'post'>('employee')
  const subjectLoading = ref(false)
  const selectedSubjectId = ref<number>()
  const subjectOptions = ref<SubjectOption[]>([])
  const chainLoading = ref(false)
  const chainData = ref<EvidenceChain | null>(null)
  const selectedAbility = ref<EvidenceChainAbility | null>(null)

  async function remoteSearchSubjects(keyword: string, mode: 'employee' | 'post') {
    subjectLoading.value = true
    try {
      const params = { current: 1, size: 20, keyword }
      const res = mode === 'employee' ? await pageEmployees(params) : await pagePosts(params)
      subjectOptions.value = res.code === 200 ? (res.data.records as SubjectOption[]) : []
    } finally {
      subjectLoading.value = false
    }
  }

  async function loadEvidenceChain(mode: 'employee' | 'post', subjectId: number) {
    chainLoading.value = true
    try {
      const res = mode === 'employee'
        ? await getEmployeeEvidenceChain(subjectId)
        : await getPostEvidenceChain(subjectId)

      if (res.code === 200) {
        chainData.value = res.data
        selectedAbility.value = res.data.abilities[0] || null
      } else {
        chainData.value = null
        selectedAbility.value = null
        ElMessage.error('证据链加载失败')
      }
    } finally {
      chainLoading.value = false
    }
  }

  async function fetchEvidence(filters: { sourceType?: string; targetType?: string; evidenceStatus?: string; abilityName?: string }) {
    loading.value = true
    try {
      const params: Record<string, unknown> = { current: currentPage.value, size: pageSize.value }
      if (filters.sourceType) params.sourceType = filters.sourceType
      if (filters.targetType) params.targetType = filters.targetType
      if (filters.evidenceStatus) params.evidenceStatus = filters.evidenceStatus
      if (filters.abilityName) params.abilityName = filters.abilityName
      const res = await pageContestEvidence(params)
      if (res.code === 200) {
        evidenceData.value = res.data.records
        total.value = res.data.total
      }
    } finally {
      loading.value = false
    }
  }

  async function fetchSummary() {
    const res = await getContestEvidenceSummary()
    if (res.code === 200) summary.value = res.data
  }

  async function submitReview(evidenceId: number, status: string, comment: string) {
    const res = await reviewContestEvidence(evidenceId, { evidenceStatus: status, reviewComment: comment })
    if (res.code === 200) ElMessage.success('审核成功')
    return res.code === 200
  }

  async function handleBackfill(sourceType: string) {
    backfillLoading.value = true
    try {
      const res = await backfillContestEvidence(sourceType, 100)
      if (res.code === 200) {
        ElMessage.success(`回填完成，新增 ${res.data.created} 条证据`)
        return true
      }
      return false
    } finally {
      backfillLoading.value = false
    }
  }

  return {
    loading, backfillLoading, evidenceData, total, currentPage, pageSize, summary,
    chainMode, subjectLoading, selectedSubjectId, subjectOptions,
    chainLoading, chainData, selectedAbility,
    remoteSearchSubjects, loadEvidenceChain, fetchEvidence,
    fetchSummary, submitReview, handleBackfill,
  }
}
