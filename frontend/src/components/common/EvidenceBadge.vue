<script setup lang="ts">
/**
 * 数据来源证据标签 — 统一展示数据来源类型、采集时间和可信度等级
 * 用于新兴岗位发现、岗位演化、差距诊断等多处需要标注数据来源的场景
 */

defineOptions({ name: 'EvidenceBadge' })

export interface EvidenceSource {
  sourceType: string
  sourceName?: string
  collectedAt?: string
  confidenceLevel?: 'HIGH' | 'MEDIUM' | 'LOW'
  sampleCount?: number
}

const props = withDefaults(defineProps<{
  source: EvidenceSource
  /** 是否显示时间 */
  showTime?: boolean
  /** 是否显示样本数 */
  showCount?: boolean
}>(), {
  showTime: true,
  showCount: false,
})

const sourceTypeLabel: Record<string, string> = {
  CLOUD_KNOWLEDGE_INTERNAL: '企业云知识库',
  INDUSTRY_WHITEPAPER: '行业白皮书',
  INTERNAL_POST_INFO: '内部岗位资料',
  INTERNAL_BUSINESS_UPDATE: '内部业务资料',
  INTERNAL_POLICY: '内部制度资料',
  JD_IMPORT: 'JD解析',
  RESUME_PARSE: '简历解析',
  AI_TEST: 'AI测评',
  VIDEO_INTERVIEW: 'AI面试',
  PMS_ANALYSIS: '项目分析',
  MANUAL: '人工维护',
  POST_EVOLUTION: '岗位演化',
  POST_ABILITY_MODEL: '岗位模型',
  CONTEST_EVIDENCE: '来源证据',
  RECRUITMENT_SITE: '招聘网站',
  INDUSTRY_REPORT: '行业报告',
  ACADEMIC_PAPER: '学术论文',
  INTERNAL_FEEDBACK: '内部反馈',
  CLOUD_KNOWLEDGE: '云知识库',
  RAG_DOCUMENT: '知识文档',
}

const confidenceConfig: Record<string, { label: string; color: string; bg: string }> = {
  HIGH: { label: '高可信', color: '#059669', bg: 'rgba(5,150,105,0.1)' },
  MEDIUM: { label: '中可信', color: '#d97706', bg: 'rgba(217,119,6,0.1)' },
  LOW: { label: '低可信', color: '#dc2626', bg: 'rgba(220,38,38,0.1)' },
}

function sourceLabel(type: string): string {
  return sourceTypeLabel[type] || type
}

function formatTime(time?: string): string {
  if (!time) return ''
  const d = new Date(time)
  const now = Date.now()
  const days = Math.floor((now - d.getTime()) / 86400000)
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 7) return `${days}天前`
  if (days < 30) return `${Math.floor(days / 7)}周前`
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function stalenessClass(time?: string): string {
  if (!time) return ''
  const days = Math.floor((Date.now() - new Date(time).getTime()) / 86400000)
  if (days < 7) return 'is-fresh'
  if (days < 30) return 'is-recent'
  return 'is-stale'
}
</script>

<template>
  <span class="evidence-badge" :class="[stalenessClass(source.collectedAt)]">
    <span class="evidence-badge__dot" :class="`is-${(source.confidenceLevel || 'MEDIUM').toLowerCase()}`"></span>
    <span class="evidence-badge__type">{{ sourceLabel(source.sourceType) }}</span>
    <span v-if="source.sourceName" class="evidence-badge__name">{{ source.sourceName }}</span>
    <span v-if="showTime && source.collectedAt" class="evidence-badge__time">{{ formatTime(source.collectedAt) }}</span>
    <span v-if="source.confidenceLevel" class="evidence-badge__confidence" :style="{ color: confidenceConfig[source.confidenceLevel]?.color, background: confidenceConfig[source.confidenceLevel]?.bg }">
      {{ confidenceConfig[source.confidenceLevel]?.label }}
    </span>
    <span v-if="showCount && source.sampleCount != null" class="evidence-badge__count">×{{ source.sampleCount }}</span>
  </span>
</template>

<style scoped>
.evidence-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
  background: rgba(148, 163, 184, 0.08);
  color: var(--app-text-secondary);
  white-space: nowrap;
  transition: opacity 0.2s;
}

.evidence-badge:hover {
  opacity: 0.85;
}

.evidence-badge__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.evidence-badge__dot.is-high { background: var(--app-success); }
.evidence-badge__dot.is-medium { background: var(--app-warning); }
.evidence-badge__dot.is-low { background: var(--app-danger); }

.evidence-badge__type {
  color: var(--app-primary);
}

.evidence-badge__name {
  color: var(--app-text-muted);
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.evidence-badge__time {
  color: var(--app-text-muted);
  font-size: 10px;
  border-left: 1px solid var(--app-divider);
  padding-left: 5px;
}

.evidence-badge.is-stale .evidence-badge__time {
  color: var(--app-warning);
}

.evidence-badge__confidence {
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
}

.evidence-badge__count {
  color: var(--app-text-muted);
  font-size: 10px;
}
</style>
