<script setup lang="ts">
import { ref, computed } from 'vue'
import { getPriorityMeta, getStatusMeta } from './types'
import type { LearningPathStep } from '@/api'
import type { GapFilter } from './types'

const props = defineProps<{
  steps: LearningPathStep[]
  currentScore?: number | null
  targetScore?: number | null
  empName?: string
  postName?: string
}>()

const emit = defineEmits<{
  selectStep: [stepId: number]
}>()

const activeFilter = ref<GapFilter>('ALL')

const filters: { key: GapFilter; label: string }[] = [
  { key: 'ALL', label: '全部' },
  { key: 'HIGH', label: '高优先级' },
  { key: 'PENDING', label: '未开始' },
  { key: 'IN_PROGRESS', label: '进行中' },
  { key: 'COMPLETED', label: '已完成' },
]

const filteredSteps = computed(() => {
  if (activeFilter.value === 'ALL') return props.steps
  if (activeFilter.value === 'HIGH') return props.steps.filter(s => s.priority === 'HIGH')
  return props.steps.filter(s => s.status === activeFilter.value)
})

function handleStepClick(step: LearningPathStep) {
  emit('selectStep', step.id)
}
</script>

<template>
  <aside class="gap-panel">
    <!-- 学习目标 -->
    <section class="gap-panel__section">
      <div class="gap-panel__section-title">学习目标</div>
      <div class="gap-panel__goal">
        <div class="gap-panel__goal-row">
          <span class="gap-panel__goal-label">当前</span>
          <span class="gap-panel__goal-value">{{ empName || '-' }}</span>
        </div>
        <div class="gap-panel__goal-row">
          <span class="gap-panel__goal-label">目标</span>
          <span class="gap-panel__goal-value">{{ postName || '-' }}</span>
        </div>
        <div class="gap-panel__goal-row" v-if="currentScore != null">
          <span class="gap-panel__goal-label">分数</span>
          <span class="gap-panel__goal-value">
            {{ Number(currentScore).toFixed(1) }}
            <span v-if="targetScore != null" class="gap-panel__goal-arrow">→ {{ Number(targetScore).toFixed(1) }}</span>
          </span>
        </div>
      </div>
    </section>

    <!-- 能力差距列表 -->
    <section class="gap-panel__section">
      <div class="gap-panel__section-title">
        能力差距
        <span class="gap-panel__count">{{ steps.length }}</span>
      </div>

      <!-- 筛选 -->
      <div class="gap-panel__filters">
        <button
          v-for="f in filters"
          :key="f.key"
          class="gap-panel__filter-btn"
          :class="{ 'is-active': activeFilter === f.key }"
          @click="activeFilter = f.key"
        >
          {{ f.label }}
        </button>
      </div>

      <!-- 差距列表 -->
      <div class="gap-panel__list">
        <div
          v-for="step in filteredSteps"
          :key="step.id"
          class="gap-item"
          @click="handleStepClick(step)"
        >
          <div class="gap-item__dot" :style="{ background: getStatusMeta(step.status).color }"></div>
          <div class="gap-item__body">
            <div class="gap-item__name">{{ step.abilityName }}</div>
            <div class="gap-item__levels">
              <span class="gap-item__level">L{{ step.currentLevel || 0 }}</span>
              <span class="gap-item__arrow">→</span>
              <span class="gap-item__level gap-item__level--target">L{{ step.targetLevel || '-' }}</span>
            </div>
          </div>
          <span
            class="gap-item__priority"
            :style="{ color: getPriorityMeta(step.priority).color, background: getPriorityMeta(step.priority).bg }"
          >
            {{ getPriorityMeta(step.priority).label }}
          </span>
          <span
            class="gap-item__res"
            :class="{ 'gap-item__res--empty': !(step.resourceCount && step.resourceCount > 0) }"
            :title="(step.resourceCount || 0) > 0 ? `匹配 ${step.resourceCount} 个学习资源` : '暂无匹配学习资源'"
          >
            {{ (step.resourceCount || 0) > 0 ? `${step.resourceCount} 资源` : '暂无资源' }}
          </span>
        </div>

        <div v-if="filteredSteps.length === 0" class="gap-panel__empty">
          暂无匹配项
        </div>
      </div>
    </section>
  </aside>
</template>

<style scoped>
.gap-panel {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 0;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  overflow: hidden;
}

.gap-panel__section {
  padding: 16px;
  border-bottom: 1px solid #f3f4f6;
}

.gap-panel__section:last-child {
  border-bottom: none;
}

.gap-panel__section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 12px;
}

.gap-panel__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background: #f3f4f6;
  font-size: 11px;
  font-weight: 600;
  color: #6b7280;
}

.gap-panel__goal {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.gap-panel__goal-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.gap-panel__goal-label {
  flex-shrink: 0;
  width: 36px;
  color: #9ca3af;
  font-size: 12px;
}

.gap-panel__goal-value {
  color: #111827;
  font-weight: 500;
}

.gap-panel__goal-arrow {
  color: #9ca3af;
  font-weight: 400;
}

/* Filters */
.gap-panel__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.gap-panel__filter-btn {
  padding: 4px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
  color: #6b7280;
  cursor: pointer;
  transition: all 0.15s;
}

.gap-panel__filter-btn:hover {
  border-color: #2563eb;
  color: #2563eb;
}

.gap-panel__filter-btn.is-active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

/* Gap list */
.gap-panel__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 420px;
  overflow-y: auto;
}

.gap-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.gap-item:hover {
  background: #f9fafb;
}

.gap-item__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.gap-item__body {
  flex: 1;
  min-width: 0;
}

.gap-item__name {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.gap-item__levels {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  font-size: 11px;
}

.gap-item__level {
  color: #9ca3af;
}

.gap-item__level--target {
  color: #2563eb;
  font-weight: 600;
}

.gap-item__arrow {
  color: #d1d5db;
  font-size: 10px;
}

.gap-item__priority {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.gap-item__res {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 4px;
  background: #ecfdf5;
  color: #059669;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.gap-item__res--empty {
  background: #f3f4f6;
  color: #9ca3af;
}

.gap-panel__empty {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
}
</style>
