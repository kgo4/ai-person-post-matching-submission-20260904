/**
 * 员工管理相关类型定义
 */

export interface EmpEmployee {
  id: number
  empCode: string
  realName: string
  gender: number
  phone: string
  email: string
  extendFields: string
  isLocked: number
  status: number
  createdTime: string
}

export type EmpEmployeeCreateDTO = Omit<EmpEmployee, 'empCode'> & {
  empCode?: string
}

export interface EmpAbility {
  id: number
  empId: number
  tagId?: number
  tagName?: string
  abilityName?: string
  assessmentAbilityId?: number
  workflowId?: number
  masteryLevel: number
  evaluationSource: string
  sourceWeight: number
  evaluationDate: string
  remark: string
}

export interface EmpAbilitySaveDTO {
  id?: number
  empId: number
  abilityName?: string
  tagId?: number | null
  masteryLevel: number
  evaluationSource: string
  sourceWeight?: number
  evaluationDate?: string
  remark?: string
  governanceTemplate?: import('@/api/ability-governance').GovernanceTemplate
}

export interface EmpAbilityProfileVO {
  empId: number
  empCode: string
  realName: string
  overallScore: number
  abilityDetails: EmpAbilityDetailVO[]
}

export interface PendingAbilityClaim {
  id: number
  empId: number
  tagId?: number
  abilityName: string
  claimedLevel?: number
  sourceType: string
  sourceRefId?: number
  evidenceText?: string
  confidenceScore?: number
  harnessDecision?: string
  harnessLogId?: number
  status: string
  createdTime?: string
}

export interface EmpAbilityDetailVO {
  tagId: number
  tagName: string
  tagCategory: string
  masteryLevel: number
  masteryLevelName: string
}

export interface VideoInterviewCreateDTO {
  empId: number
  postId?: number
  sessionName: string
  interviewMode: 'POST_BASED' | 'GENERAL'
}

export interface VideoInterviewQuestionGenerateDTO {
  mode?: string
  includeGeneralQuestions?: boolean
}

export interface VideoInterviewWsTicketVO {
  ticket: string
  expiresAt: number
}

export interface VideoInterviewFrameDTO {
  questionOrder: number
  followUpId?: number
  captureSecond: number
  imageDataUrl: string
}

export interface VideoInterviewImportDTO {
  abilityIds: number[]
  overwriteExistingSource?: boolean
  remarkSuffix?: string
}

export interface VideoInterviewSession {
  id: number
  empId: number
  postId?: number
  sessionName: string
  interviewMode: string
  videoFilePath?: string
  transcriptText?: string
  summaryReport?: string
  overallScore?: number
  status: number
  durationSeconds?: number
  questionCount: number
  errorMessage?: string
  createdTime: string
  updatedTime: string
}

export interface VideoInterviewQuestion {
  id: number
  questionOrder: number
  questionType: string
  questionText: string
  answerTranscript?: string
  answerSummary?: string
  startSecond?: number
  endSecond?: number
  answerScore?: number
  analysisComment?: string
}

export interface VideoInterviewEvidence {
  id: number
  questionId?: number
  evidenceType: string
  startSecond?: number
  endSecond?: number
  evidenceText?: string
  confidenceScore?: number
  rawScore?: number
}

export interface VideoInterviewAbility {
  id: number
  tagId: number
  tagName: string
  masteryLevel: number
  confidenceScore: number
  sourceWeight: number
  evidenceSummary?: string
  analysisComment?: string
  importedFlag: boolean
}

export interface VideoInterviewDetailVO {
  id: number
  empId: number
  postId?: number
  sessionName: string
  interviewMode: string
  videoFilePath?: string
  transcriptText?: string
  summaryReport?: string
  overallScore?: number
  status: number
  durationSeconds?: number
  questionCount: number
  errorMessage?: string
  createdTime: string
  updatedTime: string
  questions: VideoInterviewQuestion[]
  evidences: VideoInterviewEvidence[]
  abilities: VideoInterviewAbility[]
}
