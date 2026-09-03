<script setup lang="ts">
import { reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { MatchingApprovalFlow } from '@/api/matching/types'

export interface ApprovalPayload {
  matchingRecordId: number
  approvalStatus: 2 | 3
  approvalRemark: string
}

const props = defineProps<{
  visible: boolean
  flow: MatchingApprovalFlow | null
  recordId?: number | null
  submitting: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submit: [payload: ApprovalPayload]
}>()

const form = reactive({ approved: true, remark: '' })

watch(
  () => props.visible,
  (open) => {
    if (open) {
      form.approved = true
      form.remark = ''
    }
  },
)

function handleSubmit() {
  if (!form.approved && !form.remark.trim()) {
    ElMessage.warning('驳回时需要填写审核意见')
    return
  }
  emit('submit', {
    matchingRecordId: Number(props.recordId),
    approvalStatus: form.approved ? 2 : 3,
    approvalRemark: form.remark,
  })
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="审批复核"
    width="480px"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <el-form label-width="96px">
      <el-form-item label="审批结论">
        <el-radio-group v-model="form.approved">
          <el-radio :value="true">通过</el-radio>
          <el-radio :value="false">驳回</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="审核意见">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          placeholder="驳回时必须填写审核意见"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
    </template>
  </el-dialog>
</template>
