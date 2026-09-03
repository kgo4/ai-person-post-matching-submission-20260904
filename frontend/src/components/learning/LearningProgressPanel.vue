<script setup lang="ts">
import { computed } from 'vue'
import { progressPercent } from './types'
import type { LearningPathStep } from '@/api'

const props = defineProps<{
  steps: LearningPathStep[]
  currentScore?: number | null
  targetScore?: number | null
}>()

const completed = computed(() => props.steps.filter(s => s.status === 'COMPLETED').length)
const inProgress = computed(() => props.steps.filter(s => s.status === 'IN_PROGRESS').length)
const pending = computed(() => props.steps.filter(s => s.status === 'PENDING').length)
const submitted = computed(() => props.steps.filter(s => s.status === 'SUBMITTED').length)
const total = computed(() => props.steps.length)
const percent = computed(() => progressPercent(completed.value, total.value))
const totalHours = computed(() => {
  return props.steps
    .filter(s => s.status !== 'COMPLETED')
    .reduce((sum, s) => sum + (s.estimatedHours || 0), 0)
})
</script>

<template>
  <aside class="progress-panel">
    <!-- 进度概览 -->
    <section class="progress-panel__section">
      <div class="progress-panel__section-title">进度概览</div>
      <div class="progress-panel__ring-row">
        <div class="progress-panel__ring">
          <svg viewBox="0 0 80 80" class="progress-panel__svg">
            <circle cx="40" cy="40" r="34" fill="none" stroke="#f3f4f6" stroke-width="6" />
            <circle
              cx="40" cy="40" r="34"
              fill="none"
              stroke="#2563eb"
              stroke-width="6"
              stroke-linecap="round"
              :stroke-dasharray="`${percent * 2.136} 213.6`"
              transform="rotate(-90 40 40)"
            />
          </svg>
          <span class="progress-panel__ring-value">{{ percent }}%</span>
        </div>
        <div class="progress-panel__ring-stats">
          <div class="progress-panel__stat-row">
            <span class="progress-panel__stat-dot" style="background:#059669"></span>
            <span>已完成</span>
            <strong>{{ completed }}</strong>
          </div>
          <div class="progress-panel__stat-row">
            <span class="progress-panel__stat-dot" style="background:#2563eb"></span>
            <span>进行中</span>
            <strong>{{ inProgress }}</strong>
          </div>
          <div class="progress-panel__stat-row">
            <span class="progress-panel__stat-dot" style="background:#d97706"></span>
            <span>待验证</span>
            <strong>{{ submitted }}</strong>
          </div>
          <div class="progress-panel__stat-row">
            <span class="progress-panel__stat-dot" style="background:#9ca3af"></span>
            <span>未开始</span>
            <strong>{{ pending }}</strong>
          </div>
        </div>
      </div>

      <div v-if="totalHours > 0" class="progress-panel__hours">
        预计剩余 <strong>{{ totalHours }}h</strong>
      </div>
    </section>

    <!-- 当前步骤详情 (slot) -->
    <section class="progress-panel__section">
      <div class="progress-panel__section-title">当前任务</div>
      <slot name="active-step">
        <div class="progress-panel__placeholder">点击左侧步骤查看详情</div>
      </slot>
    </section>
  </aside>
</template>

<style scoped>
.progress-panel {
  width: 340px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 0;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  overflow: hidden;
}

.progress-panel__section {
  padding: 16px;
  border-bottom: 1px solid #f3f4f6;
}

.progress-panel__section:last-child {
  border-bottom: none;
}

.progress-panel__section-title {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 14px;
}

.progress-panel__ring-row {
  display: flex;
  align-items: center;
  gap: 20px;
}

.progress-panel__ring {
  position: relative;
  width: 80px;
  height: 80px;
  flex-shrink: 0;
}

.progress-panel__svg {
  width: 100%;
  height: 100%;
}

.progress-panel__ring-value {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  color: #2563eb;
}

.progress-panel__ring-stats {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: #6b7280;
}

.progress-panel__stat-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress-panel__stat-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.progress-panel__stat-row strong {
  margin-left: auto;
  color: #111827;
  font-weight: 600;
}

.progress-panel__hours {
  margin-top: 12px;
  font-size: 13px;
  color: #6b7280;
  text-align: center;
}

.progress-panel__hours strong {
  color: #111827;
}

.progress-panel__placeholder {
  padding: 20px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
}
</style>
