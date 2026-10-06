<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMatchingAiContext } from '@/api'
import type { AiContextPackage } from '@/api'

const props = defineProps<{
  visible: boolean
  recordId?: number | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'show-source-ref': [ref: string]
}>()

const loading = ref(false)
const aiContext = ref<AiContextPackage | null>(null)
const activeTab = ref('gaps')
const sourceRefDetailVisible = ref(false)
const currentSourceRef = ref('')

async function handleOpen() {
  if (!props.recordId) {
    ElMessage.warning('匹配记录未加载')
    return
  }
  loading.value = true
  try {
    const res = await getMatchingAiContext(props.recordId)
    aiContext.value = res.data
  } catch (error: unknown) {
    ElMessage.error(error instanceof Error ? error.message : '加载AI上下文失败')
  } finally {
    loading.value = false
  }
}

function showSourceRef(ref: string) {
  currentSourceRef.value = ref
  sourceRefDetailVisible.value = true
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="AI 读取上下文"
    size="560px"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
    @open="handleOpen"
  >
    <div v-loading="loading">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="能力差距" name="gaps">
          <el-empty v-if="!aiContext?.gaps?.length" description="无差距数据" />
          <div v-for="gap in aiContext?.gaps || []" :key="gap.abilityTagId" class="context-item">
            <div class="context-item__title">{{ gap.abilityName }}</div>
            <div class="context-item__meta">
              要求 {{ gap.requiredLevel }} 级 / 当前 {{ gap.currentLevel }} 级
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="来源引用" name="sources">
          <el-empty v-if="!aiContext?.sourceRefs?.length" description="无来源引用" />
          <div v-for="(ref, index) in aiContext?.sourceRefs || []" :key="index" class="context-item">
            <el-link type="primary" @click="showSourceRef(ref.ref || '')">
              {{ ref.title || ref.ref }}
            </el-link>
            <div class="context-item__meta">{{ ref.refType }} / {{ ref.refId }}</div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<style scoped>
.context-item {
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color-lighter, #ebeef5);
}
.context-item__title {
  font-weight: 600;
}
.context-item__meta {
  font-size: 12px;
  color: #94a3b8;
}
</style>
