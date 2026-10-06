<script setup lang="ts">
/** 阶段定义 */
export interface LearningPhase {
  key: string
  label: string
  description: string
  icon?: string
}

defineProps<{
  phases: LearningPhase[]
  activePhase: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  selectPhase: [key: string]
}>()
</script>

<template>
  <div class="step-bar">
    <div
      v-for="(phase, idx) in phases"
      :key="phase.key"
      class="step-bar__item"
      :class="{
        'is-active': activePhase === phase.key,
        'is-done': phases.findIndex(p => p.key === activePhase) > idx,
        'is-disabled': disabled,
      }"
      @click="!disabled && emit('selectPhase', phase.key)"
    >
      <div class="step-bar__dot">
        <span v-if="phases.findIndex(p => p.key === activePhase) > idx" class="step-bar__check">✓</span>
        <span v-else class="step-bar__num">{{ idx + 1 }}</span>
      </div>
      <div class="step-bar__text">
        <span class="step-bar__label">{{ phase.label }}</span>
        <span class="step-bar__desc">{{ phase.description }}</span>
      </div>
      <div v-if="idx < phases.length - 1" class="step-bar__line" />
    </div>
  </div>
</template>

<style scoped>
.step-bar {
  display: flex;
  align-items: flex-start;
  gap: 0;
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 12px;
  margin-bottom: 16px;
}

.step-bar__item {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  position: relative;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 8px;
  transition: background 0.15s;
}

.step-bar__item:hover {
  background: rgba(37, 99, 235, 0.04);
}

.step-bar__item.is-disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.step-bar__item.is-done:hover {
  background: rgba(5, 150, 105, 0.04);
}

.step-bar__dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 2px solid #d1d5db;
  background: #fff;
  transition: all 0.2s;
}

.step-bar__item.is-active .step-bar__dot {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}

.step-bar__item.is-done .step-bar__dot {
  border-color: #059669;
  background: #059669;
  color: #fff;
}

.step-bar__num {
  font-size: 12px;
  font-weight: 700;
  color: #9ca3af;
}

.step-bar__item.is-active .step-bar__num {
  color: #fff;
}

.step-bar__check {
  font-size: 13px;
  font-weight: 700;
  color: #fff;
}

.step-bar__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.step-bar__label {
  font-size: 13px;
  font-weight: 700;
  color: #9ca3af;
  white-space: nowrap;
  transition: color 0.15s;
}

.step-bar__item.is-active .step-bar__label {
  color: #111827;
}

.step-bar__item.is-done .step-bar__label {
  color: #059669;
}

.step-bar__desc {
  font-size: 11px;
  color: #9ca3af;
  white-space: nowrap;
}

.step-bar__line {
  position: absolute;
  right: -4px;
  top: 20px;
  width: 100%;
  max-width: 60px;
  height: 2px;
  background: #e5e7eb;
  border-radius: 1px;
  transition: background 0.2s;
}

.step-bar__item.is-done .step-bar__line {
  background: #059669;
}

@media (max-width: 800px) {
  .step-bar {
    flex-wrap: wrap;
    gap: 8px;
  }
  .step-bar__item {
    flex: unset;
  }
  .step-bar__line {
    display: none;
  }
}
</style>
