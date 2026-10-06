<script setup lang="ts">
/**
 * 运行审计（AI 基础设施）—— PLATFORM_ADMIN / AUDIT:READ
 *
 * 页面分三块：
 *  1. 运行时指标：token 消耗、LLM / 工具的调用量与响应时间（Micrometer，进程级累计）
 *     —— 卡片给结论数字，下面配**环形 + 柱状图**看结构；
 *  2. 调用趋势与耗时分布：按小时趋势折线、各 Prompt 平均耗时横向柱（服务端聚合）；
 *  3. 明细表：Prompt 实验效果、Prompt 调用明细、RAG 检索日志（可筛选分页）。
 *
 * 「统计图谱化」的做法：**聚合在服务端做**（按小时 / 按维度 SQL group by），
 * 前端只负责把序列画出来 —— 审计窗口内明细可能上万行，拉到前端再聚合会很重。
 *
 * ⚠️ token 与响应时间来自 Micrometer 计数器：**进程级累计、重启归零**、没有时间维度；
 * 需要时间维度的看下面「按小时趋势」（数据源是 prompt_invocation_log，已落库）。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh, TrendCharts } from '@element-plus/icons-vue'
import EChartsWrapper from '@/components/chart/EChartsWrapper.vue'
import {
  getDlqSummary,
  getPromptExperiments,
  getRuntimeAuditAggregate,
  getRuntimeMetrics,
  pagePromptInvocationLogs,
  pageRagAuditLogs,
} from '@/api'
import type {
  DlqSummary,
  PromptExperimentGroup,
  PromptInvocationLog,
  RagAuditLog,
  RuntimeAuditAggregate,
  RuntimeMetrics,
} from '@/api'
import {
  AUDIT_CHART_HEIGHT,
  callOutcomeOption,
  chartHeightForRows,
  latencyTrendOption,
  promptBreakdownOption,
  ragLatencyTrendOption,
  ragScenarioOption,
  tokenDonutOption,
} from './audit-charts'

const metricsLoading = ref(false)
const metrics = ref<RuntimeMetrics | null>(null)

const dlqLoading = ref(false)
const dlq = ref<DlqSummary | null>(null)

const aggregateLoading = ref(false)
const aggregate = ref<RuntimeAuditAggregate | null>(null)
/** 图表统计窗口（小时）：只在"看趋势"这一层变化，与下面明细表的筛选互不影响。 */
const windowHours = ref(24)

const experimentLoading = ref(false)
const experimentDays = ref(7)
const experimentGroups = ref<PromptExperimentGroup[]>([])
const experimentHint = ref('')

const promptLoading = ref(false)
const promptLogs = ref<PromptInvocationLog[]>([])
const promptTotal = ref(0)
const promptQuery = reactive({
  promptName: '',
  success: '' as '' | 'true' | 'false',
  current: 1,
  size: 10,
})

const ragLoading = ref(false)
const ragLogs = ref<RagAuditLog[]>([])
const ragTotal = ref(0)
const ragQuery = reactive({
  scenario: '',
  current: 1,
  size: 10,
})

/* ------------------------------ 图表选项（纯函数产出） ------------------------------ */

const tokenOption = computed(() => tokenDonutOption(metrics.value))
const callOption = computed(() => callOutcomeOption(metrics.value))
const llmTrendOption = computed(() => latencyTrendOption(aggregate.value?.latencyTrend))
const promptOption = computed(() => promptBreakdownOption(aggregate.value?.promptBreakdown))
const ragTrendOption = computed(() => ragLatencyTrendOption(aggregate.value?.ragLatencyTrend))
const ragScenarioChartOption = computed(() => ragScenarioOption(aggregate.value?.ragScenarioBreakdown))

const hasLlmTrend = computed(() => (aggregate.value?.latencyTrend?.length ?? 0) > 0)
const hasPromptBreakdown = computed(() => (aggregate.value?.promptBreakdown?.length ?? 0) > 0)
const hasRagTrend = computed(() => (aggregate.value?.ragLatencyTrend?.length ?? 0) > 0)
const hasRagScenario = computed(() => (aggregate.value?.ragScenarioBreakdown?.length ?? 0) > 0)

function formatNumber(value?: number | null) {
  if (value === null || value === undefined) return '0'
  return value.toLocaleString('zh-CN')
}

function formatDuration(value?: number | null) {
  if (!value) return '0 ms'
  return `${value} ms`
}

/* ----------------------------------- 数据加载 ----------------------------------- */

async function fetchMetrics() {
  metricsLoading.value = true
  try {
    const res = await getRuntimeMetrics()
    metrics.value = res.data
  } catch {
    metrics.value = null
  } finally {
    metricsLoading.value = false
  }
}

async function fetchDlq() {
  dlqLoading.value = true
  try {
    const res = await getDlqSummary()
    dlq.value = res.data
  } catch {
    dlq.value = null
  } finally {
    dlqLoading.value = false
  }
}

async function fetchAggregate() {
  aggregateLoading.value = true
  try {
    const res = await getRuntimeAuditAggregate(windowHours.value)
    aggregate.value = res.data
  } catch {
    aggregate.value = null
  } finally {
    aggregateLoading.value = false
  }
}

async function fetchExperiments() {
  experimentLoading.value = true
  try {
    const res = await getPromptExperiments(experimentDays.value)
    const data = res.data || {}
    experimentGroups.value = Object.values(data.groups || {})
    // 后端在没有埋点数据时返回 message 而不是 groups，这里如实透出，避免看起来"加载失败"
    experimentHint.value = data.message || ''
  } catch {
    experimentGroups.value = []
    experimentHint.value = ''
  } finally {
    experimentLoading.value = false
  }
}

async function fetchPromptLogs() {
  promptLoading.value = true
  try {
    const params: Record<string, unknown> = {
      current: promptQuery.current,
      size: promptQuery.size,
    }
    if (promptQuery.promptName) params.promptName = promptQuery.promptName
    if (promptQuery.success !== '') params.success = promptQuery.success === 'true'
    const res = await pagePromptInvocationLogs(params)
    promptLogs.value = res.data.records
    promptTotal.value = res.data.total
  } catch {
    promptLogs.value = []
    promptTotal.value = 0
  } finally {
    promptLoading.value = false
  }
}

async function fetchRagLogs() {
  ragLoading.value = true
  try {
    const params: Record<string, unknown> = {
      current: ragQuery.current,
      size: ragQuery.size,
    }
    if (ragQuery.scenario) params.scenario = ragQuery.scenario
    const res = await pageRagAuditLogs(params)
    ragLogs.value = res.data.records
    ragTotal.value = res.data.total
  } catch {
    ragLogs.value = []
    ragTotal.value = 0
  } finally {
    ragLoading.value = false
  }
}

function searchPromptLogs() {
  promptQuery.current = 1
  fetchPromptLogs()
}

function resetPromptLogs() {
  promptQuery.promptName = ''
  promptQuery.success = ''
  searchPromptLogs()
}

function searchRagLogs() {
  ragQuery.current = 1
  fetchRagLogs()
}

function resetRagLogs() {
  ragQuery.scenario = ''
  searchRagLogs()
}

function refreshAll() {
  fetchMetrics()
  fetchDlq()
  fetchAggregate()
  fetchExperiments()
  fetchPromptLogs()
  fetchRagLogs()
}

onMounted(() => {
  refreshAll()
})
</script>

<template>
  <div class="page-container">
    <el-alert
      type="info"
      show-icon
      :closable="false"
      title="运维审计（进程级累计指标 + 已落库的调用明细）"
      description="token 消耗与调用总量来自运行时指标计数器，统计自本次应用启动以来、应用重启后归零；按时段的趋势与耗时分布取自已落库的调用日志，可切换统计窗口。"
      class="audit-alert"
    />

    <!-- 1. 运行时指标：数字 + 结构图 -->
    <el-card shadow="hover" class="audit-card">
      <template #header>
        <div class="card-header">
          <span>运行时指标</span>
          <el-button link type="primary" :icon="Refresh" @click="fetchMetrics">刷新</el-button>
        </div>
      </template>

      <div v-loading="metricsLoading || dlqLoading" class="metric-grid">
        <div class="metric-card">
          <div class="metric-label">Token 消耗（输入）</div>
          <div class="metric-value">{{ formatNumber(metrics?.inputTokens) }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">Token 消耗（输出）</div>
          <div class="metric-value">{{ formatNumber(metrics?.outputTokens) }}</div>
        </div>
        <div class="metric-card metric-card--strong">
          <div class="metric-label">Token 消耗（合计）</div>
          <div class="metric-value">{{ formatNumber(metrics?.totalTokens) }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">LLM 调用次数</div>
          <div class="metric-value">{{ formatNumber(metrics?.llmCallCount) }}</div>
          <div class="metric-sub">失败 {{ formatNumber(metrics?.llmErrorCount) }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">LLM 响应时间（平均）</div>
          <div class="metric-value">{{ formatDuration(metrics?.llmAvgMs) }}</div>
          <div class="metric-sub">最大 {{ formatDuration(metrics?.llmMaxMs) }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">工具调用次数</div>
          <div class="metric-value">{{ formatNumber(metrics?.toolCallCount) }}</div>
          <div class="metric-sub">
            失败 {{ formatNumber(metrics?.toolErrorCount) }} · 缓存命中 {{ formatNumber(metrics?.toolCacheHitCount) }}
          </div>
        </div>
        <div class="metric-card">
          <div class="metric-label">工具耗时（平均）</div>
          <div class="metric-value">{{ formatDuration(metrics?.toolAvgMs) }}</div>
          <div class="metric-sub">最大 {{ formatDuration(metrics?.toolMaxMs) }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">JSON 守卫拦截</div>
          <div class="metric-value">{{ formatNumber(metrics?.jsonGuardCount) }}</div>
        </div>
        <div class="metric-card">
          <div class="metric-label">死信队列积压</div>
          <div class="metric-value">{{ formatNumber(dlq?.messageCount) }}</div>
          <div class="metric-sub">
            <template v-if="dlq?.alertThreshold">
              {{ dlq?.alerting ? `已超阈值 ${dlq?.alertThreshold}` : `阈值 ${dlq?.alertThreshold} 内` }}
            </template>
            <template v-else>未设告警阈值</template>
          </div>
        </div>
      </div>

      <div class="chart-grid">
        <div class="chart-cell">
          <div class="chart-title">Token 消耗结构</div>
          <EChartsWrapper :option="tokenOption" :height="AUDIT_CHART_HEIGHT" />
        </div>
        <div class="chart-cell">
          <div class="chart-title">调用量与失败数</div>
          <EChartsWrapper :option="callOption" :height="AUDIT_CHART_HEIGHT" />
        </div>
      </div>

      <div v-if="metrics?.note" class="metric-note">{{ metrics.note }}</div>
      <div v-if="dlq?.checkedAt" class="metric-note">死信队列最近检查时间：{{ dlq.checkedAt }}</div>
    </el-card>

    <!-- 2. 趋势与耗时分布（服务端聚合） -->
    <el-card shadow="hover" class="audit-card">
      <template #header>
        <div class="card-header">
          <span>调用趋势与耗时分布</span>
          <div class="header-actions">
            <el-select v-model="windowHours" style="width: 150px;" @change="fetchAggregate">
              <el-option :value="6" label="最近 6 小时" />
              <el-option :value="24" label="最近 24 小时" />
              <el-option :value="72" label="最近 3 天" />
              <el-option :value="168" label="最近 7 天" />
              <!-- 补更长窗口：调用日志是按天累积的，只给到 7 天时
                   "上次跑过评测但已过一周"的环境会看到空图，且无从判断是"没数据"还是"窗口太窄" -->
              <el-option :value="720" label="最近 30 天" />
              <el-option :value="8760" label="最近 1 年" />
            </el-select>
            <el-button link type="primary" :icon="Refresh" @click="fetchAggregate">刷新</el-button>
          </div>
        </div>
      </template>

      <div v-loading="aggregateLoading">
        <div class="chart-title">
          <el-icon><TrendCharts /></el-icon>
          LLM 调用量与响应时间（按小时）
        </div>
        <!-- 空状态要说清"为什么空"和"怎么才有数据"：
             本页数据来自 prompt_invocation_log 的落库记录，没有调用就没有行 ——
             只写「没有记录」会让人以为是功能坏了。同时提示窗口可放大。 -->
        <el-empty v-if="!aggregateLoading && !hasLlmTrend" description="所选窗口内没有 LLM 调用记录">
          <template #description>
            <div>所选窗口内没有 LLM 调用记录。</div>
            <div class="empty-hint">
              趋势数据取自落库的调用日志，需要窗口内**实际发生过** AI 调用才有内容；
              可先把窗口调到「最近 30 天」或「最近 1 年」再试。
            </div>
          </template>
        </el-empty>
        <EChartsWrapper v-else :option="llmTrendOption" :height="AUDIT_CHART_HEIGHT" />

        <div class="chart-title chart-title--spaced">各 Prompt 平均耗时（越靠上越慢）</div>
        <el-empty v-if="!aggregateLoading && !hasPromptBreakdown" description="所选窗口内没有 Prompt 调用记录" />
        <EChartsWrapper v-else :option="promptOption" :height="chartHeightForRows(aggregate?.promptBreakdown?.length)" />
      </div>
    </el-card>

    <!-- 3. Prompt 实验效果 -->
    <el-card shadow="hover" class="audit-card">
      <template #header>
        <div class="card-header">
          <span>Prompt 实验效果（按 Prompt × 版本聚合）</span>
          <div class="header-actions">
            <el-select v-model="experimentDays" style="width: 120px;" @change="fetchExperiments">
              <el-option :value="1" label="最近 1 天" />
              <el-option :value="7" label="最近 7 天" />
              <el-option :value="30" label="最近 30 天" />
            </el-select>
            <el-button link type="primary" :icon="Refresh" @click="fetchExperiments">刷新</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="experimentHint" type="warning" :closable="false" show-icon :title="experimentHint" class="audit-alert" />

      <el-table :data="experimentGroups" v-loading="experimentLoading" border stripe>
        <el-table-column prop="promptName" label="Prompt" min-width="200px" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="120px" show-overflow-tooltip />
        <el-table-column prop="totalCalls" label="调用次数" width="100px" />
        <el-table-column label="平均响应时间" width="130px">
          <template #default="{ row }">{{ formatDuration(Math.round(row.avgLatencyMs)) }}</template>
        </el-table-column>
        <el-table-column label="成功率" width="110px">
          <template #default="{ row }">{{ row.successRate?.toFixed?.(1) ?? row.successRate }}%</template>
        </el-table-column>
        <el-table-column label="平均评分" width="110px">
          <template #default="{ row }">{{ row.avgFeedbackScore ? row.avgFeedbackScore.toFixed(2) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="feedbackCount" label="评分样本" width="110px" />
      </el-table>
      <el-empty v-if="!experimentLoading && experimentGroups.length === 0" description="所选时间范围内没有 Prompt 埋点数据" />
    </el-card>

    <!-- 4. RAG 检索统计 -->
    <el-card shadow="hover" class="audit-card">
      <template #header>
        <div class="card-header">
          <span>RAG 检索统计</span>
          <el-button link type="primary" :icon="Refresh" @click="fetchAggregate">刷新</el-button>
        </div>
      </template>

      <div v-loading="aggregateLoading">
        <div class="chart-title">检索量与延迟（按小时）</div>
        <el-empty v-if="!aggregateLoading && !hasRagTrend" description="所选窗口内没有 RAG 检索记录" />
        <EChartsWrapper v-else :option="ragTrendOption" :height="AUDIT_CHART_HEIGHT" />

        <div class="chart-title chart-title--spaced">各场景检索量</div>
        <el-empty v-if="!aggregateLoading && !hasRagScenario" description="所选窗口内没有 RAG 场景数据" />
        <EChartsWrapper
          v-else
          :option="ragScenarioChartOption"
          :height="chartHeightForRows(aggregate?.ragScenarioBreakdown?.length)"
        />
      </div>
    </el-card>

    <!-- 5. Prompt 调用明细 -->
    <el-card shadow="hover" class="audit-card">
      <template #header>
        <div class="card-header">
          <span>Prompt 调用明细</span>
        </div>
      </template>

      <div class="search-bar">
        <el-input
          v-model="promptQuery.promptName"
          placeholder="Prompt 名称"
          clearable
          style="width: 200px;"
          @keyup.enter="searchPromptLogs"
        />
        <el-select v-model="promptQuery.success" placeholder="调用结果" clearable style="width: 140px;">
          <el-option label="成功" value="true" />
          <el-option label="失败" value="false" />
        </el-select>
        <el-button type="primary" @click="searchPromptLogs">搜索</el-button>
        <el-button @click="resetPromptLogs">重置</el-button>
      </div>

      <el-table :data="promptLogs" v-loading="promptLoading" border stripe>
        <el-table-column prop="promptName" label="Prompt" min-width="180px" show-overflow-tooltip />
        <el-table-column prop="promptVersion" label="版本" width="110px" show-overflow-tooltip />
        <el-table-column prop="scenario" label="场景" width="150px" show-overflow-tooltip />
        <el-table-column prop="modelName" label="模型" width="150px" show-overflow-tooltip />
        <el-table-column label="总耗时" width="100px">
          <template #default="{ row }">{{ formatDuration(row.latencyMs) }}</template>
        </el-table-column>
        <el-table-column label="工具耗时" width="100px">
          <template #default="{ row }">{{ formatDuration(row.toolLatencyMs) }}</template>
        </el-table-column>
        <el-table-column label="排队等待" width="100px">
          <template #default="{ row }">{{ formatDuration(row.queueWaitMs) }}</template>
        </el-table-column>
        <el-table-column prop="modelRounds" label="模型轮次" width="100px" />
        <el-table-column prop="retryCount" label="重试" width="80px" />
        <el-table-column label="结果" width="90px">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small" disable-transitions>
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="缓存" width="80px">
          <template #default="{ row }">
            <el-tag v-if="row.cacheHit" type="info" size="small" disable-transitions>命中</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="降级" width="80px">
          <template #default="{ row }">
            <el-tag v-if="row.fallbackUsed" type="warning" size="small" disable-transitions>降级</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="输入/输出字符" width="140px">
          <template #default="{ row }">
            {{ formatNumber(row.inputChars) }} / {{ formatNumber(row.outputChars) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="时间" width="170px" />
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="promptQuery.current"
          v-model:page-size="promptQuery.size"
          :page-sizes="[10, 20, 50]"
          background
          layout="total, sizes, prev, pager, next"
          :total="promptTotal"
          @size-change="searchPromptLogs"
          @current-change="fetchPromptLogs"
        />
      </div>
    </el-card>

    <!-- 6. RAG 检索日志 -->
    <el-card shadow="hover" class="audit-card">
      <template #header>
        <div class="card-header">
          <span>RAG 检索日志</span>
        </div>
      </template>

      <div class="search-bar">
        <el-input
          v-model="ragQuery.scenario"
          placeholder="RAG 场景"
          clearable
          style="width: 200px;"
          @keyup.enter="searchRagLogs"
        />
        <el-button type="primary" @click="searchRagLogs">搜索</el-button>
        <el-button @click="resetRagLogs">重置</el-button>
      </div>

      <el-table :data="ragLogs" v-loading="ragLoading" border stripe>
        <el-table-column prop="queryCode" label="查询编码" width="180px" show-overflow-tooltip />
        <el-table-column prop="scenario" label="场景" width="150px" show-overflow-tooltip />
        <el-table-column prop="queryText" label="查询文本" min-width="220px" show-overflow-tooltip />
        <el-table-column prop="topK" label="TopK" width="80px" />
        <el-table-column prop="hitCount" label="命中数" width="90px" />
        <el-table-column label="检索延迟" width="110px">
          <template #default="{ row }">{{ formatDuration(row.latencyMs) }}</template>
        </el-table-column>
        <el-table-column label="上下文 token" width="120px">
          <template #default="{ row }">{{ formatNumber(row.contextTokenEstimate) }}</template>
        </el-table-column>
        <el-table-column label="是否降级" width="100px">
          <template #default="{ row }">
            <el-tag :type="row.isDegraded ? 'warning' : 'success'" size="small" disable-transitions>
              {{ row.isDegraded ? '降级' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fallbackReason" label="降级原因" width="160px" show-overflow-tooltip />
        <el-table-column prop="createdTime" label="时间" width="170px" />
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="ragQuery.current"
          v-model:page-size="ragQuery.size"
          :page-sizes="[10, 20, 50]"
          background
          layout="total, sizes, prev, pager, next"
          :total="ragTotal"
          @size-change="searchRagLogs"
          @current-change="fetchRagLogs"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
/* 空状态的补充说明：比 el-empty 默认文案略小、颜色收敛，避免喧宾夺主 */
.empty-hint { margin-top: 6px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }

.audit-alert {
  margin-bottom: 16px;
}

.audit-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.metric-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px 14px;
  background: var(--el-fill-color-blank);
}

.metric-card--strong {
  border-color: var(--el-color-primary-light-5);
}

.metric-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.metric-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  line-height: 1.2;
}

.metric-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.metric-note {
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 图表两列自适应：窄屏落成一列，不做横向滚动（横向滚动只交给 el-table） */
.chart-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.chart-cell {
  min-width: 0;
}

.chart-title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.chart-title--spaced {
  margin-top: 20px;
}

.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  align-items: center;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
