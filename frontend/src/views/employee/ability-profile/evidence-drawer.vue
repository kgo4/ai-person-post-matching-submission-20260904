<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { getAbilityEvidence } from '@/api/ability-governance'
import type { AbilityEvidence, PersonAbilityProfile } from '@/api/ability-governance'

const props = defineProps<{
  visible: boolean
  ability: PersonAbilityProfile | null
  empId: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const drawerVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const loading = ref(false)
const evidenceList = ref<AbilityEvidence[]>([])

// 按来源分组
const groupedEvidence = computed(() => {
  const groups: Record<string, AbilityEvidence[]> = {}
  evidenceList.value.forEach((e) => {
    if (!groups[e.sourceType]) groups[e.sourceType] = []
    groups[e.sourceType].push(e)
  })
  return groups
})

const sourceLabels: Record<string, string> = {
  RESUME_PARSE: '简历证据',
  AI_TEST: 'AI测试证据',
  VIDEO_INTERVIEW: 'AI面试证据',
  PMS_ANALYSIS: 'PMS证据',
}

const harnessLabels: Record<string, string> = {
  PASS: '通过',
  REVIEW: '待审核',
  BLOCK: '拦截',
}

watch(() => props.visible, async (val) => {
  if (val && props.ability) {
    await loadEvidence()
  }
})

async function loadEvidence() {
  if (!props.ability) return
  loading.value = true
  try {
    const res = await getAbilityEvidence(props.empId, props.ability.tagId)
    evidenceList.value = res.data || []
  } catch (error) {
    console.error('加载证据失败', error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-drawer
    v-model="drawerVisible"
    :title="`${ability?.tagName || ''} - 证据详情`"
    size="500px"
  >
    <div v-loading="loading" class="evidence-drawer">
      <!-- 来源统计 -->
      <div class="source-stats">
        <div class="source-stats__title">来源构成</div>
        <div class="source-stats__grid">
          <div v-for="(evidences, source) in groupedEvidence" :key="source" class="source-stat-item">
            <div class="source-stat-item__label">{{ sourceLabels[source] || source }}</div>
            <div class="source-stat-item__count">{{ evidences.length }} 条</div>
          </div>
        </div>
      </div>

      <!-- 证据列表 -->
      <div class="evidence-list">
        <template v-for="(evidences, source) in groupedEvidence" :key="source">
          <div class="evidence-group">
            <div class="evidence-group__header">
              <span class="evidence-group__title">{{ sourceLabels[source] || source }}</span>
              <el-tag size="small">{{ evidences.length }} 条</el-tag>
            </div>
            <div class="evidence-group__list">
              <div v-for="evidence in evidences" :key="evidence.id" class="evidence-item">
                <div class="evidence-item__header">
                  <el-tag :type="evidence.harnessDecision === 'PASS' ? 'success' : evidence.harnessDecision === 'BLOCK' ? 'danger' : 'warning'" size="small">
                    {{ harnessLabels[evidence.harnessDecision] || evidence.harnessDecision }}
                  </el-tag>
                  <span v-if="evidence.harnessScore" class="evidence-item__score">
                    置信度: {{ Math.round(evidence.harnessScore) }}%
                  </span>
                  <span class="evidence-item__time">{{ evidence.extractedTime }}</span>
                </div>
                <div class="evidence-item__content">
                  {{ evidence.evidenceText }}
                </div>
                <div class="evidence-item__actions">
                  <el-button link type="success" size="small">采纳</el-button>
                  <el-button link type="danger" size="small">不采纳</el-button>
                  <el-button link type="warning" size="small">标记误判</el-button>
                </div>
              </div>
            </div>
          </div>
        </template>

        <el-empty v-if="!loading && evidenceList.length === 0" description="暂无证据" />
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.evidence-drawer {
  @apply space-y-6;
}

.source-stats {
  @apply bg-gray-50 rounded-lg p-4;
}

.source-stats__title {
  @apply text-sm font-medium text-gray-700 mb-3;
}

.source-stats__grid {
  @apply grid grid-cols-2 gap-3;
}

.source-stat-item {
  @apply flex items-center justify-between bg-white rounded px-3 py-2;
}

.source-stat-item__label {
  @apply text-sm text-gray-600;
}

.source-stat-item__count {
  @apply text-sm font-medium text-gray-900;
}

.evidence-group {
  @apply mb-6;
}

.evidence-group__header {
  @apply flex items-center justify-between mb-3 pb-2 border-b;
}

.evidence-group__title {
  @apply text-sm font-medium text-gray-700;
}

.evidence-item {
  @apply border rounded-lg p-3 mb-3;
}

.evidence-item__header {
  @apply flex items-center gap-2 mb-2;
}

.evidence-item__score {
  @apply text-xs text-gray-500;
}

.evidence-item__time {
  @apply text-xs text-gray-400 ml-auto;
}

.evidence-item__content {
  @apply text-sm text-gray-700 mb-2 whitespace-pre-wrap;
}

.evidence-item__actions {
  @apply flex items-center gap-2 pt-2 border-t;
}
</style>
