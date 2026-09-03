<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, CircleClose, Loading } from '@element-plus/icons-vue'
import {
  pageEvolutionTasks,
  analyzeEvolutionTask,
  applyEvolutionChanges,
  runEvolutionAgent,
  getAgentProgress,
  getEvolutionTask,
  deleteEvolutionTask,
  uploadIndustryWhitepaper,
  uploadInternalDocument,
  syncEvolutionCloudKnowledge,
  searchEvolutionExternalResources,
  pageSchedules,
  createSchedule,
  updateSchedule,
  deleteSchedule,
  runScheduleNow,
} from '@/api/evolution'
import type {
  PostEvolutionTask,
  AgentProgressVO,
  PostEvolutionScheduleConfig,
} from '@/api/evolution'
import { pagePosts, getPost } from '@/api/post'
import type { PostPost } from '@/api/types'
import EvolutionOverview from './EvolutionOverview.vue'

const router = useRouter()
const route = useRoute()
const activeTab = ref('overview')
const loading = ref(false)
const uploading = ref(false)
const syncing = ref(false)
const externalLoading = ref(false)
const externalQuery = ref('')
const externalResult = ref<Awaited<ReturnType<typeof searchEvolutionExternalResources>>['data'] | null>(null)
const agentRunning = ref(false)
const scheduleLoading = ref(false)

// Whitepaper upload
const whitepaperFile = ref<File | null>(null)
const whitepaperForm = reactive({ title: '', industry: '', trustLevel: 'HIGH', evolutionEnabled: true })

// Internal doc upload
const internalFile = ref<File | null>(null)
const internalForm = reactive({ title: '', sourceCategory: 'INTERNAL_BUSINESS_UPDATE', businessDomain: '', trustLevel: 'MEDIUM', evolutionEnabled: true })

// Cloud sync
const cloudSyncForm = reactive({ knowledgeBaseCode: '', businessDomain: '' })

// Agent
const postSearchLoading = ref(false)
const postOptions = ref<PostPost[]>([])
const agentForm = reactive({ postId: undefined as number | undefined, industry: '', businessDomain: '', triggerType: 'MANUAL_RUN', includeWhitepaper: true, includeCloudKnowledge: true, includeMarketJd: false, includeZhihu: true })
const agentProgress = ref<AgentProgressVO | null>(null)
const agentResult = ref<any>(null)
const marketDiscoveryContext = ref<{ candidateName: string; abilities: string; reason: string; evidenceCount: string } | null>(null)
const selectedPost = computed(() => postOptions.value.find(post => post.id === agentForm.postId) || null)

const buildZhihuQuery = () => {
  const postName = selectedPost.value?.postName?.trim()
  if (!postName) return ''
  const context = [agentForm.industry.trim(), agentForm.businessDomain.trim()].filter(Boolean).join(' ')
  return [postName, context, '岗位能力 技术趋势'].filter(Boolean).join(' ')
}

const handlePostChange = () => {
  externalQuery.value = buildZhihuQuery()
  externalResult.value = null
}

// Task list
const tasks = ref<PostEvolutionTask[]>([])
const postNameMap = reactive<Record<number, string>>({})
const searchStatus = ref('')
const pagination = reactive({ current: 1, size: 10, total: 0 })

// Schedules
const schedules = ref<PostEvolutionScheduleConfig[]>([])
const showScheduleDialog = ref(false)
const editingSchedule = ref<PostEvolutionScheduleConfig | null>(null)
const scheduleForm = reactive({ postId: 0, industry: '', businessDomain: '', cronExpression: '0 0 2 * * ?', includeWhitepaper: 1, includeCloudKnowledge: 1, includeMarketJd: 0, enabled: 1 })

const TABS = [
  { key: 'overview', label: '动态概览', icon: 'DataLine' },
  { key: 'sources', label: '资料输入', icon: 'Upload' },
  { key: 'agent', label: '运行演化', icon: 'Promotion' },
  { key: 'review', label: '变更审核', icon: 'Checked' },
  { key: 'schedule', label: '定时演化', icon: 'Timer' },
]

const handleUploadWhitepaper = async () => {
  if (!whitepaperFile.value) { ElMessage.warning('请选择文件'); return }
  if (!whitepaperForm.title) { ElMessage.warning('请填写标题'); return }
  uploading.value = true
  try {
    const uploadResult = await uploadIndustryWhitepaper(whitepaperFile.value, whitepaperForm)
    ElMessage.success(`行业白皮书已上传并索引，共 ${uploadResult.data?.chunkCount ?? 0} 个知识片段`)
    whitepaperFile.value = null
    whitepaperForm.title = ''
    whitepaperForm.industry = ''
  } catch (error) {
    console.error('行业白皮书上传或索引失败', error)
    ElMessage.error('行业白皮书上传或索引失败，请查看提示后重试')
  } finally {
    uploading.value = false
  }
}

const handleUploadInternal = async () => {
  if (!internalFile.value) { ElMessage.warning('请选择文件'); return }
  if (!internalForm.title) { ElMessage.warning('请填写标题'); return }
  uploading.value = true
  try {
    const uploadResult = await uploadInternalDocument(internalFile.value, internalForm)
    ElMessage.success(`内部资料已上传并索引，共 ${uploadResult.data?.chunkCount ?? 0} 个知识片段`)
    internalFile.value = null
    internalForm.title = ''
    internalForm.businessDomain = ''
  } catch (error) {
    console.error('内部资料上传或索引失败', error)
    ElMessage.error('内部资料上传或索引失败，请查看提示后重试')
  } finally {
    uploading.value = false
  }
}

const handleCloudSync = async () => {
  if (!cloudSyncForm.knowledgeBaseCode) { ElMessage.warning('请填写知识库编码'); return }
  syncing.value = true
  try { const res = await syncEvolutionCloudKnowledge(cloudSyncForm); ElMessage.success(`同步完成，共同步 ${res.data.syncedCount} 个文档`) } finally { syncing.value = false }
}

const handleExternalSearch = async () => {
  externalQuery.value = buildZhihuQuery()
  if (!externalQuery.value) { ElMessage.warning('请先在“运行演化”中选择目标岗位'); return }
  externalLoading.value = true
  try {
    const res = await searchEvolutionExternalResources({ query: externalQuery.value.trim(), count: 8 })
    externalResult.value = res.data
  } catch (error) {
    console.warn('外部趋势资源查询失败', error)
    ElMessage.error('外部趋势资源暂时不可用')
  } finally { externalLoading.value = false }
}

const searchPosts = async (keyword: string) => {
  postSearchLoading.value = true
  try { const res = await pagePosts({ current: 1, size: 500, keyword: keyword || undefined }); postOptions.value = res.data?.records || [] } finally { postSearchLoading.value = false }
}

const handleRunAgent = async () => {
  if (!agentForm.postId) { ElMessage.warning('请选择目标岗位'); return }
  agentRunning.value = true; agentProgress.value = null; agentResult.value = null
  try {
    const res = await runEvolutionAgent({ ...agentForm, postId: agentForm.postId as number })
    agentResult.value = res.data
    if (res.data.taskId) {
      try { const progressRes = await getAgentProgress(res.data.taskId); agentProgress.value = progressRes.data } catch {}
      ElMessage.success('演化 Agent 已进入队列，正在后台分析')
      pollAgentProgress(res.data.taskId)
    }
    loadTasks()
  } finally { agentRunning.value = false }
}

const pollAgentProgress = async (taskId: number) => {
  for (let attempt = 0; attempt < 120; attempt++) {
    await new Promise(resolve => window.setTimeout(resolve, 1500))
    try {
      const progressRes = await getAgentProgress(taskId)
      agentProgress.value = progressRes.data
      if (['COMPLETED', 'FAILED'].includes(progressRes.data.currentStep)) {
        if (progressRes.data.currentStep === 'COMPLETED') {
          try {
            const taskRes = await getEvolutionTask(taskId)
            const task = taskRes.data
            const summary = task.summaryJson ? JSON.parse(task.summaryJson) : null
            agentResult.value = { ...task, summary }
            if (summary?.savedChangeItems > 0) ElMessage.success(`岗位演化分析已完成，生成 ${summary.savedChangeItems} 条待审核变更`)
            else if (summary?.harnessBlock > 0) ElMessage.warning(`分析完成，但 ${summary.harnessBlock} 条提议被 Harness 阻断，请查看详情原因`)
            else ElMessage.info('岗位演化分析已完成，当前没有可审核的变更')
          } catch {
            ElMessage.success('岗位演化分析已完成，请查看任务结果')
          }
        } else ElMessage.error('岗位演化分析失败，请查看任务错误信息')
        await loadTasks()
        return
      }
    } catch {
      return
    }
  }
}

const loadTasks = async () => {
  loading.value = true
  try {
    const res = await pageEvolutionTasks({ current: pagination.current, size: pagination.size, taskStatus: searchStatus.value || undefined })
    tasks.value = res.data.records
    pagination.total = res.data.total
    await Promise.all([...new Set(tasks.value.map(task => task.postId))].map(async postId => {
      try { const postRes = await getPost(postId); if (postRes.data?.postName) postNameMap[postId] = postRes.data.postName } catch {}
    }))
  } finally { loading.value = false }
}

const displayPostName = (task: PostEvolutionTask) => postNameMap[task.postId] || (task.taskName?.replace(/\s*演化任务\s*$/, '') || '岗位名称待补充')

const handleDeleteTask = async (task: PostEvolutionTask) => {
  if (task.taskStatus === 'RUNNING' || task.progressStatus === 'RUNNING') {
    ElMessage.warning('运行中的演化任务不能删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除“${displayPostName(task)}”这条演化记录？关联证据和变更项也会删除，但不会影响岗位能力模型。`, '删除演化记录', { type: 'warning' })
    await deleteEvolutionTask(task.id)
    ElMessage.success('演化记录已删除')
    loadTasks()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('删除演化记录失败')
  }
}

const handleAnalyze = async (task: PostEvolutionTask) => {
  await ElMessageBox.confirm('确认执行演化分析？', '提示')
  loading.value = true
  try { await analyzeEvolutionTask(task.id); ElMessage.success('分析完成'); loadTasks() } finally { loading.value = false }
}

const handleApply = async (task: PostEvolutionTask) => {
  await ElMessageBox.confirm('确认应用已审核通过的变更？', '提示')
  loading.value = true
  try { const res = await applyEvolutionChanges(task.id); ElMessage.success(`已应用 ${res.data.applied} 项变更`); loadTasks() } finally { loading.value = false }
}

const openReviewTask = (taskId: number) => { router.push(`/post/evolution/detail/${taskId}`) }

const loadSchedules = async () => {
  scheduleLoading.value = true
  try { const res = await pageSchedules({ current: 1, size: 50 }); schedules.value = res.data.records } finally { scheduleLoading.value = false }
}

const openScheduleDialog = (schedule?: PostEvolutionScheduleConfig) => {
  if (schedule) { editingSchedule.value = schedule; Object.assign(scheduleForm, { postId: schedule.postId, industry: schedule.industry || '', businessDomain: schedule.businessDomain || '', cronExpression: schedule.cronExpression || '0 0 2 * * ?', includeWhitepaper: schedule.includeWhitepaper, includeCloudKnowledge: schedule.includeCloudKnowledge, includeMarketJd: schedule.includeMarketJd, enabled: schedule.enabled }) }
  else { editingSchedule.value = null; Object.assign(scheduleForm, { postId: 0, industry: '', businessDomain: '', cronExpression: '0 0 2 * * ?', includeWhitepaper: 1, includeCloudKnowledge: 1, includeMarketJd: 0, enabled: 1 }) }
  showScheduleDialog.value = true
}

const handleSaveSchedule = async () => {
  if (!scheduleForm.postId) { ElMessage.warning('请填写岗位ID'); return }
  try { if (editingSchedule.value) { await updateSchedule(editingSchedule.value.id, scheduleForm); ElMessage.success('更新成功') } else { await createSchedule(scheduleForm); ElMessage.success('创建成功') }; showScheduleDialog.value = false; loadSchedules() } catch {}
}

const handleRunSchedule = async (schedule: PostEvolutionScheduleConfig) => { await ElMessageBox.confirm('确认立即执行此定时任务？', '提示'); try { await runScheduleNow(schedule.id); ElMessage.success('任务已启动'); loadSchedules() } catch {} }
const handleDeleteSchedule = async (schedule: PostEvolutionScheduleConfig) => { await ElMessageBox.confirm('确认删除此定时配置？', '提示'); try { await deleteSchedule(schedule.id); ElMessage.success('删除成功'); loadSchedules() } catch {} }

const statusType = (s: string) => ({ PENDING: 'info', RUNNING: 'warning', WAIT_CONFIRM: 'primary', APPLIED: 'success', FAILED: 'danger' } as any)[s] || 'info'
const statusLabel = (s: string) => ({ PENDING: '待处理', RUNNING: '运行中', WAIT_CONFIRM: '待确认', APPLIED: '已应用', FAILED: '失败' } as any)[s] || s
const triggerTypeLabel = (t: string) => ({ MANUAL_RUN: '手动运行', MANUAL_UPLOAD: '资料上传', SCHEDULED: '定时执行', CLOUD_SYNC: '云知识库同步', MARKET_DISCOVERY: '市场发现线索' } as any)[t] || t || '-'

watch(
  [() => agentForm.postId, () => agentForm.industry, () => agentForm.businessDomain],
  () => { externalQuery.value = buildZhihuQuery() },
)

onMounted(() => {
  loadTasks(); loadSchedules()
  const postId = Number(route.query.postId)
  if (route.query.trigger === 'MARKET_DISCOVERY' && Number.isFinite(postId) && postId > 0) {
    agentForm.postId = postId
    agentForm.triggerType = 'MARKET_DISCOVERY'
    agentForm.includeMarketJd = true
    activeTab.value = 'agent'
    marketDiscoveryContext.value = {
      candidateName: String(route.query.candidateName || '市场能力社区'), abilities: String(route.query.abilities || '未提供'),
      reason: String(route.query.reason || '与既有岗位能力模型相近'), evidenceCount: String(route.query.evidenceCount || '0'),
    }
    searchPosts('')
  }
})
</script>

<template>
  <div class="page-shell">
    <!-- Header -->
    <section class="evo-header">
      <div class="evo-header__text">
        <div class="evo-header__eyebrow">Post Evolution</div>
        <h1 class="evo-header__title">既有岗位能力演化</h1>
        <p class="evo-header__desc">基于原文证据生成既有岗位的变更建议；审核通过后仍需人工点击应用。</p>
      </div>
    </section>

    <!-- Tab Nav -->
    <nav class="evo-nav">
      <button
        v-for="tab in TABS"
        :key="tab.key"
        class="evo-nav__item"
        :class="{ 'evo-nav__item--active': activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </nav>

    <!-- Tab Content -->
    <div class="evo-content">
      <!-- Overview -->
      <div v-show="activeTab === 'overview'">
        <EvolutionOverview @review-task="openReviewTask" />
      </div>

      <!-- Sources -->
        <div v-show="activeTab === 'sources'">
          <div class="evo-flow-strip">
            <div class="evo-flow-step is-active"><span>01</span><strong>准备证据</strong><small>上传、同步或采集外部资料</small></div>
            <div class="evo-flow-line" />
            <div class="evo-flow-step"><span>02</span><strong>选择岗位</strong><small>在运行演化中确定目标岗位</small></div>
            <div class="evo-flow-line" />
            <div class="evo-flow-step"><span>03</span><strong>运行分析</strong><small>生成待审核的岗位变化建议</small></div>
          </div>
          <div class="evo-sources">
          <section class="evo-card">
            <div class="evo-card__head">
              <span class="evo-card__title">行业白皮书上传</span>
              <el-tag type="success" size="small">推荐</el-tag>
            </div>
            <div class="evo-card__body">
              <div class="evo-form-row">
                <label class="evo-label">文件</label>
                <el-upload :auto-upload="false" :limit="1" accept=".pdf,.doc,.docx,.txt" :on-change="(f: any) => whitepaperFile = f.raw">
                  <el-button type="primary" size="small" plain>选择文件</el-button>
                  <span class="evo-hint" style="margin-left:8px">支持 PDF、Word、TXT</span>
                </el-upload>
              </div>
              <div class="evo-form-row">
                <label class="evo-label">标题</label>
                <el-input v-model="whitepaperForm.title" placeholder="如：2026新一代信息技术产业白皮书" />
              </div>
              <div class="evo-form-row">
                <label class="evo-label">行业</label>
                <el-input v-model="whitepaperForm.industry" placeholder="如：新一代信息技术" />
              </div>
              <div class="evo-inline-fields">
                <div class="evo-form-row">
                  <label class="evo-label">可信等级</label>
                  <el-select v-model="whitepaperForm.trustLevel">
                    <el-option label="高" value="HIGH" />
                    <el-option label="中" value="MEDIUM" />
                    <el-option label="低" value="LOW" />
                  </el-select>
                </div>
                <div class="evo-form-row">
                  <label class="evo-label">参与演化</label>
                  <el-switch v-model="whitepaperForm.evolutionEnabled" />
                </div>
              </div>
              <el-button type="primary" @click="handleUploadWhitepaper" :loading="uploading">上传并索引</el-button>
            </div>
          </section>

          <section class="evo-card">
            <div class="evo-card__head">
              <span class="evo-card__title">公司内部资料上传</span>
            </div>
            <div class="evo-card__body">
              <div class="evo-form-row">
                <label class="evo-label">文件</label>
                <el-upload :auto-upload="false" :limit="1" accept=".pdf,.doc,.docx,.txt" :on-change="(f: any) => internalFile = f.raw">
                  <el-button type="primary" size="small" plain>选择文件</el-button>
                </el-upload>
              </div>
              <div class="evo-form-row">
                <label class="evo-label">标题</label>
                <el-input v-model="internalForm.title" placeholder="资料标题" />
              </div>
              <div class="evo-inline-fields">
                <div class="evo-form-row">
                  <label class="evo-label">资料类型</label>
                  <el-select v-model="internalForm.sourceCategory">
                    <el-option label="岗位信息" value="INTERNAL_POST_INFO" />
                    <el-option label="业务更新" value="INTERNAL_BUSINESS_UPDATE" />
                    <el-option label="内部规范" value="INTERNAL_POLICY" />
                  </el-select>
                </div>
                <div class="evo-form-row">
                  <label class="evo-label">业务领域</label>
                  <el-input v-model="internalForm.businessDomain" placeholder="如：智能制造" />
                </div>
              </div>
              <div class="evo-inline-fields">
                <div class="evo-form-row">
                  <label class="evo-label">可信等级</label>
                  <el-select v-model="internalForm.trustLevel">
                    <el-option label="高" value="HIGH" />
                    <el-option label="中" value="MEDIUM" />
                    <el-option label="低" value="LOW" />
                  </el-select>
                </div>
              </div>
              <el-button type="primary" @click="handleUploadInternal" :loading="uploading">上传并索引</el-button>
            </div>
          </section>

          <section class="evo-card">
            <div class="evo-card__head">
              <span class="evo-card__title">云知识库同步</span>
            </div>
            <div class="evo-card__body">
              <div class="evo-inline-fields">
                <div class="evo-form-row" style="flex:1">
                  <label class="evo-label">知识库编码</label>
                  <el-input v-model="cloudSyncForm.knowledgeBaseCode" placeholder="company-post-kb" />
                </div>
                <div class="evo-form-row" style="flex:1">
                  <label class="evo-label">业务领域</label>
                  <el-input v-model="cloudSyncForm.businessDomain" placeholder="智能制造" />
                </div>
              </div>
              <el-button type="primary" @click="handleCloudSync" :loading="syncing">同步</el-button>
            </div>
          </section>

          <section class="evo-card evo-card--external">
            <div class="evo-card__head">
              <span class="evo-card__title">外部信息采集</span>
              <el-tag type="success" size="small">趋势参考</el-tag>
            </div>
            <div class="evo-card__body">
              <div class="evo-source-note">
                <strong>来源边界</strong>
                <span>知乎公开内容只提供行业趋势背景；不会直接写入岗位能力模型，也不会替代市场 JD、白皮书或内部资料。</span>
              </div>
              <div class="evo-external-options">
                <el-switch v-model="agentForm.includeZhihu" active-text="纳入岗位演化分析" />
                <span class="evo-hint">关键词由运行演化页选择的岗位自动生成，本页只负责采集和查看结果。</span>
              </div>
              <div class="evo-external-search">
                <div class="evo-query-preview">
                  <span class="evo-query-preview__label">当前检索词</span>
                  <strong>{{ externalQuery || '尚未选择目标岗位' }}</strong>
                </div>
                <el-button type="primary" :loading="externalLoading" :disabled="!externalQuery" @click="handleExternalSearch">按岗位采集趋势</el-button>
              </div>
              <div v-if="externalResult" class="evo-external-meta">
                <el-tag size="small" type="info">{{ externalResult.sourceType }}</el-tag>
                <span>保留 {{ externalResult.items.length }} 条</span>
                <span>过滤 {{ externalResult.filteredCount + externalResult.noiseRemovedCount }} 条</span>
                <span>去重 {{ externalResult.deduplicatedCount }} 条</span>
              </div>
              <div v-if="externalResult?.degraded" class="evo-external-empty">外部来源暂不可用，岗位演化主流程不受影响。</div>
              <div v-else-if="externalResult?.items.length" class="evo-external-list">
                <a v-for="item in externalResult.items" :key="item.url + item.contentId" :href="item.url" target="_blank" rel="noreferrer" class="evo-external-item">
                  <strong>{{ item.title }}</strong>
                  <span>{{ item.summary }}</span>
                  <small>赞 {{ item.voteUpCount ?? 0 }} · 评 {{ item.commentCount ?? 0 }} · 外部参考</small>
                </a>
              </div>
              <div v-else-if="externalResult" class="evo-external-empty">没有找到可用的趋势参考。</div>
            </div>
          </section>
        </div>
      </div>

      <!-- Agent -->
      <div v-show="activeTab === 'agent'">
        <div class="evo-agent">
          <section class="evo-card">
            <div class="evo-card__head">
              <span class="evo-card__title">运行岗位演化 Agent</span>
              <el-tag type="warning" size="small">只生成建议</el-tag>
            </div>
            <div class="evo-card__body">
              <div class="evo-agent-summary">
                <div class="evo-agent-summary__item"><span class="evo-agent-summary__label">目标</span><span>发现既有岗位能力变化</span></div>
                <div class="evo-agent-summary__item"><span class="evo-agent-summary__label">输出</span><span>待审核变更建议与证据</span></div>
                <div class="evo-agent-summary__item"><span class="evo-agent-summary__label">生效</span><span>人工审核后手动应用</span></div>
              </div>
              <el-alert v-if="marketDiscoveryContext" type="info" :closable="false" class="evo-market-context">
                <template #title>市场发现线索：{{ marketDiscoveryContext.candidateName }}</template>
                <div>能力社区：{{ marketDiscoveryContext.abilities }}。{{ marketDiscoveryContext.reason }}。系统会重新检索 {{ marketDiscoveryContext.evidenceCount }} 条关联市场 JD 原文，不直接采信跳转参数。</div>
              </el-alert>
              <div class="evo-form-grid">
                <div class="evo-form-row">
                  <label class="evo-label">目标岗位</label>
                  <el-select v-model="agentForm.postId" filterable remote reserve-keyword clearable placeholder="输入岗位名称搜索" :remote-method="searchPosts" :loading="postSearchLoading" @change="handlePostChange">
                    <el-option v-for="p in postOptions" :key="p.id" :label="`${p.postName}（${p.postCode || p.id}）`" :value="p.id" />
                  </el-select>
                </div>
                <div class="evo-form-row">
                  <label class="evo-label">行业</label>
                  <el-input v-model="agentForm.industry" placeholder="如：新一代信息技术" />
                </div>
                <div class="evo-form-row">
                  <label class="evo-label">业务领域</label>
                  <el-input v-model="agentForm.businessDomain" placeholder="如：企业数字化平台" />
                </div>
                <div class="evo-form-row">
                  <label class="evo-label">触发方式</label>
                  <el-select v-model="agentForm.triggerType">
                    <el-option label="手动运行" value="MANUAL_RUN" />
                    <el-option label="资料上传触发" value="MANUAL_UPLOAD" />
                  </el-select>
                </div>
              </div>
              <div class="evo-switch-row">
                <el-switch v-model="agentForm.includeWhitepaper" active-text="白皮书" />
                <el-switch v-model="agentForm.includeCloudKnowledge" active-text="云知识库" />
                <el-switch v-model="agentForm.includeMarketJd" active-text="市场演化线索" />
                <el-switch v-model="agentForm.includeZhihu" active-text="知乎趋势" />
              </div>
              <div class="evo-hint">选择岗位后，知乎趋势关键词会自动绑定到该岗位；这里仅选择已准备好的证据来源，最终仍以岗位能力模型和人工审核结果为准。</div>
              <el-button type="primary" @click="handleRunAgent" :loading="agentRunning" size="large">运行岗位演化 Agent</el-button>
            </div>
          </section>

          <section v-if="agentProgress" class="evo-card">
            <div class="evo-card__head">
              <span class="evo-card__title">执行进度</span>
              <el-tag :type="agentProgress.percent >= 100 ? 'success' : 'warning'" size="small">{{ agentProgress.percent }}%</el-tag>
            </div>
            <div class="evo-card__body">
              <el-progress :percentage="agentProgress.percent" :status="agentProgress.currentStep === 'FAILED' ? 'exception' : (agentProgress.percent >= 100 ? 'success' : undefined)" />
              <el-alert v-if="agentProgress.currentStep === 'FAILED'" type="error" :closable="false" show-icon title="岗位演化分析失败" :description="agentProgress.errorMessage || '任务执行失败，请查看任务详情或稍后重试。'" />
              <div class="evo-progress-steps">
                <div v-for="step in agentProgress.steps" :key="step.name" class="evo-progress-step" :class="step.status.toLowerCase()">
                  <el-icon v-if="step.status === 'DONE'"><CircleCheck /></el-icon>
                  <el-icon v-else-if="step.status === 'RUNNING'"><Loading /></el-icon>
                  <el-icon v-else><CircleClose /></el-icon>
                  <span>{{ step.name }}</span>
                </div>
              </div>
            </div>
          </section>

          <section v-if="agentResult" class="evo-card">
            <div class="evo-card__head">
              <span class="evo-card__title">演化结果</span>
              <el-button type="primary" link @click="$router.push(`/post/evolution/detail/${agentResult.taskId}`)">查看详情</el-button>
            </div>
            <div class="evo-card__body">
              <div class="evo-result-meta">
                <div><span>任务ID</span><strong>{{ agentResult.taskId }}</strong></div>
                <div><span>状态</span><el-tag :type="statusType(agentResult.taskStatus)" size="small">{{ statusLabel(agentResult.taskStatus) }}</el-tag></div>
                <div><span>编码</span><strong>{{ agentResult.taskCode }}</strong></div>
              </div>
              <div v-if="agentResult.summary" class="evo-result-stats">
                <div class="evo-stat-item"><span class="evo-stat-val">{{ agentResult.summary.signalCount || 0 }}</span><span class="evo-stat-lbl">信号数</span></div>
                <div class="evo-stat-item"><span class="evo-stat-val">{{ agentResult.summary.savedChangeItems || 0 }}</span><span class="evo-stat-lbl">变更建议</span></div>
                <div class="evo-stat-item"><span class="evo-stat-val">{{ agentResult.summary.aiAcceptedSuggestions || 0 }}</span><span class="evo-stat-lbl">AI有效建议</span></div>
                <div class="evo-stat-item"><span class="evo-stat-val">{{ agentResult.summary.ruleProposalCount || 0 }}</span><span class="evo-stat-lbl">规则建议</span></div>
                <div class="evo-stat-item"><span class="evo-stat-val is-ok">{{ agentResult.summary.harnessPass || 0 }}</span><span class="evo-stat-lbl">Harness通过</span></div>
                <div class="evo-stat-item"><span class="evo-stat-val is-warn">{{ agentResult.summary.harnessReview || 0 }}</span><span class="evo-stat-lbl">待审</span></div>
              </div>
              <div v-if="agentResult.summary" class="evo-result-note">
                本次构成：新增 {{ agentResult.summary.addCount || 0 }}，更新 {{ agentResult.summary.updateCount || 0 }}，删除 {{ agentResult.summary.removeCount || 0 }}。
                <template v-if="agentResult.summary.ruleFallback">AI 本次未生成可用变更，已使用规则补充。</template>
                <template v-else>AI 原始输出 {{ agentResult.summary.aiRawSuggestions || 0 }} 条，规则补充 {{ agentResult.summary.ruleProposalCount || 0 }} 条。</template>
              </div>
              <div class="evo-result-note">本次结果不会自动修改岗位能力。请进入详情审核变更项后，再执行应用。</div>
            </div>
          </section>
        </div>
      </div>

      <!-- Review -->
      <div v-show="activeTab === 'review'">
        <section class="evo-card">
          <div class="evo-card__head">
            <span class="evo-card__title">演化任务列表</span>
          </div>
          <div class="evo-card__toolbar">
            <el-select v-model="searchStatus" clearable placeholder="全部状态" size="default" style="width:140px">
              <el-option label="待处理" value="PENDING" />
              <el-option label="运行中" value="RUNNING" />
              <el-option label="待确认" value="WAIT_CONFIRM" />
              <el-option label="已应用" value="APPLIED" />
              <el-option label="失败" value="FAILED" />
            </el-select>
            <el-button type="primary" @click="loadTasks" plain>查询</el-button>
          </div>
          <div class="evo-card__body--flush">
            <el-table :data="tasks" v-loading="loading">
              <el-table-column prop="taskCode" label="任务编码" width="200" />
              <el-table-column label="岗位" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">{{ displayPostName(row) }}</template>
              </el-table-column>
              <el-table-column label="触发方式" width="120">
                <template #default="{ row }"><el-tag size="small" type="info">{{ triggerTypeLabel(row.triggerType) }}</el-tag></template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }"><el-tag :type="statusType(row.taskStatus)" size="small">{{ statusLabel(row.taskStatus) }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="createdTime" label="创建时间" width="170" />
              <el-table-column label="操作" width="250" fixed="right">
                <template #default="{ row }">
                  <el-button link size="small" @click="$router.push(`/post/evolution/detail/${row.id}`)">详情</el-button>
                  <el-button v-if="row.taskStatus === 'PENDING' || row.taskStatus === 'FAILED'" type="success" link size="small" @click="handleAnalyze(row)">分析</el-button>
                  <el-button v-if="row.taskStatus === 'WAIT_CONFIRM'" type="warning" link size="small" @click="handleApply(row)">应用</el-button>
                  <el-button v-if="row.taskStatus !== 'RUNNING'" type="danger" link size="small" @click="handleDeleteTask(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="evo-pagination">
              <el-pagination
                v-model:current-page="pagination.current"
                v-model:page-size="pagination.size"
                :total="pagination.total"
                layout="total, prev, pager, next"
                size="small"
                @current-change="loadTasks"
                @size-change="loadTasks"
              />
            </div>
          </div>
        </section>
      </div>

      <!-- Schedule -->
      <div v-show="activeTab === 'schedule'">
        <section class="evo-card">
          <div class="evo-card__head">
          <span class="evo-card__title">定时演化配置（只生成待确认建议）</span>
            <el-button type="primary" size="small" @click="openScheduleDialog()">新建配置</el-button>
          </div>
          <div class="evo-card__body--flush">
            <el-table :data="schedules" v-loading="scheduleLoading">
              <el-table-column prop="postId" label="岗位ID" width="100" />
              <el-table-column prop="industry" label="行业" width="150" />
              <el-table-column prop="businessDomain" label="业务领域" width="150" />
              <el-table-column prop="cronExpression" label="执行频率" width="150" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }"><el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">{{ row.enabled === 1 ? '启用' : '禁用' }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="runCount" label="执行次数" width="100" />
              <el-table-column prop="lastRunTime" label="最近执行" width="170" />
              <el-table-column label="操作" width="250" fixed="right">
                <template #default="{ row }">
                  <el-button link size="small" @click="openScheduleDialog(row)">编辑</el-button>
                  <el-button type="success" link size="small" @click="handleRunSchedule(row)">立即执行</el-button>
                  <el-button type="danger" link size="small" @click="handleDeleteSchedule(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>
      </div>
    </div>

    <!-- Schedule Dialog -->
    <el-dialog v-model="showScheduleDialog" :title="editingSchedule ? '编辑定时配置' : '新建定时配置'" width="560px">
      <div class="evo-dialog-form">
        <div class="evo-form-row">
          <label class="evo-label">岗位ID</label>
          <el-input-number v-model="scheduleForm.postId" :min="1" style="width:100%" />
        </div>
        <div class="evo-inline-fields">
          <div class="evo-form-row" style="flex:1"><label class="evo-label">行业</label><el-input v-model="scheduleForm.industry" /></div>
          <div class="evo-form-row" style="flex:1"><label class="evo-label">业务领域</label><el-input v-model="scheduleForm.businessDomain" /></div>
        </div>
        <div class="evo-form-row">
          <label class="evo-label">执行频率</label>
          <el-input v-model="scheduleForm.cronExpression" placeholder="0 0 2 * * ?" />
          <span class="evo-hint" style="margin-top:4px">Cron 表达式，默认每天凌晨2点</span>
        </div>
        <div class="evo-switch-row">
          <el-switch v-model="scheduleForm.includeWhitepaper" :active-value="1" :inactive-value="0" active-text="白皮书" />
          <el-switch v-model="scheduleForm.includeCloudKnowledge" :active-value="1" :inactive-value="0" active-text="云知识库" />
          <el-switch v-model="scheduleForm.includeMarketJd" :active-value="1" :inactive-value="0" active-text="市场演化线索" />
        </div>
        <div class="evo-hint">定时任务只生成待确认的变更建议，不会自动修改岗位能力模型。</div>
        <div class="evo-switch-row">
          <el-switch v-model="scheduleForm.enabled" :active-value="1" :inactive-value="0" active-text="启用" />
        </div>
      </div>
      <template #footer>
        <el-button @click="showScheduleDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveSchedule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* ====== Evolution — Variant C ====== */

/* Header */
.evo-header {
  padding: 22px 26px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.05), transparent 50%), rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(12px);
}
.evo-header__eyebrow { display: inline-flex; padding: 4px 10px; border-radius: 6px; background: rgba(59, 130, 246, 0.08); color: var(--app-primary); font-size: 11px; font-weight: 700; letter-spacing: 0.08em; margin-bottom: 8px; }
.evo-header__title { margin: 0; font-size: 26px; font-weight: 800; color: var(--app-text-strong); letter-spacing: -0.04em; }
.evo-header__desc { margin: 4px 0 0; color: var(--app-text-secondary); font-size: 13px; max-width: 560px; }

/* Tab Nav */
.evo-nav { display: flex; gap: 0; border-bottom: 1px solid rgba(148, 163, 184, 0.14); margin-top: 4px; }
.evo-nav__item { padding: 11px 20px; border: none; border-bottom: 2px solid transparent; background: transparent; color: var(--app-text-muted); font-size: 14px; font-weight: 600; cursor: pointer; transition: color 0.2s, border-color 0.2s; }
.evo-nav__item:hover { color: var(--app-text-secondary); }
.evo-nav__item--active { color: var(--app-primary); border-bottom-color: var(--app-primary); }

/* Content Area */
.evo-content { padding-top: 4px; }

/* Card */
.evo-card { border: 1px solid rgba(148, 163, 184, 0.12); border-radius: 14px; background: rgba(255, 255, 255, 0.58); backdrop-filter: blur(10px); overflow: hidden; margin-bottom: 14px; }
.evo-card--external { border-color: rgba(14, 165, 233, 0.24); }
.evo-source-note { display: flex; gap: 8px; align-items: flex-start; padding: 10px 12px; border: 1px solid rgba(14, 165, 233, 0.18); border-radius: 8px; background: rgba(14, 165, 233, 0.05); color: var(--app-text-secondary); font-size: 12px; line-height: 1.6; }
.evo-source-note strong { color: var(--app-text-strong); white-space: nowrap; }
.evo-external-options { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.evo-flow-strip { display: flex; align-items: stretch; gap: 10px; padding: 12px 14px; margin-bottom: 14px; border: 1px solid rgba(148, 163, 184, 0.14); border-radius: 10px; background: rgba(248, 250, 252, 0.72); }
.evo-flow-step { display: flex; flex: 1; flex-direction: column; gap: 3px; min-width: 0; color: var(--app-text-muted); }
.evo-flow-step span { color: var(--app-primary); font-size: 11px; font-weight: 700; }
.evo-flow-step strong { color: var(--app-text-secondary); font-size: 13px; }
.evo-flow-step small { font-size: 11px; line-height: 1.4; }
.evo-flow-step.is-active strong { color: var(--app-text-strong); }
.evo-flow-line { align-self: center; width: 28px; height: 1px; background: rgba(148, 163, 184, 0.35); }
.evo-card__head { display: flex; align-items: center; gap: 8px; padding: 13px 18px; border-bottom: 1px solid rgba(148, 163, 184, 0.1); }
.evo-card__title { font-size: 14px; font-weight: 700; color: var(--app-text-strong); }
.evo-card__toolbar { display: flex; align-items: center; gap: 8px; padding: 10px 18px; border-bottom: 1px solid rgba(148, 163, 184, 0.08); }
.evo-card__body { padding: 16px 18px; display: flex; flex-direction: column; gap: 12px; }
.evo-card__body--flush { padding: 0; }

/* Form */
.evo-form-row { display: flex; flex-direction: column; gap: 4px; }
.evo-form-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.evo-inline-fields { display: flex; gap: 16px; flex-wrap: wrap; }
.evo-label { font-size: 11px; font-weight: 600; color: var(--app-text-muted); letter-spacing: 0.03em; }
.evo-hint { font-size: 11px; color: var(--app-text-muted); }
.evo-switch-row { display: flex; align-items: center; gap: 18px; flex-wrap: wrap; padding: 8px 0; }
.evo-external-search { display: flex; gap: 8px; }
.evo-external-search .el-input { flex: 1; }
.evo-query-preview { display: flex; flex: 1; flex-direction: column; justify-content: center; gap: 2px; min-width: 0; padding: 7px 10px; border: 1px solid rgba(148, 163, 184, 0.18); border-radius: 6px; background: rgba(248, 250, 252, 0.8); }
.evo-query-preview__label { color: var(--app-text-muted); font-size: 11px; }
.evo-query-preview strong { overflow: hidden; color: var(--app-text-secondary); font-size: 12px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.evo-external-meta { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; color: var(--app-text-muted); font-size: 11px; }
.evo-external-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.evo-external-item { display: flex; flex-direction: column; gap: 4px; padding: 10px; border: 1px solid rgba(14, 165, 233, 0.16); border-radius: 8px; color: inherit; text-decoration: none; background: rgba(255, 255, 255, 0.5); }
.evo-external-item:hover { border-color: rgba(14, 165, 233, 0.45); background: rgba(239, 248, 255, 0.8); }
.evo-external-item strong { color: var(--app-primary); font-size: 12px; line-height: 1.4; }
.evo-external-item span { color: var(--app-text-secondary); font-size: 12px; line-height: 1.5; }
.evo-external-item small, .evo-external-empty { color: var(--app-text-muted); font-size: 11px; }
.evo-external-empty { padding: 8px 0; }

/* Sources */
.evo-sources { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.evo-sources > *:nth-child(3),
.evo-sources > *:nth-child(4) { grid-column: span 2; }

/* Agent */
.evo-agent { display: flex; flex-direction: column; gap: 14px; }
.evo-agent-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; padding: 10px 12px; border: 1px solid rgba(148, 163, 184, 0.14); border-radius: 8px; background: rgba(248, 250, 252, 0.72); }
.evo-agent-summary__item { display: flex; flex-direction: column; gap: 3px; color: var(--app-text-secondary); font-size: 12px; }
.evo-agent-summary__label { color: var(--app-text-muted); font-size: 11px; }
.evo-result-note { margin-top: 4px; padding: 8px 10px; border-left: 3px solid var(--app-primary); color: var(--app-text-secondary); font-size: 12px; line-height: 1.5; background: rgba(59, 130, 246, 0.04); }
@media (max-width: 760px) { .evo-agent-summary { grid-template-columns: 1fr; } }
.evo-progress-steps { display: flex; flex-wrap: wrap; gap: 14px; font-size: 12px; color: var(--app-text-muted); }
.evo-progress-step { display: flex; align-items: center; gap: 4px; }
.evo-progress-step.done { color: #10b981; }
.evo-progress-step.running { color: var(--app-primary); }

.evo-result-meta { display: flex; gap: 24px; align-items: center; }
.evo-result-meta > div { display: flex; flex-direction: column; gap: 2px; }
.evo-result-meta span { font-size: 11px; color: var(--app-text-muted); }
.evo-result-meta strong { font-size: 14px; color: var(--app-text-strong); }

.evo-result-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-top: 4px; }
.evo-stat-item { display: flex; flex-direction: column; gap: 3px; padding: 12px; border: 1px solid rgba(148, 163, 184, 0.12); border-radius: 8px; background: rgba(255, 255, 255, 0.45); }
.evo-stat-val { font-size: 22px; font-weight: 800; color: var(--app-text-strong); }
.evo-stat-val.is-ok { color: #10b981; }
.evo-stat-val.is-warn { color: #d97706; }
.evo-stat-lbl { font-size: 11px; color: var(--app-text-muted); }

/* Pagination */
.evo-pagination { display: flex; justify-content: flex-end; padding: 12px 18px; }

/* Dialog */
.evo-dialog-form { display: flex; flex-direction: column; gap: 14px; }

@media (max-width: 1024px) {
  .evo-sources { grid-template-columns: 1fr; }
  .evo-sources > *:nth-child(3),
  .evo-sources > *:nth-child(4) { grid-column: span 1; }
  .evo-form-grid { grid-template-columns: 1fr; }
  .evo-external-list { grid-template-columns: 1fr; }
}
</style>
