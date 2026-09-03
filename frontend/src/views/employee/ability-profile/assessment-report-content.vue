<template>
  <div class="report-content">
    <el-descriptions :column="2" border style="margin-bottom: 16px;">
      <el-descriptions-item label="综合评分">
        <span :style="{ fontSize: '22px', fontWeight: 'bold', color: (report.overallScore || 0) >= 60 ? '#67c23a' : '#f56c6c' }">{{ report.overallScore ?? '-' }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="结论" :span="2">{{ report.conclusion || '—' }}</el-descriptions-item>
      <el-descriptions-item label="建议" :span="2" v-if="report.recommendation">{{ report.recommendation }}</el-descriptions-item>
    </el-descriptions>

    <section v-for="sec in sections" :key="sec.title" class="report-section">
      <h4>{{ sec.title }}</h4>
      <el-table v-if="sec.rows.length" :data="sec.rows" border size="small">
        <el-table-column v-for="col in sec.columns" :key="col.prop" :prop="col.prop" :label="col.label" :width="col.width" show-overflow-tooltip />
      </el-table>
      <el-empty v-else description="暂无数据" :image-size="50" />
    </section>

    <section class="report-section" v-if="interview.radarItems?.length">
      <h4>能力雷达</h4>
      <el-table :data="interview.radarItems" border size="small">
        <el-table-column prop="abilityName" label="能力" show-overflow-tooltip />
        <el-table-column prop="observedLevel" label="观察等级" width="90" />
        <el-table-column prop="requiredLevel" label="岗位要求" width="90" />
        <el-table-column prop="score" label="评分" width="70" />
        <el-table-column label="Harness" width="120">
          <template #default="{ row }">{{ row.harnessDecision || '待聚合审核' }}</template>
        </el-table-column>
      </el-table>
    </section>

    <section class="report-section">
      <h4>面试能力观察</h4>
      <el-alert v-if="interview.degraded" type="warning" :closable="false" :title="`报告降级：${interview.degradedReason || '未形成可验证的面试观察'}`" style="margin-bottom: 10px;" />
      <el-table v-if="interview.observations?.length" :data="interview.observations" border size="small">
        <el-table-column prop="abilityName" label="能力" min-width="120" />
        <el-table-column prop="observedLevel" label="观察等级" width="90" />
        <el-table-column prop="confidenceScore" label="置信度" width="90" />
        <el-table-column prop="harnessDecision" label="Harness" width="100" />
        <el-table-column prop="evidenceText" label="回答证据" min-width="220" show-overflow-tooltip />
        <el-table-column prop="interviewConclusion" label="核验结论" min-width="180" show-overflow-tooltip />
      </el-table>
      <el-empty v-else description="未形成可验证的面试能力观察" :image-size="50" />
    </section>

    <section class="report-section">
      <h4>逐题问答与追问</h4>
      <el-table v-if="interview.questionAnswers?.length" :data="interview.questionAnswers" border size="small">
        <el-table-column prop="questionOrder" label="题号" width="60" />
        <el-table-column prop="questionText" label="题目" min-width="220" show-overflow-tooltip />
        <el-table-column prop="answerText" label="回答转写" min-width="240" show-overflow-tooltip />
        <el-table-column prop="answerScore" label="回答分" width="80" />
        <el-table-column prop="analysisComment" label="核验说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="追问" width="80">
          <template #default="{ row }">{{ row.followUps?.length || 0 }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="未采集到逐题回答记录" :image-size="50" />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AssessmentReportDetail } from '@/api/assessment'

const props = defineProps<{ report: AssessmentReportDetail }>()

function parse<T>(json?: string): T[] {
  if (!json) return []
  try { return JSON.parse(json) as T[] } catch { return [] }
}

const interview = computed(() => {
  if (!props.report.interviewSummaryJson) return { radarItems: [] as any[] }
  try { return JSON.parse(props.report.interviewSummaryJson) } catch { return { radarItems: [] as any[] } }
})

const sections = computed(() => [
  { title: '简历证据', columns: [
    { prop: 'abilityName', label: '能力', width: undefined },
    { prop: 'claimedLevel', label: '等级', width: '70px' },
    { prop: 'harnessDecision', label: 'Harness', width: '100px' },
  ], rows: parse<any>(props.report.resumeSummaryJson) },
  { title: 'AI 测试结果', columns: [
    { prop: 'abilityName', label: '能力', width: undefined },
    { prop: 'claimedLevel', label: '等级', width: '70px' },
    { prop: 'harnessDecision', label: 'Harness', width: '100px' },
  ], rows: parse<any>(props.report.testSummaryJson) },
  { title: '聚合审核结论', columns: [
    { prop: 'abilityName', label: '能力', width: undefined },
    { prop: 'decision', label: '决策', width: '90px' },
    { prop: 'riskLevel', label: '风险', width: '90px' },
    { prop: 'supportedLevelCeiling', label: '等级上限', width: '90px' },
  ], rows: parse<any>(props.report.aggregateSummaryJson) },
  { title: '等级确认结论', columns: [
    { prop: 'abilityName', label: '能力', width: undefined },
    { prop: 'finalLevel', label: '最终等级', width: '90px' },
    { prop: 'finalConfidence', label: '置信度', width: '80px' },
    { prop: 'decisionStatus', label: '状态', width: '160px' },
  ], rows: parse<any>(props.report.levelSummaryJson) },
])
</script>

<style scoped>
.report-section { margin-bottom: 20px; }
.report-section h4 { margin: 0 0 8px; color: #303133; }
</style>
