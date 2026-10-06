<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { getGovernanceHistory } from '@/api/ability-governance'
import type { PersonAbilityGovernanceEvent } from '@/api/ability-governance'

const props = defineProps<{
  visible: boolean
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
const events = ref<PersonAbilityGovernanceEvent[]>([])

const modifyTypeLabels: Record<string, string> = {
  TAG_RENAME: '标签重命名',
  TAG_REPLACE: '标签替换',
  LEVEL_UP: '等级提升',
  LEVEL_DOWN: '等级降低',
  MERGE_TO_EXISTING_TAG: '合并到已有标签',
  REMOVE_TAG: '删除标签',
  EVIDENCE_CORRECTION: '证据修正',
}

const modifyTypeColors: Record<string, string> = {
  TAG_RENAME: 'primary',
  TAG_REPLACE: 'warning',
  LEVEL_UP: 'success',
  LEVEL_DOWN: 'danger',
  MERGE_TO_EXISTING_TAG: 'primary',
  REMOVE_TAG: 'danger',
  EVIDENCE_CORRECTION: 'info',
}

watch(() => props.visible, async (val) => {
  if (val) {
    await loadHistory()
  }
})

async function loadHistory() {
  loading.value = true
  try {
    const res = await getGovernanceHistory(props.empId)
    events.value = res.data || []
  } catch (error) {
    console.error('加载治理历史失败', error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-drawer
    v-model="drawerVisible"
    title="能力治理历史"
    size="600px"
  >
    <div v-loading="loading" class="governance-history">
      <el-timeline v-if="events.length > 0">
        <el-timeline-item
          v-for="event in events"
          :key="event.id"
          :timestamp="event.createdTime"
          placement="top"
        >
          <el-card class="event-card">
            <div class="event-card__header">
              <el-tag :type="modifyTypeColors[event.modifyType] || 'info'" size="small">
                {{ modifyTypeLabels[event.modifyType] || event.modifyType }}
              </el-tag>
              <span class="event-card__time">{{ event.createdTime }}</span>
            </div>

            <div class="event-card__content">
              <!-- 标签变更 -->
              <div v-if="event.oldTagName || event.newTagName" class="event-detail">
                <span class="event-detail__label">标签：</span>
                <span v-if="event.oldTagName" class="event-detail__old">{{ event.oldTagName }}</span>
                <span v-if="event.oldTagName && event.newTagName" class="event-detail__arrow">→</span>
                <span v-if="event.newTagName" class="event-detail__new">{{ event.newTagName }}</span>
              </div>

              <!-- 等级变更 -->
              <div v-if="event.oldLevel || event.newLevel" class="event-detail">
                <span class="event-detail__label">等级：</span>
                <span v-if="event.oldLevel" class="event-detail__old">{{ event.oldLevel }}</span>
                <span v-if="event.oldLevel && event.newLevel" class="event-detail__arrow">→</span>
                <span v-if="event.newLevel" class="event-detail__new">{{ event.newLevel }}</span>
              </div>

              <!-- 修改原因 -->
              <div v-if="event.modifyReason" class="event-detail">
                <span class="event-detail__label">原因：</span>
                <span class="event-detail__value">{{ event.modifyReason }}</span>
              </div>

              <!-- Agent记忆 -->
              <div v-if="event.memoryId" class="event-memory">
                <el-tag type="success" size="small" effect="plain">已生成Agent记忆</el-tag>
              </div>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>

      <el-empty v-if="!loading && events.length === 0" description="暂无治理记录" />
    </div>
  </el-drawer>
</template>

<style scoped>
.governance-history {
  @apply p-4;
}

.event-card {
  @apply mb-0;
}

.event-card__header {
  @apply flex items-center justify-between mb-3;
}

.event-card__time {
  @apply text-xs text-gray-400;
}

.event-detail {
  @apply text-sm mb-2;
}

.event-detail__label {
  @apply text-gray-500;
}

.event-detail__old {
  @apply text-red-500 line-through;
}

.event-detail__arrow {
  @apply mx-2 text-gray-400;
}

.event-detail__new {
  @apply text-green-600 font-medium;
}

.event-detail__value {
  @apply text-gray-700;
}

.event-memory {
  @apply mt-2 pt-2 border-t;
}
</style>
