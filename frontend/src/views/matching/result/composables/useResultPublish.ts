import { ElMessage, ElMessageBox } from 'element-plus'
import { publishRecord, unpublishRecord } from '@/api'
import type { MatchingRecord } from '@/api'

/**
 * 匹配结果的推送 / 撤回。
 *
 * 推送与审批是两个独立动作：审批通过只代表 HR 认可匹配结论，是否让员工本人看到
 * 由推送状态决定。推送后员工侧「我的匹配结果」立即可见，撤回则立即不可见。
 *
 * 【2026-09-04 需求变更：匹配结果不再需要审核】
 * 原实现在这里硬性要求 approvalStatus===2（审批通过）才允许推送，等价于强制 HR 先走
 * 「发起审批 → 审批通过」两步，是「点推送提示请先完成审核」的直接来源。
 * 现口径：HR 人工查看/修改匹配结果本身即为审核动作，确认后可直接推送；
 * 审批流（matching_approval_flow 与审批任务页）保留可用，但不再是推送的前置条件。
 * 后端 MatchingRecordServiceImpl.updatePublishStatus 已同步移除该校验，两端口径一致。
 */
export function useResultPublish(onSuccess: () => void) {
  async function handlePublish(row: MatchingRecord) {
    if (row.publishStatus === 1) {
      ElMessage.info('该结果已推送给员工')
      return
    }
    try {
      await publishRecord(row.id)
      ElMessage.success('已推送给员工，员工现在可以查看该匹配结果')
      onSuccess()
    } catch (error: any) {
      ElMessage.error(error.message || '推送失败')
    }
  }

  async function handleUnpublish(row: MatchingRecord) {
    if (row.publishStatus !== 1) {
      ElMessage.info('该结果尚未推送')
      return
    }
    try {
      await ElMessageBox.confirm(
        '撤回后员工将无法再查看该匹配结果及其差距诊断，确定撤回？',
        '确认撤回',
        { type: 'warning' },
      )
      await unpublishRecord(row.id)
      ElMessage.success('已撤回推送')
      onSuccess()
    } catch (error: any) {
      if (error !== 'cancel') ElMessage.error(error.message || '撤回失败')
    }
  }

  return { handlePublish, handleUnpublish }
}
