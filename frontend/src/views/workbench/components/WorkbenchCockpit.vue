<script setup lang="ts">
/**
 * 工作台「匹配分析」区块（原独立「数据看板 / 匹配驾驶舱」并入）。
 *
 * 2026-09-04 合并：原 /dashboard 是独立一页，内容与工作台高度重叠，
 * 使用者要在「首页」与「数据看板」之间来回切。现并入工作台，**仅 HR 渲染**
 * （员工的数据范围只有本人，其余角色不看匹配运营数据）。
 *
 * 刻意不重复指标卡：员工数/岗位数/匹配记录总数已由工作台指标卡展示，
 * 这里只补工作台缺的 —— **分布**（分数分档、状态构成）与**最近匹配记录明细**。
 * 趋势折线也已在工作台的「趋势」面板中，故不再重复。
 *
 * 配色：白蓝为主。分档用同一蓝色梯度（越强越深）表达「分数高低是有序的」，
 * 中性灰留给「待审核」—— 避免用红绿造成「好坏」判断之外的额外暗示。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import type { EChartsOption } from 'echarts'
import { getMatchingDashboardSummary } from '@/api'
import type { MatchingRecord } from '@/api'
import EChartsWrapper from '@/components/chart/EChartsWrapper.vue'
import { WORKBENCH_CHART as CHART } from '../workbench-chart-theme'

const loading = ref(false)
const refreshing = ref(false)
const recentRecords = ref<MatchingRecord[]>([])

/** 分数分档：同色系梯度，越强越深（配色统一来自 workbench-chart-theme） */
const scoreDistribution = ref([
  { label: '强匹配 90-100', count: 0, color: CHART.strong },
  { label: '匹配 75-89', count: 0, color: CHART.primary },
  { label: '待观察 60-74', count: 0, color: CHART.light },
  { label: '不匹配 0-59', count: 0, color: CHART.lighter },
])

const matchStatusDistribution = ref([
  { label: '强匹配', count: 0, color: CHART.strong },
  { label: '匹配', count: 0, color: CHART.primary },
  { label: '待观察', count: 0, color: CHART.light },
  { label: '不匹配', count: 0, color: CHART.lighter },
  { label: '待审核', count: 0, color: CHART.neutral },
])

const hasScoreData = computed(() => scoreDistribution.value.some(item => item.count > 0))
const hasStatusData = computed(() => matchStatusDistribution.value.some(item => item.count > 0))

const scorePieOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item', formatter: '{b}：{c} 条（{d}%）' },
  legend: {
    orient: 'vertical',
    right: '4%',
    top: 'center',
    itemWidth: 10,
    itemHeight: 10,
    textStyle: { fontSize: 12, color: CHART.axis },
  },
  series: [{
    name: '匹配分数分布',
    type: 'pie',
    radius: ['50%', '72%'],
    center: ['34%', '50%'],
    avoidLabelOverlap: false,
    itemStyle: { borderRadius: 8, borderColor: CHART.cardBorder, borderWidth: 2 },
    label: { show: false },
    emphasis: { label: { show: true, fontSize: 13, fontWeight: 'bold' } },
    labelLine: { show: false },
    data: scoreDistribution.value.map(item => ({
      value: item.count,
      name: item.label,
      itemStyle: { color: item.color },
    })),
  }],
}))

const statusRoseOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item', formatter: '{b}：{c} 条（{d}%）' },
  legend: {
    orient: 'vertical',
    right: '4%',
    top: 'center',
    itemWidth: 10,
    itemHeight: 10,
    textStyle: { fontSize: 12, color: '#5b6078' },
  },
  series: [{
    name: '匹配状态分布',
    type: 'pie',
    radius: ['22%', '70%'],
    center: ['34%', '50%'],
    roseType: 'area',
    itemStyle: { borderRadius: 6, borderColor: CHART.cardBorder, borderWidth: 2 },
    label: { show: false },
    labelLine: { show: false },
    data: matchStatusDistribution.value.map(item => ({
      value: item.count,
      name: item.label,
      itemStyle: { color: item.color },
    })),
  }],
}))

const STATUS_TEXT: Record<number, string> = { 0: '待审核', 1: '强匹配', 2: '匹配', 3: '待观察', 4: '不匹配' }

function statusText(status: number) {
  return STATUS_TEXT[status] ?? '未知'
}

/** 分数配色与分布图同源，保证同一分档在两处是同一个颜色 */
/** 与分布图同档同色：同一分档在饼图与表格里必须是一个颜色 */
function scoreColor(score?: number) {
  if (score == null) return CHART.axis
  if (score >= 90) return CHART.strong
  if (score >= 75) return CHART.primary
  if (score >= 60) return CHART.light
  return CHART.axis
}

function statusTone(status: number) {
  if (status === 1) return 'is-strong'
  if (status === 2) return 'is-match'
  if (status === 3) return 'is-watch'
  if (status === 4) return 'is-none'
  return 'is-pending'
}

async function load() {
  loading.value = true
  try {
    const res = await getMatchingDashboardSummary()
    const data = (res.data || {}) as Record<string, any>
    recentRecords.value = Array.isArray(data.recent) ? data.recent : []
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
    // 分析区块拉取失败不应影响工作台其余内容：保持空态即可
    recentRecords.value = []
  } finally {
    loading.value = false
  }
}

async function refresh() {
  refreshing.value = true
  try {
    await load()
    ElMessage.success('匹配分析已刷新')
  } finally {
    refreshing.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="cockpit" v-loading="loading">
    <header class="cockpit__head">
      <div>
        <h2>匹配分析</h2>
        <p>匹配分数分档、状态构成与最近记录</p>
      </div>
      <button class="cockpit__refresh" type="button" :disabled="refreshing" @click="refresh">
        <el-icon :class="{ 'is-spinning': refreshing }"><Refresh /></el-icon>
        <span>刷新</span>
      </button>
    </header>

    <div class="cockpit__charts">
      <article class="cockpit__card">
        <h3>匹配分数分布</h3>
        <EChartsWrapper v-if="hasScoreData" :option="scorePieOption" :height="CHART.height" />
        <div v-else class="cockpit__empty">暂无匹配分数数据</div>
      </article>

      <article class="cockpit__card">
        <h3>匹配状态分布</h3>
        <EChartsWrapper v-if="hasStatusData" :option="statusRoseOption" :height="CHART.height" />
        <div v-else class="cockpit__empty">暂无匹配状态数据</div>
      </article>
    </div>

    <article class="cockpit__card cockpit__card--wide">
      <header class="cockpit__card-head">
        <h3>最近匹配记录</h3>
        <span v-if="recentRecords.length" class="cockpit__count">{{ recentRecords.length }} 条</span>
      </header>
      <el-table v-if="recentRecords.length" :data="recentRecords" size="small" style="width: 100%">
        <el-table-column prop="empName" label="员工" min-width="130">
          <template #default="{ row }">{{ row.empName || `员工#${row.empId}` }}</template>
        </el-table-column>
        <el-table-column prop="postName" label="岗位" min-width="150">
          <template #default="{ row }">{{ row.postName || `岗位#${row.postId}` }}</template>
        </el-table-column>
        <el-table-column label="AI 匹配分" width="120">
          <template #default="{ row }">
            <b class="cockpit__score" :style="{ color: scoreColor(row.aiMatchScore) }">
              {{ row.aiMatchScore ?? '--' }}
            </b>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <span class="cockpit__pill" :class="statusTone(row.matchStatus)">{{ statusText(row.matchStatus) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="时间" min-width="170" />
      </el-table>
      <div v-else class="cockpit__empty">暂无匹配记录</div>
    </article>
  </section>
</template>

<style scoped>
/* 白蓝为主：白卡 + 蓝强调，与工作台其余卡片共用同一套 token 与尺寸规范 */
.cockpit { display: flex; flex-direction: column; gap: 14px; }

.cockpit__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.cockpit__head h2 { margin: 0; color: var(--app-text-strong); font-size: 15px; font-weight: 700; }
.cockpit__head p { margin: 5px 0 0; color: var(--app-text-muted); font-size: 11px; }
.cockpit__refresh {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: var(--app-surface);
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}
.cockpit__refresh:hover:not(:disabled) { border-color: var(--app-primary); background: var(--app-surface-soft); }
.cockpit__refresh:disabled { opacity: 0.7; cursor: default; }
.cockpit__refresh .is-spinning { animation: cockpit-spin 0.9s linear infinite; }
@keyframes cockpit-spin { to { transform: rotate(360deg); } }

.cockpit__charts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.cockpit__card {
  padding: 18px 20px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface);
  box-shadow: var(--app-shadow-sm);
}
.cockpit__card--wide { width: 100%; box-sizing: border-box; }
.cockpit__card h3 { margin: 0 0 12px; color: var(--app-text-strong); font-size: 13px; font-weight: 700; }
.cockpit__card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.cockpit__card-head h3 { margin: 0; }
.cockpit__count { color: var(--app-text-muted); font-size: 12px; }
.cockpit__empty {
  display: grid;
  place-items: center;
  min-height: 160px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.cockpit__score { font-size: 14px; font-weight: 750; }
.cockpit__pill {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}
/* 状态色与分布图同源（蓝梯度 + 中性灰） */
.cockpit__pill.is-strong { color: #1d4ed8; background: rgba(29, 78, 216, 0.12); }
.cockpit__pill.is-match { color: #2563eb; background: rgba(37, 99, 235, 0.1); }
.cockpit__pill.is-watch { color: #3b82f6; background: rgba(96, 165, 250, 0.16); }
.cockpit__pill.is-none { color: #64748b; background: rgba(148, 163, 184, 0.18); }
.cockpit__pill.is-pending { color: #64748b; background: rgba(203, 213, 225, 0.35); }

@media (max-width: 1180px) {
  .cockpit__charts { grid-template-columns: minmax(0, 1fr); }
}
</style>
