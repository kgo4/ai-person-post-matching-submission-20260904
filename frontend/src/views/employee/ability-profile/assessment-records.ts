/**
 * 「我的评估流程」页的评估记录纯逻辑。
 *
 * <p>背景：一个人可能被评估多次（每次一条 workflow），页面必须能列出每一次评估、
 * 并逐次查看该次的评估报告。同时「能力审核说明」的文案必须由**真实数据**推导 ——
 * 历史 bug：未评估过的员工也会看到「能力审核已完成…评估报告已生成」，
 * 原因是文案只看 `profile.assessmentPending`，而该字段所在的请求只在「存在进行中流程」时才发出，
 * 于是无流程时 `profile` 恒为空对象、字段为 undefined 落入「已完成」分支。
 * 现在改为「先确定有没有评估记录，再谈审核到哪一步」。
 */

/** 评估记录列表项（与后端 WorkflowReportDTO / api 层 AssessmentReportListItem 对齐的最小形状） */
export interface EvaluationRecordLike {
  workflowId: number
  workflowStatus?: string | null
  startedAt?: string | null
  completedAt?: string | null
  /** READY / FAILED / GENERATING(内容仍在生成) / null(未生成) */
  reportStatus?: string | null
  overallScore?: number | null
  postMatchScore?: number | null
}

/**
 * 流程状态码 → 中文。
 *
 * 列表项只带状态码（不含 WorkflowView 的 displayStatus），所以这里做一层映射；
 * 遇到未知状态码直接回显原码，方便排查而不是显示成「未知」把问题藏起来。
 */
const WORKFLOW_STATUS_TEXT: Record<string, string> = {
  RESUME_REQUIRED: '待上传简历',
  RESUME_PARSING: '简历解析中',
  RESUME_PARSED_NO_EVIDENCE: '简历已解析（无可用证据）',
  RESUME_EVIDENCE_READY: '简历证据就绪',
  TEST_GENERATING: '测试生成中',
  TEST_IN_PROGRESS: '测试进行中',
  TEST_EVALUATING: '测试评分中',
  TEST_EVIDENCE_READY: '测试证据就绪',
  INTERVIEW_PREPARING: '面试准备中',
  INTERVIEW_IN_PROGRESS: '面试进行中',
  INTERVIEW_ANALYZING: '面试分析中',
  AGGREGATE_HARNESS_RUNNING: '聚合审核中',
  LEVEL_CONFIRMING: '等级确认中',
  COMPLETED: '已完成',
  REVIEW_REQUIRED: '待人工复核',
  RECOVERY_REQUIRED: '流程待恢复',
  FAILED: '流程失败',
  CANCELLED: '已取消',
}

export function evaluationStatusText(status?: string | null): string {
  if (!status) return '进行中'
  return WORKFLOW_STATUS_TEXT[status] ?? status
}

export type EvaluationTagType = 'success' | 'danger' | 'info' | 'warning'

/**
 * 流程是否已进入「HR 人工审核」阶段。
 *
 * <p>【2026-09-04 修复】此前「报告还没生成」一律被说成「审核中」，
 * 于是员工刚上传简历、流程还在简历解析/测试/面试阶段时，
 * 「我的评估记录」就直接显示 HR 在审核 —— 实际上 HR 根本没有参与，
 * 只是流程没走完。只有走到聚合审核 / 等级确认这两步，HR 才真正在做审核动作。
 */
export function isHrReviewStage(workflowStatus?: string | null): boolean {
  return workflowStatus === 'AGGREGATE_HARNESS_RUNNING'
    || workflowStatus === 'LEVEL_CONFIRMING'
    || workflowStatus === 'REVIEW_REQUIRED'
}

export interface ReportStatusMeta {
  text: string
  tagType: EvaluationTagType
  /** 报告内容是否可打开（未生成时不该给可点按钮，避免点了只看到一句「不可查看」） */
  viewable: boolean
}

/**
 * 报告状态 → 标签文案/颜色 + 是否可查看。
 *
 * @param workflowStatus 该次评估的流程状态。判定用**白名单**：只有流程确实停在
 *   HR 的审核环节（`isHrReviewStage`）才说「审核中」，其余一律「评估中」。
 *   传空（拿不到流程状态）时同样落「评估中」—— 宁可少说，不可把「HR 还没参与」
 *   说成「HR 正在审」，这是员工产生误解的源头。
 */
export function reportStatusMeta(
  reportStatus?: string | null,
  workflowStatus?: string | null,
): ReportStatusMeta {
  if (reportStatus === 'READY') {
    return { text: '已生成', tagType: 'success', viewable: true }
  }
  if (reportStatus === 'FAILED') {
    return { text: '生成失败', tagType: 'danger', viewable: false }
  }
  // 报告行已落库（AI 面试结束即 READY），但「最终能力等级 / AI 洞察」等内容还在生成。
  // 后端在列表里把这种行标成 GENERATING（与详情闸门 3 同源）：不能显示「已生成」，
  // 否则员工点开只会看到一句「内容仍在生成中」。
  if (reportStatus === 'GENERATING') {
    return { text: '生成中', tagType: 'warning', viewable: false }
  }
  // 报告未生成时就断言「HR 审核中」是假信息：简历解析 / AI 测试 / AI 面试这几步
  // 都是员工与系统在推进，HR 根本没有参与。
  if (isHrReviewStage(workflowStatus)) {
    return { text: '审核中', tagType: 'info', viewable: false }
  }
  return { text: '评估中', tagType: 'info', viewable: false }
}

/**
 * 「综合评分」单元格文案。
 *
 * <p>口径（2026-09-04）：综合评分属于**评估结论**，与报告正文同一闸门 ——
 * 只有 HR 把该人员全部能力项人工审核完毕（报告已生成）后才下发。
 *
 * <p>后端收口在 `CapabilityAssessmentFacadeImpl.listAssessmentReports`
 * （未清空时把 overallScore/postMatchScore 置 null），本函数是展示层纵深防御：
 * 拿不到分数时**不能显示 `--`** —— 那是「没分数」的写法，会被读成「考了 0 分」或
 * 「这人的分数丢了」；必须明确告诉员工**为什么没有**（还在审核）。
 *
 * <p>历史缺陷：这一列曾经直接渲染 `row.overallScore ?? '--'`，于是同一行里
 * 报告状态写着「审核中」、旁边却挂着一个已经算好的综合评分，自相矛盾。
 *
 * @param workflowStatus 与 `reportStatusMeta` 同一口径（见其注释）：只有流程确实
 *   停在 HR 审核环节才说「审核中」，否则说「评估中」。
 */
export function overallScoreText(
  reportStatus?: string | null,
  overallScore?: number | null,
  workflowStatus?: string | null,
): string {
  if (reportStatus === 'READY') {
    return overallScore == null ? '--' : String(overallScore)
  }
  // 生成失败是终态失败，不是「还在审」，不能拿「审核中」搪塞
  if (reportStatus === 'FAILED') return '--'
  // 报告内容仍在生成：既不是审核也不是普通的流程推进，明确说「生成中」
  if (reportStatus === 'GENERATING') return '生成中'
  return isHrReviewStage(workflowStatus) ? '审核中' : '评估中'
}

export interface EvaluationSummary {
  total: number
  /** 已完成的评估次数 */
  completed: number
  /** 报告已生成、可查看的次数 */
  reportReady: number
  /** 仍在审核/进行中的次数 */
  inProgress: number
}

export function summarizeEvaluationRecords(records: EvaluationRecordLike[]): EvaluationSummary {
  let completed = 0
  let reportReady = 0
  for (const record of records) {
    if (record.workflowStatus === 'COMPLETED') completed += 1
    if (record.reportStatus === 'READY') reportReady += 1
  }
  return {
    total: records.length,
    completed,
    reportReady,
    inProgress: records.length - completed,
  }
}

export type ReviewNoteTone = 'loading' | 'unavailable' | 'empty' | 'pending' | 'done' | 'in-progress'

export interface ReviewNoteInput {
  /** 评估记录是否已加载完成：未加载完不下任何结论，避免先闪一句错文案再改口 */
  recordsLoaded: boolean
  /**
   * 评估记录是否加载失败。
   * 必须与「没有记录」区分开：拉取失败时若按空处理，就会对着一堆真实存在的评估记录说
   * 「你还没有进行过能力评估」—— 这是同一个坑换了入口，不能踩第二次。
   */
  recordsLoadFailed?: boolean
  /** 是否存在任何一次评估记录 */
  hasRecords: boolean
  /** 后端闸门：结果未定稿（含「从未评估」与「审核未清空」两种情形，必须与 hasRecords 联合判断） */
  assessmentPending: boolean
  /** 最近一次评估的报告是否已生成 */
  reportReady: boolean
  /**
   * 最近一次评估的流程状态。
   *
   * <p>【2026-09-04 修复】后端闸门把「从未评估」与「审核未清空」都算作
   * `assessmentPending = true`，但「审核未清空」其实是两种完全不同的情形：
   * 一种是流程还在简历 / 测试 / 面试阶段（HR 尚未参与），另一种是流程已到
   * 聚合审核 / 等级确认（HR 真的在审）。不区分就会出现「刚上传完简历，
   * 页面就说 HR 正在审核你的能力项」—— 用户看到的进度比真实进度超前一大步。
   */
  latestWorkflowStatus?: string | null
}

export interface ReviewNoteState {
  tone: ReviewNoteTone
  title: string
  desc: string
  /** 是否展示「前往我的能力画像」入口：没有已定稿结果时不该把员工引到一个空画像页 */
  showProfileButton: boolean
}

export function deriveReviewNote(input: ReviewNoteInput): ReviewNoteState {
  if (!input.recordsLoaded) {
    return {
      tone: 'loading',
      title: '正在加载评估记录…',
      desc: '正在读取你的历史评估记录，请稍候。',
      showProfileButton: false,
    }
  }
  // 拉取失败时不下任何业务结论（既不谎报已完成，也不谎称从未评估）
  if (input.recordsLoadFailed) {
    return {
      tone: 'unavailable',
      title: '暂时无法读取评估记录',
      desc: '刷新页面可重试；若持续失败，请联系 HR 或权限管理员检查你的评估记录。',
      showProfileButton: false,
    }
  }
  // 关键分支：没有评估记录 → 绝不显示「已完成」，也不再假称 HR 正在审核
  if (!input.hasRecords) {
    return {
      tone: 'empty',
      title: '你还没有进行过能力评估',
      desc: '上传简历后即可开始第一次能力评估。评估流程全部完成并通过 HR 审核后，'
        + '你的能力雷达图与评估报告会在「我的能力画像」生成。',
      showProfileButton: false,
    }
  }
  if (input.assessmentPending) {
    // 流程还没走到聚合审核 / 等级确认时，HR 并未参与，不能说「HR 正在审核你的能力项」。
    // 这条分支才是「刚上传简历就看到 HR 在审核」的根因。
    if (!isHrReviewStage(input.latestWorkflowStatus)) {
      return {
        tone: 'in-progress',
        title: '本次评估进行中',
        desc: '评估流程还在推进（简历解析 / AI 测试 / AI 面试），尚未进入 HR 审核。'
          + '全部阶段完成且 HR 审核通过后，评估报告与综合评分才会在「我的能力画像」生成。',
        showProfileButton: false,
      }
    }
    return {
      tone: 'pending',
      title: 'HR 正在审核你的能力项',
      desc: '审核期间不展示能力明细（避免把未定稿的结论当成最终结果）。'
        + 'HR 完成全部能力项审核、评估报告生成后，你可以在「我的能力画像」看到本次确立的能力、能力雷达与评估报告。',
      showProfileButton: true,
    }
  }
  if (input.reportReady) {
    return {
      tone: 'done',
      title: '能力审核已完成',
      desc: '本次确立的能力、能力雷达图与评估报告已生成，可在「我的能力画像」查看。',
      showProfileButton: true,
    }
  }
  return {
    tone: 'in-progress',
    title: '本次评估尚未完成',
    desc: '评估流程还在推进中，或报告内容仍在生成。全部内容生成完成后会生成评估报告，'
      + '可先查看下方的历史评估记录。',
    showProfileButton: false,
  }
}
