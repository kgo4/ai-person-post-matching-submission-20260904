export interface AiTestImportContext {
  isAssessmentFlow: boolean
  status: number
}

export function canImportAiTestResult(context: AiTestImportContext): boolean {
  return !context.isAssessmentFlow && context.status === 2
}

export function shouldSubmitThroughAssessmentWorkflow(context: {
  isAssessmentFlow: boolean
  workflowId: number
}): boolean {
  return context.isAssessmentFlow && context.workflowId > 0
}

export function pollingPhaseForExistingTest(status: number): 'GENERATING' | 'EVALUATING' | undefined {
  if (status === -1) return 'GENERATING'
  if (status === 1) return 'EVALUATING'
  return undefined
}

export function isAiTestEvidenceInsufficient(evaluation?: { status?: string } | null): boolean {
  return evaluation?.status === 'INSUFFICIENT_EVIDENCE'
}

export function aiTestResultSummary(evaluation?: { status?: string; analysisReport?: string } | null): string {
  if (isAiTestEvidenceInsufficient(evaluation)) {
    return '本次回答未提供足够的有效证据，系统未生成分数或能力等级。请补充与题目相关的具体经历、技术决策或结果后重新测试。'
  }
  if (evaluation?.status === 'UNAVAILABLE') {
    return 'AI 批阅服务暂时不可用，本次结果未评分，请稍后重试。'
  }
  if (evaluation?.status === 'INVALID_OUTPUT') {
    return 'AI 批阅结果格式无效，本次结果未评分，请重新提交测试。'
  }
  return evaluation?.analysisReport || ''
}
