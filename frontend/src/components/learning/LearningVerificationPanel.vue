<script setup lang="ts">
/**
 * 步骤验证面板：生成测评题 + 提交项目材料。
 *
 * 提交对话框抽在 `LearningProjectSubmitDialog`（员工侧多处共用），
 * 本组件只负责按钮与说明，并把 dialog 的打开能力透给页面。
 */
import { ref } from 'vue'
import type { LearningProjectTask, LearningProjectSubmitDTO } from '@/api'
import LearningProjectSubmitDialog from './LearningProjectSubmitDialog.vue'

const emit = defineEmits<{
  submitTask: [taskId: number, data: LearningProjectSubmitDTO]
  generateAssessment: []
}>()

const dialogRef = ref<InstanceType<typeof LearningProjectSubmitDialog> | null>(null)

function openSubmitDialog(task: LearningProjectTask) {
  dialogRef.value?.open(task)
}

defineExpose({ openSubmitDialog })
</script>

<template>
  <div class="verify-panel">
    <div class="verify-panel__actions">
      <el-button type="primary" size="small" @click="emit('generateAssessment')">
        生成测评题
      </el-button>
    </div>

    <div class="verify-panel__hint">
      学习完成后通过测评或提交项目材料来验证。<strong>提交材料即视为该步骤已学完</strong>，
      系统会立即生成能力证据，不需要等待人工审核；材料会随你的「能力提升申请」一并交给 HR 复核，
      <strong>能力等级以 HR 的复核结论为准</strong>。
    </div>

    <LearningProjectSubmitDialog
      ref="dialogRef"
      @submit="(taskId: number, data: LearningProjectSubmitDTO) => emit('submitTask', taskId, data)"
    />
  </div>
</template>

<style scoped>
.verify-panel__actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.verify-panel__hint {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fef3c7;
  border: 1px solid #fde68a;
  font-size: 12px;
  color: #92400e;
  line-height: 1.5;
}
</style>
