import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteRecord, lockResult, unlockResult } from '@/api'
import type { MatchingRecord } from '@/api'

export function useResultLock(onSuccess: () => void) {
  async function handleLock(row: MatchingRecord) {
    try {
      await lockResult(row.id)
      ElMessage.success('锁定成功')
      onSuccess()
    } catch (error: any) {
      ElMessage.error(error.message || '锁定失败')
    }
  }

  async function handleUnlock(row: MatchingRecord) {
    try {
      await unlockResult(row.id)
      ElMessage.success('解锁成功')
      onSuccess()
    } catch (error: any) {
      ElMessage.error(error.message || '解锁失败')
    }
  }

  async function handleDelete(row: MatchingRecord) {
    try {
      await ElMessageBox.confirm('确定删除该匹配记录？关联的审批流程和反馈数据也会被删除。', '确认删除', { type: 'warning' })
      await deleteRecord(row.id)
      ElMessage.success('已删除')
      onSuccess()
    } catch (error: any) {
      if (error !== 'cancel') ElMessage.error(error.message || '删除失败')
    }
  }

  return { handleLock, handleUnlock, handleDelete }
}
