import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { initiateApproval, pageUsers } from '@/api'
import type { MatchingRecord, UserVO } from '@/api'

export function useResultApproval(onSuccess: () => void) {
  const approvalDialogVisible = ref(false)
  const approvalLoading = ref(false)
  const currentApprovalRecord = ref<MatchingRecord | null>(null)
  const approvalForm = reactive({
    adminApproverId: undefined as number | undefined,
  })
  const userList = ref<UserVO[]>([])
  const userLoading = ref(false)

  async function loadUsers() {
    if (userList.value.length > 0) return
    userLoading.value = true
    try {
      const res = await pageUsers({ current: 1, size: 1000 })
      userList.value = res.data?.records || []
    } finally {
      userLoading.value = false
    }
  }

  function openApprovalDialog(row: MatchingRecord) {
    if (row.approvalStatus === 1) {
      ElMessage.warning('该记录已经在审批中')
      return
    }
    if (row.isLocked) {
      ElMessage.warning('该记录已锁定，无法发起审批')
      return
    }
    currentApprovalRecord.value = row
    approvalForm.adminApproverId = undefined
    approvalDialogVisible.value = true
    loadUsers()
  }

  async function handleInitiateApproval() {
    if (!currentApprovalRecord.value) return
    if (!approvalForm.adminApproverId) {
      ElMessage.warning('请选择管理员审核人')
      return
    }
    approvalLoading.value = true
    try {
      await initiateApproval(currentApprovalRecord.value.id, approvalForm.adminApproverId)
      ElMessage.success('审批流程已发起')
      approvalDialogVisible.value = false
      onSuccess()
    } catch (error: any) {
      ElMessage.error(error.message || '发起审批失败')
    } finally {
      approvalLoading.value = false
    }
  }

  return {
    approvalDialogVisible,
    approvalLoading,
    currentApprovalRecord,
    approvalForm,
    userList,
    userLoading,
    loadUsers,
    openApprovalDialog,
    handleInitiateApproval,
  }
}
