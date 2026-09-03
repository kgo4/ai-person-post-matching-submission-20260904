<script setup lang="ts">
import { computed } from 'vue'
import { getStatusMeta, getPriorityMeta, getDifficultyMeta } from './types'
import type { LearningPathStep, LearningProjectTask } from '@/api'

const props = defineProps<{
  step: LearningPathStep
  active?: boolean
  highlight?: boolean
}>()

const emit = defineEmits<{
  startLearning: [step: LearningPathStep]
  markCompleted: [step: LearningPathStep]
  submitTask: [task: LearningProjectTask]
  selectStep: [stepId: number]
}>()

const statusMeta = computed(() => getStatusMeta(props.step.status))
const priorityMeta = computed(() => getPriorityMeta(props.step.priority))
</script>

<template>
  <article
    class="step-card"
    :class="{ 'is-active': active, 'is-completed': step.status === 'COMPLETED' }"
    @click="emit('selectStep', step.id)"
  >
    <!-- Timeline dot -->
    <div class="step-card__dot" :style="{ background: statusMeta.color }">
      <span v-if="step.status === 'COMPLETED'" class="step-card__dot-icon">✓</span>
    </div>

    <!-- Content -->
    <div class="step-card__body">
      <div class="step-card__header">
        <div class="step-card__title-row">
          <h3 class="step-card__title">{{ step.stepTitle }}</h3>
          <span class="step-card__status" :style="{ color: statusMeta.color, background: statusMeta.bg }">
            {{ statusMeta.label }}
          </span>
        </div>

        <div class="step-card__meta">
          <span class="step-card__ability">{{ step.abilityName }}</span>
          <span class="step-card__level">
            <span class="step-card__level-from">L{{ step.currentLevel || 0 }}</span>
            <span class="step-card__level-arrow">→</span>
            <span class="step-card__level-to">L{{ step.targetLevel }}</span>
          </span>
          <span
            class="step-card__priority"
            :style="{ color: priorityMeta.color, background: priorityMeta.bg }"
          >
            {{ priorityMeta.label }}优先级
          </span>
          <span v-if="step.estimatedHours" class="step-card__hours">
            预计 {{ step.estimatedHours }}h
          </span>
        </div>
      </div>

      <p v-if="step.stepDescription" class="step-card__desc">{{ step.stepDescription }}</p>

      <!-- Project tasks -->
      <div v-if="step.projectTasks?.length" class="step-card__tasks">
        <div v-for="task in step.projectTasks" :key="task.id" class="step-card__task">
          <div class="step-card__task-header">
            <span class="step-card__task-title">{{ task.taskTitle }}</span>
            <span
              class="step-card__task-status"
              :style="{ color: getStatusMeta(task.status).color, background: getStatusMeta(task.status).bg }"
            >
              {{ getStatusMeta(task.status).label }}
            </span>
          </div>
          <div class="step-card__task-meta">
            <span
              v-if="task.difficultyLevel"
              class="step-card__task-difficulty"
              :style="{ color: getDifficultyMeta(task.difficultyLevel).color }"
            >
              {{ getDifficultyMeta(task.difficultyLevel).label }}
            </span>
            <a v-if="task.projectUrl" :href="task.projectUrl" target="_blank" class="step-card__task-link">项目地址</a>
          </div>
          <div class="step-card__task-actions">
            <el-button
              v-if="task.status === 'PENDING' || task.status === 'REVISION_REQUIRED'"
              type="primary"
              size="small"
              @click.stop="emit('submitTask', task)"
            >
              提交成果
            </el-button>
          </div>
        </div>
      </div>

      <!-- Actions -->
      <div class="step-card__actions">
        <el-button
          v-if="step.status === 'PENDING'"
          type="primary"
          size="small"
          @click.stop="emit('startLearning', step)"
        >
          开始学习
        </el-button>
        <el-button
          v-if="step.status === 'IN_PROGRESS'"
          type="success"
          size="small"
          @click.stop="emit('markCompleted', step)"
        >
          标记完成
        </el-button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.step-card {
  display: flex;
  gap: 16px;
  padding: 0 0 0 40px;
  position: relative;
  cursor: pointer;
}

.step-card__dot {
  position: absolute;
  left: 0;
  top: 20px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.step-card__dot-icon {
  font-size: 8px;
  color: #fff;
  font-weight: 700;
}

.step-card__body {
  flex: 1;
  min-width: 0;
  padding: 16px 20px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.step-card:hover .step-card__body {
  border-color: #2563eb;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.06);
}

.step-card.is-active .step-card__body {
  border-color: #2563eb;
  box-shadow: 0 2px 12px rgba(37, 99, 235, 0.1);
}

.step-card.is-completed .step-card__body {
  opacity: 0.7;
}

.step-card__header {
  margin-bottom: 8px;
}

.step-card__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.step-card__title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}

.step-card__status {
  flex-shrink: 0;
  padding: 2px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.step-card__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.step-card__ability {
  font-size: 12px;
  font-weight: 600;
  color: #2563eb;
  background: #dbeafe;
  padding: 2px 8px;
  border-radius: 4px;
}

.step-card__level {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.step-card__level-from {
  color: #9ca3af;
}

.step-card__level-arrow {
  color: #d1d5db;
  font-size: 10px;
}

.step-card__level-to {
  color: #059669;
  font-weight: 600;
}

.step-card__priority {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.step-card__hours {
  font-size: 12px;
  color: #9ca3af;
}

.step-card__desc {
  margin: 8px 0;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* Tasks */
.step-card__tasks {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f3f4f6;
}

.step-card__task {
  padding: 12px;
  border: 1px solid #f3f4f6;
  border-radius: 8px;
  background: #f9fafb;
  margin-bottom: 8px;
}

.step-card__task:last-child {
  margin-bottom: 0;
}

.step-card__task-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.step-card__task-title {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.step-card__task-status {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.step-card__task-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
  font-size: 12px;
}

.step-card__task-difficulty {
  font-weight: 600;
}

.step-card__task-link {
  color: #2563eb;
  text-decoration: none;
  font-size: 12px;
}

.step-card__task-link:hover {
  text-decoration: underline;
}

.step-card__task-actions {
  margin-top: 8px;
}

/* Actions */
.step-card__actions {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #f3f4f6;
}
</style>
