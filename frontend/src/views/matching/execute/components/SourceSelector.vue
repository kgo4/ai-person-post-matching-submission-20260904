<script setup lang="ts">
import { computed } from 'vue'
import { OfficeBuilding, UserFilled } from '@element-plus/icons-vue'
import type { PostPost } from '@/api'
import type { MatchMode } from '../logic'

const props = defineProps<{
  mode: MatchMode
  employeeOptions: { id: number; realName: string; empCode: string }[]
  postOptions: PostPost[]
  employeeId?: number
  postId?: number
  loading: boolean
}>()
const emit = defineEmits<{
  (e: 'update:employeeId', v?: number): void
  (e: 'update:postId', v?: number): void
  (e: 'search-employees', keyword: string): void
}>()

const arrow = computed(() => (props.mode === 'SINGLE' ? '⇄' : props.mode === 'PERSON_TO_POSTS' ? '› 岗位' : '员工 ›'))
const showEmployee = computed(() => props.mode !== 'POST_TO_PEOPLE')
const showPost = computed(() => props.mode !== 'PERSON_TO_POSTS')

function empName(id?: number) {
  if (!id) return ''
  const e = props.employeeOptions.find(x => x.id === id)
  return e ? `${e.realName} (${e.empCode})` : `员工#${id}`
}
function postName(id?: number) {
  if (!id) return ''
  const p = props.postOptions.find(x => x.id === id)
  return p?.postName || `岗位#${id}`
}
</script>

<template>
  <div class="selector-pair">
    <div v-if="showEmployee" class="selector-unit">
      <label class="selector-unit__label">员工</label>
      <el-select
        :model-value="employeeId" placeholder="选择员工" filterable remote reserve-keyword clearable
        :remote-method="(k: string) => emit('search-employees', k)" :disabled="loading" style="width:100%"
        @update:model-value="(v?: number) => emit('update:employeeId', v)"
      >
        <el-option v-for="e in employeeOptions" :key="e.id" :label="`${e.realName} (${e.empCode})`" :value="e.id" />
      </el-select>
      <div v-if="employeeId" class="selector-unit__pick">
        <el-icon :size="13"><UserFilled /></el-icon>{{ empName(employeeId) }}
      </div>
      <div v-else class="selector-unit__empty">未选择</div>
    </div>

    <div class="selector-arrow">{{ arrow }}</div>

    <div v-if="showPost" class="selector-unit">
      <label class="selector-unit__label">岗位</label>
      <el-select
        :model-value="postId" placeholder="选择岗位" filterable clearable :disabled="loading" style="width:100%"
        @update:model-value="(v?: number) => emit('update:postId', v)"
      >
        <el-option v-for="p in postOptions" :key="p.id" :label="p.postName" :value="p.id" />
      </el-select>
      <div v-if="postId" class="selector-unit__pick">
        <el-icon :size="13"><OfficeBuilding /></el-icon>{{ postName(postId) }}
      </div>
      <div v-else class="selector-unit__empty">未选择</div>
    </div>
  </div>
</template>

<style scoped>
.selector-pair { display: flex; gap: 12px; align-items: flex-start; }
.selector-unit { flex: 1; min-width: 0; }
.selector-unit__label { display: block; font-size: 13px; font-weight: 600; color: var(--app-text-strong, #111827); margin-bottom: 8px; }
.selector-unit__pick {
  margin-top: 8px; padding: 8px 12px; background: var(--app-primary-soft, rgba(59,130,246,0.06));
  border-radius: 8px; font-size: 13px; font-weight: 600; color: var(--app-primary, #3b82f6);
  display: flex; align-items: center; gap: 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.selector-unit__empty { margin-top: 8px; padding: 8px 12px; border-radius: 8px; font-size: 13px; color: #9ca3af; text-align: center; border: 1px dashed var(--app-divider, #e5e7eb); }
.selector-arrow { display: flex; align-items: center; padding-top: 28px; font-size: 14px; font-weight: 700; color: var(--app-primary, #3b82f6); white-space: nowrap; }
</style>
