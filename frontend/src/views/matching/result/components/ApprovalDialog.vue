<script setup lang="ts">
import type { MatchingRecord, UserVO } from '@/api'

defineProps<{
  visible: boolean
  loading: boolean
  currentRecord: MatchingRecord | null
  form: { adminApproverId: number | undefined }
  userList: UserVO[]
  userLoading: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  submit: []
}>()
</script>

<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="emit('update:visible', $event)"
    title="发起审批流程"
    width="540px"
    :close-on-click-modal="false"
  >
    <div v-loading="userLoading">
      <div v-if="currentRecord" class="approval-tip">
        匹配记录 #{{ currentRecord.id }} - {{ currentRecord.empName || '员工#' + currentRecord.empId }} → {{ currentRecord.postName || '岗位#' + currentRecord.postId }}
      </div>
      <el-form :model="form" label-width="110px">
        <el-form-item label="管理员审核人" required>
          <el-select v-model="form.adminApproverId" placeholder="请选择管理员审核人" filterable style="width: 100%;">
            <el-option v-for="user in userList" :key="user.id" :label="`${user.realName} (${user.username})`" :value="user.id" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="emit('submit')">确认发起</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.approval-tip {
  margin-bottom: 18px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(37, 99, 235, 0.08);
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 700;
}
</style>
