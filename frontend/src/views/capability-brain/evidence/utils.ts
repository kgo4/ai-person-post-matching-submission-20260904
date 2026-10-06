import type { EvidenceChainAbility } from '@/api/contest'
import type { EmpEmployee, PostPost } from '@/api/types'

/** 已验证（审核通过）的证据数 */
export function getVerifiedEvidenceCount(ability: EvidenceChainAbility): number {
  if (!ability.evidences?.length) return 0
  return ability.evidences.filter(e => e.evidenceStatus === 'VERIFIED').length
}

/**
 * 证据强度判定：
 * - 无证据 → none
 * - 至少 2 条已验证证据且平均可信度 ≥ 70 → strong（证据充分且经过治理审核）
 * - 其余有证据（未审核 / 审核被拒 / 数量不足 / 可信度低）→ weak
 */
export function getEvidenceStrength(ability: EvidenceChainAbility): 'strong' | 'weak' | 'none' {
  if (ability.evidenceCount === 0) return 'none'
  const verifiedCount = getVerifiedEvidenceCount(ability)
  if (verifiedCount >= 2 && ability.averageCredibility >= 70) return 'strong'
  return 'weak'
}

export function getStrengthLabel(strength: string): string {
  const map: Record<string, string> = { strong: '强证据', weak: '弱证据', none: '无证据' }
  return map[strength] || strength
}

export function getStrengthType(strength: string): string {
  const map: Record<string, string> = { strong: 'success', weak: 'warning', none: 'danger' }
  return map[strength] || 'info'
}

export function getTargetTypeText(type?: string) {
  const map: Record<string, string> = {
    EMP_ABILITY: '员工能力', POST_ABILITY_MODEL: '岗位模型',
    MATCHING_RECORD: '匹配记录', TAG: '标签',
  }
  return type ? map[type] || type : '-'
}

/** 置信度说明：评估结果本身的把握程度 */
export const CONFIDENCE_HINT = '置信度：评估结果本身的把握程度，来自评估来源（AI 测试、面试、简历等）的权威性与权重。'
/** 可信度说明：支撑结论的证据本身的可信程度 */
export const CREDIBILITY_HINT = '可信度：支撑结论的证据本身的可信程度。证据来源越可靠、内容越完整、越经过治理审核，可信度越高。'

export function getSourceTypeText(type?: string) {
  const map: Record<string, string> = {
    JD_IMPORT: 'JD导入', RESUME_PARSE: '简历解析', AI_TEST: 'AI能力测试',
    VIDEO_INTERVIEW: 'AI视频面试', PMS_ANALYSIS: '项目数据分析',
    MATCHING_FEEDBACK: '匹配反馈', EMP_ABILITY: '员工能力档案',
    POST_ABILITY_MODEL: '岗位能力模型', EMERGING_POST: '新兴岗位',
    POST_EVOLUTION: '岗位演化', LEARNING_OUTCOME: '学习成果', MANUAL: '人工录入',
  }
  return type ? map[type] || type : '-'
}

export function getEvaluationSourceText(source?: string): string {
  const map: Record<string, string> = {
    RESUME: '简历解析', INTERVIEW: '面试评估', VIDEO_INTERVIEW: 'AI视频面试',
    AI_TEST: 'AI能力测试', PROJECT: '项目数据', LEARNING: '学习成果',
    MANUAL: '人工维护', SYSTEM: '系统推断', JD: 'JD导入',
    RAG: 'RAG检索', MODEL: '模型生成', FEEDBACK: '反馈数据',
  }
  return source ? map[source] || source : '未标注'
}

export function getStatusText(status?: string) {
  const map: Record<string, string> = { PENDING: '待审核', VERIFIED: '已验证', REJECTED: '已拒绝' }
  return status ? map[status] || status : '-'
}

export function getStatusType(status?: string) {
  const map: Record<string, string> = { PENDING: 'warning', VERIFIED: 'success', REJECTED: 'danger' }
  return map[status || ''] || 'info'
}

export function scoreText(value?: number) {
  if (value == null) return '0'
  return Number(value).toFixed(0)
}

export function formatDate(date?: string): string {
  if (!date) return '-'
  return date.substring(0, 10)
}

export function getSubjectPlaceholder(mode: 'employee' | 'post') {
  return mode === 'employee' ? '搜索姓名、工号或手机号' : '搜索岗位名称或编码'
}

export function getSubjectLabel(item: EmpEmployee | PostPost, mode: 'employee' | 'post') {
  if (mode === 'employee') {
    const emp = item as EmpEmployee
    return `${emp.realName || '-'} / ${emp.empCode || '-'}`
  }
  const post = item as PostPost
  return `${post.postName || '-'} / ${post.postCode || '-'}`
}
