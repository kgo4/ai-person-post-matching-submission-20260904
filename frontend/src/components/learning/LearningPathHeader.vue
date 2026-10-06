<script setup lang="ts">
import { computed } from 'vue'
import { getStatusMeta, formatScore, progressPercent } from './types'

const props = defineProps<{
  planTitle?: string
  empId?: number
  empName?: string
  postId?: number
  postName?: string
  matchingRecordId?: number
  planStatus?: string
  currentScore?: number | null
  targetScore?: number | null
  completedStepCount?: number
  totalStepCount?: number
  pendingSubmissionCount?: number
  loading?: boolean
}>()

const emit = defineEmits<{
  back: []
  refresh: []
  regenerate: []
  generateAssessment: []
}>()

const statusMeta = computed(() => getStatusMeta(props.planStatus || 'PENDING'))
const percent = computed(() => progressPercent(props.completedStepCount || 0, props.totalStepCount || 0))
</script>

<template>
  <header class="lp-header">
    <div class="lp-header__left">
      <div class="lp-header__title-row">
        <h1 class="lp-header__title">{{ planTitle || '学习路径' }}</h1>
        <span class="lp-header__status" :style="{ color: statusMeta.color, background: statusMeta.bg }">
          {{ statusMeta.label }}
        </span>
      </div>
      <div class="lp-header__meta">
        <span v-if="empName || empId">{{ empName || `人员#${empId}` }}</span>
        <span class="lp-header__sep">/</span>
        <span v-if="postName || postId">{{ postName || `岗位#${postId}` }}</span>
        <template v-if="matchingRecordId">
          <span class="lp-header__sep">/</span>
          <span>匹配记录 #{{ matchingRecordId }}</span>
        </template>
        <template v-if="currentScore != null">
          <span class="lp-header__sep">/</span>
          <span>当前 {{ formatScore(currentScore) }}分</span>
          <span v-if="targetScore != null"> → 目标 {{ formatScore(targetScore) }}分</span>
        </template>
      </div>
    </div>

    <div class="lp-header__right">
      <div class="lp-header__stats">
        <div class="lp-header__stat">
          <span class="lp-header__stat-value">{{ completedStepCount || 0 }}/{{ totalStepCount || 0 }}</span>
          <span class="lp-header__stat-label">完成</span>
        </div>
        <div class="lp-header__stat">
          <span class="lp-header__stat-value">{{ percent }}%</span>
          <span class="lp-header__stat-label">进度</span>
        </div>
        <div class="lp-header__stat" v-if="pendingSubmissionCount">
          <span class="lp-header__stat-value lp-header__stat-value--warn">{{ pendingSubmissionCount }}</span>
          <span class="lp-header__stat-label">待验证</span>
        </div>
      </div>

      <div class="lp-header__actions">
        <el-button size="small" @click="emit('back')">返回</el-button>
        <el-button size="small" :loading="loading" @click="emit('refresh')">刷新</el-button>
        <el-button size="small" type="primary" plain @click="emit('generateAssessment')">生成测评</el-button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.lp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  height: 64px;
  padding: 0 24px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  margin-bottom: 16px;
}

.lp-header__left {
  min-width: 0;
  flex: 1;
}

.lp-header__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.lp-header__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lp-header__status {
  flex-shrink: 0;
  padding: 2px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
}

.lp-header__meta {
  margin-top: 4px;
  font-size: 13px;
  color: #6b7280;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lp-header__sep {
  margin: 0 6px;
  color: #d1d5db;
}

.lp-header__right {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-shrink: 0;
}

.lp-header__stats {
  display: flex;
  gap: 16px;
}

.lp-header__stat {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.lp-header__stat-value {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.lp-header__stat-value--warn {
  color: #d97706;
}

.lp-header__stat-label {
  font-size: 11px;
  color: #9ca3af;
}

.lp-header__actions {
  display: flex;
  gap: 8px;
}
</style>
