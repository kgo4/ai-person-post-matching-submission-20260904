import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { modifyResult } from '@/api'
import type { MatchingRecord } from '@/api'

export function useResultModify(onSuccess: () => void) {
  const modifyDialogVisible = ref(false)
  const modifyLoading = ref(false)
  const currentModifyRecord = ref<MatchingRecord | null>(null)
  const modifyForm = reactive({
    finalMatchScore: 0,
    matchStatus: 0,
    manualRemark: '',
  })

  function openModifyDialog(row: MatchingRecord) {
    currentModifyRecord.value = row
    modifyForm.finalMatchScore = row.finalMatchScore ?? row.aiMatchScore ?? 0
    modifyForm.matchStatus = row.matchStatus
    modifyForm.manualRemark = row.manualRemark || ''
    modifyDialogVisible.value = true
  }

  async function handleModify() {
    if (!currentModifyRecord.value) return
    modifyLoading.value = true
    try {
      await modifyResult(currentModifyRecord.value.id, {
        matchScore: modifyForm.finalMatchScore,
        matchStatus: modifyForm.matchStatus,
        remark: modifyForm.manualRemark,
      })
      ElMessage.success('修改成功')
      modifyDialogVisible.value = false
      onSuccess()
    } catch (error: any) {
      ElMessage.error(error.message || '修改失败')
    } finally {
      modifyLoading.value = false
    }
  }

  return {
    modifyDialogVisible,
    modifyLoading,
    currentModifyRecord,
    modifyForm,
    openModifyDialog,
    handleModify,
  }
}
