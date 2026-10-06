<script setup lang="ts">
/**
 * 岗位趋势发现（权威材料 → 岗位建议）。
 *
 * 全流程只有两次人工动作：上传材料、审核候选。中间没有需要手填的表单——
 * 岗位名、职责、能力清单、等级、权重全部由解析产出，管理员只在「明显偏离材料原意」时才调整。
 *
 * 页面分三层，默认只露第一层（避免一屏倾倒巨量信息）：
 *   1. 上传区：一行搞定（拖拽 + 材料类别 + 开始解析）；已上传材料以 chip 列出，可逐个移除；
 *   2. 解析任务条：仅在任务在跑或刚跑完时出现，含分步进度、取消、诊断；
 *   3. 结果区：两个 Tab（新岗位候选 / 能力变更候选），卡片只放「一眼判断要不要点进去」的字段，
 *      其余全部收进右侧抽屉（全量查看 + 展开调整 + 落地）。
 *
 * 与「市场 JD 统计」的关系：那一套是纯统计 PMI 共现、零 LLM，保留为独立视图；
 * 本页是 LLM+RAG 主链路，后端前缀 /api/post/trend/**，权限 POST:MANAGE。
 */
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, MagicStick, Refresh, Select, Upload } from '@element-plus/icons-vue'
import type { UploadRequestOptions } from 'element-plus'
import {
  MATERIAL_CATEGORIES,
  batchConfirmTrendCandidates,
  cancelTrendTask,
  confirmTrendCandidate,
  createTrendTask,
  getTrendProgress,
  getTrendTask,
  pageTrendCandidates,
  pageTrendTasks,
  uploadAuthorityMaterial,
} from '@/api/post-trend'
import type {
  AuthorityMaterial,
  CandidateType,
  ConfirmStatus,
  TrendBatchConfirmResult,
  TrendCandidateSummary,
  TrendProgressVO,
  TrendTaskVO,
} from '@/api/post-trend'
import { resolveApiErrorMessage } from '@/utils/request-error-message'
import CandidateDrawer from './candidate-drawer.vue'
import MaterialLibrary from './material-library.vue'
import {
  batchConfirmBlockReason,
  candidateTypeLabel,
  confirmStatusBadge,
  coverageBadge,
  describeBatchResult,
  emphasisText,
  harnessBadge,
  isTaskAwaitingConfirm,
  isTaskRunning,
  selectableForBatch,
  similarityText,
} from './trend-candidate-meta'

const POLL_INTERVAL_MS = 2500
const PAGE_SIZE = 12

// ==================== 第一层：上传区 ====================

interface UploadedMaterial {
  documentId: number
  title: string
  sourceCategory: string
  chunkCount: number
}

const materials = ref<UploadedMaterial[]>([])
const uploadCategory = ref<string>('POLICY_DOCUMENT')
/**
 * 仅试算模式：只想看看这份材料能解析出什么岗位，材料不长期保留。
 * 这类材料不进材料库、不会被后续正式上传复用，任务结束后由后端自动清理。
 */
const ephemeralMode = ref(false)
const uploading = ref(false)
/** 常驻错误：上传失败要说清哪一份文件、为什么失败 */
const uploadError = ref('')
/** 上传结果里的提示语（例如「已复用原有索引」）—— 用常驻提示而不是一闪而过的 toast */
const uploadNotice = ref('')
const materialLibrary = ref<InstanceType<typeof MaterialLibrary> | null>(null)

const categoryLabel = (value: string) =>
  MATERIAL_CATEGORIES.find(item => item.value === value)?.label ?? value

const sourceDocumentIds = computed(() => materials.value.map(item => item.documentId))
const sourceCategories = computed(() => Array.from(new Set(materials.value.map(item => item.sourceCategory))))

async function handleUpload(options: UploadRequestOptions) {
  const file = options.file as File
  uploading.value = true
  uploadError.value = ''
  uploadNotice.value = ''
  try {
    const res = await uploadAuthorityMaterial(file, {
      // 标题取文件名去掉扩展名：管理员不用为了上传再想一个标题
      title: file.name.replace(/\.[^.]+$/, ''),
      sourceCategory: uploadCategory.value,
      ephemeral: ephemeralMode.value,
    })
    const data = res.data
    if (!data) {
      uploadError.value = `「${file.name}」上传后未返回材料编号，请重试`
      return
    }
    if (data.chunkCount === 0) {
      // 解析阶段依赖文本切片；无切片一定是扫描件或纯图片 PDF，必须当场说清
      uploadError.value = `「${file.name}」未能提取到任何文本片段：扫描件或纯图片 PDF 没有文本层，需要先做 OCR 再上传`
      return
    }
    materials.value = [
      ...materials.value.filter(item => item.documentId !== data.documentId),
      {
        documentId: data.documentId,
        title: data.title || file.name,
        sourceCategory: data.sourceCategory || uploadCategory.value,
        chunkCount: data.chunkCount,
      },
    ]
    uploadNotice.value = data.message
      ?? `「${data.title || file.name}」已上传并索引 ${data.chunkCount} 个片段`
    // 命中重复时后端没有新建材料，只复用了原有索引 —— 用 info 而不是 success，避免让人以为又传了一份
    if (data.reused) {
      ElMessage.info(uploadNotice.value)
    } else {
      ElMessage.success(uploadNotice.value)
    }
    // 材料库与新上传的材料同源，上传后立刻刷新，否则要手动点刷新才能看到
    void materialLibrary.value?.reload()
  } catch (error) {
    uploadError.value = `「${file.name}」上传失败：${resolveApiErrorMessage(error, '请检查文件格式与大小')}`
    /*
     * 索引失败时材料**已经入库**了（后端先落库、再单独建索引），报错文案里也提示了
     * 「请稍后在材料管理中重试索引」。所以这里必须把材料库刷新出来，
     * 否则用户读完提示去找材料，列表里看不到 —— 提示与可操作的入口就断了。
     * 校验类失败（格式不对）库里不会多东西，刷新是无害的空操作。
     */
    void materialLibrary.value?.reload()
  } finally {
    uploading.value = false
  }
}

function removeMaterial(documentId: number) {
  materials.value = materials.value.filter(item => item.documentId !== documentId)
}

/**
 * 从材料库挑一份已有材料加入本轮解析。
 * 只进待提交清单、不动库里的材料：用户可能只是再解析一次，不该把库里的东西藏起来。
 */
function handlePickMaterial(material: AuthorityMaterial) {
  uploadError.value = ''
  if (materials.value.some(item => item.documentId === material.documentId)) {
    uploadNotice.value = `「${material.title}」已在本轮待解析清单里，无需重复加入`
    return
  }
  materials.value = [
    ...materials.value,
    {
      documentId: material.documentId,
      title: material.title,
      sourceCategory: material.sourceCategory,
      chunkCount: material.chunkCount,
    },
  ]
  uploadNotice.value = `已把「${material.title}」加入本轮解析（${material.chunkCount} 个片段）`
  ElMessage.success(uploadNotice.value)
}

// ==================== 任务 ====================

const creating = ref(false)
const createError = ref('')
const activeTask = ref<TrendTaskVO | null>(null)
const progress = ref<TrendProgressVO | null>(null)
const showDiagnostics = ref(false)

let pollTimer: ReturnType<typeof setTimeout> | null = null

function stopPoll() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

const running = computed(() => isTaskRunning(activeTask.value?.taskStatus))
const awaitingConfirm = computed(() => isTaskAwaitingConfirm(activeTask.value?.taskStatus))
const diagnosticsText = computed(
  () => progress.value?.diagnostics || activeTask.value?.diagnostics || '',
)
const failureText = computed(() => progress.value?.errorMessage || activeTask.value?.errorMessage || '')

async function handleCreateTask() {
  createError.value = ''
  if (!sourceDocumentIds.value.length) {
    createError.value = '请先上传至少一份权威材料'
    return
  }
  creating.value = true
  try {
    const res = await createTrendTask({
      sourceDocumentIds: sourceDocumentIds.value,
      sourceCategories: sourceCategories.value,
    })
    const task = res.data
    if (!task) {
      createError.value = '任务创建后未返回任务信息，请在历史任务中确认是否已发起'
      return
    }
    activeTask.value = task
    selectedTaskId.value = task.id
    progress.value = null
    showDiagnostics.value = false
    // 材料已进入本次解析，清空待上传清单避免下次重复提交
    materials.value = []
    resetCandidateState()
    // 刷新历史下拉，让刚发起的任务立刻可选（否则要刷新整页才出现在列表里）
    await loadHistory()
    ElMessage.success('解析任务已发起，进度会自动刷新')
    schedulePoll()
  } catch (error) {
    createError.value = resolveApiErrorMessage(error, '发起解析失败')
  } finally {
    creating.value = false
  }
}

async function refreshProgress(taskId: number) {
  try {
    const res = await getTrendProgress(taskId)
    progress.value = res.data ?? null
    if (progress.value?.taskStatus && activeTask.value) {
      activeTask.value = { ...activeTask.value, taskStatus: progress.value.taskStatus }
    }
  } catch (error) {
    stopPoll()
    createError.value = resolveApiErrorMessage(error, '刷新任务进度失败')
  }
}

function schedulePoll() {
  stopPoll()
  pollTimer = setTimeout(async () => {
    const taskId = activeTask.value?.id
    if (!taskId) return
    await refreshProgress(taskId)
    if (isTaskRunning(activeTask.value?.taskStatus)) {
      schedulePoll()
      return
    }
    // 进入终态：补齐任务汇总（候选计数、诊断）并加载首批候选
    await loadTask(taskId)
    await loadCandidates()
  }, POLL_INTERVAL_MS)
}

/** 拉取任务汇总；用于加载历史任务与任务终态后的计数刷新 */
async function loadTask(taskId: number) {
  try {
    const res = await getTrendTask(taskId)
    if (res.data) activeTask.value = res.data
  } catch (error) {
    createError.value = resolveApiErrorMessage(error, '加载任务信息失败')
  }
}

async function handleCancel() {
  const taskId = activeTask.value?.id
  if (!taskId) return
  try {
    const res = await cancelTrendTask(taskId)
    if (res.data) {
      ElMessage.success('已请求取消，任务不会覆盖已有结论')
      stopPoll()
      await refreshProgress(taskId)
      await loadTask(taskId)
    } else {
      ElMessage.info('任务已进入终态，取消请求未生效')
      await loadTask(taskId)
    }
  } catch (error) {
    createError.value = resolveApiErrorMessage(error, '取消失败')
  }
}

// ---- 历史任务 ----

const historyTasks = ref<TrendTaskVO[]>([])
const selectedTaskId = ref<number | null>(null)

async function loadHistory() {
  try {
    const res = await pageTrendTasks({ current: 1, size: 20 })
    historyTasks.value = res.data?.records ?? []
  } catch {
    // 历史任务是「附加能力」，失败不阻断主流程（上传与解析仍可用）
    historyTasks.value = []
  }
}

/** 从历史任务下拉里选中一个任务：清空当前结果后按其 taskId 重新拉取候选 */
async function handleSelectHistory(taskId: number | null) {
  if (!taskId) {
    // 清空选择时不切走当前任务，避免把界面变成「无任务」的半空态
    selectedTaskId.value = activeTask.value?.id ?? null
    return
  }
  const task = historyTasks.value.find(item => item.id === taskId)
  if (!task) return
  activeTask.value = task
  progress.value = null
  showDiagnostics.value = false
  createError.value = ''
  resetCandidateState()
  stopPoll()
  await loadTask(taskId)
  await loadCandidates()
  if (isTaskRunning(activeTask.value?.taskStatus)) schedulePoll()
}

// ==================== 第三层：候选区 ====================

const activeTab = ref<CandidateType>('NEW_POST')
const confirmFilter = ref<ConfirmStatus | ''>('PENDING')
const candidates = ref<TrendCandidateSummary[]>([])
const candidateLoading = ref(false)
const candidateError = ref('')
const total = ref(0)
const pager = reactive({ current: 1 })

const selectedIds = ref<number[]>([])
const batchLoading = ref(false)
const batchResult = ref<TrendBatchConfirmResult | null>(null)
const singleLandingId = ref<number | null>(null)

const drawerVisible = ref(false)
const drawerCandidateId = ref<number | null>(null)

const newPostCount = computed(() => activeTask.value?.newPostCount ?? 0)
const changeCount = computed(() => activeTask.value?.changeCount ?? 0)

const batchableIds = computed(() => selectableForBatch(candidates.value))
const allBatchableSelected = computed(
  () => batchableIds.value.length > 0 && batchableIds.value.every(id => selectedIds.value.includes(id)),
)
const hasSelection = computed(() => selectedIds.value.length > 0)

function resetCandidateState() {
  candidates.value = []
  total.value = 0
  pager.current = 1
  selectedIds.value = []
  batchResult.value = null
  candidateError.value = ''
}

async function loadCandidates() {
  const taskId = activeTask.value?.id
  if (!taskId) return
  candidateLoading.value = true
  candidateError.value = ''
  try {
    const res = await pageTrendCandidates({
      taskId,
      current: pager.current,
      size: PAGE_SIZE,
      candidateType: activeTab.value,
      confirmStatus: confirmFilter.value || '',
    })
    candidates.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
    // 翻页/换 Tab 后旧的选择已不在当前列表里，必须丢弃，否则批量确认会带上看不见的项
    selectedIds.value = selectedIds.value.filter(id => candidates.value.some(item => item.id === id))
  } catch (error) {
    candidates.value = []
    total.value = 0
    candidateError.value = resolveApiErrorMessage(error, '加载候选列表失败')
  } finally {
    candidateLoading.value = false
  }
}

function handleTabChange() {
  pager.current = 1
  selectedIds.value = []
  batchResult.value = null
  void loadCandidates()
}

function handleFilterChange() {
  pager.current = 1
  void loadCandidates()
}

function handlePageChange(page: number) {
  pager.current = page
  void loadCandidates()
}

function toggleSelect(candidate: TrendCandidateSummary, checked: boolean) {
  if (checked) {
    if (!selectedIds.value.includes(candidate.id)) selectedIds.value = [...selectedIds.value, candidate.id]
  } else {
    selectedIds.value = selectedIds.value.filter(id => id !== candidate.id)
  }
}

function toggleSelectAll(checked: boolean) {
  selectedIds.value = checked ? [...batchableIds.value] : []
}

/** 不可批量的原因：直接显示在按钮 title 上，避免「点了却被跳过」 */
function blockReasonOf(candidate: TrendCandidateSummary): string {
  return batchConfirmBlockReason(candidate) ?? ''
}

function openDrawer(candidate: TrendCandidateSummary) {
  drawerCandidateId.value = candidate.id
  drawerVisible.value = true
}

async function handleBatchConfirm() {
  if (!hasSelection.value) return
  batchLoading.value = true
  batchResult.value = null
  try {
    const res = await batchConfirmTrendCandidates(selectedIds.value)
    const result = res.data
    if (result) {
      batchResult.value = result
      ElMessage.success(describeBatchResult(result))
    }
    selectedIds.value = []
    await loadCandidates()
    if (activeTask.value) await loadTask(activeTask.value.id)
  } catch (error) {
    candidateError.value = resolveApiErrorMessage(error, '批量确认失败')
  } finally {
    batchLoading.value = false
  }
}

/** 卡片上的快捷落地：与批量确认同一道闸门，非 PASS 一律禁用 */
async function handleQuickLand(candidate: TrendCandidateSummary) {
  const blockReason = blockReasonOf(candidate)
  if (blockReason) return
  const action = candidate.candidateType === 'NEW_POST' ? '创建岗位' : '更新该岗位能力要求'
  try {
    await ElMessageBox.confirm(
      `确认${action}「${candidate.postName}」并写入 ${candidate.abilityCount} 项能力要求？`,
      candidate.candidateType === 'NEW_POST' ? '确认创建岗位' : '确认更新能力',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }
  singleLandingId.value = candidate.id
  candidateError.value = ''
  try {
    const res = await confirmTrendCandidate(candidate.id)
    if (res.data) {
      ElMessage.success(
        res.data.createdNewPost
          ? `已创建岗位「${res.data.postName}」`
          : `已更新「${res.data.postName}」的能力要求`,
      )
    }
    await loadCandidates()
    if (activeTask.value) await loadTask(activeTask.value.id)
  } catch (error) {
    candidateError.value = resolveApiErrorMessage(error, '落地失败')
  } finally {
    singleLandingId.value = null
  }
}

async function handleLanded() {
  drawerVisible.value = false
  await loadCandidates()
  if (activeTask.value) await loadTask(activeTask.value.id)
  await loadHistory()
}

/** 空态必须说清原因（与后端「无候选必须带诊断」同一取向），不能只留一个「暂无数据」 */
function emptyText(): string {
  if (isTaskRunning(activeTask.value?.taskStatus)) return '解析进行中，进度自动刷新，稍后候选会出现在这里'
  if (!activeTask.value) return '先上传权威材料并发起一次解析，结果会出现在这里'
  const typeLabel = candidateTypeLabel(activeTab.value)
  if (confirmFilter.value === 'PENDING') {
    return `本次解析没有待确认的${typeLabel}，可切换右上角状态筛选查看已处理项`
  }
  return `没有符合条件的${typeLabel}`
}

// ==================== 生命周期 ====================

onMounted(async () => {
  await loadHistory()
})

onUnmounted(stopPoll)
</script>

<template>
  <div class="page-shell td-page">
    <section class="page-hero">
      <div>
        <div class="page-hero__eyebrow">Authority Material → Post Trend</div>
        <h1 class="page-hero__title">岗位趋势发现</h1>
        <p class="page-hero__desc">
          上传政府红头文件、政策文件、市场职业报告等权威材料，系统自动解析出趋势岗位与既有岗位的能力变更建议。
          两类候选都必须由岗位管理员审核后才会落地，不存在自动建岗路径。
        </p>
        <div class="hero-chip-row">
          <span class="hero-chip">权限：POST:MANAGE</span>
          <span class="hero-chip">材料 {{ materials.length }} 份</span>
          <span v-if="activeTask" class="hero-chip">当前任务：{{ activeTask.taskCode || activeTask.id }}</span>
        </div>
      </div>
      <div class="toolbar-group">
        <el-select
          v-model="selectedTaskId"
          class="td-history"
          placeholder="历史解析任务"
          clearable
          filterable
          @change="handleSelectHistory"
        >
          <el-option
            v-for="task in historyTasks"
            :key="task.id"
            :value="task.id"
            :label="`${task.taskName || task.taskCode || task.id} · ${task.candidateCount ?? 0} 个候选`"
          />
        </el-select>
        <el-button :loading="candidateLoading" @click="loadHistory(); loadCandidates()">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </section>

    <!-- ==================== 第一层：上传区 ==================== -->
    <section class="glass-card td-card">
      <div class="td-card__head">
        <div>
          <div class="section-title">上传权威材料</div>
          <div class="section-desc">
            上传即自动完成索引，无需手填任何岗位信息。材料类别决定「跨来源印证」——<strong>同一岗位被两类材料同时提到</strong>才说明趋势成立，所以请如实选择。
          </div>
        </div>
      </div>

      <div class="td-card__body">
        <div class="td-upload-row">
          <el-upload
            class="td-upload"
            drag
            multiple
            :show-file-list="false"
            :http-request="handleUpload"
            accept=".pdf,.doc,.docx,.txt,.md,.csv"
          >
            <el-icon class="td-upload__icon"><Upload /></el-icon>
            <div class="td-upload__text">
              将文件拖到此处，或<em>点击选择</em>
            </div>
            <div class="td-upload__hint">支持 PDF / Word / TXT，可一次选多份；扫描件需先 OCR</div>
          </el-upload>

          <div class="td-upload-side">
            <div class="td-field">
              <label>材料类别</label>
              <el-select v-model="uploadCategory" class="td-category">
                <el-option
                  v-for="item in MATERIAL_CATEGORIES"
                  :key="item.value"
                  :value="item.value"
                  :label="item.label"
                >
                  <span>{{ item.label }}</span>
                  <span class="td-option-hint">{{ item.hint }}</span>
                </el-option>
              </el-select>
            </div>
            <div class="td-field">
              <label>留存方式</label>
              <el-radio-group v-model="ephemeralMode" class="td-retention">
                <el-radio-button :value="false">存入材料库</el-radio-button>
                <el-radio-button :value="true">仅试算</el-radio-button>
              </el-radio-group>
              <div class="td-hint">
                <template v-if="ephemeralMode">
                  只想看看这份材料能解析出什么岗位时选它：<strong>不进材料库</strong>，也不会被后续上传复用，解析结束后由系统自动清理。
                </template>
                <template v-else>
                  材料会长期保留在材料库，可反复用于后续解析；<strong>同一份内容重复上传不会重复建索引</strong>。
                </template>
              </div>
            </div>
          </div>
        </div>

        <el-alert
          v-if="uploadError"
          class="td-alert"
          type="error"
          :closable="false"
          show-icon
          :title="uploadError"
        />

        <el-alert
          v-if="uploadNotice && !uploadError"
          class="td-alert"
          type="success"
          :closable="false"
          show-icon
          :title="uploadNotice"
        />

        <div v-if="materials.length" class="td-materials">
          <el-tag
            v-for="item in materials"
            :key="item.documentId"
            class="td-material"
            :closable="!running"
            size="large"
            effect="plain"
            @close="removeMaterial(item.documentId)"
          >
            <el-icon><Document /></el-icon>
            {{ item.title }}
            <span class="td-material__meta">{{ categoryLabel(item.sourceCategory) }} · {{ item.chunkCount }} 片段</span>
          </el-tag>
        </div>
        <el-empty v-else description="还没有材料：先上传一份政府红头文件或市场报告，也可以从下方材料库里挑一份" :image-size="64" />

        <el-alert
          v-if="createError"
          class="td-alert"
          type="error"
          :closable="false"
          show-icon
          :title="createError"
        />

        <div class="td-actions">
          <el-button
            type="primary"
            :loading="creating"
            :disabled="!materials.length || running"
            @click="handleCreateTask"
          >
            <el-icon><MagicStick /></el-icon>
            开始解析（{{ materials.length }} 份材料）
          </el-button>
          <span class="td-hint">解析在后台执行，可随时离开页面；完成后在下方候选区审核。</span>
        </div>
      </div>
    </section>

    <!-- ==================== 材料库：已上传材料的管理 ==================== -->
    <MaterialLibrary
      ref="materialLibrary"
      :running="running"
      :pending-ids="sourceDocumentIds"
      @pick="handlePickMaterial"
    />

    <!-- ==================== 第二层：解析任务条 ==================== -->
    <section v-if="activeTask" class="glass-card td-card td-taskbar">
      <div class="td-taskbar__main">
        <div class="td-taskbar__left">
          <el-tag
            :type="running ? 'warning' : failureText ? 'danger' : 'success'"
            effect="light"
          >
            {{ running ? '解析中' : failureText ? '解析失败' : '解析结束' }}
          </el-tag>
          <div class="td-taskbar__title">
            {{ activeTask.taskName || activeTask.taskCode || `任务 #${activeTask.id}` }}
          </div>
          <span class="td-taskbar__meta">
            候选 {{ activeTask.candidateCount ?? 0 }} 个 · 新岗位 {{ newPostCount }} · 能力变更 {{ changeCount }}
          </span>
        </div>
        <div class="toolbar-group">
          <el-button v-if="diagnosticsText" size="small" @click="showDiagnostics = !showDiagnostics">
            {{ showDiagnostics ? '收起诊断' : '查看诊断' }}
          </el-button>
          <el-button v-if="running" size="small" @click="handleCancel">取消解析</el-button>
        </div>
      </div>

      <el-progress
        v-if="running"
        class="td-taskbar__progress"
        :percentage="Math.min(100, Math.max(0, progress?.percent ?? activeTask.progressPercent ?? 0))"
        :stroke-width="10"
      />
      <div v-if="running && progress?.currentStep" class="td-hint">{{ progress.currentStep }}</div>

      <el-alert
        v-if="failureText"
        class="td-alert"
        type="error"
        :closable="false"
        show-icon
        title="解析失败"
      >
        <template #default>
          <div class="td-alert__reason">{{ failureText }}</div>
        </template>
      </el-alert>

      <el-alert
        v-if="showDiagnostics && diagnosticsText"
        class="td-alert"
        type="info"
        :closable="false"
        show-icon
        title="解析诊断"
      >
        <template #default>
          <div class="td-alert__reason">{{ diagnosticsText }}</div>
        </template>
      </el-alert>
    </section>

    <!-- ==================== 第三层：结果区 ==================== -->
    <section class="glass-card td-card">
      <div class="td-card__head td-card__head--result">
        <div class="td-tabs">
          <button
            type="button"
            class="td-tab"
            :class="{ 'td-tab--active': activeTab === 'NEW_POST' }"
            @click="activeTab = 'NEW_POST'; handleTabChange()"
          >
            新岗位候选
            <span class="td-tab__count">{{ newPostCount }}</span>
          </button>
          <button
            type="button"
            class="td-tab"
            :class="{ 'td-tab--active': activeTab === 'ABILITY_CHANGE' }"
            @click="activeTab = 'ABILITY_CHANGE'; handleTabChange()"
          >
            能力变更候选
            <span class="td-tab__count">{{ changeCount }}</span>
          </button>
        </div>
        <div class="toolbar-group">
          <el-select v-model="confirmFilter" class="td-filter" @change="handleFilterChange">
            <el-option value="PENDING" label="待确认" />
            <el-option value="APPROVED" label="已通过" />
            <el-option value="REJECTED" label="已驳回" />
            <el-option value="" label="全部状态" />
          </el-select>
          <el-button
            :disabled="!batchableIds.length || !activeTask"
            @click="toggleSelectAll(!allBatchableSelected)"
          >
            <el-icon><Select /></el-icon>
            {{ allBatchableSelected ? '取消全选' : `全选可确认（${batchableIds.length}）` }}
          </el-button>
          <el-button
            type="primary"
            :loading="batchLoading"
            :disabled="!hasSelection"
            @click="handleBatchConfirm"
          >
            批量确认落地（{{ selectedIds.length }}）
          </el-button>
        </div>
      </div>

      <div class="td-card__body">
        <el-alert
          v-if="batchResult"
          class="td-alert"
          :type="batchResult.skipped?.length ? 'warning' : 'success'"
          :closable="true"
          show-icon
          :title="describeBatchResult(batchResult)"
          @close="batchResult = null"
        >
          <template #default>
            <div v-if="batchResult.skipped?.length" class="td-skip-list">
              <div v-for="item in batchResult.skipped" :key="item.candidateId" class="td-alert__reason">
                「{{ item.postName || `候选 #${item.candidateId}` }}」被跳过：{{ item.reason }}
              </div>
            </div>
          </template>
        </el-alert>

        <el-alert
          v-if="candidateError"
          class="td-alert"
          type="error"
          :closable="false"
          show-icon
          :title="candidateError"
        />

        <div v-loading="candidateLoading" class="td-list-wrap">
          <el-empty v-if="!candidates.length && !candidateLoading" :description="emptyText()" :image-size="80" />

          <div v-else class="td-grid">
            <article v-for="item in candidates" :key="item.id" class="td-item">
              <header class="td-item__head">
                <el-checkbox
                  class="td-item__check"
                  :model-value="selectedIds.includes(item.id)"
                  :disabled="Boolean(blockReasonOf(item))"
                  :title="blockReasonOf(item)"
                  @change="(checked: boolean) => toggleSelect(item, checked)"
                />
                <div class="td-item__title-wrap">
                  <div class="td-item__title" :title="item.postName">{{ item.postName }}</div>
                  <div class="td-item__sub">
                    <span v-if="item.candidateType === 'ABILITY_CHANGE'">
                      锚定岗位：{{ item.matchedPostName || '—' }}
                    </span>
                    <span v-else>系统内无相似岗位</span>
                  </div>
                </div>
                <div class="td-item__similar">
                  <span class="td-item__similar-label">相似度</span>
                  <strong>{{ similarityText(item.similarityScore) }}</strong>
                </div>
              </header>

              <div class="td-item__badges">
                <el-tag size="small" :type="harnessBadge(item.harnessDecision).type" effect="light">
                  {{ harnessBadge(item.harnessDecision).label }}
                </el-tag>
                <el-tag
                  v-if="coverageBadge(item.sourceCoverage)"
                  size="small"
                  type="success"
                  effect="plain"
                >
                  {{ coverageBadge(item.sourceCoverage) }}
                </el-tag>
                <el-tag
                  v-if="item.confirmStatus !== 'PENDING'"
                  size="small"
                  :type="confirmStatusBadge(item.confirmStatus).type"
                  effect="light"
                >
                  {{ confirmStatusBadge(item.confirmStatus).label }}
                </el-tag>
                <span v-if="item.unresolvedAbilityCount" class="td-item__flag">
                  {{ item.unresolvedAbilityCount }} 项能力待归位
                </span>
              </div>

              <div class="td-item__abilities">
                <span v-for="name in item.previewAbilities" :key="name" class="td-chip">{{ name }}</span>
                <span
                  v-if="item.abilityCount > item.previewAbilities.length"
                  class="td-chip td-chip--more"
                >
                  +{{ item.abilityCount - item.previewAbilities.length }}
                </span>
              </div>

              <footer class="td-item__foot">
                <span class="td-item__emphasis">材料强调度 {{ emphasisText(item.emphasisScore) }}</span>
                <div class="td-item__actions">
                  <el-button size="small" @click="openDrawer(item)">展开调整</el-button>
                  <el-button
                    v-if="item.confirmStatus === 'PENDING'"
                    size="small"
                    type="primary"
                    :loading="singleLandingId === item.id"
                    :disabled="Boolean(blockReasonOf(item))"
                    :title="blockReasonOf(item)"
                    @click="handleQuickLand(item)"
                  >
                    {{ item.candidateType === 'NEW_POST' ? '确认创建' : '确认变更' }}
                  </el-button>
                  <el-tag v-else size="small" effect="plain">已处理</el-tag>
                </div>
              </footer>
            </article>
          </div>
        </div>

        <div v-if="total > PAGE_SIZE" class="td-pagination">
          <el-pagination
            :current-page="pager.current"
            :page-size="PAGE_SIZE"
            :total="total"
            layout="total, prev, pager, next"
            background
            @current-change="handlePageChange"
          />
        </div>

        <div v-if="awaitingConfirm && !total && diagnosticsText" class="td-hint">
          本次任务暂无可展示候选，原因见上方「查看诊断」。
        </div>
      </div>
    </section>

    <CandidateDrawer
      v-model="drawerVisible"
      :candidate-id="drawerCandidateId"
      @landed="handleLanded"
    />
  </div>
</template>

<style scoped>
.td-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.td-history {
  width: 240px;
}

.td-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 18px 22px 0;
}

.td-card__head--result {
  align-items: center;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--app-border);
}

.td-card__body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 22px 22px;
}

/* ---------- 上传 ---------- */

.td-upload-row {
  display: grid;
  grid-template-columns: minmax(280px, 1.4fr) minmax(220px, 1fr);
  gap: 16px;
  align-items: start;
}

.td-upload :deep(.el-upload-dragger) {
  padding: 22px 16px;
  border-radius: var(--app-radius-lg, 12px);
}

.td-upload__icon {
  font-size: 26px;
  color: var(--app-text-muted);
}

.td-upload__text {
  margin-top: 6px;
  font-size: 13px;
  color: var(--app-text-secondary);
}

.td-upload__text em {
  font-style: normal;
  color: var(--app-primary);
}

.td-upload__hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.td-upload-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.td-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.td-field > label {
  font-size: 12px;
  font-weight: 600;
  color: var(--app-text-muted);
}

.td-category {
  width: 100%;
}

/* 留存方式只有两个互斥选项，占满整行比左对齐更好点 */
.td-retention {
  width: 100%;
}

.td-retention :deep(.el-radio-button) {
  flex: 1;
}

.td-retention :deep(.el-radio-button__inner) {
  width: 100%;
}

.td-option-hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.td-material {
  height: auto !important;
  padding: 6px 10px;
  white-space: normal;
}

.td-material__meta {
  margin-left: 8px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.td-materials {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.td-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.td-hint {
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-muted);
}

.td-alert {
  margin: 0;
}

.td-alert__reason {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.6;
}

.td-skip-list {
  margin-top: 4px;
}

/* ---------- 任务条 ---------- */

.td-taskbar__main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding: 16px 22px 0;
}

.td-taskbar__left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.td-taskbar__title {
  font-size: 14px;
  font-weight: 600;
}

.td-taskbar__meta {
  font-size: 12px;
  color: var(--app-text-muted);
}

.td-taskbar__progress {
  margin: 12px 22px 0;
}

.td-taskbar .td-hint {
  padding: 6px 22px 0;
}

.td-taskbar .td-alert {
  margin: 12px 22px 18px;
}

/* ---------- Tab ---------- */

.td-tabs {
  display: flex;
  gap: 6px;
}

.td-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid transparent;
  border-radius: var(--app-radius-md, 10px);
  background: transparent;
  font-size: 14px;
  color: var(--app-text-secondary);
  cursor: pointer;
  transition: color 0.2s ease, background 0.2s ease, border-color 0.2s ease;
}

.td-tab:hover {
  color: var(--app-text-strong);
}

.td-tab--active {
  border-color: var(--app-border);
  background: var(--app-surface);
  color: var(--app-text-strong);
  font-weight: 600;
}

.td-tab__count {
  padding: 0 6px;
  border-radius: 8px;
  background: var(--app-fill-color-light, #f2f4f8);
  font-size: 12px;
  font-weight: 600;
}

.td-filter {
  width: 130px;
}

/* ---------- 候选卡片 ---------- */

.td-list-wrap {
  min-height: 120px;
}

.td-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 14px;
}

.td-item {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md, 10px);
  background: var(--app-surface);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.td-item:hover {
  border-color: var(--app-border-strong);
  box-shadow: var(--app-shadow-sm, 0 2px 10px rgba(15, 23, 42, 0.06));
}

.td-item__head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.td-item__check {
  margin-top: 3px;
  flex-shrink: 0;
}

.td-item__title-wrap {
  min-width: 0;
  flex: 1;
}

.td-item__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.td-item__sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.td-item__similar {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
}

.td-item__similar-label {
  font-size: 11px;
  color: var(--app-text-muted);
}

.td-item__similar strong {
  font-size: 16px;
  color: var(--app-text-strong);
}

.td-item__badges {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.td-item__flag {
  font-size: 12px;
  color: #d97706;
}

.td-item__abilities {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.td-chip {
  padding: 2px 8px;
  border: 1px solid var(--app-border);
  border-radius: 20px;
  background: var(--app-fill-color-light, #f7f8fa);
  font-size: 12px;
  color: var(--app-text-secondary);
}

.td-chip--more {
  border-style: dashed;
  color: var(--app-text-muted);
}

.td-item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: auto;
}

.td-item__emphasis {
  font-size: 12px;
  color: var(--app-text-muted);
}

.td-item__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.td-pagination {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .td-upload-row {
    grid-template-columns: 1fr;
  }

  .td-history {
    width: 100%;
  }
}
</style>
