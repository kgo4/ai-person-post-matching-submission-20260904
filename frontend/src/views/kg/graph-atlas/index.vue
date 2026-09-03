<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Aim, Connection, DataAnalysis, Document, Download, Operation, Refresh, Search, Setting, WarningFilled } from '@element-plus/icons-vue'
import AbilityForceGraph from '@/components/graph/AbilityForceGraph.vue'
import type { ForceEdge, ForceNode } from '@/components/graph/AbilityForceGraph.vue'
import { createSnapshot, getEmployeeCenteredGraph, getGraphBuildTask, getGraphBusinessAnalysis, getMatchGraphContext, getMemoryGraph, getNeo4jHealth, getPanorama, getPostCenteredGraph, getTimeline, rebuildFullGraph } from '@/api/kg'
import type { GraphData, GraphNode, Neo4jHealth, TimelineEvent } from '@/api/kg'
import { pageEmployees } from '@/api/employee'
import { pagePosts } from '@/api/post'
import { NODE_TYPE_LABEL_MAP, NODE_TYPE_OPTIONS, formatTime, getRelatedNodeLabel, getRelatedNodes, graphDataFromMatchContext, groupRelationsByDirection, toForceEdge, toForceNode } from '@/views/kg/workbench/kgWorkbench'
import { isBuildPollingExpired, readGraphRouteSelection } from './graphAtlasState'
import { useRoute, useRouter } from 'vue-router'

type Workspace = 'context' | 'explore' | 'operations'
const route = useRoute()
const router = useRouter()
const activeWorkspace = ref<Workspace>('context')
const loading = ref(false)
const rebuildLoading = ref(false)
const graphData = ref<GraphData | null>(null)
const selectedNode = ref<ForceNode | null>(null)
const graphRef = ref<InstanceType<typeof AbilityForceGraph> | null>(null)
const timelineEvents = ref<TimelineEvent[]>([])
const graphHealth = ref<Neo4jHealth | null>(null)
const businessAnalysis = ref<any>(null)
const performanceMode = ref(false)
const graphAreaRef = ref<HTMLElement | null>(null)
const graphCanvasWidth = ref(960)
const graphCanvasHeight = ref(620)
let resizeObserver: ResizeObserver | null = null
const employeeOptions = ref<{ id: number; label: string }[]>([])
const postOptions = ref<{ id: number; label: string }[]>([])
const filters = reactive({ keyword: '', nodeTypes: [] as string[], limit: 80, employeeId: undefined as number | undefined, postId: undefined as number | undefined })
const selection = readGraphRouteSelection(route.query as Record<string, unknown>)
filters.employeeId = selection.employeeId
filters.postId = selection.postId

const forceNodes = computed<ForceNode[]>(() => graphData.value?.nodes.map(toForceNode) ?? [])
const forceEdges = computed<ForceEdge[]>(() => graphData.value?.edges.map(toForceEdge) ?? [])
const stats = computed(() => graphData.value?.stats)
const relatedNodes = computed(() => selectedNode.value && graphData.value ? getRelatedNodes(selectedNode.value.id, graphData.value.edges, graphData.value.nodes) : [])
const selectedMetadata = computed(() => selectedNode.value?.meta || {})
const contextStatus = computed(() => {
  if (!filters.employeeId || !filters.postId) return '请选择员工与岗位，查看本次 AI 匹配可读取的能力和证据。'
  if (!graphData.value?.available) return '图谱上下文暂不可用，不会阻断匹配主业务。'
  return `已加载 ${stats.value?.abilityCount ?? 0} 项岗位能力及其可用证据。`
})
const graphVersion = computed(() => {
  const value = forceNodes.value.find(node => node.meta?.graphVersion)?.meta?.graphVersion
  return typeof value === 'string' ? value : '未标记版本'
})
const businessMetrics = computed(() => {
  const nodes = graphData.value?.nodes || []
  const edges = graphData.value?.edges || []
  const nodeType = (node: any) => String(node?.type || '').toUpperCase()
  const edgeType = (edge: any) => String(edge?.type || '').toUpperCase()
  const isAbilityNode = (node: any) => {
    const type = nodeType(node)
    const id = String(node?.id || '').toUpperCase()
    return type.includes('ABILITY') || id.startsWith('ABILITY:') || id.startsWith('EMP_ABILITY:')
  }
  return {
    posts: nodes.filter(n => ['POST', 'JOB', 'POSITION'].includes(nodeType(n))).length,
    abilities: nodes.filter(isAbilityNode).length,
    evidence: nodes.filter(n => ['EVIDENCE', 'DOCUMENT', 'KNOWLEDGE_DOCUMENT'].includes(nodeType(n))).length,
    prerequisites: edges.filter(e => edgeType(e) === 'PREREQUISITE_OF').length,
    required: edges.filter(e => ['REQUIRES', 'REQUIRES_ABILITY'].includes(edgeType(e))).length,
    covered: edges.filter(e => ['HAS_ABILITY', 'HAS_ABILITY_FACT'].includes(edgeType(e))).length,
    gaps: edges.filter(e => ['REQUIRES', 'REQUIRES_ABILITY'].includes(edgeType(e))).length - edges.filter(e => ['HAS_ABILITY', 'HAS_ABILITY_FACT'].includes(edgeType(e))).length,
  }
})
const hasMatchSelection = computed(() => Boolean(filters.employeeId && filters.postId))
const metricLabels = computed(() => hasMatchSelection.value
  ? ['岗位要求', '人员覆盖', '能力差距', '关系总数', '有效证据']
  : ['人员能力', '能力节点', '能力差距', '关系总数', '有效证据'])

function formatSourceRef(ref: string) {
  const [entity, id] = ref.split(':')
  const labels: Record<string, string> = { EMP_VIDEO_INTERVIEW_QUESTION: '面试回答', EMP_RESUME_PARSE: '简历解析', POST_ABILITY_MODEL: '岗位能力画像', GOVERNANCE_LOG: '治理审核记录' }
  return `${labels[entity] || entity || '业务来源'}${id ? ` #${id}` : ''}`
}
function nodeTrust(node: GraphNode | ForceNode) {
  const metadata = 'metadata' in node ? node.metadata : (node as ForceNode).meta
  const status = metadata?.reviewStatus || node.status
  if (status === 'APPROVED' || status === 'PASS') return '已核验'
  if (metadata?.evidenceCount === 0 || status === 'BLOCKED') return '证据不足'
  return '待确认'
}
async function fetchGraph() {
  loading.value = true; selectedNode.value = null
  try {
    if (activeWorkspace.value === 'context' && filters.employeeId && filters.postId) {
      const res = await getMatchGraphContext(filters.employeeId, filters.postId)
      graphData.value = res.code === 200 ? graphDataFromMatchContext(res.data) : null
    } else if (activeWorkspace.value === 'operations') {
      const res = await getMemoryGraph({ limit: filters.limit })
      graphData.value = res.code === 200 ? res.data : null
    } else {
      const res = filters.employeeId ? await getEmployeeCenteredGraph(filters.employeeId) : filters.postId ? await getPostCenteredGraph(filters.postId) : await getPanorama({ keyword: filters.keyword || undefined, nodeTypes: filters.nodeTypes.length ? filters.nodeTypes : undefined, limit: filters.limit })
      graphData.value = res.code === 200 ? res.data : null
    }
  } catch (error) { console.error('加载图谱失败', error); graphData.value = null } finally { loading.value = false }
}
async function fetchSupportingData() {
  try {
    const [timeline, health] = await Promise.all([getTimeline({ limit: 12 }), getNeo4jHealth()])
    if (timeline.code === 200) timelineEvents.value = timeline.data.events || []
    if (health.code === 200) graphHealth.value = health.data
    if (filters.employeeId && filters.postId) {
      const analysis = await getGraphBusinessAnalysis(filters.employeeId, filters.postId)
      if (analysis.code === 200) businessAnalysis.value = analysis.data
    } else {
      businessAnalysis.value = null
    }
  } catch (error) { console.error('加载图谱运行状态失败', error) }
}
async function searchEmployees(keyword = '') {
  const res = await pageEmployees({ current: 1, size: 20, keyword: keyword || undefined })
  if (res.code === 200) employeeOptions.value = (res.data.records || []).map(item => ({ id: item.id, label: `${item.realName} (${item.empCode})` }))
}
async function searchPosts(keyword = '') {
  const res = await pagePosts({ current: 1, size: 20, keyword: keyword || undefined })
  if (res.code === 200) postOptions.value = (res.data.records || []).map(item => ({ id: item.id, label: `${item.postName} (${item.postCode})` }))
}
async function handleRebuild() {
  rebuildLoading.value = true
  try {
    const created = await rebuildFullGraph()
    if (created.code !== 200) throw new Error('无法创建重建任务')
    const startedAt = Date.now()
    while (!isBuildPollingExpired(Date.now() - startedAt)) {
      await new Promise(resolve => setTimeout(resolve, 1500))
      const task = await getGraphBuildTask(created.data.taskCode)
      if (task.data.taskStatus === 'SUCCEEDED') { ElMessage.success(`图谱重建完成：${task.data.result?.nodeCount ?? 0} 个节点`); await Promise.all([fetchGraph(), fetchSupportingData()]); return }
      if (task.data.taskStatus === 'FAILED') throw new Error(task.data.errorMessage || '图谱重建失败')
    }
    ElMessage.warning('重建任务仍在后台执行，可稍后刷新查看')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '图谱重建失败') } finally { rebuildLoading.value = false }
}
async function handleSnapshot() {
  if (!graphData.value) return
  const res = await createSnapshot({ snapshotType: activeWorkspace.value === 'context' ? 'MATCH_CONTEXT' : 'FULL', snapshotName: `图谱快照-${new Date().toLocaleString('zh-CN')}`, graphJson: JSON.stringify({ workspace: activeWorkspace.value, filters, graph: graphData.value }) })
  if (res.code === 200) ElMessage.success('已保存当前图谱快照')
}
function selectNode(node: ForceNode) { selectedNode.value = node }
function focusNode(id: string) { const node = forceNodes.value.find(item => item.id === id); if (node) { selectedNode.value = node; graphRef.value?.centerNode(id) } }
function resetFilters() { filters.keyword = ''; filters.nodeTypes = []; filters.limit = 80; filters.employeeId = undefined; filters.postId = undefined; fetchGraph() }
function switchWorkspace(workspace: Workspace) { activeWorkspace.value = workspace; fetchGraph() }
function measureGraphArea() {
  const el = graphAreaRef.value
  if (!el) return
  graphCanvasWidth.value = Math.max(240, el.clientWidth)
  graphCanvasHeight.value = Math.max(240, el.clientHeight)
}
watch(() => route.query, query => { const next = readGraphRouteSelection(query as Record<string, unknown>); filters.employeeId = next.employeeId; filters.postId = next.postId; if (next.employeeId && next.postId) activeWorkspace.value = 'context'; fetchGraph() })
onMounted(async () => {
  if (graphAreaRef.value) {
    resizeObserver = new ResizeObserver(measureGraphArea)
    resizeObserver.observe(graphAreaRef.value)
  }
  measureGraphArea()
  await Promise.all([searchEmployees(), searchPosts(), fetchGraph(), fetchSupportingData()])
})
onUnmounted(() => resizeObserver?.disconnect())
</script>

<template>
  <main class="graph-workbench" v-loading="loading">
    <header class="topbar">
      <div class="brand">
        <h1>AI 常读图谱</h1>
        <p>展示 AI 在匹配评估时可读取的岗位、人员、能力与证据关系</p>
      </div>
      <div class="head-actions">
        <el-tag :type="graphHealth?.status === 'UP' ? 'success' : 'info'" effect="plain">
          {{ graphHealth?.status === 'UP' ? '图谱服务可用' : '图谱服务状态未知' }}
        </el-tag>
        <el-button :icon="Refresh" @click="fetchGraph">刷新</el-button>
        <el-button type="primary" :icon="Download" :disabled="!graphData" @click="handleSnapshot">保存快照</el-button>
      </div>
    </header>

    <nav class="workspace-tabs" aria-label="图谱工作区">
      <button :class="{ active: activeWorkspace === 'context' }" @click="switchWorkspace('context')">
        <el-icon><DataAnalysis /></el-icon>AI 上下文
      </button>
      <button :class="{ active: activeWorkspace === 'explore' }" @click="switchWorkspace('explore')">
        <el-icon><Connection /></el-icon>关系探索
      </button>
      <button :class="{ active: activeWorkspace === 'operations' }" @click="switchWorkspace('operations')">
        <el-icon><Operation /></el-icon>演化与治理
      </button>
    </nav>

    <section v-if="activeWorkspace !== 'operations'" class="object-bar">
      <div class="object-field">
        <label>员工</label>
        <el-select v-model="filters.employeeId" clearable filterable remote reserve-keyword :remote-method="searchEmployees" placeholder="选择员工" @change="fetchGraph">
          <el-option v-for="item in employeeOptions" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
      </div>
      <div class="object-field">
        <label>岗位</label>
        <el-select v-model="filters.postId" clearable filterable remote reserve-keyword :remote-method="searchPosts" placeholder="选择岗位" @change="fetchGraph">
          <el-option v-for="item in postOptions" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
      </div>
      <div v-if="activeWorkspace === 'explore'" class="object-field">
        <label>关键词</label>
        <el-input v-model="filters.keyword" clearable placeholder="节点名称" @keyup.enter="fetchGraph">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
      <div v-if="activeWorkspace === 'explore'" class="object-field object-field--grow">
        <label>节点类型</label>
        <el-select v-model="filters.nodeTypes" multiple collapse-tags collapse-tags-tooltip placeholder="全部类型" @change="fetchGraph">
          <el-option v-for="option in NODE_TYPE_OPTIONS" :key="option.value" :label="option.label" :value="option.value">
            <span class="type-option"><i :style="{ background: option.color }" />{{ option.label }}</span>
          </el-option>
        </el-select>
      </div>
      <el-popover v-if="activeWorkspace === 'explore'" placement="bottom-end" :width="220" trigger="click">
        <template #reference>
          <el-button :icon="Setting">筛选设置</el-button>
        </template>
        <div class="pop-settings">
          <label>加载上限 {{ filters.limit }}</label>
          <el-slider v-model="filters.limit" :min="20" :max="300" :step="10" @change="fetchGraph" />
          <el-checkbox v-model="performanceMode">性能模式</el-checkbox>
        </div>
      </el-popover>
      <el-button plain @click="resetFilters">重置</el-button>
    </section>

    <template v-if="activeWorkspace === 'context'">
      <section class="context-summary" :class="{ unavailable: !graphData?.available && filters.employeeId && filters.postId }">
        <el-icon><WarningFilled /></el-icon>
        <div class="summary-text">
          <strong>AI 上下文状态</strong>
          <span>{{ contextStatus }}</span>
        </div>
        <div class="version-box">
          <small>图谱版本</small>
          <b>{{ graphVersion }}</b>
        </div>
      </section>
      <section class="business-metrics">
        <div><span>{{ metricLabels[0] }}</span><strong>{{ hasMatchSelection ? businessMetrics.required : businessMetrics.abilities }}</strong></div>
        <div><span>{{ metricLabels[1] }}</span><strong>{{ hasMatchSelection ? businessMetrics.covered : businessMetrics.abilities }}</strong></div>
        <div><span>{{ metricLabels[2] }}</span><strong :class="{ danger: hasMatchSelection && businessMetrics.gaps > 0 }">{{ hasMatchSelection ? Math.max(0, businessMetrics.gaps) : '—' }}</strong></div>
        <div><span>关系总数</span><strong>{{ businessAnalysis?.relationCount ?? (graphData?.edges?.length ?? 0) }}</strong></div>
        <div><span>有效证据</span><strong>{{ businessAnalysis?.evidenceCount ?? businessMetrics.evidence }}</strong></div>
        <p>图谱用于计算能力覆盖、差距和前置关系，结果会提供给匹配与学习路径 Agent。</p>
      </section>
    </template>

    <section ref="graphAreaRef" class="graph-stage">
      <AbilityForceGraph
        v-if="forceNodes.length"
        ref="graphRef"
        :nodes="forceNodes"
        :edges="forceEdges"
        :width="graphCanvasWidth"
        :height="graphCanvasHeight"
        :show-legend="true"
        :performance-mode="performanceMode"
        :selected-node-id="selectedNode?.id"
        theme="tech-light"
        @node-click="selectNode"
        @node-dblclick="selectNode"
      />
      <div v-else class="graph-empty">
        <el-icon :size="30"><Connection /></el-icon>
        <strong>暂无可展示的图谱关系</strong>
        <span>{{ activeWorkspace === 'context' ? contextStatus : '调整筛选条件，或在演化与治理中重建图谱。' }}</span>
      </div>

      <aside v-if="selectedNode" class="inspector">
        <div class="inspector-head">
          <div>
            <span class="card-kicker">{{ NODE_TYPE_LABEL_MAP[selectedNode.type] || selectedNode.type }}</span>
            <h2>{{ selectedNode.label }}</h2>
          </div>
          <el-button link :icon="Aim" @click="graphRef?.centerNode(selectedNode.id)">聚焦</el-button>
        </div>
        <el-tag
          size="small"
          :type="nodeTrust(selectedNode) === '已核验' ? 'success' : nodeTrust(selectedNode) === '证据不足' ? 'danger' : 'warning'"
        >
          {{ nodeTrust(selectedNode) }}
        </el-tag>
        <dl class="facts">
          <template v-if="selectedNode.level"><dt>等级</dt><dd>Lv.{{ selectedNode.level }}</dd></template>
          <template v-if="selectedNode.weight != null"><dt>权重</dt><dd>{{ Math.round(selectedNode.weight <= 1 ? selectedNode.weight * 100 : selectedNode.weight) }}%</dd></template>
          <template v-if="selectedMetadata.requiredLevel != null"><dt>岗位要求</dt><dd>Lv.{{ selectedMetadata.requiredLevel }}</dd></template>
          <template v-if="selectedMetadata.employeeMasteryLevel != null"><dt>人员掌握</dt><dd>Lv.{{ selectedMetadata.employeeMasteryLevel }}</dd></template>
          <template v-if="selectedMetadata.evidenceCount != null"><dt>有效证据</dt><dd>{{ selectedMetadata.evidenceCount }}</dd></template>
          <dt>直接关联</dt><dd>{{ relatedNodes.length }}</dd>
        </dl>
        <h3>证据来源</h3>
        <div v-if="selectedMetadata.sourceRefs?.length" class="source-list">
          <span v-for="sourceRef in selectedMetadata.sourceRefs" :key="sourceRef">{{ formatSourceRef(sourceRef) }}</span>
        </div>
        <p v-else class="muted">此节点当前没有可展示的证据引用。</p>
        <h3>直接关系</h3>
        <button
          v-for="relation in relatedNodes"
          :key="`${relation.edgeType}-${relation.otherNodeId}`"
          class="relation-item"
          @click="focusNode(relation.otherNodeId)"
        >
          <span>{{ getRelatedNodeLabel(relation) }}</span>
          <small>{{ relation.edgeType }}</small>
        </button>
      </aside>
    </section>

    <template v-if="activeWorkspace === 'context'">
      <section class="metric-row" v-if="stats">
        <div class="metric metric--ability"><strong>{{ stats.abilityCount }}</strong><span>涉及能力</span></div>
        <div class="metric metric--satisfied"><strong>{{ forceNodes.filter(node => node.meta?.matchState === 'SATISFIED').length }}</strong><span>已满足</span></div>
        <div class="metric metric--gap"><strong>{{ forceNodes.filter(node => node.meta?.matchState === 'LEVEL_GAP').length }}</strong><span>等级不足</span></div>
        <div class="metric metric--missing"><strong>{{ forceNodes.filter(node => node.meta?.matchState === 'MISSING').length }}</strong><span>能力缺失</span></div>
      </section>
    </template>

    <template v-if="activeWorkspace === 'operations'">
      <section class="bottom-row">
        <div class="ops-cards">
          <article class="operation-card">
            <span class="card-kicker">GRAPH BUILD</span>
            <h2>图谱构建</h2>
            <p>重建只更新图谱索引与关系投影，不会修改人员正式能力、岗位能力画像或匹配结果。</p>
            <el-button type="primary" :loading="rebuildLoading" @click="handleRebuild">重建全量图谱</el-button>
          </article>
          <article class="operation-card">
            <span class="card-kicker">KNOWLEDGE ASSETS</span>
            <h2>知识资产</h2>
            <p>知识文档与资料管理独立于图谱浏览，图谱仅投影它们已生效的关系。</p>
            <el-button @click="router.push('/rag/knowledge')">
              <el-icon><Document /></el-icon>管理知识资料
            </el-button>
          </article>
        </div>
        <section class="timeline-panel">
          <span class="card-kicker">RECENT CHANGES</span>
          <h2>最近图谱变化</h2>
          <ol v-if="timelineEvents.length">
            <li v-for="(event, index) in timelineEvents" :key="`${event.timestamp}-${index}`">
              <time>{{ formatTime(event.timestamp) }}</time>
              <strong>{{ event.eventType === 'EDGE_ADDED' ? '新增关系' : '新增节点' }}</strong>
              <span>{{ event.label || event.source || event.nodeKey || '图谱变更' }}</span>
            </li>
          </ol>
          <p v-else class="muted">暂无图谱变化记录。</p>
        </section>
      </section>
    </template>
  </main>
</template>

<style scoped lang="scss">
/* ====== AI 常读图谱 ======
   平面化布局：不用卡片堆叠，控件直接铺在浅色背景上，区域用分隔线划分；
   主体视觉只有图谱画布。 */

.graph-workbench {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  padding: 16px 24px;
  background: var(--app-bg-tertiary);
  color: var(--app-text);
}

/* ---- 顶部：标题 + 操作（无盒子） ---- */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.brand h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--app-text-strong);
}

.brand p {
  margin: 3px 0 0;
  color: var(--app-text-secondary);
  font-size: 12.5px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* ---- 工作区切换（下划线，无盒子） ---- */
.workspace-tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--app-divider);
}

.workspace-tabs button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 0;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: color 0.15s ease;
}

.workspace-tabs button:hover {
  color: var(--app-primary);
}

.workspace-tabs button.active {
  color: var(--app-primary);
  border-bottom-color: var(--app-primary);
  font-weight: 600;
}

/* ---- 对象选择条（无盒子，底部分隔线） ---- */
.object-bar {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 2px 0 12px;
  border-bottom: 1px solid var(--app-divider);
}

.object-field {
  min-width: 180px;
}

.object-field--grow {
  flex: 1;
  min-width: 180px;
}

.object-field label {
  display: block;
  margin-bottom: 5px;
  color: var(--app-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.object-field :deep(.el-select) {
  width: 100%;
}

.type-option {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.type-option i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.pop-settings label {
  display: block;
  margin-bottom: 6px;
  color: var(--app-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

/* ---- AI 上下文状态条（淡色提示行） ---- */
.context-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border: 1px solid rgba(37, 99, 235, 0.18);
  border-radius: 6px;
  background: var(--app-primary-soft);
  color: #1e40af;
}

.context-summary.unavailable {
  border-color: rgba(217, 119, 6, 0.3);
  background: var(--app-warning-soft);
  color: #9a6417;
}

.context-summary > .el-icon {
  font-size: 17px;
  flex-shrink: 0;
}

.summary-text {
  display: grid;
  gap: 2px;
}

.summary-text strong {
  font-size: 13px;
  font-weight: 700;
}

.summary-text span {
  font-size: 12px;
  opacity: 0.9;
}

.version-box {
  display: grid;
  gap: 1px;
  margin-left: auto;
  padding-left: 12px;
  border-left: 1px solid currentColor;
  text-align: right;
}

.version-box small {
  font-size: 10px;
  opacity: 0.7;
}

.business-metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(110px, 1fr));
  gap: 8px;
  margin-top: 10px;
}
.business-metrics > div {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--app-border);
  border-radius: 6px;
  background: var(--app-surface);
}
.business-metrics span { color: var(--app-text-muted); font-size: 11px; }
.business-metrics strong { color: var(--app-text-strong); font-size: 18px; }
.business-metrics strong.danger { color: var(--app-danger); }
.business-metrics p { grid-column: 1 / -1; margin: 2px 0 0; color: var(--app-text-muted); font-size: 12px; }
@media (max-width: 900px) { .business-metrics { grid-template-columns: repeat(3, 1fr); } }

.version-box b {
  font-size: 12px;
  font-weight: 700;
}

/* ---- 图谱画布（flex 伸缩占满剩余空间，直接铺在页面背景上） ---- */
.graph-stage {
  position: relative;
  flex: 1 1 0;
  min-height: 0;
  overflow: hidden;
}

.graph-stage :deep(.ability-force-graph-wrapper) {
  width: 100%;
  height: 100%;
}

.graph-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 10px;
  color: var(--app-text-secondary);
  text-align: center;
}

.graph-empty strong {
  color: var(--app-text-strong);
  font-size: 15px;
}

.graph-empty span {
  max-width: 340px;
  font-size: 13px;
}

/* ---- 节点检查器（选中节点时浮出） ---- */
.inspector {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 10;
  box-sizing: border-box;
  width: 290px;
  max-height: calc(100% - 24px);
  overflow-y: auto;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.97);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  box-shadow: var(--app-shadow-lg);
}

.inspector-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.inspector-head h2 {
  margin: 4px 0 10px;
  font-size: 16px;
  line-height: 1.35;
  word-break: break-word;
  color: var(--app-text-strong);
}

.card-kicker {
  color: var(--app-text-muted);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.inspector h3 {
  margin: 14px 0 8px;
  font-size: 12px;
  font-weight: 700;
  color: var(--app-text-secondary);
}

.facts {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 7px;
  margin: 12px 0;
  padding: 11px 0;
  border-top: 1px solid var(--app-divider);
  border-bottom: 1px solid var(--app-divider);
  font-size: 12px;
}

.facts dt {
  color: var(--app-text-secondary);
}

.facts dd {
  margin: 0;
  font-weight: 700;
  color: var(--app-text-strong);
  text-align: right;
}

.source-list {
  display: grid;
  gap: 6px;
}

.source-list span {
  padding: 7px 8px;
  border: 1px solid rgba(37, 99, 235, 0.12);
  border-radius: 6px;
  background: rgba(37, 99, 235, 0.04);
  color: #1e40af;
  font-size: 12px;
}

.relation-item {
  display: flex;
  width: 100%;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 0;
  border: 0;
  border-bottom: 1px solid var(--app-divider);
  background: transparent;
  color: var(--app-text);
  cursor: pointer;
  text-align: left;
  font-size: 12px;
}

.relation-item:hover {
  color: var(--app-primary);
}

.relation-item small {
  color: var(--app-text-muted);
}

.muted {
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

/* ---- 统计指标（一行数字，无盒子） ---- */
.metric-row {
  display: flex;
  gap: 44px;
  padding: 12px 2px 2px;
  border-top: 1px solid var(--app-divider);
}

.metric {
  display: grid;
  gap: 2px;
}

.metric strong {
  font-size: 20px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
  color: var(--app-text-strong);
}

.metric span {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.metric--ability strong { color: var(--app-primary); }
.metric--satisfied strong { color: var(--app-success); }
.metric--gap strong { color: var(--app-warning); }
.metric--missing strong { color: var(--app-danger); }

/* ---- 演化与治理：操作区 + 时间线（无盒子，分隔线划分） ---- */
.bottom-row {
  display: flex;
  gap: 0;
  align-items: stretch;
  padding-top: 12px;
  border-top: 1px solid var(--app-divider);
}

.ops-cards {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
}

.operation-card {
  padding: 2px 28px 6px 0;
}

.operation-card + .operation-card {
  padding-left: 28px;
  border-left: 1px solid var(--app-divider);
}

.operation-card h2 {
  margin: 5px 0 8px;
  font-size: 15px;
  font-weight: 700;
  color: var(--app-text-strong);
}

.operation-card p {
  margin: 0 0 12px;
  color: var(--app-text-secondary);
  font-size: 12.5px;
  line-height: 1.6;
}

.timeline-panel {
  box-sizing: border-box;
  width: 340px;
  flex-shrink: 0;
  padding: 2px 0 6px 28px;
  border-left: 1px solid var(--app-divider);
  overflow-y: auto;
}

.timeline-panel h2 {
  margin: 4px 0 10px;
  font-size: 14px;
  font-weight: 700;
  color: var(--app-text-strong);
}

.timeline-panel ol {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.timeline-panel li {
  display: grid;
  grid-template-columns: 88px 70px 1fr;
  gap: 10px;
  padding: 7px 0;
  border-top: 1px solid var(--app-divider);
  font-size: 12px;
}

.timeline-panel time {
  color: var(--app-text-secondary);
}

.timeline-panel strong {
  color: var(--app-primary);
  font-weight: 600;
}

.timeline-panel span {
  color: var(--app-text);
}

/* ---- 响应式 ---- */
@media (max-width: 900px) {
  .object-bar {
    flex-wrap: wrap;
  }

  .bottom-row {
    flex-direction: column;
  }

  .timeline-panel {
    width: auto;
    padding-left: 0;
    border-left: 0;
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--app-divider);
  }
}

@media (max-width: 680px) {
  .graph-workbench {
    padding: 10px 14px;
  }

  .topbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .object-field {
    min-width: 0;
    flex: 1;
  }

  .metric-row {
    flex-wrap: wrap;
    gap: 20px 40px;
  }

  .ops-cards {
    grid-template-columns: 1fr;
  }

  .operation-card + .operation-card {
    padding-left: 0;
    border-left: 0;
    margin-top: 14px;
    padding-top: 14px;
    border-top: 1px solid var(--app-divider);
  }

  .inspector {
    right: 8px;
    left: 8px;
    width: auto;
  }
}
</style>
