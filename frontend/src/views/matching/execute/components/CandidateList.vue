<script setup lang="ts">
import type { EmployeeRecommendation, PostRecommendation } from '@/api/recommend'
import type { MatchMode } from '../logic'

type Candidate = PostRecommendation | EmployeeRecommendation

const props = defineProps<{
  mode: MatchMode
  items: Candidate[]
  selectedIds: number[]
  previewLoading: boolean
  topK: number
  pairCount: number
  executing: boolean
  stats: { total: number; selected: number; pass: number; risk: number; fail: number }
}>()
const emit = defineEmits<{
  (e: 'toggle', id: number): void
  (e: 'select-all'): void
  (e: 'select-recommended'): void
  (e: 'clear'): void
  (e: 'generate'): void
  (e: 'update:topK', v: number): void
  (e: 'execute'): void
}>()

function getId(item: Candidate) { return 'postId' in item ? item.postId : item.empId }
function getScoreColor(s: number) { return s >= 85 ? '#22c55e' : s >= 70 ? '#3b82f6' : s >= 55 ? '#f59e0b' : '#ef4444' }
function getTagType(status: string) { return status === 'PASS' ? 'success' : status === 'RISK' ? 'warning' : 'danger' }
function getEvidenceTagType(level: string) { return level === 'STRONG' ? 'success' : level === 'MEDIUM' ? 'warning' : 'info' }
function isSelected(item: Candidate) { return props.selectedIds.includes(getId(item)) }
</script>

<template>
  <div class="candidate-wrap">
    <!-- 统计栏 -->
    <div v-if="!previewLoading && items.length" class="stat-bar">
      <span>共 {{ stats.total }} 个候选</span>
      <span class="stat-pass">通过 {{ stats.pass }}</span>
      <span class="stat-risk">风险 {{ stats.risk }}</span>
      <span class="stat-fail">失败 {{ stats.fail }}</span>
      <div class="stat-bar__right">
        <el-button plain size="small" @click="emit('select-all')">全选</el-button>
        <el-button plain size="small" @click="emit('select-recommended')">选通过</el-button>
        <el-button plain size="small" @click="emit('clear')">清空</el-button>
        <span class="pair-hint">已选 {{ pairCount }} 对</span>
        <el-button type="primary" size="small" :loading="executing" :disabled="pairCount === 0" @click="emit('execute')">开始匹配</el-button>
      </div>
    </div>

    <!-- 骨架屏 -->
    <div v-if="previewLoading" class="candidate-list" aria-label="候选加载中">
      <div v-for="n in 5" :key="n" class="candidate-card skeleton-card">
        <div class="skeleton-block skeleton-check"></div>
        <div class="skeleton-info">
          <div class="skeleton-block skeleton-line skeleton-line--name"></div>
          <div class="skeleton-block skeleton-line skeleton-line--sub"></div>
        </div>
        <div class="skeleton-block skeleton-score"></div>
        <div class="skeleton-tags">
          <div class="skeleton-block skeleton-tag"></div>
          <div class="skeleton-block skeleton-tag"></div>
          <div class="skeleton-block skeleton-tag"></div>
        </div>
      </div>
    </div>

    <!-- 候选卡片 -->
    <div v-else-if="items.length" class="candidate-list">
      <article
        v-for="item in items"
        :key="getId(item)"
        class="candidate-card"
        :class="{ selected: isSelected(item) }"
        @click="emit('toggle', getId(item))"
      >
        <el-checkbox :model-value="isSelected(item)" @click.stop @change="emit('toggle', getId(item))" />
        <div class="candidate-card__info">
          <div class="candidate-card__name">{{ 'postName' in item ? item.postName : item.empName }}</div>
          <div class="candidate-card__sub">{{ 'postCode' in item ? [item.postCode, item.postLevel].filter(Boolean).join(' / ') : item.empCode }}</div>
        </div>
        <div class="candidate-card__score" :style="{ color: getScoreColor(item.recommendScore) }">{{ item.recommendScore }}</div>
        <div class="candidate-card__tags">
          <el-tag :type="getTagType(item.hardConditionStatus)" size="small">L1 {{ item.hardConditionStatus === 'PASS' ? '通过' : item.hardConditionStatus === 'RISK' ? '风险' : '失败' }}</el-tag>
          <el-tag type="primary" size="small">L2 {{ item.l2PreviewScore }}</el-tag>
          <el-tag :type="getEvidenceTagType(item.evidenceConfidence)" size="small">{{ item.evidenceConfidence }}</el-tag>
        </div>
      </article>
    </div>

    <!-- 空状态引导 -->
    <div v-else class="empty-guide">
      <div class="empty-guide__title">操作步骤</div>
      <div class="empty-guide__steps">
        <div class="guide-step"><span class="guide-step__num">1</span>选择源对象（员工或岗位）</div>
        <div class="guide-step"><span class="guide-step__num">2</span>点击「生成候选」预览匹配结果</div>
        <div class="guide-step"><span class="guide-step__num">3</span>勾选确认后点击「开始匹配」</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.candidate-wrap { padding: 0 20px 20px; }
.stat-bar { display: flex; align-items: center; gap: 12px; padding: 0 0 12px; font-size: 13px; color: #6b7280; flex-wrap: wrap; }
.stat-pass { color: #22c55e; font-weight: 600; }
.stat-risk { color: #f59e0b; font-weight: 600; }
.stat-fail { color: #ef4444; font-weight: 600; }
.stat-bar__right { margin-left: auto; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.pair-hint { font-size: 13px; color: #6b7280; }

.candidate-list { display: flex; flex-direction: column; gap: 8px; max-height: 460px; overflow-y: auto; }
.candidate-card {
  display: grid; grid-template-columns: 32px 1fr 64px 240px; gap: 12px; align-items: center;
  padding: 12px 16px; border-radius: 10px; border: 1px solid var(--app-divider, #e5e7eb);
  border-left: 4px solid var(--app-divider, #e5e7eb); position: relative;
  cursor: pointer; transition: all 0.2s ease; background: rgba(255,255,255,0.55);
}
.candidate-card:hover { background: rgba(255,255,255,0.95); border-color: rgba(59,130,246,0.2); border-left-color: rgba(59,130,246,0.4); transform: translateX(2px); }
.candidate-card.selected { background: rgba(59,130,246,0.04); border-color: #3b82f6; border-left-color: #3b82f6; box-shadow: 0 2px 8px rgba(59,130,246,0.08); }
.candidate-card.selected::before {
  content: '✓'; position: absolute; top: -7px; right: -7px; width: 18px; height: 18px;
  border-radius: 50%; background: linear-gradient(135deg, #2563eb, #3b82f6); color: #fff;
  font-size: 11px; font-weight: 700; display: flex; align-items: center; justify-content: center;
  box-shadow: 0 2px 6px rgba(37,99,235,0.35);
}
.candidate-card__name { font-size: 14px; font-weight: 600; color: var(--app-text-strong, #111827); }
.candidate-card__sub { font-size: 12px; color: #9ca3af; }
.candidate-card__score { font-size: 20px; font-weight: 800; text-align: center; }
.candidate-card__tags { display: flex; gap: 6px; flex-wrap: wrap; }

/* 骨架屏 */
.skeleton-card { cursor: default; pointer-events: none; }
.skeleton-block {
  background: linear-gradient(90deg, rgba(226,232,240,0.6) 25%, rgba(241,245,249,0.9) 50%, rgba(226,232,240,0.6) 75%);
  background-size: 200% 100%; animation: shimmer 1.4s infinite;
  border-radius: 6px;
}
.skeleton-check { width: 16px; height: 16px; }
.skeleton-info { display: flex; flex-direction: column; gap: 6px; }
.skeleton-line { height: 10px; }
.skeleton-line--name { width: 60%; }
.skeleton-line--sub { width: 40%; }
.skeleton-score { width: 40px; height: 20px; }
.skeleton-tags { display: flex; gap: 6px; }
.skeleton-tag { width: 52px; height: 18px; border-radius: 999px; }

@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

/* 空状态 */
.empty-guide { padding: 8px 0; }
.empty-guide__title { font-size: 14px; font-weight: 700; color: var(--app-text-strong, #111827); margin-bottom: 16px; }
.guide-step { display: flex; align-items: center; gap: 12px; padding: 10px 0; font-size: 13px; color: #6b7280; }
.guide-step__num {
  width: 22px; height: 22px; border-radius: 50%; background: var(--app-divider, #e5e7eb);
  display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; color: #9ca3af; flex-shrink: 0;
}

@media (max-width: 720px) {
  .candidate-card { grid-template-columns: 32px 1fr 64px; }
  .candidate-card__tags { grid-column: 1/-1; }
}
</style>
