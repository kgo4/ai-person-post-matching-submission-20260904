<script setup lang="ts">
/**
 * 角色工作台。
 *
 * 版式对齐参考图：欢迎区 → 指标卡一行 → 主区（趋势 + 待办）→ 底部三栏（进度 / 团队成员 / 通知）。
 * 业务内容按当前角色装载（见 useWorkbenchData），视图层不做业务判断；
 * 数据取不到时展示带下一步动作的业务空状态，不伪造数据。
 *
 * 趋势图统一使用 ECharts（项目既有图表底座），不再手写内联 SVG，
 * 以保持与看板等其他图表页面的交互与主题一致。
 */
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import {
  Bell,
  Briefcase,
  Clock,
  Connection,
  Cpu,
  Document,
  List,
  Reading,
  Refresh,
  TrendCharts,
  Trophy,
  User,
} from '@element-plus/icons-vue'
import EChartsWrapper from '@/components/chart/EChartsWrapper.vue'
import WorkbenchCockpit from './components/WorkbenchCockpit.vue'
import { WORKBENCH_CHART as CHART } from './workbench-chart-theme'
import { buildAbilityCloudOption } from './workbench-ability-cloud'
import { useWorkbenchData } from './useWorkbenchData'
import { useUserStore } from '@/store/modules/user'
import { hasPermission } from '@/utils/permission'
import { roleLabelOf } from '@/utils/role-label'
import type { WorkbenchIconKey } from './workbench-types'

const router = useRouter()
const userStore = useUserStore()
const { role, loading, error, snapshot, load } = useWorkbenchData()

const ICONS: Record<WorkbenchIconKey, unknown> = {
  ability: TrendCharts,
  assessment: Document,
  matching: Connection,
  learning: Reading,
  employee: User,
  post: Briefcase,
  task: Clock,
  audit: List,
  ai: Cpu,
  notice: Bell,
  score: Trophy,
}

const iconOf = (key?: WorkbenchIconKey) => (key ? ICONS[key] : TrendCharts)

/** 角色展示文案统一来自 utils/role-label（此前与 403 页各写了一份） */
const roleLabel = computed(() => roleLabelOf(role.value, role.value))

/** 匹配分析区块仅 HR 渲染：员工数据范围只有本人，其余管理角色不看匹配运营数据 */
const showCockpit = computed(() => role.value === 'HR_SPECIALIST')

/*
 * 能力热度词云：原「能力标签治理」页的热度词云迁到岗位体系管理员工作台。
 *
 * 双层判据：
 *   1. 数据由 buildJobArchitectWorkbench 产出，只有岗位体系管理员会拿到非空数组；
 *   2. 再叠加一次权限判断，与后端 /api/post/panorama/**（POST:MANAGE）保持同口径 ——
 *      否则一旦有角色复用了这个装载器，就会出现「看得到词云、点进去 403」。
 * 数据为空（岗位能力表尚未配置）时整块隐藏，不渲染一张空图占位。
 */
const abilityCloud = computed(() => snapshot.value.abilityCloud ?? [])
const showAbilityCloud = computed(
  () => role.value === 'JOB_ARCHITECT' && hasPermission('POST:MANAGE') && abilityCloud.value.length > 0,
)
const abilityCloudOption = computed<EChartsOption>(() => buildAbilityCloudOption(abilityCloud.value))

const userName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || '用户',
)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 9) return '早安'
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const today = computed(() => {
  const now = new Date()
  const week = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][now.getDay()]
  return `今天是 ${now.getFullYear()} 年 ${now.getMonth() + 1} 月 ${now.getDate()} 日 ${week}`
})

function go(path?: string) {
  if (!path) return
  router.push(path)
}

/* ------------------------------- 趋势图（ECharts） ------------------------------- */

const hasTrend = computed(() => snapshot.value.trend.points.length > 0)

/*
 * 坐标轴上界：从一组整齐刻度里取第一个不小于数据上界的值。
 *
 * 历史问题：yAxis 写死 max=100，只对「评分 / 百分比」类口径成立。
 * 岗位体系管理员的「岗位能力规模」是计数口径（每岗十几项能力），
 * 在 0-100 的轴上会被压成贴底的一条直线，看起来像"没有数据"。
 * 改成按数据取整后，评分类仍自然落到 100，计数类落到 20 / 50 这种整齐刻度。
 */
const AXIS_MAX_STEPS = [5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 5000]

const trendAxisMax = computed(() => {
  const max = snapshot.value.trend.points.reduce(
    (acc, point) => Math.max(acc, point.primary, point.secondary),
    0,
  )
  if (max <= 0) return 100
  return AXIS_MAX_STEPS.find(step => step >= max) ?? Math.ceil(max / 1000) * 1000
})

/**
 * 趋势图配色：统一走 workbench-chart-theme，避免同一屏出现多套蓝。
 * 两条线用同一蓝色梯度的深/浅两档，而不是蓝+紫 —— 折线表达的是同一指标的两个口径
 * （AI 匹配分 / 最终分），同色系深浅比异色更易读。
 */
const CHART_COLORS = {
  primary: CHART.primary,
  secondary: CHART.light,
  axis: CHART.axis,
  split: CHART.split,
}

/**
 * 双折线趋势配置。
 *
 * 与 Reference 图一致：平滑曲线、渐变面积、无边框提示框。
 * x 轴标签直接用 points 的 label，不做二次格式化，
 * 保证与列表区块（待办/通知）展示的时间口径一致。
 */
const trendOption = computed<EChartsOption>(() => {
  const trend = snapshot.value.trend
  return {
    color: [CHART_COLORS.primary, CHART_COLORS.secondary],
    grid: { top: 16, right: 12, bottom: 24, left: 40 },
    tooltip: {
      trigger: 'axis',
      borderWidth: 0,
      backgroundColor: 'rgba(22, 34, 63, 0.92)',
      textStyle: { color: '#ffffff', fontSize: 12 },
      axisPointer: { type: 'line', lineStyle: { color: CHART_COLORS.primary, width: 1 } },
    },
    legend: { show: false },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.points.map(point => point.label),
      axisLine: { lineStyle: { color: CHART_COLORS.split } },
      axisTick: { show: false },
      // 分类标签可能是岗位名（比 09-01 这种日期长得多），统一截断成一行，
      // 避免相邻标签叠在一起糊成一片。
      axisLabel: { color: CHART_COLORS.axis, fontSize: 11, interval: 0, width: 72, overflow: 'truncate' },
    },
    yAxis: {
      type: 'value',
      max: trendAxisMax.value,
      min: 0,
      splitNumber: 5,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: CHART_COLORS.axis, fontSize: 11 },
      splitLine: { lineStyle: { color: CHART_COLORS.split } },
    },
    series: [
      {
        name: trend.primaryName,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        showSymbol: true,
        itemStyle: { color: CHART_COLORS.primary, borderColor: '#ffffff', borderWidth: 1.5 },
        lineStyle: { width: 2.4, color: CHART_COLORS.primary },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(47, 107, 255, 0.20)' },
              { offset: 1, color: 'rgba(47, 107, 255, 0.01)' },
            ],
          },
        },
        data: trend.points.map(point => point.primary),
      },
      {
        name: trend.secondaryName,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        showSymbol: true,
        itemStyle: { color: CHART_COLORS.secondary, borderColor: '#ffffff', borderWidth: 1.5 },
        lineStyle: { width: 2, color: CHART_COLORS.secondary },
        data: trend.points.map(point => point.secondary),
      },
    ],
  }
})

onMounted(load)
</script>

<template>
  <div class="rb">
    <!-- 欢迎区 -->
    <section class="rb-hero">
      <div class="rb-hero__copy">
        <!-- 问候语只用纯文本：emoji 在不同系统上的字形/基线差异大，
             且与全站"白蓝 + 线性图标"的克制风格冲突 —— 不做表情符号。 -->
        <h1>{{ greeting }}，{{ userName }}</h1>
        <p>欢迎使用多源异构岗位与能力图谱平台，{{ today }}</p>
        <p class="rb-hero__role">{{ roleLabel }} · {{ snapshot.subtitle || '正在加载当前角色的业务入口' }}</p>
      </div>
      <div class="rb-hero__right">
        <div class="rb-hero__art" aria-hidden="true">
          <span></span><span></span><span></span><i></i>
        </div>
        <button
          v-if="snapshot.primaryActionLabel"
          class="rb-primary-btn"
          type="button"
          @click="go(snapshot.primaryActionPath)"
        >
          {{ snapshot.primaryActionLabel }}
        </button>
      </div>
    </section>

    <!-- 加载失败 -->
    <section v-if="error" class="rb-panel rb-state">
      <p class="rb-state__title">工作台数据加载失败</p>
      <p class="rb-state__desc">{{ error }}</p>
      <button class="rb-primary-btn rb-primary-btn--sm" type="button" @click="load">
        <el-icon><Refresh /></el-icon>重新加载
      </button>
    </section>

    <!-- 快捷入口：参考图未列此区块，但它是各角色进入业务流程的主动线，保留在指标卡上方 -->
    <section v-if="snapshot.actions.length" class="rb-shortcuts">
      <button
        v-for="action in snapshot.actions"
        :key="action.path + action.label"
        class="rb-shortcuts__item"
        type="button"
        @click="go(action.path)"
      >
        <span class="rb-shortcuts__icon"><component :is="iconOf(action.iconKey)" /></span>
        <span class="rb-shortcuts__copy">
          <b>{{ action.label }}</b>
          <small>{{ action.desc }}</small>
        </span>
      </button>
    </section>

    <!-- 指标卡 -->
    <section class="rb-stats">
      <template v-if="loading">
        <article v-for="n in 5" :key="`skeleton-${n}`" class="rb-stat rb-stat--skeleton"></article>
      </template>
      <template v-else>
        <article
          v-for="card in snapshot.stats"
          :key="card.key"
          class="rb-stat"
          :class="{ 'rb-stat--link': !!card.path }"
          @click="go(card.path)"
        >
          <div class="rb-stat__copy">
            <span class="rb-stat__label">{{ card.label }}</span>
            <strong class="rb-stat__value">{{ card.value }}</strong>
            <span class="rb-stat__foot">
              <span v-if="card.delta" class="metric-delta" :class="`metric-delta--${card.delta.direction}`">
                <span class="metric-delta__prefix">{{ card.delta.basis }}</span>
                {{ card.delta.direction === 'up' ? '↑' : card.delta.direction === 'down' ? '↓' : '—' }}{{ card.delta.text }}
              </span>
              <small class="rb-stat__hint">{{ card.hint }}</small>
            </span>
          </div>
          <span class="rb-stat__icon" :class="`is-${card.tone || 'primary'}`">
            <component :is="iconOf(card.iconKey)" />
          </span>
        </article>
      </template>
    </section>

    <!-- 主区：趋势 + 待办 -->
    <section class="rb-main">
      <div class="rb-panel rb-panel--trend">
        <header class="rb-panel__head">
          <div>
            <h2>{{ snapshot.trend.title }}</h2>
            <p>{{ snapshot.trend.subtitle }}</p>
          </div>
          <span class="rb-chip">{{ roleLabel }}视图</span>
        </header>

        <div v-if="hasTrend" class="rb-trend">
          <div class="rb-legend">
            <span><i class="rb-dot rb-dot--primary"></i>{{ snapshot.trend.primaryName }}</span>
            <span><i class="rb-dot rb-dot--secondary"></i>{{ snapshot.trend.secondaryName }}</span>
          </div>
          <EChartsWrapper :option="trendOption" :height="CHART.height" />
        </div>

        <div v-else class="rb-empty">
          <p>{{ snapshot.trend.subtitle || '暂无可用于趋势的业务数据' }}</p>
          <p class="rb-empty__desc">
            该区块的数据由对应业务模块产生，数据出现后会自动展示。
          </p>
          <button class="rb-ghost-btn" type="button" @click="go(snapshot.primaryActionPath || '/workbench')">
            去处理业务数据
          </button>
        </div>
      </div>

      <aside class="rb-panel rb-panel--todo">
        <header class="rb-panel__head">
          <div>
            <h2>待办任务</h2>
            <p>需要当前角色处理的事项</p>
          </div>
        </header>
        <div v-if="snapshot.todos.length" class="rb-todo">
          <button
            v-for="todo in snapshot.todos"
            :key="todo.title"
            class="rb-todo__row"
            type="button"
            @click="go(todo.path)"
          >
            <span class="rb-todo__check" :class="{ 'is-urgent': todo.urgent }"></span>
            <span class="rb-todo__copy">
              <b>{{ todo.title }}</b>
              <small>{{ todo.desc }}</small>
            </span>
            <em v-if="todo.urgent" class="rb-todo__tag">紧急</em>
            <time>{{ todo.meta }}</time>
          </button>
        </div>
        <div v-else class="rb-empty rb-empty--compact">
          <p>当前没有待处理事项</p>
          <p class="rb-empty__desc">这里只显示确实卡在你手上、需要你推进的事项。</p>
        </div>
      </aside>
    </section>

    <!-- 匹配分析（原独立「数据看板」并入）；仅 HR 可见 -->
    <WorkbenchCockpit v-if="showCockpit" />

    <!-- 能力热度词云（原「能力标签治理」页迁入）；仅岗位体系管理员可见 -->
    <section v-if="showAbilityCloud" class="rb-panel rb-panel--cloud">
      <header class="rb-panel__head">
        <div>
          <h2>能力热度词云</h2>
          <p>按岗位能力表统计，字号越大表示被越多岗位能力项引用</p>
        </div>
        <button class="rb-link" type="button" @click="go('/post/panorama')">查看全景图谱</button>
      </header>
      <EChartsWrapper :option="abilityCloudOption" :height="CHART.height" />
    </section>

    <!-- 底部两栏：业务进度 / 系统通知 -->
    <section class="rb-bottom">
      <div class="rb-panel">
        <header class="rb-panel__head">
          <div>
            <h2>业务进度</h2>
            <p>当前角色的关键流程推进情况</p>
          </div>
          <!--
            「查看全部」的落点必须兜底：primaryActionPath 是「主行动按钮」的字段，
            某个角色完全可以没有主行动（label 为空），此时 path 也可能为空
            （emptySnapshot 两个字段都是 ''，加载中就会渲染到这里）。
            没有兜底时这个按钮在加载期间是死的，点了没有任何反应。
          -->
          <button class="rb-link" type="button" @click="go(snapshot.primaryActionPath || snapshot.actions[0]?.path)">查看全部</button>
        </header>
        <div v-if="snapshot.progresses.length" class="rb-progress">
          <button
            v-for="item in snapshot.progresses"
            :key="item.label"
            class="rb-progress__row"
            type="button"
            @click="go(item.path)"
          >
            <span class="rb-progress__icon"><component :is="iconOf(item.iconKey)" /></span>
            <b>{{ item.label }}</b>
            <span class="rb-progress__bar"><i :style="{ width: `${Math.min(100, Math.max(0, item.value))}%` }"></i></span>
            <small>{{ item.value }}%</small>
          </button>
          <p v-for="item in snapshot.progresses" :key="`meta-${item.label}`" class="rb-progress__meta">
            {{ item.label }}：{{ item.status || '—' }}
          </p>
        </div>
        <div v-else class="rb-empty rb-empty--compact">
          <p>暂无可展示的流程进度</p>
          <p class="rb-empty__desc">开始第一项业务后，这里会显示推进情况。</p>
        </div>
      </div>

      <div class="rb-panel">
        <header class="rb-panel__head">
          <div>
            <h2>系统通知</h2>
            <p>平台运行与业务提醒</p>
          </div>
          <el-icon class="rb-panel__head-icon"><Bell /></el-icon>
        </header>
        <div v-if="snapshot.notices.length" class="rb-notices">
          <div v-for="(notice, index) in snapshot.notices" :key="`${notice.title}-${index}`" class="rb-notices__row">
            <span class="rb-notices__icon" :class="`is-${notice.kind}`">
              <component :is="notice.kind === 'warning' ? Bell : notice.kind === 'success' ? Document : TrendCharts" />
            </span>
            <span class="rb-notices__copy">
              <b>{{ notice.title }}</b>
              <small>{{ notice.desc }}</small>
            </span>
            <time>{{ notice.time }}</time>
          </div>
        </div>
        <div v-else class="rb-empty rb-empty--compact">
          <p>暂无系统通知</p>
          <p class="rb-empty__desc">评估报告、匹配发布等事件会在这里提醒。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/*
 * 颜色一律走 global.css 的 --app-* 语义变量，不再硬编码色值，
 * 这样暗色主题与后续调色只需改一处 token。
 */
/*
 * 卡片视觉规范（白蓝为主）—— 全页共用一套，避免各区块各写一套造成"割裂感"：
 *   · 容器：白底 + 1px --app-border + --app-radius-lg + --app-shadow-sm
 *   · 内边距：18px 20px（面板 / 指标卡 / 快捷入口统一）
 *   · 标题：15px/700 主标题 + 11px muted 副标题，标题与内容间距 14px
 *   · 栅格间距：页级 16px，卡片级 14px
 * 图表配色见 workbench-chart-theme.ts（同一屏不再出现两套蓝）。
 */
.rb {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 8px;
  color: var(--app-text);
}

/*
 * 欢迎区。
 *
 * 与全站 .page-hero 用同一组 token（渐变 + 高光 + 描边 + 独立阴影），
 * 但比它多两处工作台专属的处理：
 *   1. ::before —— 左侧 4px 渐变竖条，作为 hero 的"锚点"，
 *      让这块明显高于其他面板的区域看起来是"一块入口"而不是"一块背景"；
 *   2. 分辨率：渐变把冷蓝集中在右侧，与右侧的装饰卡片群呼应，左侧留给文字。
 * 三层叠在一起（高光 / 渐变 / 竖条）而不是单纯调底色，是为了避免出现
 * "一整块均匀淡蓝"—— 那是上一版最像占位图的地方。
 */
.rb-hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 132px;
  padding: 24px 30px 24px 32px;
  overflow: hidden;
  border: 1px solid var(--app-hero-border);
  border-radius: var(--app-radius-lg);
  background-image: var(--app-hero-sheen), var(--app-hero-gradient);
  box-shadow: var(--app-hero-shadow);
}
.rb-hero::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: linear-gradient(180deg, var(--app-primary), var(--app-accent-alt));
  pointer-events: none;
}
.rb-hero__copy {
  position: relative;
  z-index: 1;
}
.rb-hero__copy h1 {
  margin: 0 0 8px;
  color: var(--app-text-strong);
  font-size: 25px;
  font-weight: 800;
  letter-spacing: -0.02em;
}
.rb-hero__copy p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}
.rb-hero__role {
  margin-top: 6px !important;
  color: var(--app-text-muted) !important;
  font-size: 12px !important;
}
.rb-hero__right {
  display: flex;
  align-items: center;
  gap: 20px;
}
.rb-hero__art {
  position: relative;
  width: 190px;
  height: 96px;
  opacity: 0.9;
}
.rb-hero__art span,
.rb-hero__art i {
  position: absolute;
  display: block;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: rgba(255, 255, 255, 0.78);
  box-shadow: 0 10px 20px rgba(47, 107, 255, 0.08);
}
.rb-hero__art span:nth-child(1) {
  right: 16px;
  top: 6px;
  width: 118px;
  height: 40px;
  transform: rotate(-7deg);
}
.rb-hero__art span:nth-child(2) {
  right: 44px;
  top: 40px;
  width: 104px;
  height: 34px;
  transform: rotate(7deg);
}
.rb-hero__art span:nth-child(3) {
  right: 0;
  bottom: 0;
  width: 74px;
  height: 30px;
  transform: rotate(-4deg);
}
.rb-hero__art i {
  right: 96px;
  bottom: -4px;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  background: rgba(109, 124, 240, 0.22);
}

.rb-primary-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border: 0;
  border-radius: var(--app-radius-md);
  color: #fff;
  background: linear-gradient(100deg, var(--app-primary), var(--app-accent-alt));
  box-shadow: var(--app-glow);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}
.rb-primary-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 22px rgba(47, 107, 255, 0.3);
}
.rb-primary-btn--sm {
  padding: 8px 14px;
  font-size: 12px;
}

/* 面板通用 */
.rb-panel {
  padding: 18px 20px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface);
  box-shadow: var(--app-shadow-sm);
}
.rb-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}
.rb-panel__head h2 {
  margin: 0;
  color: var(--app-text-strong);
  font-size: 15px;
  font-weight: 700;
}
.rb-panel__head p {
  margin: 5px 0 0;
  color: var(--app-text-muted);
  font-size: 11px;
}
.rb-panel__head-icon {
  color: var(--app-text-muted);
}
.rb-chip {
  padding: 4px 10px;
  border-radius: 100px;
  color: var(--app-accent);
  background: var(--app-accent-soft);
  font-size: 11px;
  font-weight: 700;
}
.rb-link,
.rb-ghost-btn {
  border: 0;
  background: transparent;
  color: var(--app-accent);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.rb-ghost-btn {
  padding: 7px 14px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface);
}
.rb-ghost-btn:hover {
  border-color: var(--app-border-strong);
  color: var(--app-primary-hover);
}

/* 快捷入口：横向一行的胶囊按钮，位于指标卡上方 */
.rb-shortcuts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 14px;
}
.rb-shortcuts__item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface);
  box-shadow: var(--app-shadow-sm);
  text-align: left;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}
.rb-shortcuts__item:hover {
  transform: translateY(-2px);
  border-color: var(--app-border-strong);
  box-shadow: var(--app-shadow-md);
}
.rb-shortcuts__icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  border-radius: var(--app-radius-sm);
  color: var(--app-accent);
  background: var(--app-accent-soft);
}
.rb-shortcuts__icon svg {
  width: 16px !important;
  height: 16px !important;
}
.rb-shortcuts__copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}
.rb-shortcuts__copy b {
  color: var(--app-text-strong);
  font-size: 12px;
  font-weight: 650;
}
.rb-shortcuts__copy small {
  overflow: hidden;
  margin-top: 2px;
  color: var(--app-text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 指标卡 */
/* auto-fit 而非固定列数：各角色的指标卡数量不同（HR 5 张、员工 4 张…），
 * 固定 5 列会在卡片少的角色上留出空洞，这正是"割裂感"的来源之一。 */
.rb-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
}
.rb-stat {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 108px;
  padding: 18px 20px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface);
  box-shadow: var(--app-shadow-sm);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}
.rb-stat--link {
  cursor: pointer;
}
.rb-stat--link:hover {
  transform: translateY(-2px);
  border-color: var(--app-border-strong);
  box-shadow: var(--app-shadow-md);
}
.rb-stat--skeleton {
  min-height: 108px;
  background: linear-gradient(100deg, var(--app-surface-soft) 30%, var(--app-bg-secondary) 50%, var(--app-surface-soft) 70%);
  background-size: 300% 100%;
  animation: rb-shimmer 1.4s ease infinite;
}
@keyframes rb-shimmer {
  from {
    background-position: 100% 0;
  }
  to {
    background-position: 0 0;
  }
}
.rb-stat__label {
  display: block;
  color: var(--app-text-secondary);
  font-size: 12px;
}
.rb-stat__value {
  display: block;
  margin: 6px 0 6px;
  color: var(--app-text-strong);
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.02em;
}
.rb-stat__foot {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.rb-stat__hint {
  display: block;
  color: var(--app-text-muted);
  font-size: 11px;
}
.rb-stat__icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  border-radius: var(--app-radius-md);
}
.rb-stat__icon svg {
  width: 20px !important;
  height: 20px !important;
}
.rb-stat__icon.is-primary {
  color: var(--app-primary);
  background: var(--app-primary-soft);
}
.rb-stat__icon.is-success {
  color: var(--app-success);
  background: rgba(18, 183, 106, 0.12);
}
.rb-stat__icon.is-warning {
  color: var(--app-warning);
  background: rgba(247, 144, 9, 0.12);
}
.rb-stat__icon.is-danger {
  color: var(--app-danger);
  background: rgba(240, 68, 56, 0.1);
}
.rb-stat__icon.is-info {
  color: var(--app-info);
  background: rgba(79, 123, 214, 0.12);
}

/* 主区 */
.rb-main {
  display: grid;
  grid-template-columns: minmax(0, 1.62fr) minmax(300px, 0.9fr);
  gap: 14px;
}
/* 底部两栏：业务进度 / 系统通知。
 * 原第三栏「团队成员」已按需求移除（工作台不再展示他人档案）。
 * 进度信息量更大，给更宽的一列。 */
.rb-bottom {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 14px;
}

/* 折线图 */
.rb-legend {
  display: flex;
  gap: 16px;
  color: var(--app-text-muted);
  font-size: 11px;
}
.rb-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 50%;
}
.rb-dot--primary {
  background: var(--app-primary);
}
.rb-dot--secondary {
  background: var(--app-accent-alt);
}
.rb-trend {
  margin-top: 8px;
}

/* 待办 */
.rb-todo {
  display: flex;
  flex-direction: column;
}
.rb-todo__row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 11px 4px;
  border: 0;
  border-bottom: 1px solid var(--app-divider);
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.rb-todo__row:last-child {
  border-bottom: 0;
}
.rb-todo__row:hover {
  background: var(--app-surface-soft);
}
.rb-todo__check {
  width: 15px;
  height: 15px;
  flex: 0 0 15px;
  border: 1.5px solid var(--app-border-strong);
  border-radius: 4px;
}
.rb-todo__check.is-urgent {
  border-color: var(--app-danger);
  background: var(--app-danger-soft);
}
.rb-todo__copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.rb-todo__copy b {
  overflow: hidden;
  color: var(--app-text-strong);
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rb-todo__copy small {
  overflow: hidden;
  margin-top: 3px;
  color: var(--app-text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rb-todo__tag {
  flex: 0 0 auto;
  padding: 2px 7px;
  border-radius: 4px;
  color: var(--app-danger);
  background: var(--app-danger-soft);
  font-size: 10px;
  font-style: normal;
  font-weight: 700;
}
.rb-todo__row time {
  flex: 0 0 auto;
  color: var(--app-text-muted);
  font-size: 11px;
}

/* 进度 */
.rb-progress {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rb-progress__row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}
.rb-progress__icon {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  border-radius: 8px;
  color: var(--app-accent);
  background: var(--app-accent-soft);
}
.rb-progress__icon svg {
  width: 14px !important;
  height: 14px !important;
}
.rb-progress__row b {
  flex: 0 0 40%;
  overflow: hidden;
  color: var(--app-text);
  font-size: 12px;
  font-weight: 600;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rb-progress__bar {
  position: relative;
  flex: 1;
  height: 7px;
  border-radius: 100px;
  background: var(--app-bg-secondary);
}
.rb-progress__bar i {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: 100px;
  background: linear-gradient(90deg, var(--app-primary), var(--app-accent-alt));
}
.rb-progress__row small {
  flex: 0 0 38px;
  color: var(--app-text-secondary);
  font-size: 11px;
  text-align: right;
}
.rb-progress__meta {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 11px;
}

/* 通知 */
.rb-notices {
  display: flex;
  flex-direction: column;
}
.rb-notices__row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid var(--app-divider);
}
.rb-notices__row:last-child {
  border-bottom: 0;
}
.rb-notices__icon {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  border-radius: 8px;
}
.rb-notices__icon svg {
  width: 14px !important;
  height: 14px !important;
}
.rb-notices__icon.is-info {
  color: var(--app-accent);
  background: var(--app-accent-soft);
}
.rb-notices__icon.is-success {
  color: var(--app-success);
  background: var(--app-success-soft);
}
.rb-notices__icon.is-warning {
  color: var(--app-warning);
  background: var(--app-warning-soft);
}
.rb-notices__copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}
.rb-notices__copy b {
  color: var(--app-text-strong);
  font-size: 12px;
  font-weight: 650;
}
.rb-notices__copy small {
  overflow: hidden;
  margin-top: 3px;
  color: var(--app-text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rb-notices__row time {
  flex: 0 0 auto;
  color: var(--app-text-muted);
  font-size: 11px;
}

/* 空状态与失败态 */
.rb-empty,
.rb-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 180px;
  padding: 22px;
  text-align: center;
}
.rb-empty--compact {
  min-height: 128px;
}
.rb-empty p,
.rb-state p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 12px;
}
.rb-empty__desc {
  color: var(--app-text-muted) !important;
  font-size: 11px !important;
}
.rb-state__title {
  color: var(--app-text-strong) !important;
  font-size: 14px !important;
  font-weight: 700;
}
.rb-empty .rb-ghost-btn {
  margin-top: 8px;
}

/* 响应式：窄屏单列，文本不溢出 */
@media (max-width: 1280px) {
  /* 指标卡交给 auto-fit 自行折行：固定 3 列会在卡片数为 4/5 时留出空洞 */
  .rb-bottom {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 1024px) {
  .rb-main {
    grid-template-columns: minmax(0, 1fr);
  }
  .rb-hero__art {
    display: none;
  }
}
@media (max-width: 720px) {
  .rb-stats,
  .rb-bottom {
    grid-template-columns: minmax(0, 1fr);
  }
  .rb-hero {
    flex-direction: column;
    align-items: flex-start;
    padding: 20px;
  }
  .rb-hero__copy h1 {
    font-size: 21px;
  }
}
</style>
