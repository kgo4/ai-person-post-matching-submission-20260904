<script setup lang="ts">
/**
 * 岗位演化 —— 页面外壳。
 *
 * 【2026-09-04 简洁化重构】
 *
 * 重构前：本文件 867 行，混装 5 个 Tab（动态概览 / 资料输入 / 运行演化 / 变更审核 / 定时演化）
 * + 1 个抽屉 + 1 个弹窗，且与三个页面职责重叠：
 *   · `/post/trend-discovery` —— 权威材料上传与材料库（本页的「白皮书/内部资料上传」是它的旧版）；
 *   · `/post/crawler-jd`      —— 市场 JD 采集与 JD 池治理（本页的「快速采集」抽屉是它的重复实现）；
 *   · `/post/market-jd-stats` —— 市场能力线索统计（本页「市场发现线索」的来源）。
 *
 * 重构后只保留**任务主线**，共 4 个 Tab：
 *   ① 动态概览 —— 趋势与时间线（EvolutionOverview）
 *   ② 运行演化 —— 证据准备（折叠）+ 选择目标岗位 + 运行 Agent + 结果（EvolutionAgentPanel）
 *   ③ 变更审核 —— 任务列表与详情/分析/应用/删除（EvolutionTaskList）
 *   ④ 定时演化 —— Cron 周期配置（EvolutionSchedulePanel）
 *
 * 「资料输入」Tab 被撤销：它是一整屏 5 张互不相关的上传卡，其中两件在专页里有更完整的实现。
 * 需要准备证据时，「运行演化」页顶部的「证据准备」会把人送到正确的专页。
 *
 * 同时删除了 1061 行死代码 `dashboard.vue`（其路由早已变成 redirect，组件从未被加载）。
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import EvolutionOverview from './EvolutionOverview.vue'
import EvolutionEvidencePrep from './EvolutionEvidencePrep.vue'
import EvolutionAgentPanel from './EvolutionAgentPanel.vue'
import EvolutionTaskList from './EvolutionTaskList.vue'
import EvolutionSchedulePanel from './EvolutionSchedulePanel.vue'

const route = useRoute()
const router = useRouter()

const TABS = [
  { key: 'overview', label: '动态概览' },
  { key: 'agent', label: '运行演化' },
  { key: 'review', label: '变更审核' },
  { key: 'schedule', label: '定时演化' },
] as const

const activeTab = ref<string>('overview')

/** 变更审核列表：运行演化提交任务后需要立即刷新，所以要拿到子组件的 reload */
const taskListRef = ref<InstanceType<typeof EvolutionTaskList> | null>(null)

/** 从「市场 JD 统计」跳转过来时的能力社区线索（原市场发现线索流程，行为不变） */
const marketContext = ref<{ candidateName: string; abilities: string; reason: string; evidenceCount: string } | null>(null)
const initialPostId = ref<number | undefined>(undefined)

function openReviewTask(taskId: number) {
  void router.push(`/post/evolution/detail/${taskId}`)
}

onMounted(() => {
  const postId = Number(route.query.postId)
  if (route.query.trigger === 'MARKET_DISCOVERY' && Number.isFinite(postId) && postId > 0) {
    initialPostId.value = postId
    marketContext.value = {
      candidateName: String(route.query.candidateName || '市场能力社区'),
      abilities: String(route.query.abilities || '未提供'),
      reason: String(route.query.reason || '与既有岗位能力模型相近'),
      evidenceCount: String(route.query.evidenceCount || '0'),
    }
    // 带着线索进来时直接落到「运行演化」，否则用户还要自己找一遍
    activeTab.value = 'agent'
  }
})
</script>

<template>
  <div class="page-shell">
    <section class="evo-header">
      <div class="evo-header__text">
        <div class="evo-header__eyebrow">Post Evolution</div>
        <h1 class="evo-header__title">既有岗位能力演化</h1>
        <p class="evo-header__desc">
          基于原文证据生成既有岗位的变更建议；审核通过后仍需人工点击应用。
          需要准备证据时，在「运行演化」页展开「证据准备」即可跳到对应专页。
        </p>
      </div>
    </section>

    <nav class="evo-nav">
      <button
        v-for="tab in TABS"
        :key="tab.key"
        type="button"
        class="evo-nav__item"
        :class="{ 'evo-nav__item--active': activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </nav>

    <div class="evo-content">
      <div v-show="activeTab === 'overview'">
        <EvolutionOverview @review-task="openReviewTask" />
      </div>

      <div v-show="activeTab === 'agent'">
        <EvolutionEvidencePrep />
        <EvolutionAgentPanel
          :market-context="marketContext"
          :initial-post-id="initialPostId"
          @task-created="taskListRef?.reload()"
        />
      </div>

      <div v-show="activeTab === 'review'">
        <EvolutionTaskList ref="taskListRef" />
      </div>

      <div v-show="activeTab === 'schedule'">
        <EvolutionSchedulePanel />
      </div>
    </div>
  </div>
</template>

<!--
  .evo-* 是跨组件共用的设计单元（外壳、子组件都用），所以这里以非 scoped 方式引入；
  各组件不再各自复制一份，改一处颜色只需改 evolution-theme.css。
-->
<style>
@import './evolution-theme.css';
</style>
