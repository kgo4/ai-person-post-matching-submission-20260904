import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAbilityGapPath, getLearningPath, getReport, searchKnowledgeChunks } from '@/api'
import type { GraphData, KnowledgeChunkResult, LearningPathItem, MatchingRecord } from '@/api'
import { buildGapKnowledgeQuery, buildImprovementPlan, extractGapAbilities, getDefaultDimensionScores, parseMatchingReportPayload } from '../gap-diagnosis'
import type { DimensionScore, GapAbility, ImprovementPhase } from '../gap-diagnosis'

export function useAbilityGap() {
  const router = useRouter()

  const gapDrawerVisible = ref(false)
  const gapLoading = ref(false)
  const currentGapRecord = ref<MatchingRecord | null>(null)
  const gapAbilities = ref<GapAbility[]>([])
  const gapLearningPath = ref<LearningPathItem[]>([])
  const gapGraphData = ref<GraphData | null>(null)
  const gapEvidenceResults = ref<KnowledgeChunkResult[]>([])
  const gapWarnings = ref<string[]>([])
  const gapDimensionScores = ref<DimensionScore[]>([])
  const gapImprovementPlan = ref<ImprovementPhase[]>([])
  const gapExportLoading = ref(false)

  const gapGraphSummary = computed(() => {
    const nodes = gapGraphData.value?.nodes?.length || 0
    const edges = gapGraphData.value?.edges?.length || 0
    return `${nodes} nodes / ${edges} edges`
  })

  async function openGapWorkbench(row: MatchingRecord) {
    currentGapRecord.value = row
    gapDrawerVisible.value = true
    gapLoading.value = true
    gapAbilities.value = []
    gapLearningPath.value = []
    gapGraphData.value = null
    gapEvidenceResults.value = []
    gapWarnings.value = []
    gapDimensionScores.value = []
    gapImprovementPlan.value = []

    const matchScore = row.finalMatchScore ?? row.aiMatchScore ?? 0

    try {
      const reportRes = await getReport(row.id)
      const report = parseMatchingReportPayload(reportRes.data)

      gapDimensionScores.value = report.dimensionScores?.length
        ? report.dimensionScores
        : getDefaultDimensionScores(matchScore)

      const gaps = extractGapAbilities(report)
      gapAbilities.value = gaps

      if (!gaps.length) {
        gapWarnings.value = ['当前匹配报告未识别到能力缺口或弱证据项。']
        return
      }

      const abilityNames = gaps.map((item) => item.name)
      const knowledgeQuery = buildGapKnowledgeQuery(gaps)
      const [learningResult, graphResult, evidenceResult] = await Promise.allSettled([
        getLearningPath({ abilityNames, currentLevel: 1, targetLevel: 3 }),
        getAbilityGapPath(row.empId, row.postId),
        searchKnowledgeChunks({ queryText: knowledgeQuery, scenario: 'MATCHING_GAP_DIAGNOSIS', topK: 5 }),
      ])

      if (learningResult.status === 'fulfilled') {
        gapLearningPath.value = learningResult.value.data || []
      } else {
        gapWarnings.value.push('学习路径加载失败。')
      }

      if (graphResult.status === 'fulfilled') {
        gapGraphData.value = graphResult.value.data || null
      } else {
        gapWarnings.value.push('图谱路径加载失败。')
      }

      if (evidenceResult.status === 'fulfilled') {
        gapEvidenceResults.value = evidenceResult.value.data || []
      } else {
        gapWarnings.value.push('RAG 证据检索失败。')
      }

      gapImprovementPlan.value = buildImprovementPlan(gaps, gapLearningPath.value)
    } catch (error: any) {
      ElMessage.error(error.message || '加载差距诊断失败')
      gapWarnings.value = ['匹配报告加载失败，无法生成差距诊断。']
      gapDimensionScores.value = getDefaultDimensionScores(matchScore)
    } finally {
      gapLoading.value = false
    }
  }

  function handlePrintGapReport() {
    window.print()
  }

  function handleExportGapReport() {
    if (!currentGapRecord.value) return
    const data = {
      record: {
        empId: currentGapRecord.value.empId,
        empName: currentGapRecord.value.empName,
        postId: currentGapRecord.value.postId,
        postName: currentGapRecord.value.postName,
        matchScore: currentGapRecord.value.finalMatchScore ?? currentGapRecord.value.aiMatchScore,
      },
      dimensionScores: gapDimensionScores.value,
      gaps: gapAbilities.value,
      improvementPlan: gapImprovementPlan.value,
      learningResources: gapLearningPath.value.map(item => ({
        abilityName: item.abilityName,
        title: item.title,
        type: item.resourceType,
        url: item.url,
      })),
    }
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `gap-diagnosis-${currentGapRecord.value.id}.json`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('差距诊断报告已导出')
  }

  function goToResumeParse() {
    if (!currentGapRecord.value) return
    router.push({
      path: '/employee/ability-profile/resume-parse',
      query: { empId: currentGapRecord.value.empId },
    })
  }

  return {
    gapDrawerVisible,
    gapLoading,
    currentGapRecord,
    gapAbilities,
    gapLearningPath,
    gapGraphData,
    gapEvidenceResults,
    gapWarnings,
    gapDimensionScores,
    gapImprovementPlan,
    gapExportLoading,
    gapGraphSummary,
    openGapWorkbench,
    handlePrintGapReport,
    handleExportGapReport,
    goToResumeParse,
  }
}
