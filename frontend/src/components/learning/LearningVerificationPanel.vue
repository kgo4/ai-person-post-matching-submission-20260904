<script setup lang="ts">
import { ref } from 'vue'
import type { LearningProjectTask, LearningProjectSubmitDTO } from '@/api'

const props = defineProps<{
  pendingCount?: number
  canConfirm?: boolean
}>()

const emit = defineEmits<{
  submitTask: [taskId: number, data: LearningProjectSubmitDTO]
  generateAssessment: []
  confirmImprovement: []
}>()

const submitDialogVisible = ref(false)
const submitSubmitting = ref(false)
const currentTask = ref<LearningProjectTask | null>(null)
const submitForm = ref<LearningProjectSubmitDTO>({
  repoUrl: '',
  demoUrl: '',
  reportUrl: '',
  submissionText: ''
})

function openSubmitDialog(task: LearningProjectTask) {
  currentTask.value = task
  submitForm.value = { repoUrl: '', demoUrl: '', reportUrl: '', submissionText: '' }
  submitDialogVisible.value = true
}

async function handleSubmit() {
  if (!currentTask.value) return
  if (!submitForm.value.repoUrl && !submitForm.value.demoUrl && !submitForm.value.reportUrl && !submitForm.value.submissionText) {
    return
  }
  submitSubmitting.value = true
  try {
    emit('submitTask', currentTask.value.id, submitForm.value)
    submitDialogVisible.value = false
  } finally {
    submitSubmitting.value = false
  }
}

defineExpose({ openSubmitDialog })
</script>

<template>
  <div class="verify-panel">
    <div class="verify-panel__actions">
      <el-button type="primary" size="small" @click="emit('generateAssessment')">
        生成测评题
      </el-button>
      <el-button size="small" :disabled="!pendingCount">
        提交项目成果
      </el-button>
      <el-button type="success" size="small" plain :disabled="!props.canConfirm" @click="emit('confirmImprovement')">
        确认能力提升
      </el-button>
    </div>

    <div class="verify-panel__hint">
      学习完成后需要通过测评或提交项目成果来验证能力提升，验证通过后将回写人员能力。
    </div>

    <!-- Submit dialog -->
    <el-dialog v-model="submitDialogVisible" title="提交项目成果" width="540px" :close-on-click-modal="false">
      <el-form :model="submitForm" label-width="80px">
        <el-form-item label="仓库地址">
          <el-input v-model="submitForm.repoUrl" placeholder="GitHub/GitLab 仓库 URL" />
        </el-form-item>
        <el-form-item label="演示地址">
          <el-input v-model="submitForm.demoUrl" placeholder="在线演示或截图链接" />
        </el-form-item>
        <el-form-item label="报告地址">
          <el-input v-model="submitForm.reportUrl" placeholder="实现报告或文档链接" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="submitForm.submissionText" type="textarea" :rows="3" placeholder="描述实现思路和关键改动" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitSubmitting" @click="handleSubmit">提交</el-button>
      </template>
    </el-dialog>
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
