<script setup lang="ts">
/**
 * 项目材料提交对话框（员工侧共用）。
 *
 * <p>抽成独立组件而不是各页复制：提交表单一旦分叉，两处对「哪些字段必填、
 * 提交后会发生什么」的说明就会不一致，而员工看不出自己看的是哪一份。</p>
 *
 * <p>提交后立即收起对话框，不等接口返回：把关闭时机与父组件的异步流程解耦，
 * 结果由父组件用消息提示反馈（失败时父组件保持页面状态、员工可重开重填）。</p>
 */
import { computed, ref } from 'vue'
import type { LearningProjectTask, LearningProjectSubmitDTO } from '@/api'

const emit = defineEmits<{
  /** 员工确认提交；父组件负责调接口、提示结果与刷新 */
  submit: [taskId: number, data: LearningProjectSubmitDTO]
}>()

const visible = ref(false)
const currentTask = ref<LearningProjectTask | null>(null)
const form = ref<LearningProjectSubmitDTO>({
  repoUrl: '',
  demoUrl: '',
  reportUrl: '',
  submissionText: '',
})

const canSubmit = computed(() => Boolean(
  form.value.repoUrl || form.value.demoUrl || form.value.reportUrl || form.value.submissionText,
))

/** 打开对话框并重置表单（不复用上次内容，避免误交上一次的材料） */
function open(task: LearningProjectTask) {
  currentTask.value = task
  form.value = { repoUrl: '', demoUrl: '', reportUrl: '', submissionText: '' }
  visible.value = true
}

function handleSubmit() {
  if (!currentTask.value || !canSubmit.value) {
    return
  }
  emit('submit', currentTask.value.id, form.value)
  visible.value = false
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="提交项目材料" width="540px" :close-on-click-modal="false">
    <div class="submit-dialog__hint">
      提交后本步骤立即标记为「已学完」。这些材料会随你的能力提升申请一起给 HR 看，
      建议尽量填全 —— 仓库地址与说明同时具备时，证据可信度更高。
    </div>
    <el-form :model="form" label-width="80px">
      <el-form-item label="仓库地址">
        <el-input v-model="form.repoUrl" placeholder="GitHub/GitLab 仓库 URL（需以 http:// 或 https:// 开头）" />
      </el-form-item>
      <el-form-item label="演示地址">
        <el-input v-model="form.demoUrl" placeholder="在线演示或截图链接" />
      </el-form-item>
      <el-form-item label="报告地址">
        <el-input v-model="form.reportUrl" placeholder="实现报告或文档链接" />
      </el-form-item>
      <el-form-item label="说明">
        <el-input v-model="form.submissionText" type="textarea" :rows="3" placeholder="描述实现思路和关键改动" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!canSubmit" @click="handleSubmit">提交</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.submit-dialog__hint {
  margin-bottom: 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--app-bg-secondary, #f1f5f9);
  font-size: 12px;
  color: var(--app-text-muted, #64748b);
  line-height: 1.6;
}
</style>
