<template>
  <section class="evo-overview">
    <div class="evo-overview__top">
      <div class="evo-overview__filters">
        <el-select v-model="selectedRange" aria-label="时间范围" @change="loadData" style="width:140px">
          <el-option label="最近 7 天" value="7d" />
          <el-option label="最近 30 天" value="30d" />
          <el-option label="最近 90 天" value="90d" />
        </el-select>
        <el-select v-model="selectedPostId" clearable filterable placeholder="全部岗位" aria-label="岗位筛选" @change="loadData" style="width:200px">
          <el-option v-for="post in posts" :key="post.id" :label="post.postName" :value="post.id" />
        </el-select>
      </div>
    </div>

    <div class="evo-overview__stats" v-loading="statsLoading">
      <div class="evo-overview__stat">
        <span class="evo-overview__stat-val">{{ stats.totalTasks }}</span>
        <span class="evo-overview__stat-lbl">演化任务</span>
      </div>
      <div class="evo-overview__stat">
        <span class="evo-overview__stat-val is-ok">{{ stats.completedTasks }}</span>
        <span class="evo-overview__stat-lbl">已完成</span>
      </div>
      <div class="evo-overview__stat">
        <span class="evo-overview__stat-val is-warn">{{ stats.pendingChanges }}</span>
        <span class="evo-overview__stat-lbl">待审核变更</span>
      </div>
      <div class="evo-overview__stat">
        <span class="evo-overview__stat-val is-danger">{{ stats.highRiskChanges }}</span>
        <span class="evo-overview__stat-lbl">高风险变更</span>
      </div>
    </div>

    <div class="evo-overview__panels">
      <section class="evo-card" v-loading="timelineLoading">
        <div class="evo-card__head"><span class="evo-card__title">最新变化信号</span></div>
        <div class="evo-card__body">
          <div v-if="events.length === 0" class="evo-empty">当前筛选条件下暂无变化</div>
          <div v-else class="evo-timeline">
            <article v-for="event in events" :key="event.id" class="evo-timeline__item">
              <span class="evo-timeline__dot" :class="event.type"></span>
              <div class="evo-timeline__info">
                <strong>{{ event.title }}</strong>
                <p>{{ event.description }}</p>
                <div class="evo-timeline__meta">
                  <el-tag size="small" effect="plain">{{ event.taskCode || `任务 #${event.taskId}` }}</el-tag>
                  <span>{{ formatTime(event.time) }}</span>
                </div>
              </div>
              <el-button v-if="event.taskId" type="primary" link @click="openReview(event)">处理</el-button>
            </article>
          </div>
        </div>
      </section>

      <section class="evo-card" v-loading="trendLoading">
        <div class="evo-card__head"><span class="evo-card__title">变化趋势</span></div>
        <div class="evo-card__body">
          <div class="evo-trend-summary">
            <div><span>新增能力</span><strong class="is-ok">{{ trends.added }}</strong></div>
            <div><span>调整能力</span><strong class="is-warn">{{ trends.updated }}</strong></div>
            <div><span>移除能力</span><strong class="is-danger">{{ trends.removed }}</strong></div>
          </div>
          <div class="evo-divider"></div>
          <div class="evo-trend-bars">
            <div v-for="period in trendPeriods" :key="period.label" class="evo-trend-bar">
              <span class="evo-trend-bar__label">{{ period.label }}</span>
              <div class="evo-trend-bar__fills">
                <i class="is-ok" :style="{ width: barWidth(period.added) }"></i>
                <i class="is-warn" :style="{ width: barWidth(period.updated) }"></i>
                <i class="is-danger" :style="{ width: barWidth(period.removed) }"></i>
              </div>
              <b>{{ period.total }}</b>
            </div>
          </div>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { pagePosts } from '@/api/post'
import { getEvolutionDashboardStats, getEvolutionTimeline, getEvolutionTrends } from '@/api/evolution'
import type { EvolutionDashboardStats, EvolutionTimelineEvent, EvolutionTrends } from '@/api/evolution'
import type { PostPost } from '@/api/types'

const emit = defineEmits<{ (event: 'review-task', taskId: number): void }>()

const selectedRange = ref('30d')
const selectedPostId = ref<number>()
const posts = ref<PostPost[]>([])
const events = ref<EvolutionTimelineEvent[]>([])
const stats = ref<EvolutionDashboardStats>({ totalTasks: 0, completedTasks: 0, pendingChanges: 0, highRiskChanges: 0 })
const trends = ref<EvolutionTrends>({ added: 0, updated: 0, removed: 0, total: 0, monthly: {} })
const statsLoading = ref(false)
const timelineLoading = ref(false)
const trendLoading = ref(false)

const trendPeriods = computed(() => Object.entries(trends.value.monthly)
  .sort(([a], [b]) => a.localeCompare(b))
  .map(([label, values]) => ({ label, added: values.added || 0, updated: values.updated || 0, removed: values.removed || 0, total: (values.added || 0) + (values.updated || 0) + (values.removed || 0) })))
const largestPeriodTotal = computed(() => Math.max(1, ...trendPeriods.value.map(p => p.total)))

function barWidth(v: number) { return `${Math.max(v > 0 ? 8 : 0, Math.round((v / largestPeriodTotal.value) * 100))}%` }
function formatTime(v: string) { return v ? new Date(v).toLocaleString() : '-' }
function openReview(event: EvolutionTimelineEvent) { emit('review-task', event.taskId) }

async function loadPosts() { const r = await pagePosts({ current: 1, size: 100 }); posts.value = r.data?.records || [] }
async function loadData() {
  statsLoading.value = true; timelineLoading.value = true; trendLoading.value = true
  try {
    const [s, t, tr] = await Promise.all([getEvolutionDashboardStats({ range: selectedRange.value }), getEvolutionTimeline({ postId: selectedPostId.value, range: selectedRange.value, limit: 20 }), getEvolutionTrends({ range: selectedRange.value })])
    stats.value = s.data || stats.value; events.value = t.data || []; trends.value = tr.data || trends.value
  } finally { statsLoading.value = false; timelineLoading.value = false; trendLoading.value = false }
}

onMounted(() => { loadPosts(); loadData() })
</script>

<style scoped>
.evo-overview { display: flex; flex-direction: column; gap: 14px; }
.evo-overview__top { display: flex; justify-content: flex-end; }
.evo-overview__filters { display: flex; gap: 8px; }
.evo-overview__stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.evo-overview__stat { display: flex; flex-direction: column; gap: 3px; padding: 14px 16px; border: 1px solid rgba(148, 163, 184, 0.12); border-radius: 10px; background: rgba(255, 255, 255, 0.5); }
.evo-overview__stat-val { font-size: 24px; font-weight: 800; color: var(--app-text-strong); }
.evo-overview__stat-val.is-ok { color: #10b981; }
.evo-overview__stat-val.is-warn { color: #d97706; }
.evo-overview__stat-val.is-danger { color: #dc2626; }
.evo-overview__stat-lbl { font-size: 11px; color: var(--app-text-muted); font-weight: 600; }
.evo-overview__panels { display: grid; grid-template-columns: 1.4fr 1fr; gap: 14px; }

.evo-card { border: 1px solid rgba(148, 163, 184, 0.12); border-radius: 14px; background: rgba(255, 255, 255, 0.58); backdrop-filter: blur(10px); overflow: hidden; }
.evo-card__head { padding: 13px 18px; border-bottom: 1px solid rgba(148, 163, 184, 0.1); }
.evo-card__title { font-size: 14px; font-weight: 700; color: var(--app-text-strong); }
.evo-card__body { padding: 14px 18px; }

.evo-empty { display: flex; align-items: center; justify-content: center; min-height: 120px; color: var(--app-text-muted); font-size: 13px; }

.evo-timeline { display: grid; }
.evo-timeline__item { display: grid; grid-template-columns: 8px 1fr auto; gap: 10px; padding: 10px 0; border-bottom: 1px solid rgba(148, 163, 184, 0.08); align-items: start; }
.evo-timeline__item:last-child { border-bottom: none; }
.evo-timeline__dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 6px; flex-shrink: 0; background: var(--app-primary); }
.evo-timeline__dot.added { background: #10b981; }
.evo-timeline__dot.updated { background: #f59e0b; }
.evo-timeline__dot.removed { background: #ef4444; }
.evo-timeline__info strong { color: var(--app-text-strong); font-size: 13px; }
.evo-timeline__info p { margin: 4px 0 6px; color: var(--app-text-secondary); font-size: 12px; line-height: 1.5; }
.evo-timeline__meta { display: flex; align-items: center; gap: 8px; color: var(--app-text-muted); font-size: 11px; }

.evo-trend-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.evo-trend-summary div { padding: 10px 12px; border: 1px solid rgba(148, 163, 184, 0.1); border-radius: 8px; background: rgba(255, 255, 255, 0.45); }
.evo-trend-summary span { display: block; color: var(--app-text-muted); font-size: 11px; }
.evo-trend-summary strong { display: block; margin-top: 4px; font-size: 20px; color: var(--app-text-strong); }
.evo-trend-summary strong.is-ok { color: #10b981; }
.evo-trend-summary strong.is-warn { color: #f59e0b; }
.evo-trend-summary strong.is-danger { color: #ef4444; }

.evo-divider { height: 1px; background: rgba(148, 163, 184, 0.1); margin: 12px 0; }
.evo-trend-bars { display: grid; gap: 10px; }
.evo-trend-bar { display: grid; grid-template-columns: 64px 1fr 28px; gap: 8px; align-items: center; font-size: 11px; color: var(--app-text-secondary); }
.evo-trend-bar__label { text-align: right; }
.evo-trend-bar__fills { display: flex; gap: 3px; height: 8px; }
.evo-trend-bar__fills i { display: block; min-width: 0; border-radius: 2px; }
.evo-trend-bar__fills i.is-ok { background: #10b981; }
.evo-trend-bar__fills i.is-warn { background: #f59e0b; }
.evo-trend-bar__fills i.is-danger { background: #ef4444; }
.evo-trend-bar b { text-align: right; font-weight: 700; color: var(--app-text-strong); }

@media (max-width: 960px) {
  .evo-overview__stats { grid-template-columns: repeat(2, 1fr); }
  .evo-overview__panels { grid-template-columns: 1fr; }
}
@media (max-width: 560px) {
  .evo-overview__stats { grid-template-columns: 1fr; }
  .evo-timeline__item { grid-template-columns: 8px 1fr; }
  .evo-timeline__item > .el-button { grid-column: 2; justify-self: start; }
}
</style>
