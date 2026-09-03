<script setup lang="ts">
import { Briefcase, Connection, User } from '@element-plus/icons-vue'
import type { MatchMode } from '../logic'

export interface ModeStats {
  /** 单人单岗：员工与岗位是否已就绪 */
  single: { employee: boolean; post: boolean }
  /** 人找岗：当前选中员工名（未选为 undefined） */
  personToPosts: { empName?: string }
  /** 岗找人：当前选中岗位名 + 已选候选数 */
  postToPeople: { postName?: string; count: number }
}

const props = defineProps<{
  modelValue: MatchMode
  loading: boolean
  stats: ModeStats
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: MatchMode): void }>()

const modeDefs = [
  {
    mode: 'SINGLE' as MatchMode, title: '单人单岗', desc: '1人+1岗直接评估',
    icon: Connection,
    badge: () => (props.stats.single.employee && props.stats.single.post ? '就绪' : '待选'),
  },
  {
    mode: 'PERSON_TO_POSTS' as MatchMode, title: '人找岗', desc: '选人→推荐岗位→勾选匹配',
    icon: User,
    badge: () => (props.stats.personToPosts.empName ? props.stats.personToPosts.empName! : '未选员工'),
  },
  {
    mode: 'POST_TO_PEOPLE' as MatchMode, title: '岗找人', desc: '选岗→推荐人员→勾选匹配',
    icon: Briefcase,
    badge: () => (props.stats.postToPeople.postName
      ? `${props.stats.postToPeople.postName} · 已选 ${props.stats.postToPeople.count}`
      : '未选岗位'),
  },
]
</script>

<template>
  <section class="glass-card motion-rise">
    <div class="mode-row">
      <button
        v-for="m in modeDefs"
        :key="m.mode"
        class="mode-chip"
        :class="{ active: modelValue === m.mode }"
        :disabled="loading"
        @click="emit('update:modelValue', m.mode)"
      >
        <el-icon :size="18"><component :is="m.icon" /></el-icon>
        <span class="mode-chip__title">{{ m.title }}</span>
        <span class="mode-chip__desc">{{ m.desc }}</span>
        <span class="mode-chip__badge" :class="{ 'is-ready': m.badge() !== '待选' && m.badge() !== '未选员工' && m.badge() !== '未选岗位' }">{{ m.badge() }}</span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.mode-row { display: flex; gap: 12px; padding: 16px; }
.mode-chip {
  flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px;
  padding: 16px 12px 14px; border: none; border-radius: 14px;
  background: rgba(255,255,255,0.55); cursor: pointer; transition: all 0.25s ease;
  font-family: inherit; box-shadow: 0 1px 3px rgba(0,0,0,0.04), 0 0 0 2px var(--app-divider, #e5e7eb);
  position: relative; overflow: hidden;
}
.mode-chip::after {
  content: ''; position: absolute; bottom: 0; left: 20%; right: 20%; height: 3px;
  border-radius: 3px 3px 0 0; background: transparent; transition: all 0.25s ease;
}
.mode-chip:hover { background: rgba(255,255,255,0.95); box-shadow: 0 2px 8px rgba(59,130,246,0.1), 0 0 0 2px rgba(59,130,246,0.3); transform: translateY(-1px); }
.mode-chip:disabled { opacity: 0.6; cursor: not-allowed; }
.mode-chip.active { background: rgba(59,130,246,0.05); box-shadow: 0 2px 12px rgba(59,130,246,0.12), 0 0 0 2px #3b82f6; transform: translateY(-1px); }
.mode-chip.active::after { background: linear-gradient(90deg, #3b82f6, #8b5cf6); }
.mode-chip__title { font-size: 14px; font-weight: 700; color: var(--app-text-strong, #111827); }
.mode-chip__desc { font-size: 11px; color: #6b7280; }
.mode-chip__badge {
  max-width: 90%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  font-size: 11px; font-weight: 600; padding: 2px 10px; border-radius: 999px;
  background: var(--app-divider, #e5e7eb); color: var(--app-text-muted, #94a3b8);
}
.mode-chip__badge.is-ready { background: var(--app-primary-soft, rgba(37,99,235,0.1)); color: var(--app-primary, #2563eb); }
@media (max-width: 720px) { .mode-row { flex-direction: column; } }
</style>
