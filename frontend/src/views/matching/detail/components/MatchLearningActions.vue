<script setup lang="ts">
import { ElMessage } from 'element-plus'
import type { NormalizedClosureGap } from '../closure-diagnosis'
import type { LearningPathItem } from '@/api/learning'

const props = defineProps<{
  empId?: number | null
  tagId?: number | null
  submitting: boolean
}>()

const emit = defineEmits<{
  'confirm-outcome': [gap: NormalizedClosureGap, resource?: LearningPathItem]
  'generate-path': []
}>()

function handleConfirm(gap: NormalizedClosureGap, resource?: LearningPathItem) {
  if (!props.empId) {
    ElMessage.warning('缺少员工信息，无法确认学习成果')
    return
  }
  emit('confirm-outcome', gap, resource)
}

defineExpose({ handleConfirm })
</script>

<template>
  <div class="learning-actions">
    <el-button type="primary" :loading="submitting" @click="emit('generate-path')">
      生成学习路径
    </el-button>
  </div>
</template>
