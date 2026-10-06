<script setup lang="ts">
import type { LearningPathStep } from '@/api'

const props = defineProps<{
  step?: LearningPathStep | null
}>()

const typeMeta: Record<string, { icon: string; label: string }> = {
  COURSE: { icon: '📚', label: '课程' },
  DOC: { icon: '📄', label: '文档' },
  PRACTICE: { icon: '🧪', label: '练习' },
  PROJECT: { icon: '🛠', label: '项目' },
  BOOK: { icon: '📖', label: '书籍' },
  VIDEO: { icon: '🎬', label: '视频' },
}

function iconFor(type?: string): string {
  return (type && typeMeta[type]?.icon) || '📄'
}

function typeLabel(type?: string): string {
  return (type && typeMeta[type]?.label) || '学习资源'
}
</script>

<template>
  <div class="resource-list">
    <div v-if="step" class="resource-list__content">
      <!-- 真实资源记录（来自 learning_resource 表） -->
      <div v-if="step.resourceId" class="resource-card">
        <div class="resource-card__main">
          <span class="resource-card__icon">{{ iconFor(step.resourceType) }}</span>
          <div class="resource-card__info">
            <div class="resource-card__title">{{ step.resourceTitle || '未命名资源' }}</div>
            <div class="resource-card__meta">{{ typeLabel(step.resourceType) }} · 匹配 {{ step.resourceCount ?? 1 }} 个资源</div>
          </div>
        </div>
        <a
          v-if="step.resourceUrl"
          :href="step.resourceUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="resource-card__open"
        >打开资源</a>
      </div>
      <div v-else class="resource-card resource-card--empty">
        <span class="resource-card__empty-text">暂无匹配学习资源</span>
        <span class="resource-card__empty-hint">系统暂未收录「{{ step.abilityName }}」相关资源，可在资源管理中补充</span>
      </div>

      <div v-if="step.stepDescription" class="resource-list__detail">
        <div class="resource-list__detail-title">学习说明</div>
        <p class="resource-list__detail-text">{{ step.stepDescription }}</p>
      </div>

      <div v-if="step.projectTasks?.length" class="resource-list__detail">
        <div class="resource-list__detail-title">项目任务要求</div>
        <div v-for="task in step.projectTasks" :key="task.id">
          <pre v-if="task.taskRequirements" class="resource-list__pre">{{ task.taskRequirements }}</pre>
          <div v-if="task.acceptanceCriteria" class="resource-list__criteria">
            <div class="resource-list__criteria-label">验收标准</div>
            <pre class="resource-list__pre">{{ task.acceptanceCriteria }}</pre>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="resource-list__empty">
      选择一个步骤查看学习资源
    </div>
  </div>
</template>

<style scoped>
.resource-list__content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 真实资源卡片 */
.resource-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.resource-card__main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.resource-card__icon {
  font-size: 16px;
  flex-shrink: 0;
}

.resource-card__info {
  min-width: 0;
}

.resource-card__title {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-card__meta {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 2px;
}

.resource-card__open {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 6px;
  background: #2563eb;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
  transition: background 0.15s;
}

.resource-card__open:hover {
  background: #1d4ed8;
}

.resource-card--empty {
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  background: #f9fafb;
}

.resource-card__empty-text {
  font-size: 13px;
  font-weight: 600;
  color: #6b7280;
}

.resource-card__empty-hint {
  font-size: 11px;
  color: #9ca3af;
}

.resource-list__detail {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #f3f4f6;
}

.resource-list__detail-title {
  font-size: 12px;
  font-weight: 700;
  color: #6b7280;
  margin-bottom: 6px;
}

.resource-list__detail-text {
  margin: 0;
  font-size: 13px;
  color: #6b7280;
  line-height: 1.6;
}

.resource-list__pre {
  margin: 0;
  padding: 10px;
  border-radius: 6px;
  background: #f9fafb;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.6;
  white-space: pre-wrap;
  font-family: inherit;
}

.resource-list__criteria {
  margin-top: 8px;
}

.resource-list__criteria-label {
  font-size: 12px;
  font-weight: 700;
  color: #d97706;
  margin-bottom: 4px;
}

.resource-list__empty {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
}
</style>
