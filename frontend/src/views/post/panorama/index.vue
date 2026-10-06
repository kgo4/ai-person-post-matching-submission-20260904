<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search, Setting, Aim } from '@element-plus/icons-vue'
import PostPanorama3DGraph from '@/components/graph/PostPanorama3DGraph.vue'
import PostPanorama2D from '@/components/graph/PostPanorama2D.vue'
import type { Panorama3DLayoutMode, Panorama3DNode } from '@/components/graph/postPanorama3d'
import type { Panorama2DPost } from '@/components/graph/postPanorama2d'
import {
  getAbilityPanoramaDetail,
  getPostPanoramaDetail,
  getPostPanoramaFilters,
  getPostPanoramaGraph,
  getPostPanoramaOverview,
} from '@/api/post-panorama'
import type {
  AbilityPanoramaDetail,
  PanoramaFilters,
  PanoramaGraphData,
  PostAbilityDetail,
} from '@/api/post-panorama'

/**
 * 详情抽屉只用到节点的这几个字段，故不要求上游一定是 3D 节点。
 * 2D 图谱点选岗位时没有 ring/radius/position（它本来就不是 3D 坐标），
 * 用这个最小结构承载即可，避免为了喂抽屉去伪造一份假的三维坐标。
 */
interface PanoramaSelectedNode {
  id: string
  label: string
  type: string
  category?: string
  level?: number
  weight?: number
  meta?: Record<string, any>
}

/** 2D 是主展示，3D 保留为可切换视图。 */
type PanoramaViewMode = 'graph2d' | 'graph3d'

const loading = ref(false)
const graphData = ref<PanoramaGraphData | null>(null)
const graphRef = ref<InstanceType<typeof PostPanorama3DGraph>>()
const filterOpen = ref(true)
const detailOpen = ref(false)
const selectedNode = ref<PanoramaSelectedNode | null>(null)
const nodeDetail = ref<PostAbilityDetail | AbilityPanoramaDetail | null>(null)
const detailLoading = ref(false)
const layoutMode = ref<Panorama3DLayoutMode>('stack')
const viewMode = ref<PanoramaViewMode>('graph2d')

const viewModeOptions: { label: string; value: PanoramaViewMode }[] = [
  { label: '2D', value: 'graph2d' },
  { label: '3D', value: 'graph3d' },
]

const filterOptions = reactive<PanoramaFilters>({
  levels: [],
  abilityCategories: [],
  techStacks: [],
})

const filters = reactive({
  postId: undefined as number | undefined,
  level: '',
  techStack: '',
  requiredLevel: undefined as number | undefined,
  coreOnly: false,
  keyword: '',
  limit: 160,
})

const stats = computed(() => graphData.value?.stats)
const postOptions = computed(() => graphData.value?.nodes
  .filter(node => node.type === 'POST' || node.type === 'post')
  .map(node => ({
    id: Number(node.meta?.postId || node.id.split(':').at(-1)),
    label: node.label,
  }))
  .filter(node => Number.isFinite(node.id)) || [])
const focusNodeId = computed(() => {
  if (filters.postId) {
    return graphData.value?.nodes.find(node => (
      (node.type === 'POST' || node.type === 'post')
      && (node.meta?.postId === filters.postId || node.id.endsWith(`:${filters.postId}`))
    ))?.id
  }
  if (filters.techStack) return `TECH_STACK:${filters.techStack}`
  return undefined
})
const centerLabel = computed(() => {
  const postNode = graphData.value?.nodes.find(node => node.type === 'POST' || node.type === 'post')
  return postNode?.label || '暂无岗位数据'
})

const selectedRelations = computed(() => {
  if (!selectedNode.value || !graphData.value) return []
  const id = selectedNode.value.id
  return graphData.value.edges
    .filter(edge => edge.source === id || edge.target === id)
    .slice(0, 12)
    .map(edge => {
      const otherId = edge.source === id ? edge.target : edge.source
      const other = graphData.value?.nodes.find(node => node.id === otherId)
      return {
        id: edge.id,
        type: edge.type,
        label: edge.label || edge.type,
        otherLabel: other?.label || otherId,
      }
    })
})

async function fetchGraph() {
  loading.value = true
  try {
    const params: Record<string, any> = { limit: filters.limit }
    if (filters.postId) params.postId = filters.postId
    if (filters.level) params.level = filters.level
    if (filters.techStack) params.techStack = filters.techStack
    if (filters.requiredLevel) params.requiredLevel = filters.requiredLevel
    if (filters.coreOnly) params.coreOnly = true
    if (filters.keyword) params.keyword = filters.keyword

    const res = await getPostPanoramaGraph(params)
    if (res?.code === 200) {
      graphData.value = res.data
      // 技术栈/岗位视图只渲染对应关系边。旧后端可能返回了统计和节点，但没有
      // TECH_STACK_POST/POST_TECH_STACK，组件会将节点全部过滤掉，表现为“无数据”。
      const hasViewRelations = (res.data?.edges || []).some(edge =>
        edge.type === 'TECH_STACK_POST' || edge.type === 'POST_TECH_STACK',
      )
      // 兼容旧后端或关系投影尚未生成的部署版本：从岗位表和岗位能力表重建视图。
      if (!res.data?.nodes?.length || !hasViewRelations) {
        const overview = await getPostPanoramaOverview({
          level: filters.level || undefined,
          techStack: filters.techStack || undefined,
          keyword: filters.keyword || undefined,
        })
        const posts = overview.data?.posts || []
        const abilities = overview.data?.abilities || []
        const stackNames = [...new Set(abilities.map(a => a.category || '通用工程能力'))]
        const nodes = [
          ...posts.map(post => ({ id: `POST:${post.id}`, type: 'post', label: post.postName, category: post.level, meta: { postId: post.id } })),
          ...stackNames.map(stack => ({ id: `TECH_STACK:${stack}`, type: 'techStack', label: stack, category: stack })),
          ...abilities.map(ability => ({ id: `SKILL_POINT:${ability.id}`, type: 'skillPoint', label: ability.tagName, category: ability.category, level: ability.requiredLevel, weight: ability.weight, meta: { postId: ability.postId, modelId: ability.modelId } })),
        ]
        const edges = abilities.flatMap(ability => {
          const stack = ability.category || '通用工程能力'
          return [
            { id: `POST:${ability.postId}-TECH_STACK->TECH_STACK:${stack}`, source: `POST:${ability.postId}`, target: `TECH_STACK:${stack}`, type: 'POST_TECH_STACK', label: '岗位技术栈', weight: 0.9 },
            { id: `TECH_STACK:${stack}-POST->POST:${ability.postId}`, source: `TECH_STACK:${stack}`, target: `POST:${ability.postId}`, type: 'TECH_STACK_POST', label: '技术栈岗位', weight: 0.9 },
            { id: `POST:${ability.postId}-REQ->SKILL_POINT:${ability.id}`, source: `POST:${ability.postId}`, target: `SKILL_POINT:${ability.id}`, type: 'REQUIRES', label: '岗位能力', weight: ability.weight },
          ]
        })
        graphData.value = { available: true, nodes, edges, stats: { postCount: posts.length, abilityCount: abilities.length, skillPointCount: abilities.length, nodeCount: nodes.length, edgeCount: edges.length } }
      }
      if (!graphData.value?.nodes?.length) ElMessage.warning('暂无符合条件的岗位图谱数据')
    }
  } catch (error) {
    console.error('加载岗位全景图谱失败', error)
    ElMessage.error('加载岗位全景图谱失败')
  } finally {
    loading.value = false
  }
}

async function loadFilters() {
  try {
    const res = await getPostPanoramaFilters()
    if (res?.code === 200 && res.data) {
      Object.assign(filterOptions, res.data)
    }
  } catch (error) {
    console.warn('加载岗位全景筛选项失败', error)
  }
}

async function handleNodeClick(node: Panorama3DNode) {
  selectedNode.value = node
  detailOpen.value = true
  nodeDetail.value = null
  if (layoutMode.value === 'stack' && (node.type === 'techStack' || node.type === 'TECH_STACK')) {
    const stack = node.label?.trim()
    if (stack && filters.techStack !== stack) {
      filters.techStack = stack
      await fetchGraph()
    }
  } else if (layoutMode.value === 'post' && (node.type === 'post' || node.type === 'POST')) {
    const postId = Number(node.meta?.postId || parseNumericId(node.id, 'POST'))
    if (Number.isFinite(postId) && filters.postId !== postId) {
      filters.postId = postId
      await fetchGraph()
    }
  }
  await loadNodeDetail(node)
}

async function loadNodeDetail(node: PanoramaSelectedNode) {
  detailLoading.value = true
  try {
    const postId = node.meta?.postId || parseNumericId(node.id, 'POST')
    const abilityId = node.meta?.tagId || parseNumericId(node.id, 'ABILITY_TAG')
    if ((node.type === 'post' || node.type === 'POST') && postId) {
      const res = await getPostPanoramaDetail(postId)
      if (res?.code === 200) nodeDetail.value = res.data
    } else if ((node.type === 'postAbility' || node.type === 'postAbilityFact'
      || node.type === 'unnormalizedPostAbilityFact' || node.type === 'skillPoint') && node.meta?.postId) {
      const res = await getPostPanoramaDetail(node.meta.postId)
      if (res?.code === 200) nodeDetail.value = res.data
    } else if ((node.type === 'ability' || node.type === 'ABILITY') && abilityId) {
      const res = await getAbilityPanoramaDetail(abilityId)
      if (res?.code === 200) nodeDetail.value = res.data
    }
  } catch (error) {
    console.warn('加载节点详情失败', error)
  } finally {
    detailLoading.value = false
  }
}

/**
 * 2D 图谱点岗位 → 只开详情抽屉。
 *
 * 刻意**不**像 3D 那样顺手改 filters 再重拉数据：2D 的三视图是同一份 graphData 的
 * 确定性投影，一旦按岗位过滤，同心环会塌成单节点、矩阵只剩一列，反而把刚点开的那张图弄没了。
 */
async function handlePostClick2D(post: Panorama2DPost) {
  const node: PanoramaSelectedNode = {
    id: post.id,
    label: post.name,
    type: 'post',
    category: post.stack,
    meta: { postId: parseNumericId(post.id, 'POST') ?? undefined },
  }
  selectedNode.value = node
  detailOpen.value = true
  nodeDetail.value = null
  await loadNodeDetail(node)
}

function parseNumericId(id: string, prefix: string): number | null {
  const normalized = `${prefix}:`
  if (!id.startsWith(normalized)) return null
  const value = Number(id.slice(normalized.length))
  return Number.isFinite(value) ? value : null
}

function resetFilters() {
  filters.postId = undefined
  filters.level = ''
  filters.techStack = ''
  filters.requiredLevel = undefined
  filters.coreOnly = false
  filters.keyword = ''
  filters.limit = 160
  fetchGraph()
}

function getNodeTypeName(type?: string) {
  const map: Record<string, string> = {
    abilityDomain: '能力域',
    domain: '岗位领域',
    POST: '岗位',
    post: '岗位',
    TECH_STACK: '技术栈',
    ABILITY: '技能点',
    ability: '技能点',
    skillPoint: '技能点',
    techStack: '技术栈',
    SKILL_POINT: '技能点',
  }
  return type ? map[type] || type : '-'
}

function formatPercent(value?: number) {
  if (!Number.isFinite(value)) return '-'
  const normalized = Number(value) > 1 ? Number(value) : Number(value) * 100
  return `${Math.round(normalized)}%`
}

onMounted(() => {
  fetchGraph()
  loadFilters()
})
</script>

<template>
  <!--
    v-loading 不能挂在这个根节点上：3D 分支是 position:absolute 脱离文档流，
    根节点高度会被压成 0，遮罩跟着塌成一条线。所以按分支挂。
  -->
  <div class="panorama-root">
    <!--
      形态切换：2D 是主展示，3D 保留为可切换视图。
      同一个控件两种摆法 —— 2D 时随文档流落在页面顶部，3D 时浮在画布右上角，
      这样两种版式都不必各写一份切换器。
    -->
    <div class="pano-switch-bar" :class="{ 'is-overlay': viewMode === 'graph3d' }">
      <span class="pano-switch-bar__hint">图谱形态</span>
      <el-segmented v-model="viewMode" size="small" :options="viewModeOptions" />
    </div>

    <!-- ==================== 主展示：2D 全景图谱 ==================== -->
    <div v-if="viewMode === 'graph2d'" class="page-shell panorama-2d" v-loading="loading">
      <section class="page-hero">
        <div>
          <span class="page-hero__eyebrow">Post Panorama</span>
          <h1 class="page-hero__title">新一代信息技术岗位全景图谱</h1>
          <p class="page-hero__desc">
            以「技术栈 → 岗位 → 能力」为主线，用三种读法回答三个问题：全景看技术栈如何构成岗位族，
            关联看哪些能力是跨岗位的枢纽，聚焦看一个岗位能通向哪些其他岗位。
          </p>
          <div class="page-hero__meta">
            <span class="hero-chip">岗位方向 {{ stats?.postCount ?? 0 }}</span>
            <span class="hero-chip">岗位能力 {{ stats?.abilityCount ?? stats?.factCount ?? 0 }}</span>
            <span class="hero-chip">技能点 {{ stats?.skillPointCount ?? 0 }}</span>
            <span class="hero-chip">关系 {{ stats?.edgeCount ?? graphData?.edges.length ?? 0 }}</span>
          </div>
        </div>
      </section>

      <section class="glass-card">
        <div class="toolbar-panel">
          <div class="toolbar-group">
            <el-input
              v-model="filters.keyword"
              placeholder="搜索岗位 / 能力 / 技能点"
              clearable
              size="small"
              class="search-input"
              @keyup.enter="fetchGraph"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button size="small" :icon="Setting" @click="filterOpen = !filterOpen">筛选</el-button>
            <el-button size="small" type="primary" :icon="Refresh" :loading="loading" @click="fetchGraph">刷新</el-button>
          </div>
          <span class="section-desc">点岗位节点查看能力要求，右侧面板同步联动</span>
        </div>

        <div v-show="filterOpen" class="panorama-2d__filters">
          <label v-if="filterOptions.levels.length" class="pano-field">
            <span>岗位职级</span>
            <el-select v-model="filters.level" clearable placeholder="全部职级" size="small">
              <el-option v-for="item in filterOptions.levels" :key="item" :label="item" :value="item" />
            </el-select>
          </label>

          <label class="pano-field">
            <span>技术栈</span>
            <el-select v-model="filters.techStack" clearable placeholder="全部技术栈" size="small">
              <el-option v-for="item in filterOptions.techStacks" :key="item" :label="item" :value="item" />
            </el-select>
          </label>

          <label class="pano-field">
            <span>岗位</span>
            <el-select v-model="filters.postId" clearable placeholder="全部岗位" size="small">
              <el-option v-for="item in postOptions" :key="item.id" :label="item.label" :value="item.id" />
            </el-select>
          </label>

          <label class="pano-field">
            <span>最低能力等级</span>
            <el-select v-model="filters.requiredLevel" clearable placeholder="全部等级" size="small">
              <el-option v-for="item in [1, 2, 3, 4, 5]" :key="item" :label="`L${item}`" :value="item" />
            </el-select>
          </label>

          <label class="pano-field pano-field--switch">
            <span>仅核心技能</span>
            <el-switch v-model="filters.coreOnly" />
          </label>

          <label class="pano-field pano-field--slider">
            <span>节点数量 {{ filters.limit }}</span>
            <el-slider v-model="filters.limit" :min="60" :max="360" :step="20" />
          </label>

          <div class="pano-field pano-field--actions">
            <el-button type="primary" size="small" :loading="loading" @click="fetchGraph">应用</el-button>
            <el-button size="small" @click="resetFilters">重置</el-button>
          </div>
        </div>
      </section>

      <PostPanorama2D :graph-data="graphData" @post-click="handlePostClick2D" />
    </div>

    <!-- ==================== 保留：3D 立体图谱（样式与版式不动） ==================== -->
    <div v-else class="post-panorama-page" v-loading="loading">
      <PostPanorama3DGraph
        ref="graphRef"
        class="pano-graph"
        :graph-data="graphData"
        :layout-mode="layoutMode"
        :focus-node-id="focusNodeId"
        @node-click="handleNodeClick"
      />

      <header class="pano-header">
        <h1 class="pano-title">
          <span class="title-accent">新一代信息技术岗位全景图谱</span>
        </h1>
        <p class="pano-subtitle">以技术栈或岗位为中心，查看直属关联关系</p>
      </header>

      <div class="pano-toolbar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索岗位 / 能力 / 技能点"
          clearable
          size="small"
          class="search-input"
          @keyup.enter="fetchGraph"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-segmented
          v-model="layoutMode"
          size="small"
          :options="[
            { label: '按技术栈', value: 'stack' },
            { label: '按岗位', value: 'post' },
          ]"
        />
        <el-button size="small" :icon="Setting" @click="filterOpen = !filterOpen">筛选</el-button>
        <el-button size="small" :icon="Aim" @click="graphRef?.resetCamera()">视角</el-button>
        <el-button size="small" type="primary" :icon="Refresh" :loading="loading" @click="fetchGraph">刷新</el-button>
      </div>

      <div class="reading-path" aria-label="图谱阅读层级">
        <template v-if="layoutMode === 'stack'">
          <span class="reading-path__item reading-path__item--stack">技术栈</span>
          <span class="reading-path__arrow">→</span>
        </template>
        <template v-else>
          <span class="reading-path__item reading-path__item--post">岗位（中心）</span>
          <span class="reading-path__arrow">→</span>
        </template>
        <span v-if="layoutMode === 'stack'" class="reading-path__item reading-path__item--post">岗位</span>
        <template v-else>
          <span class="reading-path__item reading-path__item--stack">技术栈</span>
        </template>
      </div>

      <aside v-show="filterOpen" class="filter-panel">
        <div class="panel-title">图谱筛选</div>
        <template v-if="filterOptions.levels.length">
          <label class="field-label">岗位职级</label>
          <el-select v-model="filters.level" clearable placeholder="全部职级" class="full-field">
            <el-option v-for="item in filterOptions.levels" :key="item" :label="item" :value="item" />
          </el-select>
        </template>

        <label class="field-label">技术栈</label>
        <el-select v-model="filters.techStack" clearable placeholder="全部技术栈" class="full-field">
          <el-option v-for="item in filterOptions.techStacks" :key="item" :label="item" :value="item" />
        </el-select>

        <label class="field-label">岗位</label>
        <el-select v-model="filters.postId" clearable placeholder="全部岗位" class="full-field">
          <el-option v-for="item in postOptions" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>

        <label class="field-label">最低能力等级</label>
        <el-select v-model="filters.requiredLevel" clearable placeholder="全部等级" class="full-field">
          <el-option v-for="item in [1, 2, 3, 4, 5]" :key="item" :label="`L${item}`" :value="item" />
        </el-select>

        <el-checkbox v-model="filters.coreOnly" class="core-filter">仅核心技能</el-checkbox>

        <label class="field-label">节点数量</label>
        <el-slider v-model="filters.limit" :min="60" :max="360" :step="20" />

        <div class="filter-actions">
          <el-button type="primary" @click="fetchGraph" :loading="loading">应用</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </aside>

      <aside class="stats-panel" v-if="stats">
        <div class="stat-card stat-post">
          <span class="stat-num">{{ stats.postCount }}</span>
          <small class="stat-label">
            <i class="stat-icon">📋</i>岗位方向
          </small>
        </div>
        <div class="stat-card stat-ability">
          <span class="stat-num">{{ stats.abilityCount ?? stats.factCount ?? 0 }}</span>
          <small class="stat-label">
            <i class="stat-icon">🎯</i>岗位能力
          </small>
        </div>
        <div class="stat-card stat-skill">
          <span class="stat-num">{{ stats.skillPointCount ?? 0 }}</span>
          <small class="stat-label">
            <i class="stat-icon">⚡</i>技能点
          </small>
        </div>
        <div class="stat-card stat-edge">
          <span class="stat-num">{{ stats.edgeCount ?? graphData?.edges.length ?? 0 }}</span>
          <small class="stat-label">
            <i class="stat-icon">🔗</i>关系
          </small>
        </div>
      </aside>

      <aside class="legend-panel">
        <div v-if="layoutMode === 'stack'" class="legend-item"><i class="dot stack" /><span>技术栈分组</span></div>
        <div class="legend-item"><i class="dot post" /><span>岗位方向</span></div>
        <div class="legend-item"><i class="dot skill" /><span>技能点（岗位能力）</span></div>
        <div class="legend-item"><i class="dot edge-dot" /><span class="edge-line"></span><span>聚焦关系</span></div>
      </aside>
    </div>

    <!-- 抽屉不再挂在 3D 分支里 —— 它 position:fixed，与具体形态无关，共用一份即可 -->
    <el-drawer
      v-model="detailOpen"
      size="360px"
      direction="rtl"
      custom-class="panorama-detail-drawer"
      :with-header="false"
    >
      <div class="detail-panel" v-if="selectedNode" v-loading="detailLoading">
        <button type="button" class="detail-close" @click="detailOpen = false">关闭</button>
        <div class="detail-type">{{ getNodeTypeName(selectedNode.type) }}</div>
        <h2>{{ selectedNode.label }}</h2>

        <div class="detail-grid">
          <span>类别</span><strong>{{ selectedNode.category || '-' }}</strong>
          <span>等级</span><strong>{{ selectedNode.level ? `L${selectedNode.level}` : '-' }}</strong>
          <span>权重</span><strong>{{ formatPercent(selectedNode.weight) }}</strong>
        </div>

        <template v-if="nodeDetail && 'abilities' in nodeDetail">
          <div class="detail-section">
            <h3>能力要求</h3>
            <div v-for="ability in nodeDetail.abilities" :key="ability.modelId ?? ability.abilityTagId ?? ability.abilityName" class="ability-card">
              <div>
                <strong>{{ ability.abilityName }}</strong>
                <el-tag v-if="ability.isCore" type="warning" size="small">核心</el-tag>
              </div>
              <small>L{{ ability.requiredLevel }} / {{ ability.weight }}%</small>
              <div class="skill-list" v-if="ability.skillPoints?.length">
                <el-tag v-for="skill in ability.skillPoints" :key="skill" size="small" effect="plain">
                  {{ skill }}
                </el-tag>
              </div>
            </div>
          </div>
        </template>

        <template v-if="nodeDetail && 'posts' in nodeDetail">
          <div class="detail-section">
            <h3>关联岗位</h3>
            <div v-for="post in nodeDetail.posts" :key="post.postId" class="relation-row">
              <span>{{ post.postName }}</span>
              <small>L{{ post.requiredLevel }} / {{ post.weight }}%</small>
            </div>
          </div>
          <div class="detail-section" v-if="nodeDetail.skillPoints?.length">
            <h3>技能点</h3>
            <div class="skill-list">
              <el-tag v-for="skill in nodeDetail.skillPoints" :key="skill" size="small" effect="plain">
                {{ skill }}
              </el-tag>
            </div>
          </div>
        </template>

        <div class="detail-section" v-if="selectedRelations.length">
          <h3>直接关联</h3>
          <div v-for="rel in selectedRelations" :key="rel.id" class="relation-row">
            <span>{{ rel.otherLabel }}</span>
            <small>{{ rel.label }}</small>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
/* ====== Panorama — Beautified Edition ====== */

/*
 * 页面根节点刻意**不加 position**：
 *   · 2D 分支要靠它撑高页面（浏览器窗口滚动）；
 *   · 3D 分支是 position:absolute + inset:0，必须继续相对 .layout-page-wrapper
 *     定位（那里有视口高度）。若在此处加 position:relative，根节点在 3D 模式下
 *     高度为 0（子元素全部脱离文档流），球体画布会跟着塌成 0 高度。
 */
.panorama-root {
  display: block;
}

/* ---- 形态切换（2D / 3D） ---- */
.pano-switch-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-bottom: 14px;
}

.pano-switch-bar__hint {
  color: var(--app-text-muted);
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

/*
 * 3D 模式下浮到画布右上角。
 * 与 3D 顶部居中的工具栏**同一行会有碰撞**（工具栏 643px 居中，窄屏时右端会伸过来），
 * 所以放在标题行右侧（top:16px）—— 那里只有居中的页面标题，且标题是 pointer-events:none。
 * 组件已实测：stage 宽 1500 时本控件在 1274~1478，标题块在中央，互不遮挡。
 */
.pano-switch-bar.is-overlay {
  position: absolute;
  top: 16px;
  right: 22px;
  z-index: 6;
  margin-bottom: 0;
  padding: 5px 10px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.7) inset,
    0 4px 20px rgba(15, 23, 42, 0.06),
    0 1px 4px rgba(15, 23, 42, 0.04);
}

/* ---- 2D 主展示 ---- */
.panorama-2d {
  gap: 18px;
  padding-bottom: 6px;
}

/* 2D 的筛选是横向栅格：字段多且都短，竖排会把卡片拉得很高 */
.panorama-2d__filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px 18px;
  align-items: end;
  padding: 16px 20px 20px;
  border-top: 1px solid var(--app-divider, rgba(148, 163, 184, 0.14));
}

.pano-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;

  > span {
    color: var(--app-text-muted);
    font-size: 11.5px;
    font-weight: 600;
  }
}

/* 「仅核心技能」和「应用/重置」不需要 label 上方的空隙对齐，单独微调 */
.pano-field--switch {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 2px;
}

.pano-field--slider {
  grid-column: span 2;
}

.pano-field--actions {
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

.panorama-2d__filters :deep(.el-select) {
  width: 100%;
}

.post-panorama-page {
  position: absolute;
  inset: 0;
  overflow: hidden;
  border-radius: 14px;
  color: var(--app-text);
  background:
    radial-gradient(ellipse 60% 50% at 50% 50%, rgba(59, 130, 246, 0.06) 0%, transparent 80%),
    radial-gradient(ellipse 40% 35% at 25% 65%, rgba(5, 150, 105, 0.04) 0%, transparent 70%),
    radial-gradient(ellipse 35% 40% at 75% 30%, rgba(139, 92, 246, 0.04) 0%, transparent 70%),
    linear-gradient(165deg, #e6ecf5 0%, #edf1f8 40%, #eaf0f8 70%, #e6ecf5 100%);
}

.pano-graph {
  position: absolute;
  inset: 0;
}

/* ---- Page Header ---- */
.pano-header {
  position: absolute;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 4;
  text-align: center;
  pointer-events: none;
  user-select: none;
}

.pano-title {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.3;
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 8px;
}

.title-accent {
  background: linear-gradient(135deg, #2563eb 0%, #7c3aed 100%);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  text-shadow: none;
}

.title-main {
  color: #0f172a;
}

.pano-subtitle {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 11.5px;
  font-weight: 500;
  letter-spacing: 0.02em;
}

/* ---- Toolbar ---- */
.pano-toolbar {
  position: absolute;
  top: 104px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 6px 12px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  pointer-events: auto;
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.7) inset,
    0 4px 20px rgba(15, 23, 42, 0.06),
    0 1px 4px rgba(15, 23, 42, 0.04);
}

.search-input {
  width: 210px;
}

.reading-path {
  position: absolute;
  top: 152px;
  left: 50%;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  transform: translateX(-50%);
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.62);
  color: #475569;
  font-size: 11px;
  font-weight: 600;
  pointer-events: none;
}

.reading-path__item {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 7px;
  border-radius: 4px;
}

/* 阅读层级只剩「岗位 / 技术栈」两层；level / ability / skill 的配色已随层级一并移除 */
.reading-path__item--post { background: rgba(5, 150, 105, 0.1); color: #047857; }
.reading-path__item--stack { background: rgba(124, 58, 237, 0.1); color: #6d28d9; }
.reading-path__arrow { color: #94a3b8; }

/* ---- Panel Base ---- */
.filter-panel,
.stats-panel,
.legend-panel {
  position: absolute;
  z-index: 5;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  pointer-events: auto;
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.6) inset,
    0 4px 18px rgba(15, 23, 42, 0.05);
}

/* ---- Filter Panel ---- */
.filter-panel {
  top: 150px;
  left: 22px;
  width: 240px;
  padding: 18px 20px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  color: var(--app-text-strong);
  font-size: 13.5px;
  font-weight: 700;
  letter-spacing: -0.01em;

  &::before {
    content: '';
    display: inline-block;
    width: 3px;
    height: 15px;
    border-radius: 2px;
    background: linear-gradient(180deg, #2563eb, #7c3aed);
  }
}

.field-label {
  display: block;
  margin: 12px 0 5px;
  color: #64748b;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.01em;

  &:first-of-type {
    margin-top: 0;
  }
}

.full-field {
  width: 100%;
}

.filter-actions {
  display: flex;
  gap: 8px;
  margin-top: 18px;
}

.core-filter {
  margin-top: 14px;
}

/* ---- Stats Panel ---- */
.stats-panel {
  left: 22px;
  bottom: 22px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 2px;
  padding: 6px 8px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 10px 12px;
  border-radius: 10px;
  transition: background 0.25s ease, transform 0.25s ease;

  &:hover {
    transform: translateY(-1px);
  }

  &.stat-post:hover    { background: rgba(16, 185, 129, 0.08); }
  &.stat-ability:hover { background: rgba(59, 130, 246, 0.08); }
  &.stat-skill:hover   { background: rgba(245, 158, 11, 0.08); }
  &.stat-edge:hover    { background: rgba(139, 92, 246, 0.08); }
}

.stat-num {
  color: var(--app-text-strong);
  font-size: 20px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  line-height: 1.15;
  letter-spacing: -0.02em;
}

.stat-label {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #64748b;
  font-size: 10.5px;
  font-weight: 600;
}

.stat-icon {
  font-style: normal;
  font-size: 12px;
  line-height: 1;
}

.stat-post .stat-num    { color: #059669; }
.stat-ability .stat-num { color: #2563eb; }
.stat-skill .stat-num   { color: #d97706; }
.stat-edge .stat-num    { color: #7c3aed; }

/* ---- Legend ---- */
.legend-panel {
  right: 22px;
  bottom: 22px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 14px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--app-text-secondary);
  font-size: 11px;
  font-weight: 500;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 6px currentColor;

  &.post      { background: #10b981; box-shadow: 0 0 8px rgba(16, 185, 129, 0.4); }
  &.stack     { background: #7c3aed; box-shadow: 0 0 8px rgba(124, 58, 237, 0.4); }
  &.ability   { background: #3b82f6; box-shadow: 0 0 8px rgba(59, 130, 246, 0.4); }
  &.skill     { background: #f59e0b; box-shadow: 0 0 8px rgba(245, 158, 11, 0.4); }
  &.edge-dot  {
    width: 5px; height: 5px;
    background: #8b5cf6;
    border-radius: 0;
    transform: rotate(45deg);
    box-shadow: 0 0 6px rgba(139, 92, 246, 0.35);
  }
}

.edge-line {
  display: inline-block;
  width: 12px;
  height: 1.5px;
  background: linear-gradient(90deg, #8b5cf6, #a78bfa);
  border-radius: 1px;
  flex-shrink: 0;
}

/* ====== Detail Drawer ====== */
.detail-panel {
  min-height: 100%;
  padding: 26px 24px;
  background: linear-gradient(180deg, #f8fafc 0%, #f5f7fb 30%, #f5f7fb 100%);
  color: var(--app-text);
}

.detail-close {
  float: right;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.6);
  color: var(--app-text-secondary);
  height: 32px;
  padding: 0 14px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  backdrop-filter: blur(8px);
  transition: border-color 0.2s, color 0.2s, background 0.2s, box-shadow 0.2s;

  &:hover {
    border-color: rgba(37, 99, 235, 0.35);
    color: #2563eb;
    background: rgba(255, 255, 255, 0.85);
    box-shadow: 0 2px 8px rgba(37, 99, 235, 0.1);
  }
}

.detail-type {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 5px;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  font-size: 10.5px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.07em;
}

.detail-panel h2 {
  clear: both;
  margin: 8px 0 18px;
  color: #0f172a;
  font-size: 21px;
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.2;
}

.detail-grid {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 10px 16px;
  padding: 14px 16px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.65);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.8) inset;

  span {
    color: #94a3b8;
    font-size: 12px;
    font-weight: 500;
  }

  strong {
    color: #0f172a;
    text-align: right;
    font-size: 13px;
    font-weight: 700;
  }
}

.detail-section {
  margin-top: 22px;

  h3 {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0 0 12px;
    color: #0f172a;
    font-size: 13.5px;
    font-weight: 700;
    letter-spacing: -0.01em;

    &::before {
      content: '';
      display: inline-block;
      width: 3px;
      height: 14px;
      border-radius: 2px;
      background: linear-gradient(180deg, #2563eb, #7c3aed);
    }
  }
}

.ability-card {
  padding: 12px 14px;
  margin-bottom: 8px;
  border: 1px solid rgba(148, 163, 184, 0.1);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.55);
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 2px 10px rgba(15, 23, 42, 0.04);
  }

  &:last-child {
    margin-bottom: 0;
  }

  > div:first-child {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    font-size: 13px;
    font-weight: 600;
  }

  small {
    display: block;
    margin-top: 5px;
    color: #94a3b8;
    font-size: 11px;
    font-weight: 500;
  }
}

.skill-list {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 8px;
}

.skill-list :deep(.el-tag) {
  border-radius: 5px;
  font-size: 11px;
  font-weight: 500;
}

.relation-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid rgba(148, 163, 184, 0.08);

  &:last-child {
    border-bottom: none;
  }

  span {
    color: var(--app-text);
    font-size: 13px;
    font-weight: 500;
  }

  small {
    flex-shrink: 0;
    color: #94a3b8;
    font-size: 11px;
    font-weight: 500;
  }
}

/* ---- Deep Styles: Segmented ---- */
:deep(.el-segmented) {
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(148, 163, 184, 0.12);
  border-radius: 9px;
  backdrop-filter: blur(10px);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.03);
}

:deep(.el-segmented .el-segmented__item) {
  border-radius: 7px;
  font-weight: 600;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

:deep(.el-segmented .el-segmented__item.is-selected) {
  background: linear-gradient(135deg, #2563eb 0%, #4f46e5 100%);
  color: #fff;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.3);
}

/* ---- Deep Styles: Drawer ---- */
:deep(.el-drawer__body) {
  padding: 0;
  background: linear-gradient(180deg, #f8fafc 0%, #f5f7fb 30%, #f5f7fb 100%);
}

:deep(.el-drawer) {
  background: transparent;
}

/* ---- Deep Styles: Select & Input ---- */
:deep(.el-select .el-input__wrapper) {
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.6);
  box-shadow: none !important;
  border: 1px solid rgba(148, 163, 184, 0.14);
}

:deep(.el-select .el-input__wrapper:hover) {
  border-color: rgba(37, 99, 235, 0.25);
}

:deep(.el-select .el-input__wrapper.is-focus) {
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.08) !important;
}

:deep(.el-input-number .el-input__wrapper) {
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.6);
  box-shadow: none !important;
  border: 1px solid rgba(148, 163, 184, 0.14);
}

:deep(.el-slider__runway) {
  background: rgba(148, 163, 184, 0.15);
}

:deep(.el-slider__bar) {
  background: linear-gradient(90deg, #60a5fa, #2563eb);
}

:deep(.el-slider__button) {
  border-color: #2563eb;
}

/* ---- Responsive ---- */
@media (max-width: 980px) {
  .pano-header {
    top: 8px;
  }

  .pano-title {
    font-size: 17px;
  }

  /*
   * 窄屏下**仍然保持浮层**：3D 分支是 position:absolute 的全屏层且排在后面，
   * 变成静态元素会被它整个盖住，切换器就点不到了。这里只收紧贴边。
   */
  .pano-switch-bar.is-overlay {
    top: 12px;
    right: 12px;
  }

  .pano-switch-bar {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .panorama-2d__filters {
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  }

  .pano-field--slider {
    grid-column: span 1;
  }

  .pano-toolbar {
    top: 80px;
    left: 14px;
    right: 14px;
    transform: none;
    flex-wrap: wrap;
  }

  .search-input {
    width: 100%;
  }

  .filter-panel {
    top: 176px;
    width: calc(100vw - 44px);
  }

  .reading-path {
    top: 134px;
  }

  .stats-panel {
    grid-template-columns: repeat(4, 1fr);
    right: 22px;
    left: 22px;
    bottom: 14px;
  }

  .legend-panel {
    display: none;
  }
}
</style>
