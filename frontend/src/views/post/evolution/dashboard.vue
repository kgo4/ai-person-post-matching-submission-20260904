<template>
  <div class="evolution-dashboard">
    <!-- Animated Background -->
    <div class="bg-particles">
      <div v-for="i in 15" :key="i" class="particle" :style="getParticleStyle(i)"></div>
    </div>

    <!-- Header -->
    <div class="dashboard-header">
      <div class="header-left">
        <div class="title-row">
          <h2 class="page-title">岗位动态演化</h2>
          <div class="pulse-badge">
            <span class="pulse-dot"></span>
            <span>实时监控中</span>
          </div>
        </div>
        <p class="page-subtitle">追踪岗位能力的动态变化轨迹与演化趋势</p>
      </div>
      <div class="header-right">
        <el-select v-model="selectedRange" class="time-select" @change="handleRangeChange">
          <el-option label="最近 7 天" value="7d" />
          <el-option label="最近 30 天" value="30d" />
          <el-option label="最近 90 天" value="90d" />
        </el-select>
        <el-select v-model="selectedPostId" class="post-select" clearable placeholder="全部岗位" @change="handlePostChange">
          <el-option v-for="post in postOptions" :key="post.id" :label="post.postName" :value="post.id" />
        </el-select>
      </div>
    </div>

    <!-- Stats Cards -->
    <div class="stats-grid">
      <div v-for="(stat, index) in statsCards" :key="stat.id" class="stat-card" :style="{ animationDelay: `${index * 0.1}s` }">
        <div class="stat-card__icon" :style="{ background: stat.iconBg }">
          <el-icon :size="20" :color="stat.iconColor"><component :is="stat.icon" /></el-icon>
        </div>
        <div class="stat-card__content">
          <div class="stat-card__value">{{ stat.value }}</div>
          <div class="stat-card__label">{{ stat.label }}</div>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="main-content">
      <!-- Left: Timeline with drag -->
      <div class="timeline-section">
        <div class="timeline-card">
          <div class="timeline-card__header">
            <h3><el-icon><Clock /></el-icon> 演化时间轴</h3>
            <el-button text type="primary" size="small" @click="refreshTimeline">刷新</el-button>
          </div>

          <!-- Draggable timeline scrubber -->
          <div class="timeline-scrubber" ref="scrubberRef">
            <div class="scrubber-track" @mousedown="startScrub" @touchstart.passive="startScrubTouch">
              <div class="scrubber-fill" :style="{ width: scrubPosition + '%' }"></div>
              <div class="scrubber-handle" :style="{ left: scrubPosition + '%' }" @mousedown.stop="startDrag">
                <div class="handle-tooltip">{{ currentTimeLabel }}</div>
              </div>
            </div>
            <div class="scrubber-labels">
              <span>{{ timelineStartLabel }}</span>
              <span>{{ timelineEndLabel }}</span>
            </div>
          </div>

          <!-- Timeline events -->
          <div class="timeline-container" ref="timelineContainer">
            <div class="timeline-line"></div>
            <div
              v-for="(event, index) in timelineEvents"
              :key="event.id"
              class="timeline-item"
              :class="{ 'is-selected': selectedEventId === event.id }"
              :style="{ animationDelay: `${index * 0.05}s` }"
              @click="handleEventClick(event)"
            >
              <div class="timeline-dot" :class="event.type">
                <div class="dot-pulse"></div>
              </div>
              <div class="timeline-content">
                <div class="timeline-time">{{ formatTimeAgo(event.time) }}</div>
                <div class="timeline-event">
                  <div class="event-icon" :class="event.type">
                    <el-icon :size="14"><component :is="getEventIcon(event.icon)" /></el-icon>
                  </div>
                  <div class="event-info">
                    <span class="event-title">{{ event.title }}</span>
                    <span class="event-desc">{{ event.description }}</span>
                  </div>
                </div>
                <div v-if="event.abilities?.length" class="timeline-abilities">
                  <span v-for="ability in event.abilities" :key="ability" class="ability-tag" :class="event.type">
                    {{ ability }}
                  </span>
                </div>
              </div>
            </div>

            <div v-if="timelineLoading" class="timeline-loading">
              <el-icon class="loading-spinner"><Loading /></el-icon>
              加载中...
            </div>

            <div v-if="!timelineLoading && timelineEvents.length === 0" class="timeline-empty">
              暂无演化事件
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Graph + Chart -->
      <div class="right-panel">
        <!-- Dynamic Evolution Graph -->
        <div class="graph-card">
          <div class="graph-card__header">
            <h3><el-icon><Share /></el-icon> 能力演化图谱</h3>
            <div class="graph-controls">
              <el-button-group size="small">
                <el-button :type="graphView === 'force' ? 'primary' : ''" @click="graphView = 'force'">力导向</el-button>
                <el-button :type="graphView === 'tree' ? 'primary' : ''" @click="graphView = 'tree'">树形</el-button>
              </el-button-group>
            </div>
          </div>
          <div class="graph-container" ref="graphContainer">
            <e-charts-wrapper v-if="graphOption" :option="graphOption" height="350px" />
            <div v-else class="graph-placeholder">
              <el-icon :size="48"><Share /></el-icon>
              <p>选择岗位查看能力演化图谱</p>
            </div>
          </div>
        </div>

        <!-- Trend Chart -->
        <div class="chart-card">
          <div class="chart-card__header">
            <h3><el-icon><TrendCharts /></el-icon> 演化趋势</h3>
            <div class="chart-legend">
              <span class="legend-item"><span class="legend-dot" style="background: #10b981"></span> 新增</span>
              <span class="legend-item"><span class="legend-dot" style="background: #f59e0b"></span> 变更</span>
              <span class="legend-item"><span class="legend-dot" style="background: #ef4444"></span> 移除</span>
            </div>
          </div>
          <div class="chart-container">
            <e-charts-wrapper :option="trendChartOption" height="250px" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import {
  Clock, Loading, Share, TrendCharts, Plus, Edit, Delete, Finished,
  DataLine, CircleCheck, Timer, Warning
} from '@element-plus/icons-vue'
import EChartsWrapper from '@/components/chart/EChartsWrapper.vue'
import type { EChartsOption } from 'echarts'
import {
  getEvolutionTimeline,
  getEvolutionDashboardStats,
  getEvolutionTrends,
  getEvolutionGraph
} from '@/api/evolution'
import { pagePosts } from '@/api/post'
import type {
  EvolutionTimelineEvent,
  EvolutionDashboardStats,
  EvolutionTrends,
  EvolutionGraph
} from '@/api/evolution'

const router = useRouter()

// ===================== State =====================
const selectedRange = ref('30d')
const selectedPostId = ref<number | undefined>(undefined)
const selectedEventId = ref<string | null>(null)
const graphView = ref<'force' | 'tree'>('force')

// Data
const timelineEvents = ref<EvolutionTimelineEvent[]>([])
const dashboardStats = ref<EvolutionDashboardStats>({ totalTasks: 0, completedTasks: 0, pendingChanges: 0, highRiskChanges: 0 })
const evolutionTrends = ref<EvolutionTrends | null>(null)
const evolutionGraph = ref<EvolutionGraph | null>(null)
const postOptions = ref<{ id: number; postName: string }[]>([])

// Loading states
const timelineLoading = ref(false)
const statsLoading = ref(false)

// Timeline scrubber
const scrubPosition = ref(100)
const scrubberRef = ref<HTMLElement | null>(null)
const timelineContainer = ref<HTMLElement | null>(null)
const isDragging = ref(false)

// ===================== Computed =====================
const statsCards = computed(() => [
  {
    id: 'total',
    label: '总演化任务',
    value: dashboardStats.value.totalTasks,
    icon: 'DataLine',
    iconBg: 'linear-gradient(135deg, rgba(99, 102, 241, 0.15), rgba(99, 102, 241, 0.25))',
    iconColor: '#6366f1',
  },
  {
    id: 'completed',
    label: '已完成任务',
    value: dashboardStats.value.completedTasks,
    icon: 'CircleCheck',
    iconBg: 'linear-gradient(135deg, rgba(16, 185, 129, 0.15), rgba(16, 185, 129, 0.25))',
    iconColor: '#10b981',
  },
  {
    id: 'pending',
    label: '待审核变更',
    value: dashboardStats.value.pendingChanges,
    icon: 'Timer',
    iconBg: 'linear-gradient(135deg, rgba(245, 158, 11, 0.15), rgba(245, 158, 11, 0.25))',
    iconColor: '#f59e0b',
  },
  {
    id: 'risk',
    label: '高风险变更',
    value: dashboardStats.value.highRiskChanges,
    icon: 'Warning',
    iconBg: 'linear-gradient(135deg, rgba(239, 68, 68, 0.15), rgba(239, 68, 68, 0.25))',
    iconColor: '#ef4444',
  },
])

const currentTimeLabel = computed(() => {
  const events = timelineEvents.value
  if (events.length === 0) return ''
  const idx = Math.floor((scrubPosition.value / 100) * (events.length - 1))
  const event = events[Math.min(idx, events.length - 1)]
  return event ? formatTimeAgo(event.time) : ''
})

const timelineStartLabel = computed(() => {
  if (timelineEvents.value.length === 0) return ''
  return formatTimeAgo(timelineEvents.value[timelineEvents.value.length - 1]?.time)
})

const timelineEndLabel = computed(() => {
  if (timelineEvents.value.length === 0) return ''
  return formatTimeAgo(timelineEvents.value[0]?.time)
})

const graphOption = computed((): EChartsOption | null => {
  if (!evolutionGraph.value) return null
  const { nodes, edges } = evolutionGraph.value

  const colorMap: Record<string, string> = {
    post: '#3b82f6',
    core: '#10b981',
    normal: '#93c5fd',
    change: '#f59e0b',
  }

  return {
    tooltip: {
      trigger: 'item' as const,
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: 'rgba(148, 163, 184, 0.2)',
      textStyle: { color: '#1e293b' },
    },
    series: [{
      type: 'graph' as const,
      layout: graphView.value === 'force' ? 'force' as const : 'none' as const,
      roam: true,
      label: { show: true, fontSize: 11 },
      force: graphView.value === 'force' ? {
        repulsion: 200,
        gravity: 0.1,
        edgeLength: [80, 160],
        layoutAnimation: true,
      } : undefined,
      data: nodes.map(n => ({
        id: n.id,
        name: n.label,
        symbolSize: n.size,
        itemStyle: { color: colorMap[n.type] || '#94a3b8' },
        category: n.type,
      })),
      edges: edges.map(e => ({
        source: e.source,
        target: e.target,
        lineStyle: {
          color: e.type === 'change' ? '#f59e0b' : 'rgba(148, 163, 184, 0.3)',
          width: e.weight ? Math.max(1, e.weight / 20) : 1,
          type: e.type === 'change' ? 'dashed' : 'solid',
        },
      })),
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 3 },
      },
      animationDuration: 1500,
      animationEasing: 'cubicOut',
    }],
  }
})

const trendChartOption = computed((): EChartsOption => {
  const monthly = evolutionTrends.value?.monthly || {}
  const months = Object.keys(monthly).sort()

  return {
    tooltip: { trigger: 'axis' as const },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category' as const,
      data: months,
      axisLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.2)' } },
      axisLabel: { color: '#64748b' },
    },
    yAxis: {
      type: 'value' as const,
      axisLine: { show: false },
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.1)' } },
    },
    series: [
      {
        name: '新增',
        type: 'bar' as const,
        stack: 'total',
        data: months.map(m => monthly[m]?.added || 0),
        itemStyle: { color: '#10b981' },
        barWidth: '40%',
      },
      {
        name: '变更',
        type: 'bar' as const,
        stack: 'total',
        data: months.map(m => monthly[m]?.updated || 0),
        itemStyle: { color: '#f59e0b' },
      },
      {
        name: '移除',
        type: 'bar' as const,
        stack: 'total',
        data: months.map(m => monthly[m]?.removed || 0),
        itemStyle: { color: '#ef4444' },
      },
    ],
  }
})

// ===================== Methods =====================
const getParticleStyle = (index: number) => ({
  width: `${Math.random() * 4 + 2}px`,
  height: `${Math.random() * 4 + 2}px`,
  left: `${Math.random() * 100}%`,
  top: `${Math.random() * 100}%`,
  animationDuration: `${Math.random() * 10 + 10}s`,
  animationDelay: `${Math.random() * 5}s`,
})

const formatTimeAgo = (timeStr: string) => {
  if (!timeStr) return ''
  const time = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - time.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  if (hours < 24) return `${hours} 小时前`
  if (days < 30) return `${days} 天前`
  return time.toLocaleDateString()
}

const getEventIcon = (icon: string) => {
  const iconMap: Record<string, any> = {
    Plus: Plus,
    Edit: Edit,
    Delete: Delete,
    Finished: Finished,
  }
  return iconMap[icon] || Edit
}

// Timeline scrubber drag handlers
const startScrub = (e: MouseEvent) => {
  isDragging.value = true
  updateScrubPosition(e)
  document.addEventListener('mousemove', handleScrubMove)
  document.addEventListener('mouseup', stopScrub)
}

const startScrubTouch = (e: TouchEvent) => {
  isDragging.value = true
  updateScrubPositionTouch(e)
  document.addEventListener('touchmove', handleScrubMoveTouch, { passive: false })
  document.addEventListener('touchend', stopScrub)
}

const startDrag = (e: MouseEvent) => {
  isDragging.value = true
  document.addEventListener('mousemove', handleScrubMove)
  document.addEventListener('mouseup', stopScrub)
}

const handleScrubMove = (e: MouseEvent) => {
  if (!isDragging.value) return
  updateScrubPosition(e)
}

const handleScrubMoveTouch = (e: TouchEvent) => {
  if (!isDragging.value) return
  e.preventDefault()
  updateScrubPositionTouch(e)
}

const stopScrub = () => {
  isDragging.value = false
  document.removeEventListener('mousemove', handleScrubMove)
  document.removeEventListener('mouseup', stopScrub)
  document.removeEventListener('touchmove', handleScrubMoveTouch)
  document.removeEventListener('touchend', stopScrub)

  // Scroll to corresponding event
  scrollToEventAtPosition()
}

const updateScrubPosition = (e: MouseEvent) => {
  if (!scrubberRef.value) return
  const rect = scrubberRef.value.getBoundingClientRect()
  const x = e.clientX - rect.left
  scrubPosition.value = Math.max(0, Math.min(100, (x / rect.width) * 100))
}

const updateScrubPositionTouch = (e: TouchEvent) => {
  if (!scrubberRef.value || !e.touches[0]) return
  const rect = scrubberRef.value.getBoundingClientRect()
  const x = e.touches[0].clientX - rect.left
  scrubPosition.value = Math.max(0, Math.min(100, (x / rect.width) * 100))
}

const scrollToEventAtPosition = () => {
  const events = timelineEvents.value
  if (events.length === 0 || !timelineContainer.value) return
  const idx = Math.floor((scrubPosition.value / 100) * (events.length - 1))
  const eventElements = timelineContainer.value.querySelectorAll('.timeline-item')
  if (eventElements[idx]) {
    eventElements[idx].scrollIntoView({ behavior: 'smooth', block: 'center' })
    selectedEventId.value = events[idx].id
  }
}

const handleEventClick = (event: EvolutionTimelineEvent) => {
  selectedEventId.value = event.id
  // Update scrub position to match clicked event
  const idx = timelineEvents.value.findIndex(e => e.id === event.id)
  if (idx >= 0) {
    scrubPosition.value = (idx / (timelineEvents.value.length - 1)) * 100
  }
}

const handleRangeChange = () => {
  loadData()
}

const handlePostChange = () => {
  loadData()
}

const refreshTimeline = () => {
  loadTimeline()
}

// ===================== Data Loading =====================
const loadTimeline = async () => {
  timelineLoading.value = true
  try {
    const res = await getEvolutionTimeline({
      postId: selectedPostId.value,
      range: selectedRange.value,
      limit: 20,
    })
    timelineEvents.value = res.data || []
  } catch (e) {
    console.error('Failed to load timeline:', e)
  } finally {
    timelineLoading.value = false
  }
}

const loadStats = async () => {
  statsLoading.value = true
  try {
    const res = await getEvolutionDashboardStats({ range: selectedRange.value })
    dashboardStats.value = res.data || { totalTasks: 0, completedTasks: 0, pendingChanges: 0, highRiskChanges: 0 }
  } catch (e) {
    console.error('Failed to load stats:', e)
  } finally {
    statsLoading.value = false
  }
}

const loadTrends = async () => {
  try {
    const res = await getEvolutionTrends({ range: selectedRange.value })
    evolutionTrends.value = res.data || null
  } catch (e) {
    console.error('Failed to load trends:', e)
  }
}

const loadGraph = async () => {
  if (!selectedPostId.value) {
    evolutionGraph.value = null
    return
  }
  try {
    const res = await getEvolutionGraph(selectedPostId.value)
    evolutionGraph.value = res.data || null
  } catch (e) {
    console.error('Failed to load graph:', e)
  }
}

const loadPosts = async () => {
  try {
    const res = await pagePosts({ current: 1, size: 500 })
    postOptions.value = res.data?.records || []
  } catch (e) {
    console.error('Failed to load posts:', e)
  }
}

const loadData = () => {
  loadTimeline()
  loadStats()
  loadTrends()
  loadGraph()
}

// ===================== Lifecycle =====================
onMounted(() => {
  loadPosts()
  loadData()
})
</script>

<style scoped>
.evolution-dashboard {
  padding: 20px;
  min-height: 100%;
  position: relative;
  overflow: hidden;
}

/* Background Particles */
.bg-particles {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.particle {
  position: absolute;
  background: rgba(59, 130, 246, 0.15);
  border-radius: 50%;
  animation: floatParticle linear infinite;
}

@keyframes floatParticle {
  0%, 100% { transform: translateY(0); opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  50% { transform: translateY(-100px) translateX(50px); }
}

/* Header */
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  position: relative;
  z-index: 1;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 8px;
}

.page-title {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  background: linear-gradient(135deg, #1e293b, #475569);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.pulse-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: rgba(16, 185, 129, 0.1);
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  color: #10b981;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  background: #10b981;
  border-radius: 50%;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.5); opacity: 0.5; }
}

.page-subtitle {
  margin: 0;
  font-size: 14px;
  color: var(--app-text-muted);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.time-select, .post-select {
  width: 150px;
}

/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
  position: relative;
  z-index: 1;
}

.stat-card {
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 14px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  animation: fadeIn 0.6s ease forwards;
  opacity: 0;
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(15, 23, 42, 0.06);
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.stat-card__icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card__value {
  font-size: 28px;
  font-weight: 800;
  color: var(--app-text-strong);
}

.stat-card__label {
  font-size: 13px;
  color: var(--app-text-muted);
}

/* Main Content */
.main-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  position: relative;
  z-index: 1;
}

/* Timeline Section */
.timeline-card {
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 14px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  max-height: 700px;
}

.timeline-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.timeline-card__header h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--app-text-strong);
}

/* Timeline Scrubber */
.timeline-scrubber {
  margin-bottom: 20px;
  padding: 0 8px;
}

.scrubber-track {
  position: relative;
  height: 8px;
  background: rgba(148, 163, 184, 0.15);
  border-radius: 4px;
  cursor: pointer;
}

.scrubber-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #60a5fa);
  border-radius: 4px;
  transition: width 0.1s;
}

.scrubber-handle {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 20px;
  height: 20px;
  background: #fff;
  border: 3px solid #3b82f6;
  border-radius: 50%;
  cursor: grab;
  transition: box-shadow 0.2s;
  z-index: 2;
}

.scrubber-handle:hover {
  box-shadow: 0 0 0 6px rgba(59, 130, 246, 0.2);
}

.scrubber-handle:active {
  cursor: grabbing;
}

.handle-tooltip {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  padding: 4px 8px;
  background: #1e293b;
  color: #fff;
  font-size: 11px;
  border-radius: 6px;
  white-space: nowrap;
  margin-bottom: 8px;
  opacity: 0;
  transition: opacity 0.2s;
}

.scrubber-handle:hover .handle-tooltip {
  opacity: 1;
}

.scrubber-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 11px;
  color: var(--app-text-muted);
}

/* Timeline Container */
.timeline-container {
  position: relative;
  padding-left: 24px;
  overflow-y: auto;
  flex: 1;
}

.timeline-line {
  position: absolute;
  left: 8px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: linear-gradient(180deg, rgba(148, 163, 184, 0.2), rgba(148, 163, 184, 0.1));
}

.timeline-item {
  position: relative;
  margin-bottom: 20px;
  animation: fadeInLeft 0.4s ease forwards;
  opacity: 0;
  cursor: pointer;
  transition: background-color 0.2s;
  border-radius: 12px;
  padding: 4px;
}

.timeline-item:hover {
  background: rgba(59, 130, 246, 0.04);
}

.timeline-item.is-selected {
  background: rgba(59, 130, 246, 0.08);
}

@keyframes fadeInLeft {
  from { opacity: 0; transform: translateX(-20px); }
  to { opacity: 1; transform: translateX(0); }
}

.timeline-dot {
  position: absolute;
  left: -20px;
  top: 8px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 3px solid;
  background: white;
}

.timeline-dot.added { border-color: #10b981; }
.timeline-dot.updated { border-color: #f59e0b; }
.timeline-dot.removed { border-color: #ef4444; }

.dot-pulse {
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  border: 2px solid;
  border-color: inherit;
  opacity: 0;
  animation: dotPulse 2s ease-out infinite;
}

@keyframes dotPulse {
  0% { transform: scale(0.8); opacity: 0.5; }
  100% { transform: scale(1.5); opacity: 0; }
}

.timeline-content {
  background: rgba(148, 163, 184, 0.04);
  border-radius: 12px;
  padding: 12px;
}

.timeline-time {
  font-size: 11px;
  color: var(--app-text-muted);
  margin-bottom: 6px;
}

.timeline-event {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.event-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.event-icon.added { background: rgba(16, 185, 129, 0.1); color: #10b981; }
.event-icon.updated { background: rgba(245, 158, 11, 0.1); color: #f59e0b; }
.event-icon.removed { background: rgba(239, 68, 68, 0.1); color: #ef4444; }

.event-info { flex: 1; }

.event-title {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-strong);
  margin-bottom: 2px;
}

.event-desc {
  font-size: 12px;
  color: var(--app-text-secondary);
  line-height: 1.5;
}

.timeline-abilities {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.ability-tag {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
}

.ability-tag.added { background: rgba(16, 185, 129, 0.1); color: #10b981; }
.ability-tag.updated { background: rgba(245, 158, 11, 0.1); color: #f59e0b; }
.ability-tag.removed { background: rgba(239, 68, 68, 0.1); color: #ef4444; }

.timeline-loading, .timeline-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: var(--app-text-muted);
  font-size: 14px;
}

.loading-spinner {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Right Panel */
.right-panel {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.graph-card, .chart-card {
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 14px;
  padding: 24px;
}

.graph-card__header, .chart-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.graph-card__header h3, .chart-card__header h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--app-text-strong);
}

.graph-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 350px;
  color: var(--app-text-muted);
}

.chart-legend {
  display: flex;
  gap: 16px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

/* Responsive */
@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .main-content {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
  .dashboard-header {
    flex-direction: column;
    gap: 16px;
  }
  .header-right {
    width: 100%;
  }
  .time-select, .post-select {
    flex: 1;
  }
}
</style>
