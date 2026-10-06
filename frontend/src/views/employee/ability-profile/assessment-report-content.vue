<script setup lang="ts">
/**
 * 员工全方位评估报告渲染器（能力评估域的唯一报告）。
 *
 * 报告构成（docs/comprehensive-assessment-report-design.md）：
 *   ① 简历提取证据  ② AI 测试结果  ③ AI 面试报告（原独立面试报告全部内容并入）
 *   ④ 聚合审核 / 最终等级结论  ⑤ AI 综合洞察（无分数）
 *
 * 三条呈现口径：
 *   · **不展示汇总分**：overall_score / post_match_score 字段存在但不呈现（产品口径）；
 *   · **数值来自既有链路**：本组件只读展示，不重算任何分数；
 *   · **AI 洞察只有文字**：降级为模板时只在洞察区标注，数值区不做任何标注。
 *
 * 四部分数据由后端以 JSON 字符串下发（历史结构），这里统一解析渲染；
 * 单块解析失败只降级该块，不阻塞整份报告。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { VideoCamera } from '@element-plus/icons-vue'
import type { ComprehensiveAssessmentReportDetail } from '@/api/assessment'
import AbilityRadarChart from '@/components/graph/AbilityRadarChart.vue'
import { hasPermission } from '@/utils/permission'
import { learningPathPriorityMeta, normalizeLearningPath } from './learning-path'

const props = withDefaults(defineProps<{
  report: ComprehensiveAssessmentReportDetail
  /** 是否展示「查看面试过程记录」入口（有面试会话时才有意义） */
  showInterviewEntry?: boolean
}>(), {
  showInterviewEntry: true,
})

const router = useRouter()

const LEVEL_MAP: Record<number, string> = {
  1: 'L1 初级', 2: 'L2 中级', 3: 'L3 高级', 4: 'L4 专家', 5: 'L5 权威',
}

function levelText(level?: number | null): string {
  if (level == null) return '--'
  return LEVEL_MAP[level] ?? `L${level}`
}

/** 宽松解析：任一结构异常都降级为空，绝不让整份报告因一块脏数据打不开 */
function parse<T>(json: string | null | undefined, fallback: T): T {
  if (!json) return fallback
  try {
    const parsed = JSON.parse(json)
    return (parsed ?? fallback) as T
  } catch {
    return fallback
  }
}

function parseList<T>(json?: string | null): T[] {
  const parsed = parse<T[] | null>(json, null)
  return Array.isArray(parsed) ? parsed : []
}

function formatTime(raw?: string | null): string {
  return raw ? raw.replace('T', ' ').slice(0, 16) : '--'
}

/* ===================== 四部分数据 ===================== */

const resumeClaims = computed(() => parseList<Record<string, any>>(props.report.resumeSummaryJson))
const testItems = computed(() => parseList<Record<string, any>>(props.report.testSummaryJson))
const aggregateItems = computed(() => parseList<Record<string, any>>(props.report.aggregateSummaryJson))
const finalLevels = computed(() => parseList<Record<string, any>>(props.report.levelSummaryJson))

/** 面试报告：一份结构化对象（含雷达 / 观察 / 优劣势 / 风险 / 建议 / 逐题） */
const interview = computed(() => {
  const raw = parse<Record<string, any> | null>(props.report.interviewSummaryJson, null)
  return raw ?? {}
})

const radarItems = computed<any[]>(() => (Array.isArray(interview.value.radarItems) ? interview.value.radarItems : []))
const observations = computed<any[]>(() => (Array.isArray(interview.value.observations) ? interview.value.observations : []))
const questionAnswers = computed<any[]>(() => (Array.isArray(interview.value.questionAnswers) ? interview.value.questionAnswers : []))
const interviewStrengths = computed<string[]>(() => (Array.isArray(interview.value.strengths) ? interview.value.strengths : []))
const interviewWeaknesses = computed<string[]>(() => (Array.isArray(interview.value.weaknesses) ? interview.value.weaknesses : []))
const interviewRisks = computed<string[]>(() => (Array.isArray(interview.value.riskSignals) ? interview.value.riskSignals : []))
const interviewSuggestions = computed<string[]>(() => (Array.isArray(interview.value.improvementSuggestions) ? interview.value.improvementSuggestions : []))
/**
 * 学习路径建议：后端下发的是**对象数组**
 * （`{tagId, abilityName, currentLevel, targetLevel, suggestion, priority}`），
 * 不能按 `string[]` 插值 —— 否则会把整个对象当字符串打印成原始 JSON（员工可见的脏数据显示）。
 * 归一化逻辑在 `./learning-path.ts`（可测纯函数），这里只负责接入 + 预计算优先级标签。
 */
const interviewLearningPath = computed(() =>
  normalizeLearningPath(interview.value.learningPathSuggestions)
    .map(entry => ({ ...entry, priorityMeta: learningPathPriorityMeta(entry.priority) })),
)

/** 面试综合分：属于「面试部分内部」的既有数值，仍需展示 */
const interviewScore = computed<number | null>(() => {
  const v = interview.value.overallScore
  return typeof v === 'number' ? v : null
})

/** 能力雷达图数据：以「面试观察等级」为轴（等级上限 4，直接取自既有观察结果） */
const radarData = computed(() =>
  radarItems.value
    .filter(item => item && item.abilityName)
    .map(item => ({
      axis: String(item.abilityName),
      value: Number(item.observedLevel ?? 0),
      maxValue: 4,
    })),
)

/* ===================== ⑤ AI 洞察 ===================== */

const sectionInsights = computed(() => parseList<{ section: string; insight: string }>(props.report.sectionInsightsJson))
const strengths = computed(() => parseList<string>(props.report.strengthsJson))
const weaknesses = computed(() => parseList<string>(props.report.weaknessesJson))
const riskSignals = computed(() => parseList<string>(props.report.riskSignalsJson))
const suggestions = computed(() => parseList<string>(props.report.suggestionsJson))

const hasInsight = computed(() => props.report.insightAvailable)

const SECTION_LABEL: Record<string, string> = {
  RESUME: '简历提取证据',
  AI_TEST: 'AI 测试结果',
  AI_INTERVIEW: 'AI 面试结果',
  FINAL_LEVEL: '最终等级结论',
}

function sectionLabel(section: string): string {
  return SECTION_LABEL[section] ?? section
}

/* ===================== 面试过程记录入口（仅 HR） ===================== */

/**
 * 「查看面试过程记录」（逐题问答 / 转写）只对 HR 开放。
 *
 * 2026-09-04 口径：员工只能看面试**评价**（能力观察、证据、优劣势、结论），
 * 过程记录不向员工开放。这里用权限判定而不是依赖调用方传参 ——
 * 报告渲染器被三处复用（HR 档案页 / 我的能力画像 / 评估流程页），
 * 只要有一个调用方忘了传 `show-interview-entry=false` 就会漏出去。
 */
const canViewInterviewTrace = computed(() => props.showInterviewEntry && hasPermission('EMPLOYEE:READ'))

function openInterviewTrace() {
  const query: Record<string, string> = {}
  if (props.report.interviewSessionId) query.sessionId = String(props.report.interviewSessionId)
  router.push({ path: '/employee/ability-profile/live-interview', query })
}
</script>

<template>
  <div class="rc">
    <!-- 报告头：刻意不展示汇总分 -->
    <header class="rc__head">
      <div class="rc__head-main">
        <h3 class="rc__title">{{ report.empName || '员工' }} · 全方位评估报告</h3>
        <p class="rc__meta">
          评估完成：{{ formatTime(report.completedAt) }}
          <span class="rc__dot">·</span>
          洞察生成：{{ formatTime(report.insightGeneratedAt) }}
        </p>
      </div>
      <div v-if="report.sourceWeights?.length" class="rc__weights">
        <span class="rc__weights-title">各部分来源权重</span>
        <el-tag
          v-for="weight in report.sourceWeights"
          :key="weight.sourceType"
          size="small"
          effect="plain"
          round
        >
          {{ weight.sourceLabel }} {{ weight.weight }}%
        </el-tag>
      </div>
    </header>

    <!-- ① 简历提取证据 -->
    <section class="rc__section">
      <h4 class="rc__section-title">① 简历提取证据</h4>
      <el-table v-if="resumeClaims.length" :data="resumeClaims" border size="small">
        <el-table-column label="能力项" min-width="150">
          <template #default="{ row }">{{ row.abilityName || '--' }}</template>
        </el-table-column>
        <el-table-column label="声明等级" width="100">
          <template #default="{ row }">{{ row.claimedLevel == null ? '--' : `L${row.claimedLevel}` }}</template>
        </el-table-column>
        <el-table-column label="置信度" width="90">
          <template #default="{ row }">{{ row.confidenceScore ?? '--' }}</template>
        </el-table-column>
        <el-table-column label="Harness 判定" width="140">
          <template #default="{ row }">{{ row.harnessDecision || '--' }}</template>
        </el-table-column>
        <el-table-column label="证据原文" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.evidenceText || '--' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无简历提取数据" :image-size="50" />
    </section>

    <!-- ② AI 测试结果 -->
    <section class="rc__section">
      <h4 class="rc__section-title">② AI 测试结果</h4>
      <el-table v-if="testItems.length" :data="testItems" border size="small">
        <el-table-column label="能力项" min-width="150">
          <template #default="{ row }">{{ row.abilityName || '--' }}</template>
        </el-table-column>
        <el-table-column label="掌握等级" width="110">
          <template #default="{ row }">{{ levelText(row.claimedLevel ?? row.masteryLevel) }}</template>
        </el-table-column>
        <el-table-column label="得分" width="90">
          <template #default="{ row }">{{ row.confidenceScore ?? row.score ?? '--' }}</template>
        </el-table-column>
        <el-table-column label="Harness 判定" width="140">
          <template #default="{ row }">{{ row.harnessDecision || '--' }}</template>
        </el-table-column>
        <el-table-column label="证据原文" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.evidenceText || '--' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无 AI 测试数据" :image-size="50" />
    </section>

    <!-- ③ AI 面试报告（原独立面试报告全部内容并入本报告） -->
    <section class="rc__section">
      <div class="rc__section-head">
        <h4 class="rc__section-title">③ AI 面试报告</h4>
        <el-button
          v-if="canViewInterviewTrace && report.interviewSessionId"
          size="small"
          :icon="VideoCamera"
          @click="openInterviewTrace"
        >
          查看面试过程记录
        </el-button>
      </div>

      <el-alert
        v-if="interview.degraded"
        type="warning"
        :closable="false"
        :title="`面试报告降级：${interview.degradedReason || '未形成可验证的面试观察'}`"
        style="margin-bottom: 10px;"
      />

      <el-descriptions :column="3" border size="small" style="margin-bottom: 12px;">
        <el-descriptions-item label="面试综合分">
          {{ interviewScore == null ? '--' : Math.round(interviewScore) }}
        </el-descriptions-item>
        <el-descriptions-item label="能力观察项">
          {{ observations.length }}
        </el-descriptions-item>
        <el-descriptions-item label="风险信号">
          {{ interviewRisks.length }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 能力雷达：以面试观察等级为轴 -->
      <div v-if="radarData.length >= 3" class="rc__radar">
        <AbilityRadarChart :data="radarData" :width="420" :height="380" :max-value="4" :levels="4" />
      </div>
      <el-table v-if="radarItems.length" :data="radarItems" border size="small" style="margin-top: 10px;">
        <el-table-column label="能力" min-width="150">
          <template #default="{ row }">{{ row.abilityName || '--' }}</template>
        </el-table-column>
        <el-table-column label="观察等级" width="100">
          <template #default="{ row }">{{ row.observedLevel == null ? '--' : `L${row.observedLevel}` }}</template>
        </el-table-column>
        <el-table-column label="岗位要求" width="100">
          <template #default="{ row }">{{ row.requiredLevel == null ? '--' : `L${row.requiredLevel}` }}</template>
        </el-table-column>
        <el-table-column label="评分" width="80">
          <template #default="{ row }">{{ row.score ?? '--' }}</template>
        </el-table-column>
      </el-table>

      <el-table
        v-if="observations.length"
        :data="observations"
        border
        size="small"
        style="margin-top: 10px;"
      >
        <el-table-column label="能力" min-width="130">
          <template #default="{ row }">{{ row.abilityName || '--' }}</template>
        </el-table-column>
        <el-table-column label="观察等级" width="90">
          <template #default="{ row }">{{ row.observedLevel == null ? '--' : `L${row.observedLevel}` }}</template>
        </el-table-column>
        <el-table-column label="置信度" width="90">
          <template #default="{ row }">{{ row.confidenceScore ?? '--' }}</template>
        </el-table-column>
        <el-table-column label="回答证据" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.evidenceText || '--' }}</template>
        </el-table-column>
        <el-table-column label="核验结论" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.interviewConclusion || '--' }}</template>
        </el-table-column>
      </el-table>

      <!-- 面试给出的优劣势 / 风险 / 建议 -->
      <div class="rc__grid">
        <article class="rc__block">
          <h5>面试优势</h5>
          <ul v-if="interviewStrengths.length" class="rc__ul">
            <li v-for="(item, i) in interviewStrengths" :key="`is-${i}`">{{ item }}</li>
          </ul>
          <p v-else class="rc__none">--</p>
        </article>
        <article class="rc__block">
          <h5>面试劣势</h5>
          <ul v-if="interviewWeaknesses.length" class="rc__ul">
            <li v-for="(item, i) in interviewWeaknesses" :key="`iw-${i}`">{{ item }}</li>
          </ul>
          <p v-else class="rc__none">--</p>
        </article>
        <article class="rc__block">
          <h5>风险信号</h5>
          <ul v-if="interviewRisks.length" class="rc__ul">
            <li v-for="(item, i) in interviewRisks" :key="`ir-${i}`">{{ item }}</li>
          </ul>
          <p v-else class="rc__none">--</p>
        </article>
        <article class="rc__block">
          <h5>提升建议</h5>
          <ul v-if="interviewSuggestions.length" class="rc__ul">
            <li v-for="(item, i) in interviewSuggestions" :key="`ix-${i}`">{{ item }}</li>
          </ul>
          <p v-else class="rc__none">--</p>
        </article>
      </div>

      <div v-if="interviewLearningPath.length" class="rc__block rc__block--wide">
        <h5>学习路径建议</h5>
        <ul class="rc__lp">
          <li v-for="(item, i) in interviewLearningPath" :key="`lp-${i}`" class="rc__lp-item">
            <div class="rc__lp-head">
              <span class="rc__lp-name">{{ item.abilityName || '能力提升' }}</span>
              <span v-if="item.currentLevel != null || item.targetLevel != null" class="rc__lp-level">
                {{ levelText(item.currentLevel) }}
                <span class="rc__lp-arrow">→</span>
                {{ levelText(item.targetLevel) }}
              </span>
              <el-tag
                v-if="item.priorityMeta"
                size="small"
                effect="plain"
                round
                :type="item.priorityMeta.tagType"
              >
                {{ item.priorityMeta.text }}
              </el-tag>
            </div>
            <p v-if="item.suggestion" class="rc__lp-text">{{ item.suggestion }}</p>
          </li>
        </ul>
      </div>

      <div v-if="report.conclusion || report.recommendation" class="rc__block rc__block--wide">
        <h5>面试结论</h5>
        <p class="rc__text">{{ report.conclusion || '--' }}</p>
        <p v-if="report.recommendation" class="rc__text rc__text--muted">{{ report.recommendation }}</p>
      </div>

      <el-collapse v-if="questionAnswers.length" class="rc__collapse">
        <el-collapse-item :title="`逐题问答与追问（${questionAnswers.length} 题）`" name="qa">
          <el-table :data="questionAnswers" border size="small">
            <el-table-column label="题号" width="60">
              <template #default="{ row }">{{ row.questionOrder ?? '--' }}</template>
            </el-table-column>
            <el-table-column label="题目" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ row.questionText || '--' }}</template>
            </el-table-column>
            <el-table-column label="回答转写" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">{{ row.answerText || '--' }}</template>
            </el-table-column>
            <el-table-column label="回答分" width="80">
              <template #default="{ row }">{{ row.answerScore ?? '--' }}</template>
            </el-table-column>
            <el-table-column label="追问" width="70">
              <template #default="{ row }">{{ row.followUps?.length || 0 }}</template>
            </el-table-column>
          </el-table>
        </el-collapse-item>
      </el-collapse>
    </section>

    <!-- ④ 最终等级结论（聚合审核 + 等级确认，直接引用既有结论） -->
    <section class="rc__section">
      <h4 class="rc__section-title">④ 最终等级结论</h4>
      <el-table v-if="finalLevels.length" :data="finalLevels" border size="small">
        <el-table-column label="能力项" min-width="160">
          <template #default="{ row }">{{ row.abilityName || '--' }}</template>
        </el-table-column>
        <el-table-column label="最终等级" width="120">
          <template #default="{ row }">{{ levelText(row.finalLevel) }}</template>
        </el-table-column>
        <el-table-column label="置信度" width="90">
          <template #default="{ row }">{{ row.finalConfidence ?? '--' }}</template>
        </el-table-column>
        <el-table-column label="确认状态" width="150">
          <template #default="{ row }">{{ row.decisionStatus || '--' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无最终等级结论" :image-size="50" />

      <el-collapse v-if="aggregateItems.length" class="rc__collapse">
        <el-collapse-item :title="`聚合审核明细（${aggregateItems.length} 项）`" name="agg">
          <el-table :data="aggregateItems" border size="small">
            <el-table-column label="能力项" min-width="160">
              <template #default="{ row }">{{ row.abilityName || '--' }}</template>
            </el-table-column>
            <el-table-column prop="decision" label="决策" width="120" />
            <el-table-column prop="riskLevel" label="风险" width="110" />
            <el-table-column prop="supportedLevelCeiling" label="等级上限" width="110" />
          </el-table>
        </el-collapse-item>
      </el-collapse>
    </section>

    <!-- ⑤ AI 综合洞察（无分数） -->
    <section class="rc__section rc__section--insight">
      <div class="rc__section-head">
        <h4 class="rc__section-title">⑤ AI 综合洞察</h4>
        <el-tag
          v-if="hasInsight && report.insightFallbackUsed"
          size="small"
          type="warning"
          effect="plain"
        >
          文字由模板生成
        </el-tag>
      </div>

      <el-alert
        v-if="!hasInsight"
        type="info"
        show-icon
        :closable="false"
        title="洞察尚未生成"
        description="数值与结论均来自既有评估链路，不受影响。可由 HR 在报告中重新生成洞察文字。"
      />

      <template v-else>
        <div v-if="sectionInsights.length" class="rc__insights">
          <article v-for="item in sectionInsights" :key="item.section" class="rc__block">
            <h5>{{ sectionLabel(item.section) }}</h5>
            <p class="rc__text">{{ item.insight }}</p>
          </article>
        </div>

        <div class="rc__grid">
          <article class="rc__block">
            <h5>优势</h5>
            <ul v-if="strengths.length" class="rc__ul rc__ul--plus">
              <li v-for="(item, i) in strengths" :key="`s-${i}`">{{ item }}</li>
            </ul>
            <p v-else class="rc__none">--</p>
          </article>
          <article class="rc__block">
            <h5>短板</h5>
            <ul v-if="weaknesses.length" class="rc__ul rc__ul--minus">
              <li v-for="(item, i) in weaknesses" :key="`w-${i}`">{{ item }}</li>
            </ul>
            <p v-else class="rc__none">--</p>
          </article>
          <article class="rc__block">
            <h5>风险信号</h5>
            <ul v-if="riskSignals.length" class="rc__ul rc__ul--risk">
              <li v-for="(item, i) in riskSignals" :key="`r-${i}`">{{ item }}</li>
            </ul>
            <p v-else class="rc__none">--</p>
          </article>
          <article class="rc__block">
            <h5>改进建议</h5>
            <ul v-if="suggestions.length" class="rc__ul">
              <li v-for="(item, i) in suggestions" :key="`g-${i}`">{{ item }}</li>
            </ul>
            <p v-else class="rc__none">--</p>
          </article>
        </div>

        <div v-if="report.aiConclusion" class="rc__block rc__block--wide">
          <h5>综合结论</h5>
          <p class="rc__text">{{ report.aiConclusion }}</p>
        </div>
      </template>
    </section>
  </div>
</template>

<style scoped>
.rc { display: flex; flex-direction: column; gap: 16px; }
.rc__head {
  display: flex; flex-wrap: wrap; align-items: flex-start; justify-content: space-between;
  gap: 12px; padding-bottom: 12px; border-bottom: 1px solid var(--app-divider, #e4e7ed);
}
.rc__title { margin: 0; font-size: 16px; font-weight: 700; color: var(--app-text-strong, #1a2440); }
.rc__meta { margin: 6px 0 0; font-size: 12px; color: var(--app-text-muted, #8b95ab); }
.rc__dot { margin: 0 6px; }
.rc__weights { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.rc__weights-title { font-size: 12px; color: var(--app-text-muted, #8b95ab); }

.rc__section { border: 1px solid var(--app-divider, #e4e7ed); border-radius: 10px; padding: 14px 16px; }
.rc__section--insight { background: rgba(47, 107, 255, 0.03); }
.rc__section-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.rc__section-title { margin: 0 0 10px; font-size: 14px; font-weight: 700; color: var(--app-text-strong, #1a2440); }

.rc__radar { display: flex; justify-content: center; overflow-x: auto; }
.rc__grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; margin-top: 12px; }
.rc__block { border: 1px solid var(--app-divider, #ebeef5); border-radius: 8px; padding: 10px 12px; }
.rc__block--wide { margin-top: 12px; }
.rc__block h5 { margin: 0 0 6px; font-size: 12px; font-weight: 700; color: var(--app-text-strong, #1a2440); }
.rc__text { margin: 0; font-size: 13px; line-height: 1.75; color: var(--app-text, #414a63); }
.rc__text--muted { margin-top: 6px; color: var(--app-text-muted, #8b95ab); }
.rc__ul { margin: 0; padding-left: 18px; }
.rc__ul li { font-size: 13px; line-height: 1.8; color: var(--app-text, #414a63); }
.rc__ul--plus li::marker { color: var(--app-success, #12b76a); }
.rc__ul--minus li::marker { color: var(--app-warning, #f79009); }
.rc__ul--risk li::marker { color: var(--app-danger, #f04438); }
.rc__none { margin: 0; font-size: 12px; color: var(--app-text-muted, #9aa3b8); }

/* 学习路径建议：卡片式条目（原来是把对象当字符串打印，这里是结构化渲染） */
.rc__lp { margin: 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 8px; }
.rc__lp-item { border: 1px solid var(--app-divider, #ebeef5); border-radius: 8px; padding: 8px 10px; }
.rc__lp-head { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.rc__lp-name { font-size: 13px; font-weight: 600; color: var(--app-text-strong, #1a2440); }
.rc__lp-level { font-size: 12px; color: var(--app-text-muted, #8b95ab); }
.rc__lp-arrow { margin: 0 4px; }
.rc__lp-text { margin: 6px 0 0; font-size: 13px; line-height: 1.75; color: var(--app-text, #414a63); }
.rc__insights { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 12px; }
.rc__collapse { margin-top: 12px; }
</style>
