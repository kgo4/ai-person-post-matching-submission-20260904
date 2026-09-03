<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import { batchPostHardConditionRules, executeMatchingAsync, listEnabledPosts, listPostHardConditionRules, pageEmployees } from '@/api'
import { recommendEmployeesForPost, recommendPostsForEmployee } from '@/api/recommend'
import { precheckCapabilityEligibility } from '@/api/assessment'
import type { PostPost } from '@/api'
import type { EmployeeRecommendation, PostRecommendation } from '@/api/recommend'
import { buildExecutePayload } from './logic'
import type { MatchingMode, MatchMode } from './logic'
import { useMatchingTaskStore } from '@/store/modules/matching-tasks'
import ModeSelector from './components/ModeSelector.vue'
import SourceSelector from './components/SourceSelector.vue'
import HardConditionCard from './components/HardConditionCard.vue'
import AiConfigBar from './components/AiConfigBar.vue'
import PipelineChart from './components/PipelineChart.vue'
import CandidateList from './components/CandidateList.vue'

const router = useRouter()
const mode = ref<MatchMode>('PERSON_TO_POSTS')
const loading = ref(false)
const previewLoading = ref(false)
const showTaskResult = ref(false)
const taskResultCount = ref(0)

const postOptions = ref<PostPost[]>([])
const employeeOptions = ref<{ id: number; realName: string; empCode: string }[]>([])

const selectedEmployeeId = ref<number>()
const selectedPostId = ref<number>()
const topK = ref(8)
const selectedCandidateIds = ref<number[]>([])

const previewPosts = ref<PostRecommendation[]>([])
const previewEmployees = ref<EmployeeRecommendation[]>([])
const previewGenerated = ref(false)

const hardConditions = ref<{ field: string; operator: string; value: string; label: string }[]>([])

const enableAi = ref(true)
const forceAi = ref(false)
const aiTopN = ref(5)
const aiThreshold = ref(70)

// ---- 全局匹配任务 store（轮询/通知/并行由全局 watcher 承担） ----
const matchingTaskStore = useMatchingTaskStore()
const currentTaskId = ref<string | null>(null)

// 当前任务的实时状态（供管道图 progress 驱动）
const taskStatus = computed(() => (currentTaskId.value ? matchingTaskStore.byTaskId(currentTaskId.value) ?? null : null))

// 任务终态时复位 loading 并展示结果入口（替代原 useMatchingTask 的 onDone/onError/onTimeout）
watch(() => taskStatus.value?.status, (s) => {
  if (s === 2) {
    loading.value = false
    showTaskResult.value = true
    taskResultCount.value = taskStatus.value?.totalCount || 0
  } else if (s === 3 || s === 4) {
    loading.value = false
  }
})

// ---- 计算属性 ----
const previewItems = computed(() => (mode.value === 'PERSON_TO_POSTS' ? previewPosts.value : previewEmployees.value))

const pairCount = computed(() => {
  if (mode.value === 'SINGLE') return selectedEmployeeId.value && selectedPostId.value ? 1 : 0
  return selectedCandidateIds.value.length
})

const candidateStats = computed(() => {
  const items = previewItems.value
  const sel = new Set(selectedCandidateIds.value)
  return {
    total: items.length,
    selected: items.filter(i => sel.has(getId(i))).length,
    pass: items.filter(i => i.hardConditionStatus === 'PASS').length,
    risk: items.filter(i => i.hardConditionStatus === 'RISK').length,
    fail: items.filter(i => i.hardConditionStatus === 'FAIL').length,
  }
})

const canPreview = computed(() => (mode.value === 'PERSON_TO_POSTS' ? !!selectedEmployeeId.value : mode.value === 'POST_TO_PEOPLE' ? !!selectedPostId.value : false))

const activeStage = computed(() => {
  if (!enableAi.value) return loading.value || previewGenerated.value || pairCount.value > 0 ? 2 : hardConditions.value.length > 0 ? 1 : 0
  if (loading.value) return 3
  if (previewGenerated.value || pairCount.value > 0) return 2
  if (hardConditions.value.length > 0) return 1
  return 0
})

// 模式选择数据感知
const modeStats = computed(() => ({
  single: { employee: !!selectedEmployeeId.value, post: !!selectedPostId.value },
  personToPosts: { empName: getEmpName(selectedEmployeeId.value) },
  postToPeople: { postName: getPostName(selectedPostId.value), count: selectedCandidateIds.value.length },
}))

// ---- 硬条件加载/保存 ----
watch(selectedPostId, async (pid) => {
  hardConditions.value = []
  if (!pid) return
  try {
    const res = await listPostHardConditionRules(pid)
    hardConditions.value = (res.data || []).filter((r: any) => r.enabled !== 0).map((r: any) => ({ field: r.fieldName, operator: r.operator, value: r.expectedValue, label: r.fieldLabel }))
  } catch { /* ignore */ }
})

async function saveHardConditions() {
  if (!selectedPostId.value) return
  try {
    await batchPostHardConditionRules(selectedPostId.value, hardConditions.value.map((c, i) => ({
      postId: selectedPostId.value!, fieldName: c.field, fieldLabel: c.label || c.field,
      fieldType: 'text', operator: c.operator, expectedValue: c.value, enabled: 1, sortOrder: i,
    })))
    ElMessage.success('硬条件已保存到岗位规则')
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
}

// ---- 候选生成/选择 ----
function resetPreview() { previewPosts.value = []; previewEmployees.value = []; previewGenerated.value = false; selectedCandidateIds.value = [] }

async function generateCandidates() {
  if (!canPreview.value) { ElMessage.warning(mode.value === 'PERSON_TO_POSTS' ? '请先选择员工' : '请先选择岗位'); return }
  previewLoading.value = true; resetPreview()
  try {
    if (mode.value === 'PERSON_TO_POSTS') {
      const res = await recommendPostsForEmployee({ empId: selectedEmployeeId.value!, topK: topK.value, enableHardConditionPreview: true, enableL2Preview: true })
      previewPosts.value = res.data?.recommendations || []
    } else {
      const res = await recommendEmployeesForPost({ postId: selectedPostId.value!, topK: topK.value, enableHardConditionPreview: true, enableL2Preview: true })
      previewEmployees.value = res.data?.recommendations || []
    }
    previewGenerated.value = true
    selectedCandidateIds.value = previewItems.value.filter(i => i.hardConditionStatus !== 'FAIL').map(i => getId(i))
  } catch (e: any) { ElMessage.error(e.message || '生成失败') }
  finally { previewLoading.value = false }
}

function getId(item: PostRecommendation | EmployeeRecommendation) { return 'postId' in item ? item.postId : item.empId }
function toggleCandidate(id: number) {
  if (loading.value) return
  selectedCandidateIds.value = selectedCandidateIds.value.includes(id) ? selectedCandidateIds.value.filter(x => x !== id) : [...selectedCandidateIds.value, id]
}
function selectAll() { selectedCandidateIds.value = previewItems.value.map(i => getId(i)) }
function selectRecommended() { selectedCandidateIds.value = previewItems.value.filter(i => i.hardConditionStatus !== 'FAIL').map(i => getId(i)) }
function clearSelection() { selectedCandidateIds.value = [] }

async function searchEmployees(keyword = '') {
  const response = await pageEmployees({ current: 1, size: 50, keyword })
  employeeOptions.value = ((response.data as any)?.records || [])
    .map((employee: any) => ({ id: employee.id, realName: employee.realName, empCode: employee.empCode }))
}

// ---- 执行与预检 ----
async function handleExecute() {
  if (loading.value) return // 预检/提交进行中，防重复触发
  if (pairCount.value === 0) { ElMessage.warning('请至少选择一个匹配对象'); return }
  // 匹配资格预检：存在待确立能力时弹窗确认（默认仅正式能力匹配）
  try {
    const { empIds, postIds } = resolvePrecheckScope()
    if (empIds.length > 0 && postIds.length > 0) {
      const pre = await precheckCapabilityEligibility(empIds, postIds)
      const forbidden = (pre.data || []).filter((p) => !p.hasConfirmedAbilities)
      if (forbidden.length) {
        ElMessage.warning(`员工 ${forbidden.map((p) => p.empId).join(', ')} 无正式能力，禁止匹配`)
        return
      }
    }
  } catch { /* 预检失败不阻塞，交由后端校验 */ }

  await doExecute()
}

/** 计算预检范围 */
function resolvePrecheckScope(): { empIds: number[]; postIds: number[] } {
  if (mode.value === 'SINGLE') {
    return { empIds: selectedEmployeeId.value ? [selectedEmployeeId.value] : [], postIds: selectedPostId.value ? [selectedPostId.value] : [] }
  }
  if (mode.value === 'PERSON_TO_POSTS') {
    return { empIds: selectedEmployeeId.value ? [selectedEmployeeId.value] : [], postIds: selectedCandidateIds.value }
  }
  return { empIds: selectedCandidateIds.value, postIds: selectedPostId.value ? [selectedPostId.value] : [] }
}

async function doExecute() {
  if (loading.value) return // 已在执行/提交中，防并发提交（覆盖预检弹窗三条路径）
  loading.value = true; showTaskResult.value = false; taskResultCount.value = 0
  try {
    const payload = buildExecutePayload({
      mode: toBackendMode(mode.value),
      selectedEmployeeId: selectedEmployeeId.value,
      selectedPostId: selectedPostId.value,
      selectedCandidateIds: selectedCandidateIds.value,
      enableAiMatching: enableAi.value,
      forceAiMatching: forceAi.value,
      aiTopN: aiTopN.value,
      aiThreshold: aiThreshold.value,
      hardConditions: hardConditions.value,
    })
    const res = await executeMatchingAsync(payload)
    currentTaskId.value = res.data.taskId
    void matchingTaskStore.track(res.data.taskId) // 注册全局跟踪（watcher 轮询）
    ElMessage.info(`已提交 ${payload.pairs?.length || 0} 个匹配任务`)
  } catch (e: any) { loading.value = false; ElMessage.error(e.message || '提交失败') }
}

function toBackendMode(value: MatchMode): MatchingMode {
  if (value === 'SINGLE') return 'SINGLE_EVAL'
  if (value === 'PERSON_TO_POSTS') return 'EMP_TO_POST'
  return 'POST_TO_EMP'
}

function getEmpName(id?: number) { if (!id) return '未选择'; const e = employeeOptions.value.find(x => x.id === id); return e ? `${e.realName} (${e.empCode})` : `员工#${id}` }
function getPostName(id?: number) { if (!id) return '未选择'; const p = postOptions.value.find(x => x.id === id); return p?.postName || `岗位#${id}` }

onMounted(async () => {
  // 岗位和员工是两个独立的选择源；员工接口偶发断开时不能丢弃已成功返回的岗位。
  await Promise.all([
    listEnabledPosts()
      .then((pr) => { postOptions.value = pr.data || [] })
      .catch(() => { /* 岗位加载失败时保持空状态，由页面提示/重试处理 */ }),
    searchEmployees().catch(() => { /* employee loading failure does not affect post selection */ }),
  ])
  // 切页返回后恢复最近的进行中/已完成任务，管道图继续显示进度或结果入口
  if (!currentTaskId.value) {
    const recent = [...matchingTaskStore.tasks].reverse().find(t => t.status === 0 || t.status === 1 || t.status === 2)
    if (recent) {
      currentTaskId.value = recent.taskId
      if (recent.status === 2) {
        showTaskResult.value = true
        taskResultCount.value = recent.totalCount || 0
      }
    }
  }
})

watch(mode, () => { selectedEmployeeId.value = undefined; selectedPostId.value = undefined; resetPreview() })
</script>

<template>
  <div class="page-shell motion-page">
    <!-- Hero -->
    <section class="page-hero motion-scan">
      <div>
        <div class="page-hero__eyebrow">Graph-Driven Matching</div>
        <h1 class="page-hero__title">图谱匹配工作台</h1>
        <p class="page-hero__desc">选择匹配模式，预览候选，确认后发起正式评估</p>
      </div>
    </section>

    <!-- 模式选择 -->
    <ModeSelector v-model="mode" :loading="loading" :stats="modeStats" />

    <!-- 三段式：选择 + 动画 + 预览 -->
    <div class="execute-grid">
      <!-- 左：源对象 + 硬条件 -->
      <div class="execute-left">
        <section class="glass-card motion-rise">
          <div class="toolbar-panel">
            <div>
              <div class="section-title">源对象</div>
              <div class="section-desc">{{ mode === 'SINGLE' ? '选择一名员工和一个岗位' : mode === 'PERSON_TO_POSTS' ? '选择一名员工作为匹配起点' : '选择一个岗位作为匹配起点' }}</div>
            </div>
          </div>
          <div class="panel-body">
            <SourceSelector
              :mode="mode"
              :employee-options="employeeOptions"
              :post-options="postOptions"
              :employee-id="selectedEmployeeId"
              :post-id="selectedPostId"
              :loading="loading"
              @update:employee-id="(v?: number) => (selectedEmployeeId = v)"
              @update:post-id="(v?: number) => (selectedPostId = v)"
              @search-employees="searchEmployees"
            />
            <!-- 单人模式：直接匹配按钮 -->
            <div v-if="mode === 'SINGLE'" class="single-exec">
              <el-button type="primary" size="large" :loading="loading" :disabled="!selectedEmployeeId || !selectedPostId" @click="handleExecute" style="width:100%">
                {{ loading ? '执行中…' : '开始匹配' }}
              </el-button>
              <div v-if="showTaskResult" class="task-done">
                已完成 {{ taskResultCount }} 条
                <el-button text type="primary" @click="router.push('/matching/result')">查看 ></el-button>
              </div>
            </div>
          </div>
        </section>

        <!-- L1 硬条件独立卡片 -->
        <HardConditionCard
          v-model:conditions="hardConditions"
          :post-id="selectedPostId"
          :loading="loading"
          :active="activeStage === 1"
          @save="saveHardConditions"
        />
      </div>

      <!-- 右：管道图 + 候选预览 -->
      <div class="execute-right">
        <!-- 原理动画 -->
        <section class="glass-card motion-rise">
          <div class="toolbar-panel">
            <div>
              <div class="section-title">匹配流程</div>
              <div class="section-desc">{{ activeStage === 0 ? '选择岗位后自动加载硬条件规则' : activeStage === 1 ? '硬条件已加载，生成候选后进入评分' : activeStage === 2 ? 'L2 能力模型评分中' : 'L3 AI 深度分析中' }}</div>
            </div>
          </div>
          <AiConfigBar
            :enable-ai="enableAi" :force-ai="forceAi" :ai-top-n="aiTopN" :ai-threshold="aiThreshold" :loading="loading"
            @update:enable-ai="(v: boolean) => (enableAi = v)"
            @update:force-ai="(v: boolean) => (forceAi = v)"
            @update:ai-top-n="(v: number) => (aiTopN = v)"
            @update:ai-threshold="(v: number) => (aiThreshold = v)"
          />
          <div class="panel-body">
            <div v-if="previewGenerated && mode !== 'SINGLE'" class="ai-coverage">
              <el-icon :size="13"><MagicStick /></el-icon>
              <span>
                {{ !enableAi ? 'AI 分析已关闭' : forceAi ? `强制模式：硬条件通过的 ${candidateStats.pass} 条将进入 AI 分析` : `预计 ${candidateStats.pass} 条（L1 通过）进入 AI 分析` }}
              </span>
            </div>
            <PipelineChart
              :items="previewItems" :preview-generated="previewGenerated" :mode="mode"
              :enable-ai="enableAi" :active-stage="activeStage" :task-status="taskStatus"
              :done="showTaskResult" :task-result-count="taskResultCount"
            />
          </div>
        </section>

        <!-- 候选预览 / 结果 -->
        <section class="glass-card motion-rise">
          <div class="toolbar-panel">
            <div>
              <div class="section-title">{{ mode === 'SINGLE' ? '匹配结果' : '候选预览' }}</div>
              <div class="section-desc">{{ mode === 'SINGLE' ? '直接执行后查看结果' : '生成候选后勾选确认，再发起正式匹配' }}</div>
            </div>
            <div v-if="mode !== 'SINGLE'" class="toolbar-group">
              <el-input-number v-model="topK" :min="3" :max="20" size="small" />
              <el-button type="primary" :loading="previewLoading" :disabled="!canPreview" @click="generateCandidates">生成候选</el-button>
            </div>
          </div>

          <!-- 非单人模式：候选列表（含统计/骨架屏/卡片/空状态） -->
          <div v-if="mode !== 'SINGLE'" class="panel-body">
            <CandidateList
              :mode="mode" :items="previewItems" :selected-ids="selectedCandidateIds"
              :preview-loading="previewLoading" :top-k="topK" :pair-count="pairCount"
              :executing="loading" :stats="candidateStats"
              @toggle="toggleCandidate"
              @select-all="selectAll"
              @select-recommended="selectRecommended"
              @clear="clearSelection"
              @generate="generateCandidates"
              @update:top-k="(v: number) => (topK = v)"
              @execute="handleExecute"
            />
            <!-- 异步任务完成横幅 -->
            <div v-if="showTaskResult" class="task-done">
              匹配完成，共 {{ taskResultCount }} 条记录
              <el-button text type="primary" @click="router.push('/matching/result')">查看全部结果 ></el-button>
            </div>
          </div>

          <!-- 单人模式：结果/引导 -->
          <div v-if="mode === 'SINGLE'" class="panel-body">
            <div v-if="!showTaskResult" class="empty-guide">
              <div class="empty-guide__title">单人评估</div>
              <div class="empty-guide__steps">
                <div class="guide-step"><span class="guide-step__num">1</span>左侧选择员工与岗位</div>
                <div class="guide-step"><span class="guide-step__num">2</span>可选配置 L1 硬条件</div>
                <div class="guide-step"><span class="guide-step__num">3</span>点击「开始匹配」执行三级评估</div>
              </div>
            </div>
            <div v-if="showTaskResult" class="task-done">
              匹配完成，共 {{ taskResultCount }} 条记录
              <el-button text type="primary" @click="router.push('/matching/result')">查看全部结果 ></el-button>
            </div>
          </div>
        </section>
      </div>
    </div>

  </div>
</template>

<style scoped>
/* ===== 三段网格 ===== */
.execute-grid { display: grid; grid-template-columns: 360px 1fr; gap: 20px; align-items: start; }
.execute-left { display: flex; flex-direction: column; gap: 20px; }
.execute-right { display: flex; flex-direction: column; gap: 20px; }
.toolbar-group { display: flex; align-items: center; gap: 8px; }

/* ===== AI 覆盖标注 ===== */
.ai-coverage {
  display: flex; align-items: center; gap: 6px;
  margin-bottom: 10px; padding: 6px 12px; border-radius: 8px;
  background: rgba(139, 92, 246, 0.06); border: 1px dashed rgba(139, 92, 246, 0.25);
  font-size: 12px; font-weight: 600; color: #7c3aed;
}

/* ===== 单人执行 ===== */
.single-exec { margin-top: 20px; }
.task-done { margin-top: 12px; padding: 10px 16px; background: rgba(34,197,94,0.08); border-radius: 10px; font-size: 13px; font-weight: 600; color: #16a34a; display: flex; align-items: center; gap: 12px; }

/* ===== 空状态引导（单人模式） ===== */
.empty-guide { padding: 8px 0; }
.empty-guide__title { font-size: 14px; font-weight: 700; color: var(--app-text-strong, #111827); margin-bottom: 16px; }
.guide-step { display: flex; align-items: center; gap: 12px; padding: 10px 0; font-size: 13px; color: #6b7280; }
.guide-step__num {
  width: 22px; height: 22px; border-radius: 50%; background: var(--app-divider, #e5e7eb);
  display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; color: #9ca3af; flex-shrink: 0;
}

@media (max-width: 1024px) {
  .execute-grid { grid-template-columns: 1fr; }
}
</style>
