<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Briefcase, Connection, Refresh, TrendCharts, User } from '@element-plus/icons-vue'
import type { EChartsOption } from 'echarts'
import { pageEmployees, pagePosts, getMatchingDashboardSummary, testAll } from '@/api'
import type { MatchingRecord } from '@/api'
import EChartsWrapper from '@/components/chart/EChartsWrapper.vue'

const loading = ref(false)
const syncLoading = ref(false)

const stats = ref([
  { label: '员工总数', value: 0, icon: User, color: '#2563eb', bg: 'rgba(37,99,235,0.12)' },
  { label: '岗位总数', value: 0, icon: Briefcase, color: '#059669', bg: 'rgba(5,150,105,0.12)' },
  { label: '匹配记录', value: 0, icon: Connection, color: '#d97706', bg: 'rgba(217,119,6,0.12)' },
  { label: '服务状态', value: '--', icon: TrendCharts, color: '#dc2626', bg: 'rgba(220,38,38,0.12)' },
])

const recentRecords = ref<MatchingRecord[]>([])

const scoreDistribution = ref([
  { label: '强匹配 90-100', count: 0, color: '#059669' },
  { label: '匹配 75-89', count: 0, color: '#2563eb' },
  { label: '待观察 60-74', count: 0, color: '#d97706' },
  { label: '不匹配 0-59', count: 0, color: '#dc2626' },
])

const matchStatusDistribution = ref([
  { label: '强匹配', count: 0, color: '#059669' },
  { label: '匹配', count: 0, color: '#2563eb' },
  { label: '待观察', count: 0, color: '#d97706' },
  { label: '不匹配', count: 0, color: '#dc2626' },
  { label: '待审核', count: 0, color: '#64748b' },
])

const scorePieOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { orient: 'vertical', right: '2%', top: 'center', textStyle: { fontSize: 12, color: '#5b6078' } },
  series: [{
    name: '匹配分数分布',
    type: 'pie',
    radius: ['48%', '74%'],
    center: ['38%', '50%'],
    avoidLabelOverlap: false,
    itemStyle: { borderRadius: 10, borderColor: 'rgba(255,255,255,0.72)', borderWidth: 2 },
    label: { show: false },
    emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
    labelLine: { show: false },
    data: scoreDistribution.value.map((item) => ({ value: item.count, name: item.label, itemStyle: { color: item.color } })),
  }],
}))

const statusRoseOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { orient: 'vertical', right: '2%', top: 'center', textStyle: { fontSize: 12, color: '#5b6078' } },
  series: [{
    name: '匹配状态分布',
    type: 'pie',
    radius: ['18%', '72%'],
    center: ['38%', '50%'],
    roseType: 'area',
    itemStyle: { borderRadius: 8, borderColor: 'rgba(255,255,255,0.72)', borderWidth: 2 },
    label: { show: true, formatter: '{b}\n{c}', fontSize: 11 },
    labelLine: { length: 12, length2: 10 },
    data: matchStatusDistribution.value.map((item) => ({ value: item.count, name: item.label, itemStyle: { color: item.color } })),
  }],
}))

const trendOption = computed<EChartsOption>(() => {
  const records = recentRecords.value.slice(0, 10).reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '3%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: records.map((_, index) => `记录 ${index + 1}`),
      axisLabel: { fontSize: 11, color: '#8b90a7' },
      axisLine: { lineStyle: { color: 'rgba(0,0,0,0.06)' } },
    },
    yAxis: {
      type: 'value',
      name: '分数',
      min: 0,
      max: 100,
      splitLine: { lineStyle: { type: 'dashed', color: 'rgba(0,0,0,0.06)' } },
      axisLabel: { color: '#8b90a7' },
    },
    series: [{
      name: 'AI 匹配分',
      type: 'bar',
      barWidth: '42%',
      data: records.map((record) => ({
        value: record.aiMatchScore,
        itemStyle: {
          color: record.aiMatchScore >= 90 ? '#059669' : record.aiMatchScore >= 75 ? '#2563eb' : record.aiMatchScore >= 60 ? '#d97706' : '#dc2626',
          borderRadius: [8, 8, 0, 0],
        },
      })),
      label: { show: true, position: 'top', fontSize: 11, color: '#5b6078' },
    }],
  }
})

function getScoreColor(score: number) {
  if (score >= 90) return '#059669'
  if (score >= 75) return '#2563eb'
  if (score < 60) return '#dc2626'
  return '#d97706'
}

function getMatchStatusText(status: number) {
  const map: Record<number, string> = { 0: '待审核', 1: '强匹配', 2: '匹配', 3: '待观察', 4: '不匹配' }
  return map[status] || '未知'
}

async function loadStats() {
  loading.value = true
  try {
    try {
      const empRes = await pageEmployees({ current: 1, size: 1 })
      const data = empRes.data as any
      stats.value[0].value = data?.total || data?.records?.length || 0
    } catch {
      stats.value[0].value = 0
    }

    try {
      const postRes = await pagePosts({ current: 1, size: 1 })
      const data = postRes.data as any
      stats.value[1].value = data?.total || data?.records?.length || 0
    } catch {
      stats.value[1].value = 0
    }

    try {
      const recordSummary = await getMatchingDashboardSummary()
      const data = recordSummary.data || {}
      recentRecords.value = data.recent || []
      stats.value[2].value = data.total || 0
      scoreDistribution.value[0].count = data.score90 || 0
      scoreDistribution.value[1].count = data.score75 || 0
      scoreDistribution.value[2].count = data.score60 || 0
      scoreDistribution.value[3].count = data.scoreBelow60 || 0
      matchStatusDistribution.value[0].count = data.status1 || 0
      matchStatusDistribution.value[1].count = data.status2 || 0
      matchStatusDistribution.value[2].count = data.status3 || 0
      matchStatusDistribution.value[3].count = data.status4 || 0
      matchStatusDistribution.value[4].count = data.status0 || 0
    } catch {
      stats.value[2].value = 0
    }

    try {
      await testAll()
      stats.value[3].value = '正常'
    } catch {
      stats.value[3].value = '异常'
    }
  } finally {
    loading.value = false
  }
}

async function syncGraph() {
  syncLoading.value = true
  try {
    await loadStats()
    ElMessage.success('数据已刷新')
  } finally {
    syncLoading.value = false
  }
}

onMounted(() => {
  loadStats()
})
</script>

<template>
  <div class="page-shell" v-loading="loading">
    <!-- Hero -->
    <section class="dash-hero">
      <div class="dash-hero__badge">Graph Dashboard</div>
      <div class="dash-hero__body">
        <div class="dash-hero__text">
          <h1 class="dash-hero__title">匹配驾驶舱</h1>
          <p class="dash-hero__desc">多源异构数据驱动的岗位与能力图谱动态演化实时态势感知。</p>
          <div class="dash-hero__tags">
            <span>Multi-Source Data Fusion</span>
            <span>Dynamic Graph Evolution</span>
            <span>Feedback-Driven Optimization</span>
          </div>
        </div>
        <div class="dash-hero__action">
          <button class="btn-refresh" :disabled="syncLoading" @click="syncGraph">
            <el-icon :class="{ 'is-spinning': syncLoading }"><Refresh /></el-icon>
            <span>刷新数据</span>
          </button>
        </div>
      </div>
    </section>

    <!-- Metric Cards -->
    <section class="dash-metrics">
      <article
        v-for="(item, i) in stats"
        :key="item.label"
        class="dash-metric"
        :style="{ animationDelay: `${0.08 + i * 0.06}s` }"
      >
        <div class="dash-metric__icon" :style="{ backgroundColor: item.bg, color: item.color }">
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
        </div>
        <div class="dash-metric__info">
          <div class="dash-metric__label">{{ item.label }}</div>
          <div class="dash-metric__value">{{ item.value }}</div>
        </div>
      </article>
    </section>

    <!-- Charts Row -->
    <section class="dash-charts">
      <div class="dash-card" :style="{ animationDelay: '0.15s' }">
        <div class="dash-card__header">
          <span class="dash-card__title">匹配分数分布</span>
        </div>
        <div class="dash-card__body">
          <template v-if="scoreDistribution.some((item) => item.count > 0)">
            <EChartsWrapper :option="scorePieOption" height="280px" />
          </template>
          <div v-else class="dash-empty">暂无数据</div>
        </div>
      </div>

      <div class="dash-card" :style="{ animationDelay: '0.21s' }">
        <div class="dash-card__header">
          <span class="dash-card__title">匹配状态分布</span>
        </div>
        <div class="dash-card__body">
          <template v-if="matchStatusDistribution.some((item) => item.count > 0)">
            <EChartsWrapper :option="statusRoseOption" height="280px" />
          </template>
          <div v-else class="dash-empty">暂无数据</div>
        </div>
      </div>
    </section>

    <!-- Trend -->
    <section class="dash-card" :style="{ animationDelay: '0.27s' }">
      <div class="dash-card__header">
        <span class="dash-card__title">最近匹配分趋势</span>
      </div>
      <div class="dash-card__body">
        <template v-if="recentRecords.length > 0">
          <EChartsWrapper :option="trendOption" height="260px" />
        </template>
        <div v-else class="dash-empty">暂无匹配记录</div>
      </div>
    </section>

    <!-- Table -->
    <section class="dash-card" :style="{ animationDelay: '0.33s' }">
      <div class="dash-card__header">
        <span class="dash-card__title">最近匹配记录</span>
        <span class="dash-card__count" v-if="recentRecords.length > 0">{{ recentRecords.length }} 条</span>
      </div>
      <div class="dash-card__body--flush">
        <el-table v-if="recentRecords.length > 0" :data="recentRecords" style="width: 100%">
          <el-table-column prop="batchNo" label="批次号" min-width="180" />
          <el-table-column prop="empId" label="员工 ID" min-width="120" />
          <el-table-column prop="postId" label="岗位 ID" min-width="120" />
          <el-table-column label="AI 匹配分" min-width="120">
            <template #default="{ row }">
              <span class="dash-score" :style="{ color: getScoreColor(row.aiMatchScore) }">{{ row.aiMatchScore }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="120">
            <template #default="{ row }">
              <span
                class="dash-pill"
                :class="{
                  'is-success': row.matchStatus === 1,
                  'is-primary': row.matchStatus === 2,
                  'is-warning': row.matchStatus === 3,
                  'is-danger': row.matchStatus === 4,
                }"
              >
                {{ getMatchStatusText(row.matchStatus) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="createdTime" label="时间" min-width="180" />
        </el-table>
        <div v-else class="dash-empty">暂无匹配记录</div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ====== Dashboard — Variant C ====== */

/* ---- Entry Animation ---- */
@keyframes dashRise {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes dashGlare {
  0%, 48% { transform: translateX(-120%); opacity: 0; }
  58% { opacity: 1; }
  78%, 100% { transform: translateX(120%); opacity: 0; }
}

/* ---- Hero ---- */
.dash-hero {
  position: relative;
  padding: 20px 24px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.05), transparent 50%),
              rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(12px);
  box-shadow: 0 2px 16px rgba(15, 23, 42, 0.04);
  overflow: hidden;
  animation: dashRise 0.45s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.dash-hero::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(110deg, transparent 0%, rgba(255, 255, 255, 0.35) 42%, transparent 68%);
  transform: translateX(-120%);
  animation: dashGlare 5.2s ease-in-out infinite;
}

.dash-hero__badge {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 6px;
  background: rgba(59, 130, 246, 0.08);
  color: var(--app-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  margin-bottom: 10px;
}

.dash-hero__body {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.dash-hero__text {
  flex: 1;
  min-width: 0;
}

.dash-hero__title {
  margin: 0;
  font-size: 30px;
  font-weight: 800;
  color: var(--app-text-strong);
  letter-spacing: -0.04em;
  line-height: 1.1;
}

.dash-hero__desc {
  margin: 6px 0 0;
  max-width: 600px;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.55;
}

.dash-hero__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.dash-hero__tags span {
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--app-text-muted);
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(148, 163, 184, 0.1);
}

.dash-hero__action {
  flex-shrink: 0;
}

.btn-refresh {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.8);
  color: var(--app-text-secondary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.22s ease;
}

.btn-refresh:hover {
  background: rgba(59, 130, 246, 0.06);
  border-color: rgba(59, 130, 246, 0.25);
  color: var(--app-primary);
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.1);
}

.btn-refresh:active {
  transform: translateY(0);
}

.is-spinning {
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ---- Metrics ---- */
.dash-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.dash-metric {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.13);
  background: rgba(255, 255, 255, 0.54);
  backdrop-filter: blur(8px);
  box-shadow: 0 1px 8px rgba(15, 23, 42, 0.03);
  animation: dashRise 0.45s cubic-bezier(0.22, 1, 0.36, 1) both;
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
  cursor: default;
  position: relative;
  overflow: hidden;
}

.dash-metric:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(15, 23, 42, 0.06);
  border-color: rgba(59, 130, 246, 0.16);
}

.dash-metric::after {
  content: '';
  position: absolute;
  inset: auto 12px 8px 12px;
  height: 2px;
  border-radius: 1px;
  background: linear-gradient(90deg, transparent, rgba(59, 130, 246, 0.3), transparent);
  opacity: 0;
  transform: scaleX(0.4);
  transition: opacity 0.3s ease, transform 0.5s ease;
}

.dash-metric:hover::after {
  opacity: 1;
  transform: scaleX(1);
}

.dash-metric__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 9px;
  flex-shrink: 0;
  transition: transform 0.22s ease;
}

.dash-metric:hover .dash-metric__icon {
  transform: scale(1.08);
}

.dash-metric__info {
  min-width: 0;
}

.dash-metric__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--app-text-muted);
  letter-spacing: 0.02em;
  line-height: 1;
}

.dash-metric__value {
  margin-top: 5px;
  font-size: 24px;
  font-weight: 800;
  color: var(--app-text-strong);
  letter-spacing: -0.03em;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

/* ---- Cards ---- */
.dash-charts {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.dash-card {
  position: relative;
  border: 1px solid rgba(148, 163, 184, 0.13);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  box-shadow: 0 1px 12px rgba(15, 23, 42, 0.04);
  overflow: hidden;
  animation: dashRise 0.45s cubic-bezier(0.22, 1, 0.36, 1) both;
  transition: box-shadow 0.25s ease, border-color 0.25s ease;
}

.dash-card:hover {
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.06);
  border-color: rgba(148, 163, 184, 0.2);
}

.dash-card::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.18), transparent 40%);
  border-radius: inherit;
  z-index: 0;
}

.dash-card__header {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px 0;
}

.dash-card__title {
  font-size: 14px;
  font-weight: 700;
  color: var(--app-text-strong);
  letter-spacing: -0.01em;
}

.dash-card__count {
  font-size: 11px;
  color: var(--app-text-muted);
  font-weight: 500;
}

.dash-card__body {
  position: relative;
  z-index: 1;
  padding: 12px 14px 18px;
}

.dash-card__body--flush {
  position: relative;
  z-index: 1;
  padding: 0;
}

/* ---- Empty State ---- */
.dash-empty {
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--app-text-muted);
  font-size: 13px;
}

/* ---- Score ---- */
.dash-score {
  font-size: 16px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

/* ---- Pills ---- */
.dash-pill {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
}

.dash-pill.is-success {
  color: var(--app-success);
  background: var(--app-success-soft);
}

.dash-pill.is-primary {
  color: var(--app-primary);
  background: var(--app-primary-soft);
}

.dash-pill.is-warning {
  color: var(--app-warning);
  background: var(--app-warning-soft);
}

.dash-pill.is-danger {
  color: var(--app-danger);
  background: var(--app-danger-soft);
}

/* ---- Responsive ---- */
@media (max-width: 1024px) {
  .dash-metrics { grid-template-columns: repeat(2, 1fr); }
  .dash-charts { grid-template-columns: 1fr; }
}

@media (max-width: 720px) {
  .dash-metrics { grid-template-columns: 1fr; }
  .dash-hero__body { flex-direction: column; }
  .dash-hero__title { font-size: 24px; }
}
</style>
