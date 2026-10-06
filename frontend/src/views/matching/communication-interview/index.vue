<script setup lang="ts">
/**
 * HR 视频终面（人岗匹配闭环设计 P5）。
 *
 * 路线：由 HR 从**全部匹配结果**中手动选择并发起真实视频沟通（入职前最终确认）。
 * 匹配通过（强适配/适配）会排在前并标「推荐」，但**不是发起的前置条件**。
 * 平台不做媒体 —— 会议链接由 HR 在讯飞会议侧创建后粘贴进来，平台只管
 * 「发起邀请 → 通知 → 留痕 → 定论」。
 *
 * 平台无法感知员工是否真的入会，终面完成与否以 HR 手动录入结论为准。
 */
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Refresh, VideoCamera } from '@element-plus/icons-vue'
import { resolveApiErrorMessage } from '@/utils/request-error-message'
import {
  cancelInterview,
  createInterview,
  getInterviewBriefing,
  getPendingInterviewCount,
  listInterviewCandidates,
  pageInterviews,
  recordInterviewResult,
  INTERVIEW_RESULT,
  INTERVIEW_RESPONSE,
  INTERVIEW_STATUS,
  INVITE_MAIL_STATUS,
} from '@/api/communication-interview'
import type {
  CommunicationInterview,
  InterviewBriefing,
  InterviewCandidate,
} from '@/api/communication-interview'
import { containsHttpUrl } from './meeting-invite'
// 「进入会议」按钮的状态判据（唯一来源，员工侧同源复用），纯函数便于单测覆盖
import { resolveMeetingAction } from './meeting-action'

/* ===================== 候选池 ===================== */

const candidates = ref<InterviewCandidate[]>([])
const candidatesLoading = ref(false)
/**
 * 仅看推荐（匹配通过）。默认关闭 —— 池子是全部匹配结果，
 * 但大多数情况下 HR 关心的就是推荐项，给一个一键过滤。
 */
const onlyRecommended = ref(false)
const visibleCandidates = computed(() =>
  onlyRecommended.value ? candidates.value.filter(item => item.recommended) : candidates.value)
const recommendedCount = computed(() => candidates.value.filter(item => item.recommended).length)

async function loadCandidates() {
  candidatesLoading.value = true
  try {
    const res = await listInterviewCandidates()
    candidates.value = res.data || []
  } catch {
    candidates.value = []
  } finally {
    candidatesLoading.value = false
  }
}

/* ===================== 终面记录 ===================== */

const records = ref<CommunicationInterview[]>([])
const recordsLoading = ref(false)
const total = ref(0)
const pendingCount = ref(0)

const query = reactive({
  current: 1,
  size: 10,
  status: '' as number | '',
})

async function loadRecords() {
  recordsLoading.value = true
  try {
    const res = await pageInterviews({
      current: query.current,
      size: query.size,
      status: query.status === '' ? undefined : query.status,
    })
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    records.value = []
    total.value = 0
  } finally {
    recordsLoading.value = false
  }
}

async function loadPendingCount() {
  try {
    const res = await getPendingInterviewCount()
    pendingCount.value = res.data ?? 0
  } catch {
    pendingCount.value = 0
  }
}

function refreshAll() {
  loadCandidates()
  loadRecords()
  loadPendingCount()
}

function statusFilterChange() {
  query.current = 1
  loadRecords()
}

/* ===================== 发起（沟通要点 + 链接） ===================== */

const launchVisible = ref(false)
const launching = ref(false)
const briefing = ref<InterviewBriefing | null>(null)
const launchTarget = ref<InterviewCandidate | null>(null)
const launchFormRef = ref<FormInstance>()

/**
 * 沟通要点的加载状态。
 *
 * 它是一份**辅助材料**（要跨模块读能力报告、跑差距诊断，通常比打开弹窗慢），
 * 所以必须与弹窗的打开解耦：弹窗立刻出现、会议链接立即可填，
 * 要点区自己维护 idle → loading → ready / error 的状态并给出可重试入口。
 * 之前用整屏 `v-loading` 遮罩整个弹窗，HR 在要点返回前连链接都填不了。
 */
const briefingStatus = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const briefingError = ref('')

/** 发起失败的持久化提示（跟着弹窗留在原地，不是一闪而过的 toast） */
const launchError = ref('')

const launchForm = reactive({
  meetingUrl: '',
  scheduledTime: '' as string | '',
})

/**
 * 会议邀请的本地校验：只把「一眼能看出错」的情况拦在请求之前。
 *
 * 讯飞会议「复制邀请信息」给出的是一整段多行文本，里面同时有**入会链接**和
 * **客户端下载链接**，因此这里**不做 `new URL(整段文本)` 严格校验** ——
 * 那会把合法输入直接判错。只要求「能识别到至少一条 http(s) 链接」，
 * 具体挑哪条（优先含 /join 的）、域名是否在白名单，全部交给后端，
 * 避免两边口径漂移。判定逻辑抽在 `meeting-invite.ts` 里由 `.test.mjs` 直测。
 */
function validateMeetingUrl(_rule: unknown, value: string, callback: (error?: Error) => void) {
  const text = (value || '').trim()
  if (!text) {
    callback(new Error('请粘贴会议邀请信息'))
    return
  }
  if (!containsHttpUrl(text)) {
    callback(new Error('没有识别到会议链接：请粘贴讯飞会议「复制邀请信息」给出的整段内容，或直接粘贴完整链接（含 https:// 前缀）'))
    return
  }
  callback()
}

const launchRules: FormRules = {
  meetingUrl: [{ validator: validateMeetingUrl, trigger: ['blur', 'change'] }],
}

function openLaunch(candidate: InterviewCandidate) {
  launchTarget.value = candidate
  launchForm.meetingUrl = ''
  launchForm.scheduledTime = ''
  briefing.value = null
  briefingError.value = ''
  briefingStatus.value = 'idle'
  launchError.value = ''
  launchVisible.value = true
  nextTick(() => {
    launchFormRef.value?.clearValidate()
    // 表单先可用，要点在后头异步加载 —— 不 await，不阻塞弹窗
    void loadBriefing()
  })
}

async function loadBriefing() {
  const target = launchTarget.value
  if (!target) return
  briefingStatus.value = 'loading'
  briefingError.value = ''
  try {
    const res = await getInterviewBriefing(target.empId, target.matchingRecordId)
    briefing.value = res.data || null
    if (briefing.value) {
      briefingStatus.value = 'ready'
    } else {
      briefingStatus.value = 'error'
      briefingError.value = '暂无可用的沟通要点（可能缺少能力报告或匹配记录）'
    }
  } catch (e) {
    briefing.value = null
    briefingStatus.value = 'error'
    briefingError.value = resolveApiErrorMessage(e, '沟通要点加载失败')
  }
}

async function submitLaunch() {
  const target = launchTarget.value
  if (!target) return
  launchError.value = ''

  // 表单未挂载（极端时序）时退化为手工校验，避免「点了没反应」
  if (launchFormRef.value) {
    const valid = await launchFormRef.value.validate().catch(() => false)
    if (!valid) return
  } else if (!containsHttpUrl(launchForm.meetingUrl)) {
    ElMessage.warning('没有识别到会议链接，请粘贴讯飞会议给出的整段邀请信息或完整链接')
    return
  }

  launching.value = true
  try {
    await createInterview({
      empId: target.empId,
      postId: target.postId ?? undefined,
      matchingRecordId: target.matchingRecordId,
      meetingUrl: launchForm.meetingUrl.trim(),
      scheduledTime: launchForm.scheduledTime || undefined,
    })
    launchVisible.value = false
    ElMessage.success('已发起视频终面：系统正在向员工邮箱发送邀请邮件，并推送站内通知')
    refreshAll()
  } catch (e) {
    // 失败时保持弹窗打开、保留已填内容，把可读原因就地常驻展示。
    // 典型场景：链接域名不在白名单 —— 后端会把「当前允许哪些域名」写进提示语，
    // 这里必须原样透出，而不是吞成一句「发起失败」。
    launchError.value = resolveApiErrorMessage(e, '发起失败，请稍后重试')
  } finally {
    launching.value = false
  }
}

async function copyBriefing() {
  const text = briefing.value?.plainText
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('沟通要点已复制为纯文本')
  } catch {
    ElMessage.warning('复制失败，请手动选择文本复制')
  }
}

/* ===================== 记录操作 ===================== */

/**
 * 「进入会议」按钮的呈现方式（文案 / 禁用 / 是否显示 / 原因）。
 * 判据全部在 `./meeting-action.ts`，模板只做渲染，避免两处页面各写一套规则。
 */
function meetingAction(row: CommunicationInterview) {
  return resolveMeetingAction(row)
}

/**
 * 进入会议。
 *
 * 必须带 row 而不是裸 url：表格是几十秒前的快照，期间状态可能已被员工放弃或 HR 录入结论，
 * 拿 row 重新判一次，避免把用户送进一个早已结束（甚至从未开始）的会议室。
 */
function joinMeeting(row: CommunicationInterview) {
  const action = resolveMeetingAction(row)
  if (!action.visible || action.disabled) {
    ElMessage.warning(action.reason)
    return
  }
  window.open(row.meetingUrl, '_blank', 'noopener')
}

async function handleCancel(row: CommunicationInterview) {
  try {
    await ElMessageBox.confirm(
      `确认取消「${row.empName || `员工#${row.empId}`}」的这次视频终面吗？取消后可重新发起。`,
      '取消视频终面',
      { type: 'warning' },
    )
    await cancelInterview(row.id)
    ElMessage.success('已取消')
    refreshAll()
  } catch {
    // 用户取消
  }
}

const resultVisible = ref(false)
const resultSaving = ref(false)
const resultTarget = ref<CommunicationInterview | null>(null)
const resultForm = reactive({
  result: INTERVIEW_RESULT.PASS as number,
  comment: '',
})

function openResult(row: CommunicationInterview) {
  resultTarget.value = row
  resultForm.result = INTERVIEW_RESULT.PASS
  resultForm.comment = ''
  resultVisible.value = true
}

async function submitResult() {
  if (!resultTarget.value) return
  resultSaving.value = true
  try {
    await recordInterviewResult(resultTarget.value.id, resultForm.result, resultForm.comment || undefined)
    resultVisible.value = false
    ElMessage.success(
      resultForm.result === INTERVIEW_RESULT.UNDECIDED
        ? '已记录为「待定」，该记录保持待沟通，可改期再谈'
        : '结论已记录，员工可查看结果与评价原文',
    )
    refreshAll()
  } catch (e: any) {
    ElMessage.error(e?.message || '录入失败，请稍后重试')
  } finally {
    resultSaving.value = false
  }
}

/* ===================== 展示辅助 ===================== */

const launchTitle = computed(() => {
  const target = launchTarget.value
  if (!target) return '发起视频终面'
  return `发起视频终面 · ${target.empName || `员工#${target.empId}`}`
})

/** 匹配状态标签色：强适配/适配=success（推荐），待观察=warning，不适配=info */
function matchStatusTagType(status: number | null) {
  if (status === 1 || status === 2) return 'success'
  if (status === 3) return 'warning'
  return 'info'
}

function candidateLabel(row: InterviewCandidate) {
  return `${row.empName || `员工#${row.empId}`} → ${row.postName || `岗位#${row.postId}`}`
}

function resultTagType(result: number | null) {
  if (result === INTERVIEW_RESULT.PASS) return 'success'
  if (result === INTERVIEW_RESULT.FAIL) return 'danger'
  if (result === INTERVIEW_RESULT.UNDECIDED) return 'warning'
  return 'info'
}

function statusTagType(status: number | null) {
  if (status === INTERVIEW_STATUS.PENDING) return 'warning'
  if (status === INTERVIEW_STATUS.FINISHED) return 'success'
  return 'info'
}

/** 员工响应标签色：接受=success，放弃=danger，未响应=info（「待响应」是要去催的状态） */
function employeeResponseTagType(response: number | null) {
  if (response === INTERVIEW_RESPONSE.ACCEPTED) return 'success'
  if (response === INTERVIEW_RESPONSE.DECLINED) return 'danger'
  return 'info'
}

/** 邀请邮件标签色：已发送=success，失败=danger，跳过=warning（多为未配置发信账号/员工无邮箱） */
function mailStatusTagType(status: number | null) {
  if (status === INVITE_MAIL_STATUS.SENT) return 'success'
  if (status === INVITE_MAIL_STATUS.FAILED) return 'danger'
  if (status === INVITE_MAIL_STATUS.SKIPPED) return 'warning'
  return 'info'
}

onMounted(refreshAll)
</script>

<template>
  <div class="page-shell">
    <section class="page-hero">
      <div>
        <span class="page-hero__eyebrow">Communication Interview</span>
        <h1 class="page-hero__title">视频终面</h1>
        <p class="page-hero__desc">
          由 HR 与员工进行一次真实视频沟通，作为入职前的最终确认。
          发起范围为下方全部匹配结果：匹配通过只影响排序与「推荐」标记，不是发起的前置条件。
          会议使用讯飞会议，将「复制邀请信息」的整段内容粘贴到表单即可，系统会自动识别其中的入会链接；
          平台不承载音视频，也不监测员工是否入会。
          发起后系统推送站内通知，并在邮箱可用时发送邀请邮件；员工可在本人能力画像页选择「接受」或「放弃」，
          终面是否完成以 HR 录入的结论为准。
        </p>
        <div class="page-hero__meta">
          <span class="hero-chip">待沟通：{{ pendingCount }} 次</span>
          <span class="hero-chip">可邀约匹配结果：{{ candidates.length }} 条（推荐 {{ recommendedCount }} 条）</span>
        </div>
      </div>
      <div class="mr__actions">
        <el-button :icon="Refresh" :loading="recordsLoading || candidatesLoading" @click="refreshAll">
          刷新
        </el-button>
      </div>
    </section>

    <!-- 候选池：HR 手选，平台不自动发起；匹配通过只作推荐排序 -->
    <section class="glass-card">
      <div class="panel-body">
        <div class="toolbar-panel" style="margin-bottom: 12px;">
          <div>
            <div style="font-weight: 600; margin-bottom: 4px;">可发起沟通（全部匹配结果）</div>
            <div style="font-size: 12px; opacity: 0.7;">
              匹配通过排在前并标「推荐」，但不是发起的前置条件；任意匹配结果都可直接发起。平台不会自动发起沟通。
            </div>
          </div>
          <div class="toolbar-group">
            <el-switch
              v-model="onlyRecommended"
              active-text="仅看推荐"
              inline-prompt
              style="--el-switch-on-color: var(--app-primary);"
            />
          </div>
        </div>
        <el-table :data="visibleCandidates" v-loading="candidatesLoading" size="small" style="width: 100%">
          <el-table-column label="员工 → 岗位" min-width="220">
            <template #default="{ row }">{{ candidateLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="推荐" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.recommended" type="success" effect="dark" round size="small">推荐</el-tag>
              <span v-else style="opacity: 0.5;">--</span>
            </template>
          </el-table-column>
          <el-table-column label="匹配状态" width="110">
            <template #default="{ row }">
              <el-tag :type="matchStatusTagType(row.matchStatus)" effect="plain" round size="small">
                {{ row.matchStatusName || '未评分' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="匹配分" width="100">
            <template #default="{ row }">{{ row.matchScore == null ? '--' : Number(row.matchScore).toFixed(1) }}</template>
          </el-table-column>
          <el-table-column label="终面进展" width="150">
            <template #default="{ row }">
              <el-tag v-if="row.hasPendingInterview" type="warning" effect="plain" round size="small">已有待沟通终面</el-tag>
              <el-tag v-else-if="row.latestResult" :type="resultTagType(row.latestResult)" effect="plain" round size="small">
                上次：{{ row.latestResult === 1 ? '通过' : row.latestResult === 2 ? '不通过' : '待定' }}
              </el-tag>
              <span v-else style="opacity: 0.6;">未发起</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="openLaunch(row)">发起视频终面</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="!candidatesLoading && visibleCandidates.length === 0"
          :description="onlyRecommended ? '暂无匹配通过的员工（可关闭「仅看推荐」查看全部）' : '暂无匹配结果，请先执行人岗匹配'"
        />
      </div>
    </section>

    <!-- 终面记录 -->
    <section class="glass-card">
      <div class="panel-body">
        <div class="toolbar-panel" style="margin-bottom: 12px;">
          <div style="font-weight: 600;">终面记录</div>
          <div class="toolbar-group">
            <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px;" @change="statusFilterChange">
              <el-option label="待沟通" :value="INTERVIEW_STATUS.PENDING" />
              <el-option label="已完成" :value="INTERVIEW_STATUS.FINISHED" />
              <el-option label="已取消" :value="INTERVIEW_STATUS.CANCELLED" />
            </el-select>
            <el-button :icon="Refresh" @click="statusFilterChange">查询</el-button>
          </div>
        </div>

        <el-table :data="records" v-loading="recordsLoading" size="small" style="width: 100%">
          <el-table-column label="员工 → 岗位" min-width="200">
            <template #default="{ row }">
              {{ row.empName || `员工#${row.empId}` }} → {{ row.postName || `岗位#${row.postId}` }}
            </template>
          </el-table-column>
          <el-table-column label="预约时间" width="170">
            <template #default="{ row }">{{ row.scheduledTime || '尽快' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" effect="plain" round size="small">
                {{ row.statusName || '--' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="员工响应" width="150">
            <template #default="{ row }">
              <el-tag :type="employeeResponseTagType(row.employeeResponse)" effect="plain" round size="small">
                {{ row.employeeResponseName || '待响应' }}
              </el-tag>
              <div v-if="row.employeeResponseComment" class="cell-sub">
                原因：{{ row.employeeResponseComment }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="邀请邮件" width="200">
            <template #default="{ row }">
              <!-- 失败/跳过时把原因挂在 tooltip 上：只给一个「发送失败」标签，
                   HR 无法判断该去改员工邮箱还是找管理员配发信账号。 -->
              <el-tooltip
                v-if="row.inviteMailError"
                :content="row.inviteMailError"
                placement="top"
                :show-after="200"
              >
                <span>
                  <el-tag :type="mailStatusTagType(row.inviteMailStatus)" effect="plain" round size="small">
                    {{ row.inviteMailStatusName || '未记录' }}
                  </el-tag>
                </span>
              </el-tooltip>
              <el-tag
                v-else
                :type="mailStatusTagType(row.inviteMailStatus)"
                effect="plain"
                round
                size="small"
              >
                {{ row.inviteMailStatusName || '未记录' }}
              </el-tag>
              <!-- 收件邮箱：让 HR 一眼看出这封邮件发给了谁，邮箱填错时不必再去翻人员档案 -->
              <div class="ci-mail-recipient" :title="row.employeeEmail || ''">
                {{ row.employeeEmail || '员工档案未维护邮箱' }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="结论" width="150">
            <template #default="{ row }">
              <el-tag v-if="row.result" :type="resultTagType(row.result)" effect="plain" round size="small">
                {{ row.resultName }}
              </el-tag>
              <span v-else style="opacity: 0.6;">--</span>
            </template>
          </el-table-column>
          <el-table-column label="HR 评价" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.comment || '--' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <!--
                会议按钮随终面状态变化，判据唯一来源 `./meeting-action.ts`（员工侧同源）：
                员工放弃 / 已取消 / 已录入「通过·不通过」结论后不再给按钮，改为占位说明；
                「待定」保留按钮但文案变「再次进入会议」；没有链接时按钮禁用并说明原因。
                直接隐藏而不留占位，HR 会以为界面漏渲染了。
              -->
              <template v-if="meetingAction(row).visible">
                <el-tooltip :content="meetingAction(row).reason" placement="top" :show-after="300">
                  <span>
                    <el-button
                      :type="meetingAction(row).tone"
                      link
                      :disabled="meetingAction(row).disabled"
                      @click="joinMeeting(row)"
                    >{{ meetingAction(row).label }}</el-button>
                  </span>
                </el-tooltip>
              </template>
              <span v-else class="cell-sub" :title="meetingAction(row).reason">
                {{ meetingAction(row).placeholder }}
              </span>
              <template v-if="row.status === INTERVIEW_STATUS.PENDING">
                <el-button type="success" link @click="openResult(row)">录入结论</el-button>
                <el-button type="danger" link @click="handleCancel(row)">取消</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!recordsLoading && records.length === 0" description="暂无终面记录" />
      </div>

      <div class="panel-footer">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="query.current"
          :page-size="query.size"
          :page-sizes="[10, 20, 50]"
          @current-change="(p: number) => { query.current = p; loadRecords() }"
          @size-change="(s: number) => { query.size = s; query.current = 1; loadRecords() }"
        />
      </div>
    </section>

    <!-- 发起弹窗：上「沟通要点」下「会议链接」 -->
    <el-dialog v-model="launchVisible" :title="launchTitle" width="760px" :close-on-click-modal="false">
      <!--
        沟通要点区：只在这里做异步 loading，**不遮罩下面的表单**。
        要点慢/失败都不影响填写链接与发起 —— 它只是沟通时的参考材料。
      -->
      <section class="briefing-block">
        <div class="briefing-block__head">
          <span class="briefing-block__title">沟通要点</span>
          <div class="briefing-block__actions">
            <el-tag v-if="briefingStatus === 'loading'" size="small" type="info" effect="plain">生成中…</el-tag>
            <el-tag v-else-if="briefingStatus === 'error'" size="small" type="danger" effect="plain">生成失败</el-tag>
            <el-button
              v-if="briefingStatus === 'error'"
              link
              type="primary"
              @click="loadBriefing"
            >重试</el-button>
            <el-button link type="primary" :disabled="briefingStatus !== 'ready'" @click="copyBriefing">
              复制纯文本
            </el-button>
          </div>
        </div>

        <el-skeleton v-if="briefingStatus === 'loading'" :rows="4" animated />

        <el-alert
          v-else-if="briefingStatus === 'error'"
          type="warning"
          show-icon
          :closable="false"
          :title="briefingError"
          description="沟通要点只是沟通时的参考材料，不影响发起。你可以直接填写会议链接并提交，或点右上角「重试」。"
        />

        <template v-else-if="briefingStatus === 'ready' && briefing">
          <el-descriptions :column="1" border size="small" style="margin-bottom: 12px;">
            <el-descriptions-item label="一、能力概况">
              <div>{{ briefing.ability.summary || '暂无报告' }}</div>
              <div class="briefing-block__sub">
                harness 自动通过 {{ briefing.ability.autoPassedCount }} 项 ·
                人工确认通过 {{ briefing.ability.manualConfirmedCount }} 项 ·
                人工拒绝 {{ briefing.ability.manualRejectedCount }} 项
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="二、匹配概况">
              匹配分 {{ briefing.match.finalMatchScore ?? '--' }} ·
              状态 {{ briefing.match.matchStatusName || '--' }}
            </el-descriptions-item>
            <el-descriptions-item label="三、差距与学习">
              <div>
                差距项 {{ briefing.gapAndLearning.gapCount }} 项
                <span v-if="briefing.gapAndLearning.gapAbilities.length">
                  ：{{ briefing.gapAndLearning.gapAbilities.join('、') }}
                </span>
              </div>
              <div class="briefing-block__sub">
                学习成果：已通过 {{ briefing.gapAndLearning.approvedOutcomeCount }} 项 ·
                待复核 {{ briefing.gapAndLearning.pendingOutcomeCount }} 项 ·
                被驳回 {{ briefing.gapAndLearning.rejectedOutcomeCount }} 项
              </div>
            </el-descriptions-item>
          </el-descriptions>

          <div class="briefing-block__title" style="margin-bottom: 6px;">四、建议沟通要点</div>
          <ul class="briefing-points">
            <li v-for="(point, idx) in briefing.talkingPoints" :key="idx">{{ point }}</li>
          </ul>
        </template>
      </section>

      <el-divider />

      <!-- 发起失败的常驻提示：留在弹窗内，附可读原因，避免 HR 关掉重来一遍 -->
      <el-alert
        v-if="launchError"
        type="error"
        show-icon
        :closable="false"
        :title="launchError"
        style="margin-bottom: 12px;"
      />

      <el-alert
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 12px;"
        title="发起后系统会做什么"
        description="① 向员工推送站内通知；② 员工档案邮箱可用时，另发一封邀请邮件（QQ 邮箱）；③ 员工在本人能力画像页「接受」或「放弃」，其响应会回到你的通知里。邀请邮件是否发出、发给了哪个邮箱，以下方「终面记录」的「邀请邮件」列为准。"
      />

      <el-form ref="launchFormRef" :model="launchForm" :rules="launchRules" label-width="90px">
        <!-- 用多行文本框：讯飞会议「复制邀请信息」出来就是一整段多行文本，
             单行 input 会把换行吃掉、现场看不到粘贴了什么，容易让 HR 以为没粘上。 -->
        <el-form-item label="会议邀请" prop="meetingUrl">
          <el-input
            v-model="launchForm.meetingUrl"
            type="textarea"
            :rows="4"
            resize="vertical"
            placeholder="在讯飞会议点「复制邀请信息」，把整段内容直接粘到这里即可（会自动识别其中的入会链接）"
            @input="launchError = ''"
          />
          <div class="form-hint">
            支持两种输入：① 讯飞会议「复制邀请信息」给出的整段多行文本，会议号、密码、下载地址一起粘进来也没关系，系统只取其中的入会链接；
            ② 直接粘贴完整入会链接。若提示链接不可用，说明该地址不在系统认可的会议域名内（默认 meeting.iflyrec.com），改用「复制邀请信息」原文即可。
          </div>
        </el-form-item>
        <el-form-item label="预约时间">
          <el-date-picker
            v-model="launchForm.scheduledTime"
            type="datetime"
            placeholder="不填表示尽快沟通"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%;"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="launchVisible = false">取消</el-button>
        <el-button type="primary" :icon="VideoCamera" :loading="launching" @click="submitLaunch">
          发起并通知员工
        </el-button>
      </template>
    </el-dialog>

    <!-- 录入结论 -->
    <el-dialog v-model="resultVisible" title="录入视频沟通结论" width="520px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="结论" required>
          <el-radio-group v-model="resultForm.result">
            <el-radio :value="INTERVIEW_RESULT.PASS">通过（人工沟通确认）</el-radio>
            <el-radio :value="INTERVIEW_RESULT.FAIL">不通过</el-radio>
            <el-radio :value="INTERVIEW_RESULT.UNDECIDED">待定</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="评价">
          <el-input
            v-model="resultForm.comment"
            type="textarea"
            :rows="4"
            placeholder="沟通纪要与评价。员工可查看原文（不做脱敏）。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resultVisible = false">取消</el-button>
        <el-button type="primary" :loading="resultSaving" @click="submitResult">保存结论</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 沟通要点区：独立承载异步加载态，不遮罩下方表单 */
.briefing-block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.briefing-block__title {
  font-weight: 600;
  color: var(--app-text-primary, #1f2937);
}
.briefing-block__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.briefing-block__sub {
  margin-top: 4px;
  font-size: 12px;
  opacity: 0.75;
}
.briefing-points {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.9;
}
/* 输入框下方的辅助说明独占一行，避免和输入框挤在同一行 */
:deep(.el-form-item__content) {
  flex-wrap: wrap;
}
.form-hint {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-muted, #94a3b8);
}
/* 表格单元格内的次级说明（如员工放弃终面的原因） */
.cell-sub {
  margin-top: 4px;
  color: var(--app-text-muted, #94a3b8);
  font-size: 11px;
  line-height: 1.5;
  white-space: normal;
  word-break: break-word;
}

/* 邀请邮件的收件邮箱：单行截断 + 原生 title 兜底完整地址。
   不用 el-table 的 show-overflow-tooltip —— 这一列里还有一个状态标签，整体截断会把标签一起吃掉。 */
.ci-mail-recipient {
  margin-top: 3px;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--app-text-muted, #94a3b8);
  font-size: 11px;
  line-height: 1.5;
}
</style>
