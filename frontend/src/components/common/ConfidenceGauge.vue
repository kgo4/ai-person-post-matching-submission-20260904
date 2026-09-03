<script setup lang="ts">
/**
 * 置信度指示器 — 统一展示AI推荐/变更项的置信度评分
 * 用于新兴岗位发现、岗位演化变更审核等场景
 */

defineOptions({ name: 'ConfidenceGauge' })

const props = withDefaults(defineProps<{
  /** 置信度分数 0-100 */
  score: number
  /** 证据数量 */
  evidenceCount?: number
  /** 是否显示证据数 */
  showEvidenceCount?: boolean
  /** 尺寸 */
  size?: 'small' | 'default'
  /** 是否显示幻觉风险警告 */
  showHallucinationRisk?: boolean
}>(), {
  evidenceCount: 0,
  showEvidenceCount: false,
  size: 'default',
  showHallucinationRisk: false,
})

const level = computed(() => {
  if (props.score >= 80) return 'high'
  if (props.score >= 50) return 'medium'
  return 'low'
})

const levelConfig = computed(() => {
  const map = {
    high: { label: '高置信', color: 'var(--app-success)', bg: 'var(--app-success-soft)' },
    medium: { label: '中置信', color: 'var(--app-warning)', bg: 'var(--app-warning-soft)' },
    low: { label: '低置信', color: 'var(--app-danger)', bg: 'var(--app-danger-soft)' },
  }
  return map[level.value]
})

import { computed } from 'vue'

function scoreColor(score: number): string {
  if (score >= 80) return 'var(--app-success)'
  if (score >= 50) return 'var(--app-warning)'
  return 'var(--app-danger)'
}
</script>

<template>
  <div class="confidence-gauge" :class="`is-${size} is-${level}`">
    <div class="confidence-gauge__bar-track">
      <div
        class="confidence-gauge__bar-fill"
        :style="{ width: `${Math.min(100, Math.max(0, score))}%`, background: scoreColor(score) }"
      ></div>
    </div>
    <div class="confidence-gauge__info">
      <span class="confidence-gauge__score" :style="{ color: scoreColor(score) }">
        {{ Math.round(score) }}%
      </span>
      <span class="confidence-gauge__label" :style="{ color: levelConfig.color }">
        {{ levelConfig.label }}
      </span>
      <span v-if="showEvidenceCount && evidenceCount > 0" class="confidence-gauge__evidence">
        {{ evidenceCount }}条证据
      </span>
      <el-tooltip
        v-if="showHallucinationRisk && level === 'low'"
        content="置信度过低，可能存在AI幻觉风险，建议人工核实"
        placement="top"
      >
        <el-tag type="danger" size="small" class="confidence-gauge__risk-tag">幻觉风险</el-tag>
      </el-tooltip>
    </div>
  </div>
</template>

<style scoped>
.confidence-gauge {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 140px;
}

.confidence-gauge.is-small {
  min-width: 100px;
  gap: 6px;
}

.confidence-gauge__bar-track {
  flex: 1;
  height: 8px;
  border-radius: 999px;
  background: var(--app-border);
  overflow: hidden;
}

.confidence-gauge.is-small .confidence-gauge__bar-track {
  height: 5px;
}

.confidence-gauge__bar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.45s ease;
}

.confidence-gauge__info {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  font-size: 12px;
}

.confidence-gauge.is-small .confidence-gauge__info {
  font-size: 11px;
  gap: 4px;
}

.confidence-gauge__score {
  font-weight: 800;
  min-width: 34px;
}

.confidence-gauge__label {
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 5px;
}

.confidence-gauge__evidence {
  color: var(--app-text-muted);
  font-size: 11px;
}

.confidence-gauge__risk-tag {
  cursor: help;
  margin-left: 2px;
}
</style>
