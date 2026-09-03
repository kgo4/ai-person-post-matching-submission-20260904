/**
 * 员工能力来源扩展 API
 * 包括：简历解析、AI测试、AI视频面试
 */
import { get, post } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import service from '@/utils/request'
import type {
  VideoInterviewCreateDTO,
  VideoInterviewFrameDTO,
  VideoInterviewQuestionGenerateDTO,
  VideoInterviewImportDTO,
  VideoInterviewSession,
  VideoInterviewDetailVO,
  VideoInterviewWsTicketVO
} from '@/api/types'

// ===================== 类型定义 =====================

/** 简历解析记录 */
export interface ResumeParseRecord {
  id: number
  empId: number
  fileName: string
  fileType: string
  parsedContent?: string
  aiAnalysisResult?: string
  status: number // 0待解析/1解析中/2已完成/3失败
  errorMessage: string
  createdBy: number
  createdTime: string
  updatedTime: string
}

export interface AbilityImportResult {
  total: number
  imported: number
  reused: number
  created: number
  candidate: number
  rejected: number
  importedAbilityIds?: number[]
  candidateIds?: number[]
  rejections?: { tagName: string; reason: string }[]
  message?: string
}

/** AI测试记录 */
export interface AiTestRecord {
  id: number
  empId: number
  testTitle: string
  abilityTagId: number
  abilityTagName: string
  questions: string // JSON
  answers: string // JSON
  aiEvaluation: string // JSON
  score: number
  masteryLevel: number
  analysisReport: string
  errorMessage: string
  status: number // -1生成中/0待作答/1评估中/2已完成/3已导入
  createdBy: number
  createdTime: string
  completedTime: string
  importedTime: string
}

/** AI测试题目 */
export interface AiTestQuestion {
  id: number
  /** choice 为历史遗留值（未区分单选/多选），兼容旧数据视为单选 */
  type: 'choice' | 'choice_single' | 'choice_multiple' | 'text' | 'case'
  difficulty: 'easy' | 'medium' | 'hard'
  question: string
  options?: string[]
  referenceAnswer: string
  score: number
}

/** AI批阅结果 */
export interface AiTestEvaluation {
  status?: 'VALID' | 'INSUFFICIENT_EVIDENCE' | 'UNAVAILABLE' | 'INVALID_OUTPUT'
  score?: number | null
  masteryLevel?: number | null
  analysisReport?: string
  details: {
    questionId: number
    score: number
    maxScore: number
    comment: string
  }[]
}

// ===================== 简历解析 API =====================

/** 上传并解析简历 */
export function uploadAndParseResume(empId: number, file: File): Promise<ApiResponse<ResumeParseRecord>> {
  const formData = new FormData()
  formData.append('file', file)
  return post<ResumeParseRecord>(`/employee/ability/resume-parse/upload?empId=${empId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000, // 简历解析调用大模型需要较长时间，超时设置为120秒
  })
}

/** 查询员工简历解析记录 */
export function listResumeParseRecords(empId: number): Promise<ApiResponse<ResumeParseRecord[]>> {
  return get<ResumeParseRecord[]>(`/employee/ability/resume-parse/list/${empId}`)
}

/** 查询解析详情 */
export function getResumeParseDetail(id: number): Promise<ApiResponse<ResumeParseRecord>> {
  return get<ResumeParseRecord>(`/employee/ability/resume-parse/${id}`)
}

 /** 导入解析结果到能力档案 */
export function importResumeParseResult(id: number): Promise<ApiResponse<AbilityImportResult>> {
  return post<AbilityImportResult>(`/employee/ability/resume-parse/${id}/import`)
}

/** 查看原始简历文件（返回 AxiosResponse，data 为 blob） */
export function getResumeFile(id: number) {
  return service.get(`/employee/ability/resume-parse/${id}/file`, { responseType: 'blob' })
}

/** 重新解析简历 */
export function reparseResume(id: number): Promise<ApiResponse<ResumeParseRecord>> {
  return post<ResumeParseRecord>(`/employee/ability/resume-parse/${id}/reparse`)
}

// ===================== AI测试 API =====================

/** 生成AI测试 */
export function generateAiTest(empId: number, abilityTagId: number): Promise<ApiResponse<AiTestRecord>> {
  return post<AiTestRecord>(
    `/employee/ability/ai-test/generate?empId=${empId}&abilityTagId=${abilityTagId}`,
    undefined,
    { timeout: 120000 },
  )
}

/** Based on a post ability model, generate an AI test. */
export function generatePostAiTest(empId: number, postId: number): Promise<ApiResponse<AiTestRecord>> {
  return post<AiTestRecord>(
    `/employee/ability/ai-test/generate-by-post?empId=${empId}&postId=${postId}`,
    undefined,
    { timeout: 120000 },
  )
}

/** 提交测试答案 */
export interface AiTestAnswerSubmitRequest {
  answers: Record<string, string | string[]>
}

export function submitAiTestAnswers(testId: number, answers: Record<string, string | string[]>): Promise<ApiResponse<AiTestRecord>> {
  const payload: AiTestAnswerSubmitRequest = { answers }
  return post<AiTestRecord>(`/employee/ability/ai-test/${testId}/submit`, payload)
}

/** 获取测试结果 */
export function getAiTestResult(testId: number): Promise<ApiResponse<AiTestRecord>> {
  return get<AiTestRecord>(`/employee/ability/ai-test/${testId}/result`)
}

/** 查询员工测试列表 */
export function listAiTests(empId: number): Promise<ApiResponse<AiTestRecord[]>> {
  return get<AiTestRecord[]>(`/employee/ability/ai-test/list/${empId}`)
}

/** 导入测试结果到能力档案 */
export function importAiTestResult(testId: number): Promise<ApiResponse<boolean>> {
  return post<boolean>(`/employee/ability/ai-test/${testId}/import`)
}

// ===================== AI视频面试 API =====================

/** 创建视频面试会话 */
export function createVideoInterviewSession(dto: VideoInterviewCreateDTO): Promise<ApiResponse<VideoInterviewSession>> {
  return post<VideoInterviewSession>('/employee/ability/video-interview/session/create', dto)
}

/** 生成面试问题 */
export function generateVideoInterviewQuestions(sessionId: number, dto?: VideoInterviewQuestionGenerateDTO): Promise<ApiResponse<void>> {
  return post<void>(`/employee/ability/video-interview/${sessionId}/generate-questions`, dto || {})
}

/** 签发实时面试WebSocket票据 */
export function issueVideoInterviewWsTicket(sessionId: number): Promise<ApiResponse<VideoInterviewWsTicketVO>> {
  return post<VideoInterviewWsTicketVO>(`/employee/ability/video-interview/${sessionId}/ws-ticket`)
}

/** 上传实时视频抽帧 */
export function uploadVideoInterviewFrame(sessionId: number, dto: VideoInterviewFrameDTO): Promise<ApiResponse<void>> {
  return post<void>(`/employee/ability/video-interview/${sessionId}/frame`, dto)
}

/** 开始面试 */
export function startInterviewApi(sessionId: number): Promise<ApiResponse<void>> {
  return post<void>(`/employee/ability/video-interview/${sessionId}/start`)
}

/** 下一题 */
export function nextQuestionApi(sessionId: number): Promise<ApiResponse<void>> {
  return post<void>(`/employee/ability/video-interview/${sessionId}/next-question`)
}

/** 结束面试 */
export function finishInterviewApi(sessionId: number): Promise<ApiResponse<void>> {
  return post<void>(`/employee/ability/video-interview/${sessionId}/finish`)
}

/** 执行多模态分析 */
export function analyzeVideoInterview(sessionId: number): Promise<ApiResponse<void>> {
  return post<void>(`/employee/ability/video-interview/${sessionId}/analyze`)
}

/** 查询员工视频面试列表 */
export function listVideoInterviewSessions(empId?: number): Promise<ApiResponse<VideoInterviewSession[]>> {
  return get<VideoInterviewSession[]>(
    empId ? `/employee/ability/video-interview/list/${empId}` : '/employee/ability/video-interview/list'
  )
}

/** 获取视频面试详情 */
export function getVideoInterviewDetail(sessionId: number): Promise<ApiResponse<VideoInterviewDetailVO>> {
  return get<VideoInterviewDetailVO>(`/employee/ability/video-interview/${sessionId}`)
}

/** 导入能力到档案 */
export function importVideoInterviewAbilities(sessionId: number, dto: VideoInterviewImportDTO): Promise<ApiResponse<void>> {
  return post<void>(`/employee/ability/video-interview/${sessionId}/import`, dto)
}

// ===================== PMS项目数据分析 API =====================

/** PMS用户映射 */
export interface PmsUserMapping {
  id: number
  empId: number
  pmsUserId: number
  pmsUsername: string
  pmsNickname: string
  pmsEmployeeId: string
  createdTime: string
}

/** PMS分析任务 */
export interface PmsAnalysisTask {
  id: number
  empId: number
  pmsUserId: number
  analysisStatus: number
  dateRangeMonths: number
  workOrderCount: number
  bugCount: number
  testCaseCount: number
  projectCount: number
  extractedAbilityCount: number
  aiRawResponse: string
  errorMessage: string
  createdBy: number
  createdTime: string
  updatedTime: string
}

/** 自动映射PMS用户 */
export function autoMapPmsUser(empId: number): Promise<ApiResponse<PmsUserMapping>> {
  return post<PmsUserMapping>(`/employee/ability/pms/auto-map?empId=${empId}`)
}

/** 手动映射PMS用户 */
export function manualMapPmsUser(empId: number, pmsUserId: number): Promise<ApiResponse<PmsUserMapping>> {
  return post<PmsUserMapping>(`/employee/ability/pms/manual-map?empId=${empId}&pmsUserId=${pmsUserId}`)
}

/** 获取映射信息 */
export function getPmsMapping(empId: number): Promise<ApiResponse<PmsUserMapping>> {
  return get<PmsUserMapping>(`/employee/ability/pms/mapping/${empId}`)
}

/** 执行PMS数据分析 */
export function analyzePmsAbilities(empId: number, months: number = 6): Promise<ApiResponse<PmsAnalysisTask>> {
  return post<PmsAnalysisTask>(`/employee/ability/pms/analyze?empId=${empId}&months=${months}`)
}

/** 获取PMS分析历史 */
export function getPmsAnalysisHistory(empId: number): Promise<ApiResponse<PmsAnalysisTask[]>> {
  return get<PmsAnalysisTask[]>(`/employee/ability/pms/history/${empId}`)
}

/** 获取PMS用户列表 */
export function listPmsUsers(): Promise<ApiResponse<any[]>> {
  return get<any[]>('/employee/ability/pms/pms-users')
}

/** 测试PMS连接 */
export function testPmsConnection(): Promise<ApiResponse<boolean>> {
  return get<boolean>('/employee/ability/pms/test-connection')
}

/** 同步PMS用户 */
export function syncPmsUsers(): Promise<ApiResponse<{ newMapped: number; totalPmsUsers: number; alreadyMapped: number; newCreated: number }>> {
  return post('/employee/ability/pms/sync')
}

/** 获取PMS分析结果详情 */
export function getPmsAnalysisDetail(taskId: number): Promise<ApiResponse<{ task: PmsAnalysisTask; summary: string; abilities: Record<string, unknown>[] }>> {
  return get(`/employee/ability/pms/detail/${taskId}`)
}

/** 导入PMS分析能力到员工档案 */
export function importPmsAbilities(empId: number, taskId: number, indexes?: number[]): Promise<ApiResponse<{ importedCount: number }>> {
  return post(`/employee/ability/pms/import?empId=${empId}&taskId=${taskId}`, indexes || [])
}
