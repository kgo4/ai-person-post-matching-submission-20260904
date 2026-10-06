<script setup lang="ts">
import LearningStepCard from './LearningStepCard.vue'
import type { LearningPathStep, LearningProjectTask } from '@/api'

defineProps<{
  steps: LearningPathStep[]
  activeStepId?: number | null
  loading?: boolean
}>()

const emit = defineEmits<{
  selectStep: [stepId: number]
  startLearning: [step: LearningPathStep]
  markCompleted: [step: LearningPathStep]
  submitTask: [task: LearningProjectTask]
}>()
</script>

<template>
  <section class="lp-timeline" v-loading="loading">
    <div class="lp-timeline__header">
      <h2 class="lp-timeline__title">学习路径</h2>
      <span class="lp-timeline__count">{{ steps.length }} 个阶段</span>
    </div>

    <div v-if="steps.length" class="lp-timeline__track">
      <div class="lp-timeline__line"></div>
      <LearningStepCard
        v-for="step in steps"
        :key="step.id"
        :step="step"
        :active="activeStepId === step.id"
        @select-step="emit('selectStep', $event)"
        @start-learning="emit('startLearning', $event)"
        @mark-completed="emit('markCompleted', $event)"
        @submit-task="emit('submitTask', $event)"
      />
    </div>

    <div v-else class="lp-timeline__empty">
      暂无学习步骤，请先选择匹配记录生成学习路径
    </div>
  </section>
</template>

<style scoped>
.lp-timeline {
  flex: 1;
  min-width: 0;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 20px 24px;
}

.lp-timeline__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.lp-timeline__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.lp-timeline__count {
  font-size: 13px;
  color: #9ca3af;
}

.lp-timeline__track {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.lp-timeline__line {
  position: absolute;
  left: 5px;
  top: 12px;
  bottom: 12px;
  width: 2px;
  background: #e5e7eb;
  border-radius: 1px;
}

.lp-timeline__empty {
  padding: 48px 24px;
  text-align: center;
  font-size: 14px;
  color: #9ca3af;
}
</style>
