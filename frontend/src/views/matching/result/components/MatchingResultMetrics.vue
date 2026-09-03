<script setup lang="ts">
import { CircleCheck, Document, Timer, Checked } from '@element-plus/icons-vue'

defineProps<{
  total: number
  strongMatchCount: number
  pendingCount: number
  approvedCount: number
}>()
</script>

<template>
  <section class="metric-grid motion-stagger">
    <article class="metric-card data-stream" style="grid-column: span 3;">
      <div class="metric-card__icon" style="background: rgba(37,99,235,0.12); color: #2563eb;">
        <el-icon :size="20"><Document /></el-icon>
      </div>
      <div>
        <div class="metric-card__label">结果总数</div>
        <div class="metric-card__value">{{ total }}</div>
        <div class="metric-card__hint">当前筛选结果总量</div>
      </div>
    </article>
    <article class="metric-card data-stream" style="grid-column: span 3;">
      <div class="metric-card__icon" style="background: rgba(5,150,105,0.12); color: #059669;">
        <el-icon :size="20"><CircleCheck /></el-icon>
      </div>
      <div>
        <div class="metric-card__label">强匹配</div>
        <div class="metric-card__value">{{ strongMatchCount }}</div>
        <div class="metric-card__hint">高质量推荐候选人</div>
      </div>
    </article>
    <article class="metric-card data-stream" style="grid-column: span 3;">
      <div class="metric-card__icon" style="background: rgba(217,119,6,0.12); color: #d97706;">
        <el-icon :size="20"><Timer /></el-icon>
      </div>
      <div>
        <div class="metric-card__label">审批待处理</div>
        <div class="metric-card__value">{{ pendingCount }}</div>
        <div class="metric-card__hint">尚未完成审批或尚未发起</div>
      </div>
    </article>
    <article class="metric-card data-stream" style="grid-column: span 3;">
      <div class="metric-card__icon" style="background: rgba(16,185,129,0.12); color: #10b981;">
        <el-icon :size="20"><Checked /></el-icon>
      </div>
      <div>
        <div class="metric-card__label">已通过</div>
        <div class="metric-card__value">{{ approvedCount }}</div>
        <div class="metric-card__hint">审批已通过的数量</div>
      </div>
    </article>
  </section>
</template>

<style scoped>
@media (max-width: 1024px) {
  .metric-grid > article {
    grid-column: span 6 !important;
  }
}

@media (max-width: 720px) {
  .metric-grid > article {
    grid-column: span 12 !important;
  }
}
</style>
