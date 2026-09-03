<script setup lang="ts">
import type { ScorePart } from '../match-detail-view-model'
import { formatScore } from '../match-detail-view-model'

defineProps<{
  parts: ScorePart[]
  finalScore: number | null | undefined
  finalColor: string
  personName: string
  postName: string
  empId?: number | null
  postId?: number | null
}>()
</script>

<template>
  <div class="match-score-panel">
    <section class="detail-band">
      <div class="detail-band__entity">
        <div class="detail-band__label">人员</div>
        <div class="detail-band__value">{{ personName }}</div>
        <div class="detail-band__sub">ID: {{ empId }}</div>
      </div>
      <div class="detail-band__arrow">→</div>
      <div class="detail-band__entity">
        <div class="detail-band__label">目标岗位</div>
        <div class="detail-band__value">{{ postName }}</div>
        <div class="detail-band__sub">ID: {{ postId }}</div>
      </div>
      <div class="detail-band__score">
        <div class="detail-band__score-value" :style="{ color: finalColor }">
          {{ formatScore(finalScore) }}
        </div>
        <div class="detail-band__sub">最终分</div>
      </div>
    </section>

    <section class="score-parts">
      <article v-for="part in parts" :key="part.label" class="glass-panel score-card">
        <div class="score-card__top">
          <el-icon :style="{ color: part.color }"><component :is="part.icon" /></el-icon>
          <span>{{ part.label }}</span>
        </div>
        <div class="score-card__value" :style="{ color: part.value != null ? part.color : '#94a3b8' }">
          {{ part.value == null ? (part.emptyText || '-') : Number(part.value).toFixed(2) }}
        </div>
        <p class="score-card__desc">{{ part.desc }}</p>
      </article>
    </section>
  </div>
</template>

<style scoped>
.match-score-panel {
  /* wrapper only, no styles needed */
}

.detail-band {
  display: grid;
  grid-template-columns: 1fr auto 1fr auto;
  gap: 22px;
  align-items: center;
  margin-bottom: 22px;
  padding-bottom: 22px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
}

.detail-band__label,
.detail-band__sub {
  color: var(--app-text-muted);
  font-size: 12px;
}

.detail-band__value {
  margin-top: 6px;
  color: var(--app-text-strong);
  font-size: 24px;
  font-weight: 800;
}

.detail-band__arrow {
  color: #38bdf8;
  font-size: 26px;
}

.detail-band__score {
  min-width: 140px;
  text-align: right;
}

.detail-band__score-value {
  font-size: 42px;
  line-height: 1;
  font-weight: 900;
}

.score-parts {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 22px;
}

.score-card {
  padding: 18px;
}

.score-card__top {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--app-text-secondary);
  font-size: 13px;
  font-weight: 700;
}

.score-card__value {
  margin-top: 12px;
  font-size: 28px;
  line-height: 1;
  font-weight: 900;
}

.score-card__desc {
  margin-top: 10px;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.6;
}
</style>
