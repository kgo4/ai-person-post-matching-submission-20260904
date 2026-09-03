<template>
  <div class="emerging-post-page">
    <el-card shadow="never" class="discovery-card">
      <template #header>
        <div class="card-header">
          <div>
            <span>市场驱动岗位发现</span>
            <span class="header-subtitle">页面自动基于已分析的市场 JD 计算技能共现、趋势与岗位差异</span>
          </div>
        </div>
      </template>

      <div class="discovery-summary">
        <div class="summary-item">
          <span>有效 JD</span>
          <strong>{{ marketInsight?.analyzedJdCount ?? 0 }}</strong>
        </div>
        <div class="summary-item">
          <span>运行模式</span>
          <el-tag :type="modeTagType(discoveryMode)" effect="light">{{ modeLabel(discoveryMode) }}</el-tag>
        </div>
        <div class="summary-item">
          <span>候选数量</span>
          <strong>{{ marketInsight?.candidateCount ?? discoveryCandidates.length }}</strong>
        </div>
        <div class="summary-item summary-item--quality">
          <span>来源平台</span>
          <strong>{{ marketInsight?.sourcePlatformCount ?? 0 }}</strong>
        </div>
        <div class="summary-item summary-item--quality">
          <span>独立招聘主体</span>
          <strong>{{ marketInsight?.independentEmployerCount ?? 0 }}</strong>
        </div>
        <div class="summary-item summary-item--quality">
          <span>去重</span>
          <strong>{{ marketInsight?.deduplicatedCount ?? 0 }}</strong>
        </div>
        <div class="summary-item summary-item--quality">
          <span>噪声过滤</span>
          <strong>{{ marketInsight?.noiseFilteredCount ?? 0 }}</strong>
        </div>
        <div class="summary-note">
          <span v-if="discoveryMode === 'OBSERVATION'">小样本仅生成观察信号，不能直接创建岗位。</span>
          <span v-else>候选结果需经过现有岗位演化或新兴岗位人工确认流程。</span>
        </div>
      </div>

      <el-empty v-if="!discovering && discoveryLoaded && !discoveryCandidates.length" description="当前已分析市场 JD 尚未形成可展示的技能社区；导入并完成市场 JD 分析后将自动更新" :image-size="72" />
      <el-table v-else-if="discoveryCandidates.length" :data="discoveryCandidates" size="small" class="discovery-table">
        <el-table-column prop="candidateName" label="候选方向" min-width="170" show-overflow-tooltip />
        <el-table-column label="核心技能" min-width="220">
          <template #default="{ row }">
            <el-tag v-for="skill in row.coreAbilities" :key="skill" size="small" class="skill-tag">{{ skill }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="emergenceScore" label="新兴度" width="90">
          <template #default="{ row }"><strong>{{ row.emergenceScore ?? 0 }}</strong></template>
        </el-table-column>
        <el-table-column prop="trendGrowthScore" label="趋势" width="80" />
        <el-table-column prop="cohesionScore" label="凝聚度" width="90" />
        <el-table-column label="证据覆盖" min-width="165">
          <template #default="{ row }">
            <span class="evidence-coverage">
              {{ row.frequency ?? 0 }} 条 JD · {{ row.sourcePlatformCount ?? 0 }} 个平台 · {{ row.independentEmployerCount ?? 0 }} 个主体
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag :type="candidateStatusType(row)" size="small">{{ candidateStatusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="建议流转" min-width="150">
          <template #default="{ row }">
            <span>{{ row.differentiationReason || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.recommendedAction === 'POST_EVOLUTION'" link type="primary" size="small" @click="goToEvolution(row)">岗位演化</el-button>
            <el-button v-else link type="primary" size="small" @click="useDiscoveryCandidate(row)">人工定义</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-row :gutter="16">
      <!-- 左侧：输入表单 -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <span>新兴岗位定义</span>
          </template>
          <el-form :model="form" label-position="top">
            <el-form-item label="岗位名称" required>
              <el-input v-model="form.postName" placeholder="如：AI提示词工程师" />
            </el-form-item>
            <el-form-item label="岗位描述">
              <el-input v-model="form.description" type="textarea" :rows="4" placeholder="描述岗位的核心职责和工作内容" />
            </el-form-item>
            <el-form-item label="行业/业务方向">
              <el-input v-model="form.industry" placeholder="如：人工智能、金融科技" />
            </el-form-item>
            <el-form-item label="关键职责">
              <el-input v-model="form.keyResponsibilities" type="textarea" :rows="3" placeholder="列出关键职责，每行一条" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="analyzing" @click="handleAnalyze">AI分析推荐</el-button>
              <el-button v-if="result" text type="primary" :loading="optimizing" @click="handleReanalyze" style="margin-left:8px">
                人工优化后重分析
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 数据源概览 -->
        <el-card v-if="result?.dataSources?.length" shadow="never" style="margin-top:12px">
          <template #header>
            <span>数据源概览</span>
          </template>
          <div class="source-tags">
            <el-tag
              v-for="src in result.dataSources"
              :key="src"
              size="small"
              type="info"
              style="margin:2px"
            >{{ src }}</el-tag>
          </div>
          <div v-if="result.crossValidation" style="margin-top:8px;font-size:12px;color:var(--app-text-muted)">
            覆盖 {{ result.crossValidation.sourceDiversity }} 类数据源 ·
            最新数据 {{ freshnessLabel(result.crossValidation.freshnessLevel) }}
          </div>
        </el-card>

      </el-col>

      <!-- 右侧：分析结果 -->
      <el-col :span="14">
        <!-- 结构化岗位定义 -->
        <el-card v-if="hasStructuredDefinition" shadow="never" style="margin-bottom:12px">
          <template #header>
            <span>岗位定义</span>
          </template>
          <div v-if="result?.reasoning" style="margin-bottom:12px;padding:8px 12px;background:var(--el-fill-color-light);border-radius:4px;font-size:13px;color:var(--el-text-color-regular)">
            <strong>岗位摘要：</strong>{{ result.reasoning }}
          </div>
          <el-collapse>
            <el-collapse-item v-if="result?.coreResponsibilities?.length" title="核心职责" name="responsibilities">
              <ul class="definition-list">
                <li v-for="(item, idx) in result.coreResponsibilities" :key="idx">{{ item }}</li>
              </ul>
            </el-collapse-item>
            <el-collapse-item v-if="result?.requiredSkills?.length" title="必备技能" name="required">
              <ul class="definition-list">
                <li v-for="(item, idx) in result.requiredSkills" :key="idx">{{ item }}</li>
              </ul>
            </el-collapse-item>
            <el-collapse-item v-if="result?.bonusSkills?.length" title="加分技能" name="bonus">
              <ul class="definition-list">
                <li v-for="(item, idx) in result.bonusSkills" :key="idx">{{ item }}</li>
              </ul>
            </el-collapse-item>
            <el-collapse-item v-if="result?.industryScenarios?.length" title="典型行业应用场景" name="scenarios">
              <ul class="definition-list">
                <li v-for="(item, idx) in result.industryScenarios" :key="idx">{{ item }}</li>
              </ul>
            </el-collapse-item>
          </el-collapse>
        </el-card>

        <!-- 交叉验证摘要 -->
        <el-card v-if="result?.crossValidation" shadow="never" style="margin-bottom:12px">
          <template #header>
            <div class="card-header">
              <span>交叉验证摘要</span>
              <el-tag
                :type="result.crossValidation.consistencyScore >= 80 ? 'success' : result.crossValidation.consistencyScore >= 60 ? 'warning' : 'danger'"
                size="small"
              >一致性 {{ result.crossValidation.consistencyScore }}%</el-tag>
            </div>
          </template>
          <div class="cv-grid">
            <div v-for="item in result.crossValidation.sourceBreakdown" :key="item.sourceType" class="cv-item">
              <span class="cv-item__label">{{ item.label }}</span>
              <span class="cv-item__count">{{ item.abilityCount }} 项能力</span>
            </div>
          </div>
        </el-card>

        <!-- 推荐原型 -->
        <el-card v-if="result?.recommendedPrototypes?.length" shadow="never" style="margin-bottom: 12px">
          <template #header>
            <span>推荐岗位原型</span>
          </template>
          <el-table :data="result.recommendedPrototypes" border size="small">
            <el-table-column prop="prototypeName" label="原型名称" />
            <el-table-column prop="industry" label="行业" width="100" />
            <el-table-column prop="category" label="分类" width="80" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="applyPrototype(row)">应用</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 推荐能力 — 含证据来源、置信度、人工优化入口 -->
        <el-card v-if="result?.recommendedAbilities?.length" shadow="never">
          <template #header>
            <div class="card-header">
              <span>推荐能力要求（共 {{ result.recommendedAbilities.length }} 项）</span>
              <div>
                <el-button v-if="editedAbilities.length > 0" text type="warning" size="small" @click="resetEdits" style="margin-right:8px">
                  撤销修改 ({{ editedAbilities.length }})
                </el-button>
                <el-button type="primary" size="small" :loading="creating" :disabled="creating" @click="handleCreatePost">一键创建岗位</el-button>
              </div>
            </div>
          </template>
          <el-table :data="displayAbilities" border size="small">
            <el-table-column type="index" label="序号" width="50" />
            <el-table-column prop="suggestedName" label="能力标签" min-width="130">
              <template #default="{ row }">
                <div class="ability-name-cell">
                  <span>{{ row.suggestedName }}</span>
                  <el-tag v-if="row.hallucinationRisk" type="danger" size="small" effect="dark">幻觉风险</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="置信度" width="160">
              <template #default="{ row }">
                <ConfidenceGauge
                  :score="row.confidenceScore ?? 70"
                  :evidence-count="row.evidenceSources?.length ?? 0"
                  :show-evidence-count="true"
                  :show-hallucination-risk="true"
                  size="small"
                />
              </template>
            </el-table-column>
            <el-table-column label="证据来源" min-width="180">
              <template #default="{ row }">
                <div class="source-stack">
                  <el-tag v-for="(ev, idx) in (row.evidenceSources || [])" :key="`${ev.sourceType}-${idx}`" size="small" effect="plain" type="info">
                    {{ evidenceSourceLabel(ev) }}
                  </el-tag>
                  <span v-if="!row.evidenceSources?.length" class="text-muted">暂无来源</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="tagCategory" label="分类" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.tagCategory === 'TECHNICAL'" type="primary" size="small">技术</el-tag>
                <el-tag v-else-if="row.tagCategory === 'BUSINESS'" type="warning" size="small">业务</el-tag>
                <el-tag v-else-if="row.tagCategory === 'SOFT'" type="success" size="small">软技能</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="minRequiredLevel" label="等级" width="55" />
            <el-table-column prop="weight" label="权重" width="55" />
            <el-table-column label="核心" width="55">
              <template #default="{ row }">
                <el-tag v-if="row.isCore" type="danger" size="small">核心</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="匹配" width="70">
              <template #default="{ row }">
                <el-tag v-if="row.matchStatus === 'MATCHED'" type="success" size="small">已有</el-tag>
                <el-tag v-else-if="row.matchStatus === 'SIMILAR'" type="warning" size="small">相似</el-tag>
                <el-tag v-else type="info" size="small">新建</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row, $index }">
                <el-button link type="danger" size="small" @click="removeAbility($index)">
                  移除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="result?.reasoning" style="margin-top:10px;font-size:12px;color:var(--app-text-muted);line-height:1.6">
            <strong>分析依据：</strong>{{ result.reasoning }}
          </div>
        </el-card>

        <!-- 空状态 -->
        <el-card v-if="!result && !analyzing" shadow="never">
          <el-empty description="输入岗位信息后点击AI分析推荐" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { analyzeEmergingPost, confirmEmergingPost, discoverEmergingPosts, getMarketInsight, reanalyzeEmergingPost, getEmergingAnalyzeTask } from '@/api/emerging-post'
import type { EmergingAbilityItem, EmergingPostDiscovery, EmergingPostResponse, MarketInsight } from '@/api/emerging-post'
import type { PostPrototypeVO } from '@/api/post-prototype'
import { useRouter } from 'vue-router'
import ConfidenceGauge from '@/components/common/ConfidenceGauge.vue'

const router = useRouter()
const analyzing = ref(false)
const optimizing = ref(false)
const creating = ref(false)
const result = ref<EmergingPostResponse | null>(null)
const removedIndices = ref<Set<number>>(new Set())
const discovering = ref(false)
const discoveryLoaded = ref(false)
const discoveryCandidates = ref<EmergingPostDiscovery[]>([])
const marketInsight = ref<MarketInsight | null>(null)
const discoveryMode = computed(() => discoveryCandidates.value[0]?.discoveryMode || inferMode(marketInsight.value?.analyzedJdCount || 0))

const form = ref({
  postName: '',
  description: '',
  industry: '',
  keyResponsibilities: ''
})

const displayAbilities = computed(() => {
  if (!result.value?.recommendedAbilities) return []
  return result.value.recommendedAbilities.filter((_, idx) => !removedIndices.value.has(idx))
})

const editedAbilities = computed(() => {
  if (!result.value?.recommendedAbilities) return []
  return result.value.recommendedAbilities.filter((_, idx) => removedIndices.value.has(idx))
})

const hasStructuredDefinition = computed(() => {
  if (!result.value) return false
  return !!(result.value.coreResponsibilities?.length ||
    result.value.requiredSkills?.length ||
    result.value.bonusSkills?.length ||
    result.value.industryScenarios?.length)
})

function freshnessLabel(level: string): string {
  const map: Record<string, string> = { FRESH: '7天内', RECENT: '30天内', STALE: '超过30天' }
  return map[level] || level
}

function removeAbility(index: number) {
  const next = new Set(removedIndices.value)
  if (next.has(index)) {
    next.delete(index)
  } else {
    next.add(index)
  }
  removedIndices.value = next
}

function resetEdits() {
  removedIndices.value = new Set()
}

function inferMode(count: number): 'OBSERVATION' | 'CANDIDATE' | 'DISCOVERY' {
  if (count >= 500) return 'DISCOVERY'
  if (count >= 50) return 'CANDIDATE'
  return 'OBSERVATION'
}

function modeLabel(mode: string) {
  return ({ OBSERVATION: '观察模式', CANDIDATE: '候选模式', DISCOVERY: '发现模式' } as Record<string, string>)[mode] || mode
}

function modeTagType(mode: string) {
  return ({ OBSERVATION: 'info', CANDIDATE: 'warning', DISCOVERY: 'success' } as Record<string, 'info' | 'warning' | 'success'>)[mode] || 'info'
}

function candidateStatusLabel(candidate: EmergingPostDiscovery) {
  if (candidate.reviewStatus === 'OBSERVATION') return '仅观察'
  return candidate.recommendedAction === 'POST_EVOLUTION' ? '演化候选' : '待人工确认'
}

function candidateStatusType(candidate: EmergingPostDiscovery) {
  if (candidate.reviewStatus === 'OBSERVATION') return 'info'
  return candidate.recommendedAction === 'POST_EVOLUTION' ? 'warning' : 'success'
}

async function loadDiscovery() {
  discovering.value = true
  try {
    // 列表与顶部洞察必须使用同一候选集，否则会出现统计 50 条、列表仅 10 条的错觉。
    const [candidatesResult, insightResult] = await Promise.all([discoverEmergingPosts(50), getMarketInsight()])
    discoveryCandidates.value = candidatesResult.data || []
    marketInsight.value = insightResult.data || null
    discoveryLoaded.value = true
  } catch (error: any) {
    ElMessage.error('市场岗位发现失败: ' + (error.message || '未知错误'))
  } finally {
    discovering.value = false
  }
}

function evidenceSourceLabel(source: { sourceType?: string; sourceName?: string }): string {
  const labels: Record<string, string> = {
    MARKET_JD: '市场招聘数据', ZHIHU_TREND: '知乎趋势',
    CLOUD_KNOWLEDGE_INTERNAL: '云端知识库', INDUSTRY_WHITEPAPER: '行业白皮书', RESUME: '简历资料',
  }
  return source.sourceName?.trim() || labels[source.sourceType || ''] || source.sourceType || '未知来源'
}

function useDiscoveryCandidate(candidate: EmergingPostDiscovery) {
  form.value.postName = candidate.candidateName
  form.value.description = `${candidate.description || ''}\n\n核心技能：${(candidate.coreAbilities || []).join('、')}\n${candidate.differentiationReason || ''}`.trim()
  ElMessage.info('已填入候选岗位草案，请补充职责后进行 AI 分析与人工确认')
}

function goToEvolution(candidate: EmergingPostDiscovery) {
  const postId = candidate.relatedExistingPostIds?.[0]
  if (!postId) {
    ElMessage.warning('未找到可演化的既有岗位')
    return
  }
  router.push({
    path: '/post/evolution',
    query: {
      postId: String(postId), trigger: 'MARKET_DISCOVERY', candidateName: candidate.candidateName,
      abilities: (candidate.coreAbilities || []).join('、'), reason: candidate.differentiationReason || '',
      evidenceCount: String((candidate.sourceRefs || []).length),
    },
  })
}

const handleAnalyze = async () => {
  if (!form.value.postName) {
    ElMessage.warning('请输入岗位名称')
    return
  }
  analyzing.value = true
  removedIndices.value = new Set()
  try {
    const submitted = await analyzeEmergingPost(form.value)
    const taskId = (submitted.data as any)?.taskId
    if (!taskId) throw new Error('后台任务提交失败')
    ElMessage.info('分析任务已提交，后台处理中；你可以继续查看页面其他内容')
    for (let i = 0; i < 120; i++) {
      await new Promise(resolve => setTimeout(resolve, 1500))
      const task = await getEmergingAnalyzeTask(taskId)
      if (task.data?.status === 'SUCCEEDED') {
        result.value = task.data.result || null
        ElMessage.success('分析完成')
        return
      }
      if (task.data?.status === 'FAILED') throw new Error(task.data.error || '后台分析失败')
    }
    ElMessage.info('分析仍在后台运行，请稍后重新进入页面查看结果')
  } catch (e: any) {
    ElMessage.error('分析失败: ' + (e.message || '未知错误'))
  } finally {
    analyzing.value = false
  }
}

const handleReanalyze = async () => {
  if (!result.value) return
  optimizing.value = true
  try {
    const res = await reanalyzeEmergingPost({
      ...form.value,
      abilities: displayAbilities.value,
    })
    result.value = res.data
    removedIndices.value = new Set()
    ElMessage.success('重新分析完成')
  } catch (e: any) {
    ElMessage.error('重分析失败: ' + (e.message || '未知错误'))
  } finally {
    optimizing.value = false
  }
}

const applyPrototype = async (_prototype: PostPrototypeVO) => {
  ElMessage.info('请先创建岗位，然后在岗位详情页应用原型')
}

const handleCreatePost = async () => {
  if (creating.value) return
  const abilities = displayAbilities.value
  if (!abilities.length) {
    ElMessage.warning('没有可创建的能力项')
    return
  }
  creating.value = true
  try {
    await ElMessageBox.confirm('确认创建岗位并应用推荐的能力模型？', '确认')
    const res = await confirmEmergingPost({
      postName: form.value.postName,
      description: form.value.description,
      abilities,
    })
    if (res.data) {
      ElMessage.success('岗位创建成功，能力模型将在后台继续完善')
      router.push(`/post/detail/${res.data}`)
    } else {
      throw new Error('创建接口未返回岗位编号')
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      const message = String(e?.message || '')
      if (message.toLowerCase().includes('timeout') || message.includes('超时')) {
        ElMessage.warning('请求超时，岗位可能已创建，请刷新岗位列表确认')
      } else {
        ElMessage.error('创建失败: ' + (e.message || '未知错误'))
      }
    }
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  loadDiscovery()
})

</script>

<style scoped>
.emerging-post-page {
  padding: 16px;
}
.discovery-card { margin-bottom: 16px; }
.header-subtitle { margin-left: 10px; color: var(--app-text-muted); font-size: 12px; font-weight: normal; }
.discovery-summary { display: flex; align-items: center; flex-wrap: wrap; gap: 16px 28px; margin-bottom: 12px; }
.summary-item { display: flex; align-items: center; gap: 8px; color: var(--app-text-secondary); font-size: 13px; }
.summary-item strong { color: var(--app-text-primary); font-size: 18px; }
.summary-item--quality strong { font-size: 15px; }
.summary-note { margin-left: auto; color: var(--app-text-muted); font-size: 12px; }
.evidence-coverage { color: var(--app-text-secondary); font-size: 12px; white-space: nowrap; }
.skill-tag { margin: 2px 4px 2px 0; }
.discovery-table :deep(.cell) { line-height: 1.45; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.market-jd-input-row { display: flex; width: 100%; gap: 8px; align-items: flex-start; }
.cv-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.cv-item {
  display: flex;
  flex-direction: column;
  padding: 8px 12px;
  border-radius: 10px;
  background: rgba(148, 163, 184, 0.06);
}
.cv-item__label {
  font-size: 12px;
  color: var(--app-text-secondary);
}
.cv-item__count {
  font-size: 16px;
  font-weight: 800;
  color: var(--app-primary);
}
.evidence-stack,
.source-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.ability-name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.text-muted {
  color: var(--app-text-muted);
  font-size: 12px;
}
.source-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.definition-list {
  margin: 0;
  padding-left: 20px;
  list-style: disc;
}
.definition-list li {
  margin-bottom: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}
</style>
