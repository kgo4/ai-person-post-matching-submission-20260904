<script setup lang="ts">
import { Delete, Plus } from '@element-plus/icons-vue'
import type { HardCondition } from '@/api'

const props = defineProps<{
  conditions: HardCondition[]
  postId?: number
  loading: boolean
  /** 管道图 L1 阶段联动高亮 */
  active: boolean
}>()
const emit = defineEmits<{
  (e: 'update:conditions', v: HardCondition[]): void
  (e: 'save'): void
}>()

const fieldOptions = [
  { value: 'education', label: '学历' },
  { value: 'gender', label: '性别' },
]
const operatorOptions = [
  { value: 'eq', label: '等于' },
  { value: 'neq', label: '不等于' },
  { value: 'gte', label: '大于等于' },
  { value: 'lte', label: '小于等于' },
  { value: 'in', label: '属于' },
]

function addCondition() {
  emit('update:conditions', [...props.conditions, { field: 'education', operator: 'eq', value: '', label: '' }])
}
function removeCondition(i: number) {
  emit('update:conditions', props.conditions.filter((_, idx) => idx !== i))
}
</script>

<template>
  <section class="glass-card motion-rise hard-card" :class="{ 'is-active': active }">
    <div class="toolbar-panel">
      <div>
        <div class="section-title hard-title">
          L1 硬条件
          <span class="hard-badge" :class="{ 'is-loaded': conditions.length > 0 }">
            {{ postId ? (conditions.length > 0 ? `已加载 ${conditions.length} 条` : '未配置') : '未选择岗位' }}
          </span>
        </div>
        <div class="section-desc">字段级过滤规则，生成候选时先于能力评分执行</div>
      </div>
    </div>
    <div class="panel-body">
      <template v-if="postId">
        <div v-if="conditions.length === 0" class="hard-empty">该岗位暂无硬条件规则，可手动添加并保存</div>
        <div v-for="(c, i) in conditions" :key="i" class="hard-row">
          <span class="hard-row__index">{{ i + 1 }}</span>
          <el-select v-model="c.field" size="small" style="width:100px">
            <el-option v-for="f in fieldOptions" :key="f.value" :label="f.label" :value="f.value" />
          </el-select>
          <el-select v-model="c.operator" size="small" style="width:100px">
            <el-option v-for="o in operatorOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-input v-model="c.value" size="small" placeholder="值" style="width:120px" />
          <el-button :icon="Delete" size="small" text type="danger" @click="removeCondition(i)" />
        </div>
        <div class="hard-actions">
          <el-button :icon="Plus" size="small" @click="addCondition">添加条件</el-button>
          <el-button size="small" type="primary" :disabled="!conditions.length" @click="emit('save')">保存到岗位</el-button>
        </div>
      </template>
      <div v-else class="hard-empty">选择岗位后自动加载岗位硬条件规则</div>
    </div>
  </section>
</template>

<style scoped>
.hard-card { transition: box-shadow 0.3s ease, border-color 0.3s ease; }
.hard-card.is-active { border-color: rgba(239,68,68,0.4); box-shadow: 0 0 0 1px rgba(239,68,68,0.15), var(--app-shadow-md, 0 4px 20px rgba(15,23,42,0.06)); }
.hard-title { display: flex; align-items: center; gap: 8px; }
.hard-badge {
  font-size: 11px; font-weight: 600; padding: 2px 10px; border-radius: 999px;
  background: var(--app-divider, #e5e7eb); color: var(--app-text-muted, #94a3b8);
}
.hard-badge.is-loaded { background: rgba(239,68,68,0.1); color: #dc2626; }
.hard-empty { font-size: 12px; color: #9ca3af; padding: 8px 0; }
.hard-row { display: flex; align-items: center; gap: 6px; padding: 4px 0; }
.hard-row__index {
  width: 20px; height: 20px; border-radius: 6px; background: var(--app-divider, #e5e7eb);
  display: inline-flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; color: var(--app-text-muted, #94a3b8); flex-shrink: 0;
}
.hard-actions { display: flex; gap: 8px; margin-top: 12px; }
</style>
