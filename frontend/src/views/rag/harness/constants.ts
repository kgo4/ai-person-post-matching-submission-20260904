import type { AiHarnessCheckLog } from '@/api/ai-governance'

export const SCENARIO_LABELS: Record<string, string> = {
  PERSON_ABILITY: '人员能力治理', POST_ABILITY: '岗位能力治理',
  EMP_ABILITY_RESUME_PARSE: '简历解析', EMP_ABILITY_AI_TEST: 'AI 测评',
  EMP_ABILITY_VIDEO_INTERVIEW: '视频面试', EMP_ABILITY_PMS_ANALYSIS: 'PMS 分析',
  RESUME_PARSE: '简历解析', AI_TEST: 'AI 测评', VIDEO_INTERVIEW: '视频面试',
  PMS_ANALYSIS: 'PMS 分析', JD_ABILITY_EXTRACT: 'JD 能力提取',
  POST_ABILITY_JD_EXTRACT: 'JD 能力提取', POST_ABILITY_GENERATION: '岗位能力生成',
  POST_EVOLUTION: '岗位演化', POST_DYNAMIC_EVOLUTION: '岗位动态演化',
  MATCH_GAP_DIAGNOSIS: '匹配差距诊断', MATCH_EXPLANATION: '匹配解释',
  MATCHING_ANALYSIS: '匹配分析', AI_LEARNING_SUGGESTION: 'AI 学习建议',
  LEARNING_PATH_SUGGESTION: '学习路径建议', REPORT_GENERATION: '报告生成',
  ABILITY_TAG_GOVERNANCE: '能力标签治理', EVIDENCE_GOVERNANCE: '证据治理',
  COMPANY_POST_WEIGHT: '企业岗位权重', AI_INTERVIEW_OBSERVATION: 'AI 面试观察',
}

export const CLAIM_TYPE_LABELS: Record<string, string> = {
  EMP_ABILITY: '员工能力', POST_ABILITY: '岗位能力', ABILITY_TAG: '能力标签',
  ABILITY_CHANGE: '能力变更', POST_EVOLUTION_CHANGE: '岗位演化变更',
  POST_ABILITY_CHANGE: '岗位能力变更', INTERVIEW_ABILITY_OBSERVATION: '面试能力观察',
  DIAGNOSIS_DIMENSION: '诊断维度', LEARNING_STEP: '学习步骤',
  REPORT_MARKDOWN: '报告内容', COMPANY_POST_WEIGHT_DIFF: '岗位权重差异',
  SKILL_CLAIM: '技能声明', EXPERIENCE_CLAIM: '经验声明',
  EDUCATION_CLAIM: '学历声明', CERTIFICATION_CLAIM: '证书声明',
  ABILITY_CLAIM: '能力声明', TAG_MAPPING_CLAIM: '标签映射声明',
  EVOLUTION_SIGNAL_CLAIM: '演化信号声明', MATCH_SCORE_CLAIM: '匹配分数声明',
  GAP_DIAGNOSIS_CLAIM: '差距诊断声明',
}

export const DECISION_LABELS: Record<string, string> = { PASS: '通过', REVIEW: '复核', BLOCK: '拦截' }
export const RISK_LABELS: Record<string, string> = { HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险' }

export const SOURCE_TYPE_LABELS: Record<string, string> = {
  MATCH_GAP_DIAGNOSIS: '匹配差距诊断', REPORT_GENERATION: '报告生成',
  CONTEST_REPORT_TASK: '竞赛报告', JD_IMPORT: 'JD 导入',
  JD_ABILITY_EXTRACT: 'JD 能力提取', COMPANY_POST_REQUIREMENT: '企业岗位需求',
  RESUME_PARSE: '简历解析', AI_RESUME: 'AI 简历', EMP_ABILITY: '员工能力',
  AI_TEST: 'AI 测评', VIDEO_INTERVIEW: '视频面试', AI_VIDEO_INTERVIEW: 'AI 视频面试',
  POST_EVOLUTION_TASK: '岗位演化任务', POST_EVOLUTION: '岗位演化',
}

const CODE_WORD_LABELS: Record<string, string> = {
  AI: 'AI', JD: 'JD', PMS: 'PMS', RAG: 'RAG', EMP: '员工', EMPLOYEE: '员工',
  PERSON: '人员', POST: '岗位', ABILITY: '能力', TAG: '标签', CLAIM: '声明',
  MATCH: '匹配', MATCHING: '匹配', GAP: '差距', DIAGNOSIS: '诊断', DIMENSION: '维度',
  EVOLUTION: '演化', CHANGE: '变更', SIGNAL: '信号', RESUME: '简历', TEST: '测评',
  VIDEO: '视频', INTERVIEW: '面试', OBSERVATION: '观察', LEARNING: '学习',
  STEP: '步骤', PATH: '路径', SUGGESTION: '建议', REPORT: '报告', MARKDOWN: '内容',
  COMPANY: '企业', WEIGHT: '权重', DIFF: '差异', GENERATION: '生成', EXTRACT: '提取',
  GOVERNANCE: '治理', EVIDENCE: '证据', DYNAMIC: '动态',
}

export const humanizeCode = (v?: string) => {
  if (!v) return '-'
  return v.split('_').filter(Boolean).map((part) => CODE_WORD_LABELS[part] || part).join(' / ')
}

export const labelScenario = (v?: string) => (v && SCENARIO_LABELS[v]) || humanizeCode(v)
export const labelClaimType = (v?: string) => (v && CLAIM_TYPE_LABELS[v]) || humanizeCode(v)
export const labelDecision = (v?: string) => (v && DECISION_LABELS[v]) || humanizeCode(v)
export const labelRisk = (v?: string) => (v && RISK_LABELS[v]) || humanizeCode(v)
export const labelSourceType = (v?: string) => (v && SOURCE_TYPE_LABELS[v]) || humanizeCode(v)

export const decisionTagType = (decision: string) => {
  if (decision === 'PASS') return 'success'
  if (decision === 'REVIEW') return 'warning'
  if (decision === 'BLOCK') return 'danger'
  return 'info'
}

export const riskTagType = (riskLevel: string) => {
  if (riskLevel === 'HIGH') return 'danger'
  if (riskLevel === 'MEDIUM') return 'warning'
  if (riskLevel === 'LOW') return 'success'
  return 'info'
}

export const reviewStatusTagType = (status: string) => {
  if (status === 'ACCEPTED' || status === 'AUTO_PASSED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'PENDING') return 'warning'
  return 'info'
}

export const reviewStatusLabel = (status: string) => {
  if (status === 'ACCEPTED') return '已采纳'
  if (status === 'REJECTED') return '已驳回'
  if (status === 'RESOLVED') return '已处理'
  if (status === 'AUTO_PASSED') return '自动通过'
  return '待处理'
}

export const businessApplyStatusTagType = (status?: string) => {
  if (status === 'APPLIED') return 'success'
  if (status === 'MANUAL_ACCEPTED') return 'warning'
  if (status === 'APPLY_FAILED') return 'danger'
  if (status === 'SKIPPED') return 'info'
  return 'info'
}

export const businessApplyStatusLabel = (status?: string) => {
  if (status === 'APPLIED') return '已写入业务'
  if (status === 'MANUAL_ACCEPTED') return '待领域流程应用'
  if (status === 'APPLY_FAILED') return '应用失败'
  if (status === 'SKIPPED') return '未应用'
  return '待处理'
}

export const resolveReviewStatus = (log: AiHarnessCheckLog) => {
  if (log.reviewStatus) return log.reviewStatus
  return log.decision === 'PASS' ? 'AUTO_PASSED' : 'PENDING'
}

export const CLAIM_TYPE_OPTIONS = Object.entries(CLAIM_TYPE_LABELS).map(([value, label]) => ({ value, label }))
