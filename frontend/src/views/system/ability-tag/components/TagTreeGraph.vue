<script setup lang="ts">
import { computed } from 'vue'
import type { AbilityTagTreeVO } from '@/api'
import EChartsWrapper from '@/components/chart/EChartsWrapper.vue'
import { buildTagTreeGraphOption } from '../tag-tree-graph'

const props = defineProps<{
  treeData: AbilityTagTreeVO[]
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'select', tagId: number): void
}>()

const option = computed(() => buildTagTreeGraphOption(props.treeData))

function handleChartClick(params: any) {
  const tagId = Number(params?.data?.value)
  if (Number.isFinite(tagId)) emit('select', tagId)
}
</script>

<template>
  <section class="tag-tree-graph" v-loading="loading">
    <header class="tag-tree-graph__head">
      <div>
        <h2 class="tag-tree-graph__title">标签体系</h2>
        <p class="tag-tree-graph__sub">拖拽画布浏览，点击节点查看详情</p>
      </div>
      <div class="tag-tree-graph__legend" aria-label="标签分类图例">
        <span><i class="tag-tree-graph__dot tag-tree-graph__dot--technical" />技术</span>
        <span><i class="tag-tree-graph__dot tag-tree-graph__dot--soft" />软技能</span>
        <span><i class="tag-tree-graph__dot tag-tree-graph__dot--business" />业务</span>
      </div>
    </header>
    <EChartsWrapper v-if="treeData.length" :option="option" height="calc(100vh - 235px)" :on-chart-click="handleChartClick" />
    <el-empty v-else description="暂无标签数据" :image-size="72" />
  </section>
</template>
