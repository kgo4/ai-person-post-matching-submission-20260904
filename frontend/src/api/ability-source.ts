/**
 * 员工能力来源扩展 API
 * 包括：简历解析、AI测试、AI视频面试
 */
import { del, get, post } from '@/utils/request'
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

// ===================== PMS 项目分析 API =====================
// 【2026-09-04 定位变化】主键从 empId 改为 pmsUserId。
// 原实现挂在员工档案的逐行「项目分析」按钮上，同步 PMS 人员时会往人员库插影子档案
// （工号 PMS_<id>），于是 PMS 平台上的人混进了本系统人员列表。
// 现在同步只落映射（empId 为 null = 已同步未绑定），绑定是 HR 在花名册页的显式动作；
// 「能分析、导入才要求绑定」——分析只依赖 PMS 数据，导入能力才需要 empId。

/** PMS 用户映射；empId 为 null 表示「已同步但尚未绑定本系统员工」 */
export interface PmsUserMapping {
  id: number
  empId: number | null
  pmsUserId: number
  pmsUsername: string
  pmsNickname: string
  pmsEmployeeId: string
  createdTime: string
}

/** PMS 分析任务；未绑定员工时 empId 为 null */
export interface PmsAnalysisTask {
  id: number
  empId: number | null
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

/** 花名册一行：PMS 侧的人 + 它在本系统的绑定与分析情况 */
export interface PmsRosterItem {
  pmsUserId: number
  pmsUsername: string | null
  pmsNickname: string | null
  pmsEmployeeId: string | null
  pmsEmail: string | null
  pmsPhone: string | null
  pmsRole: string | null
  /** 是否已绑定本系统员工 */
  bound: boolean
  empId: number | null
  empName: string | null
  empCode: string | null
  /** 绑定的员工是否已有登录账号；未绑定时为 null */
  empHasAccount: boolean | null
  analysisCount: number
  /** 最近一次分析状态；从未分析过为 null */
  lastAnalysisStatus: number | null
  lastAnalysisTime: string | null
}

/**
 * 花名册响应。
 * 统计与清单**同源返回**：分成两个接口时只要其中一个失败，
 * 就会出现「统计说 3 人已绑定、列表里 0 行」的自相矛盾页面。
 */
export interface PmsRosterResponse {
  pmsConnected: boolean
  totalPmsUsers: number
  boundCount: number
  unboundCount: number
  analyzedCount: number
  /** 当前被占用的本地员工ID，绑定弹窗据此即时提示冲突 */
  boundEmpIds: number[]
  items: PmsRosterItem[]
  /** 空结果时的诊断说明；有数据时为 null。空列表必须带原因，否则无法区分「PMS 没人」与「PMS 连不上」 */
  message: string | null
}

/** 同步结果口径（2026-09-04 起：只落映射，不再创建员工） */
export interface PmsSyncResult {
  /** 本次新同步的 PMS 人员（已同步未绑定） */
  newSynced: number
  totalPmsUsers: number
  /** 此前已同步过的（含已绑定） */
  alreadySynced: number
  /** 本次按工号唯一命中并自动绑定的人数 */
  autoBound: number
}

/** PMS 人员花名册（HR 独立功能的主数据源） */
export function getPmsRoster(): Promise<ApiResponse<PmsRosterResponse>> {
  return get<PmsRosterResponse>('/employee/ability/pms/roster')
}

/** 把 PMS 人员绑定到本系统员工；任一方向已有绑定会返回 409 */
export function bindPmsRosterUser(pmsUserId: number, empId: number): Promise<ApiResponse<PmsUserMapping>> {
  return post<PmsUserMapping>(`/employee/ability/pms/roster/${pmsUserId}/bind?empId=${empId}`)
}

/** 解除 PMS 人员与员工的绑定；已导入的员工能力不会被删除 */
export function unbindPmsRosterUser(pmsUserId: number): Promise<ApiResponse<void>> {
  return del<void>(`/employee/ability/pms/roster/${pmsUserId}/bind`)
}

/** 以 PMS 人员为入口分析；未绑定员工时也允许 */
export function analyzePmsUser(pmsUserId: number, months: number = 6): Promise<ApiResponse<PmsAnalysisTask>> {
  return post<PmsAnalysisTask>(`/employee/ability/pms/roster/${pmsUserId}/analyze?months=${months}`)
}

/** 以 PMS 人员为入口查分析历史（不依赖绑定关系） */
export function getPmsUserAnalysisHistory(pmsUserId: number): Promise<ApiResponse<PmsAnalysisTask[]>> {
  return get<PmsAnalysisTask[]>(`/employee/ability/pms/roster/${pmsUserId}/history`)
}

/** 同步 PMS 人员；只建映射，不写人员库 */
export function syncPmsUsers(): Promise<ApiResponse<PmsSyncResult>> {
  return post<PmsSyncResult>('/employee/ability/pms/sync')
}

/**
 * 获取PMS分析结果详情（按任务ID；与入口无关）
 */
export function getPmsAnalysisDetail(taskId: number): Promise<ApiResponse<{ task: PmsAnalysisTask; summary: string; abilities: Record<string, unknown>[] }>> {
  return get(`/employee/ability/pms/detail/${taskId}`)
}

/** 导入PMS分析能力到员工档案；目标员工必须已绑定该 PMS 人员，否则后端返回 400 */
export function importPmsAbilities(empId: number, taskId: number, indexes?: number[]): Promise<ApiResponse<{ importedCount: number }>> {
  return post(`/employee/ability/pms/import?empId=${empId}&taskId=${taskId}`, indexes || [])
}

/*
 * 已删除三个按 empId 的前端包装（analyze?empId= / history/{empId} / test-connection）：
 *   · 分析入口已统一为 PMS 人员（analyzePmsUser / getPmsUserAnalysisHistory），
 *     再留一个「按员工分析」的包装只会让人误以为还能从员工侧发起；
 *   · 连接状态由花名册的 pmsConnected 字段带回，不必再多打一次接口。
 * 后端这三个端点仍在（兼容旧调用方），如需恢复请连同调用点一起加回。
 */
