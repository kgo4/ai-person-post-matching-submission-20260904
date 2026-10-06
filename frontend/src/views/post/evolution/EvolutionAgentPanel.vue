<script setup lang="ts">
/**
 * 运行演化：选择目标岗位 → 挑选证据来源 → 运行 Agent → 查看进度与结果。
 *
 * 从原 867 行页面里拆出。拆出的同时做了两处收敛：
 *   1. 「外部趋势（知乎）」从「资料输入」Tab 移到这里 —— 它的检索词由目标岗位生成，
 *      放在没有岗位选择器的 Tab 里，按钮会一直是灰的；
 *   2. 删除「快速采集」抽屉入口：采集命令队列未接通时它本就不可见，
 *      且「市场 JD 采集」页已提供完整的下发与浏览能力。
 *
 * 进度轮询与结果解析保持原样：Agent 是异步的，必须轮询到终态后再取任务详情，
 * 否则用户看到的是「已提交」但永远等不到结果。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheck, CircleClose, Loading } from '@element-plus/icons-vue'
import { getAgentProgress, getEvolutionTask, runEvolutionAgent, searchEvolutionExternalResources } from '@/api/evolution'
import type { AgentProgressVO } from '@/api/evolution'
import { pagePosts } from '@/api/post'
import type { PostPost } from '@/api/types'
import { resolveApiErrorMessage } from '@/utils/request-error-message'
import { statusTagType, statusText } from './evolution-status'
import { useEvolutionTarget } from './use-evolution-target'
import { useCloudKnowledge } from './use-cloud-knowledge'

const props = defineProps<{
  /** 从「市场 JD 统计」跳转过来时带的能力社区线索（仅作展示与预填，不直接采信） */
  marketContext?: { candidateName: string; abilities: string; reason: string; evidenceCount: string } | null
  /** 跳转时预选的目标岗位 */
  initialPostId?: number
}>()

const emit = defineEmits<{ (e: 'task-created'): void }>()

const router = useRouter()
const { targetPostId, targetPostName, industry, businessDomain, externalQuery, setTarget } = useEvolutionTarget()

// ---------- 云知识库可用性 ----------
/**
 * 云知识库未配置时，开关必须**置灰且复位为「关」**。
 *
 * 只置灰不复位是不够的：开关停在「开」但不可点，用户会认为这次演化已经带上了
 * 云知识库证据 —— 而后端此时既没有凭证也没有集合目标，这一路证据实际是空的。
 * 置灰的语义是「当前不可选」，那就必须同时表达成「不包含」。
 *
 * 判据与「知识资产」页的云知识库卡片同源（`/api/rag/cloud/status` 的 `usable`），
 * 避免出现「那边说未配置、这边能勾」的分叉。
 */
const {
  usable: cloudKbUsable,
  ensureLoaded: ensureCloudKbLoaded,
  notConfiguredHint: cloudKbHint,
  warnNotConfigured: warnCloudKbNotConfigured,
} = useCloudKnowledge()

// ---------- 目标岗位 ----------
const postSearchLoading = ref(false)
const postOptions = ref<PostPost[]>([])
const agentForm = reactive({
  triggerType: 'MANUAL_RUN',
  includeWhitepaper: true,
  // 由下面的可用性 watcher 决定：状态未拉到 / 未配置时一律为 false
  includeCloudKnowledge: false,
  includeMarketJd: false,
  includeZhihu: true,
})

// immediate：状态拉取前先按「未配置」算（不包含），拿到 usable=true 后才置为包含
watch(
  cloudKbUsable,
  value => {
    agentForm.includeCloudKnowledge = value
  },
  { immediate: true },
)

const selectedPost = computed(() => postOptions.value.find(post => post.id === targetPostId.value) || null)

async function searchPosts(keyword: string) {
  postSearchLoading.value = true
  try {
    const res = await pagePosts({ current: 1, size: 500, keyword: keyword || undefined })
    postOptions.value = res.data?.records || []
  } catch (error) {
    // 岗位下拉拉不到时给出原因，不要让用户以为「系统里没有岗位」
    ElMessage.error(resolveApiErrorMessage(error, '加载岗位列表失败'))
  } finally {
    postSearchLoading.value = false
  }
}

function handlePostChange(postId: number | undefined) {
  const post = postOptions.value.find(item => item.id === postId) || null
  setTarget(postId, post?.postName)
  externalResult.value = null
}

// ---------- 外部趋势（知乎） ----------
const externalLoading = ref(false)
const externalResult = ref<Awaited<ReturnType<typeof searchEvolutionExternalResources>>['data'] | null>(null)

async function handleExternalSearch() {
  if (!externalQuery.value) {
    ElMessage.warning('请先选择目标岗位')
    return
  }
  externalLoading.value = true
  try {
    const res = await searchEvolutionExternalResources({ query: externalQuery.value.trim(), count: 8 })
    externalResult.value = res.data
  } catch (error) {
    // 外部来源是「趋势参考」，失败不影响主流程，所以只提示不阻断
    ElMessage.error(resolveApiErrorMessage(error, '外部趋势资源暂时不可用'))
  } finally {
    externalLoading.value = false
  }
}

// ---------- 运行与轮询 ----------
const agentRunning = ref(false)
const agentProgress = ref<AgentProgressVO | null>(null)
const agentResult = ref<any>(null)

async function handleRunAgent() {
  if (!targetPostId.value) {
    ElMessage.warning('请选择目标岗位')
    return
  }
  agentRunning.value = true
  agentProgress.value = null
  agentResult.value = null
  try {
    const res = await runEvolutionAgent({
      postId: targetPostId.value,
      industry: industry.value,
      businessDomain: businessDomain.value,
      triggerType: agentForm.triggerType,
      includeWhitepaper: agentForm.includeWhitepaper,
      includeCloudKnowledge: agentForm.includeCloudKnowledge,
      includeMarketJd: agentForm.includeMarketJd,
      includeZhihu: agentForm.includeZhihu,
    })
    agentResult.value = res.data
    if (res.data.taskId) {
      try {
        const progressRes = await getAgentProgress(res.data.taskId)
        agentProgress.value = progressRes.data
      } catch {
        // 首次取进度失败不影响轮询，下面会继续拉
      }
      ElMessage.success('演化 Agent 已进入队列，正在后台分析')
      void pollAgentProgress(res.data.taskId)
    }
    emit('task-created')
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '提交演化任务失败'))
  } finally {
    agentRunning.value = false
  }
}

async function pollAgentProgress(taskId: number) {
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
            if (summary?.savedChangeItems > 0) {
              ElMessage.success(`岗位演化分析已完成，生成 ${summary.savedChangeItems} 条待审核变更`)
            } else if (summary?.harnessBlock > 0) {
              ElMessage.warning(`分析完成，但 ${summary.harnessBlock} 条提议被 Harness 阻断，请查看详情原因`)
            } else {
              ElMessage.info('岗位演化分析已完成，当前没有可审核的变更')
            }
          } catch {
            ElMessage.success('岗位演化分析已完成，请查看任务结果')
          }
        } else {
          ElMessage.error('岗位演化分析失败，请查看任务错误信息')
        }
        emit('task-created')
        return
      }
    } catch {
      // 轮询失败即停止：继续拉只会产生一连串重复报错
      return
    }
  }
}

onMounted(() => {
  // 云知识库可用性：与另两个入口共用同一次请求（composable 内部去重）
  void ensureCloudKbLoaded()
  if (props.initialPostId) {
    // 从市场发现线索跳进来：先按关键词把岗位选项拉出来，否则下拉里没有目标岗位
    void searchPosts('').then(() => {
      const post = postOptions.value.find(item => item.id === props.initialPostId)
      setTarget(props.initialPostId, post?.postName)
    })
  }
})

// 行业/领域变化时刷新检索词预览
watch([industry, businessDomain], () => {
  externalResult.value = null
})
</script>

<template>
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

        <el-alert v-if="marketContext" type="info" :closable="false">
          <template #title>市场发现线索：{{ marketContext.candidateName }}</template>
          <div>
            能力社区：{{ marketContext.abilities }}。{{ marketContext.reason }}。
            系统会重新检索 {{ marketContext.evidenceCount }} 条关联市场 JD 原文，不直接采信跳转参数。
          </div>
        </el-alert>

        <div class="evo-form-grid">
          <div class="evo-form-row">
            <label class="evo-label">目标岗位</label>
            <el-select
              :model-value="targetPostId"
              filterable
              remote
              reserve-keyword
              clearable
              placeholder="输入岗位名称搜索"
              :remote-method="searchPosts"
              :loading="postSearchLoading"
              @update:model-value="handlePostChange"
            >
              <el-option v-for="p in postOptions" :key="p.id" :label="`${p.postName}（${p.postCode || p.id}）`" :value="p.id" />
            </el-select>
          </div>
          <div class="evo-form-row">
            <label class="evo-label">行业</label>
            <el-input v-model="industry" placeholder="如：新一代信息技术" />
          </div>
          <div class="evo-form-row">
            <label class="evo-label">业务领域</label>
            <el-input v-model="businessDomain" placeholder="如：企业数字化平台" />
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
          <el-switch v-model="agentForm.includeWhitepaper" active-text="权威材料" />
          <!--
            云知识库：未配置时置灰。`el-switch` 一旦 disabled 就吞掉点击，
            用户只会觉得「点不动」，所以在包裹层上补一句可点击的说明。
          -->
          <el-tooltip :content="cloudKbHint" :disabled="cloudKbUsable" placement="top">
            <span class="evo-switch-guard" @click="warnCloudKbNotConfigured">
              <el-switch v-model="agentForm.includeCloudKnowledge" :disabled="!cloudKbUsable" active-text="云知识库" />
              <el-tag v-if="!cloudKbUsable" size="small" type="info" effect="plain">未配置</el-tag>
            </span>
          </el-tooltip>
          <el-switch v-model="agentForm.includeMarketJd" active-text="市场 JD" />
          <el-switch v-model="agentForm.includeZhihu" active-text="外部趋势" />
        </div>
        <div class="evo-hint">
          这里只选择**已准备好**的证据来源。准备材料请展开上方「证据准备」。
          最终仍以岗位能力模型与人工审核结果为准。
        </div>
        <div>
          <el-button type="primary" :loading="agentRunning" size="large" @click="handleRunAgent">
            运行岗位演化 Agent
          </el-button>
        </div>
      </div>
    </section>

    <!-- 外部趋势参考：检索词跟随目标岗位，所以必须和岗位选择器在一起 -->
    <section class="evo-card evo-card--external">
      <div class="evo-card__head">
        <span class="evo-card__title">外部趋势参考</span>
        <el-tag type="success" size="small">趋势参考</el-tag>
      </div>
      <div class="evo-card__body">
        <div class="evo-source-note">
          <strong>来源边界</strong>
          <span>知乎公开内容只提供行业趋势背景；不会直接写入岗位能力模型，也不会替代市场 JD、权威材料或云知识库。</span>
        </div>
        <div class="evo-external-search">
          <div class="evo-query-preview">
            <span class="evo-query-preview__label">当前检索词</span>
            <strong>{{ externalQuery || '尚未选择目标岗位' }}</strong>
          </div>
          <el-button type="primary" :loading="externalLoading" :disabled="!externalQuery" @click="handleExternalSearch">
            按岗位采集趋势
          </el-button>
        </div>
        <div v-if="externalResult" class="evo-external-meta">
          <el-tag size="small" type="info">{{ externalResult.sourceType }}</el-tag>
          <span>保留 {{ externalResult.items.length }} 条</span>
          <span>过滤 {{ externalResult.filteredCount + externalResult.noiseRemovedCount }} 条</span>
          <span>去重 {{ externalResult.deduplicatedCount }} 条</span>
        </div>
        <div v-if="externalResult?.degraded" class="evo-external-empty">外部来源暂不可用，岗位演化主流程不受影响。</div>
        <div v-else-if="externalResult?.items.length" class="evo-external-list">
          <a
            v-for="item in externalResult.items"
            :key="item.url + item.contentId"
            :href="item.url"
            target="_blank"
            rel="noreferrer"
            class="evo-external-item"
          >
            <strong>{{ item.title }}</strong>
            <span>{{ item.summary }}</span>
            <small>赞 {{ item.voteUpCount ?? 0 }} · 评 {{ item.commentCount ?? 0 }} · 外部参考</small>
          </a>
        </div>
        <div v-else-if="externalResult" class="evo-external-empty">没有找到可用的趋势参考。</div>
      </div>
    </section>

    <section v-if="agentProgress" class="evo-card">
      <div class="evo-card__head">
        <span class="evo-card__title">执行进度</span>
        <el-tag :type="agentProgress.percent >= 100 ? 'success' : 'warning'" size="small">{{ agentProgress.percent }}%</el-tag>
      </div>
      <div class="evo-card__body">
        <el-progress
          :percentage="agentProgress.percent"
          :status="agentProgress.currentStep === 'FAILED' ? 'exception' : (agentProgress.percent >= 100 ? 'success' : undefined)"
        />
        <el-alert
          v-if="agentProgress.currentStep === 'FAILED'"
          type="error"
          :closable="false"
          show-icon
          title="岗位演化分析失败"
          :description="agentProgress.errorMessage || '任务执行失败，请查看任务详情或稍后重试。'"
        />
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
        <el-button
          v-if="agentResult.taskId"
          type="primary"
          link
          @click="router.push(`/post/evolution/detail/${agentResult.taskId}`)"
        >查看详情</el-button>
      </div>
      <div class="evo-card__body">
        <div class="evo-result-meta">
          <div><span>任务ID</span><strong>{{ agentResult.taskId }}</strong></div>
          <div><span>状态</span><el-tag :type="statusTagType(agentResult.taskStatus)" size="small">{{ statusText(agentResult.taskStatus) }}</el-tag></div>
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
</template>
