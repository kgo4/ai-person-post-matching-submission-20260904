<script setup lang="ts">
defineProps<{
  enableAi: boolean
  forceAi: boolean
  aiTopN: number
  aiThreshold: number
  loading: boolean
}>()
const emit = defineEmits<{
  (e: 'update:enableAi', v: boolean): void
  (e: 'update:forceAi', v: boolean): void
  (e: 'update:aiTopN', v: number): void
  (e: 'update:aiThreshold', v: number): void
}>()
</script>

<template>
  <div class="ai-bar">
    <div class="ai-bar__switch">
      <el-switch :model-value="enableAi" :disabled="loading" size="small" @update:model-value="(v: boolean) => emit('update:enableAi', v)" />
      <span>AI 深度分析</span>
    </div>
    <div v-if="enableAi" class="ai-config-row">
      <span>L2 分 &ge;</span>
      <el-input-number :model-value="aiThreshold" :min="0" :max="100" :step="5" size="small" controls-position="right" style="width:85px" @update:model-value="(v?: number) => emit('update:aiThreshold', v ?? 0)" />
      <span>的前</span>
      <el-input-number :model-value="aiTopN" :min="1" :max="20" size="small" controls-position="right" style="width:70px" @update:model-value="(v?: number) => emit('update:aiTopN', v ?? 1)" />
      <span>名进入分析</span>
    </div>
    <label class="ai-bar__force">
      <span>强制 AI</span>
      <el-switch :model-value="forceAi" :disabled="loading || !enableAi" size="small" @update:model-value="(v: boolean) => emit('update:forceAi', v)" />
    </label>
  </div>
</template>

<style scoped>
.ai-bar { display: flex; align-items: center; gap: 16px; padding: 10px 20px 12px; border-bottom: 1px solid var(--app-divider, rgba(148,163,184,0.13)); }
.ai-bar__switch { display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; color: var(--app-text-secondary, #475569); }
.ai-config-row { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #6b7280; flex-wrap: wrap; }
.ai-bar__force { margin-left: auto; display: flex; align-items: center; gap: 6px; font-size: 12px; color: #6b7280; }
</style>
