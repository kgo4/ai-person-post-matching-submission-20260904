<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { PanoramaGraphData } from '@/api/post-panorama'
import {
  PANORAMA2D_VIEWBOX,
  PANORAMA2D_VIEWS,
  buildEgoNeighborhood,
  buildEgoOverview,
  buildMatrixLayout,
  buildPanorama2DModel,
  buildRingsLayout,
  findPostAbility,
  labelAnchor,
  polarPoint,
  wedgePath,
  type Panorama2DPost,
  type Panorama2DViewMode,
} from './postPanorama2d'

/**
 * 岗位全景图谱 · 2D 主展示。
 *
 * 三种读法共用同一份数据：
 *   全景 — 技术栈族构成同心环的扇区，同族岗位落在同一扇区，层数与结构一眼可见；
 *   关联 — 能力 × 岗位矩阵，行按「被几个岗位需要」降序，暴露跨岗位枢纽能力；
 *   聚焦 — 单个岗位的能力邻域，外环是共享这些能力的其他岗位，即转岗/复用路径。
 *
 * 之所以彻底放弃 3D 力导向：位置不可复现、同族不聚类、层级没有语义，
 * 而这三件事恰恰是这张图要回答的问题。几何全部由 `postPanorama2d.ts` 纯函数给出。
 */

const props = withDefaults(defineProps<{
  graphData: PanoramaGraphData | null
  domain?: string
}>(), {
  domain: '新一代信息技术',
})

const emit = defineEmits<{
  (e: 'post-click', post: Panorama2DPost): void
}>()

const view = ref<Panorama2DViewMode>('rings')
const focusedPostName = ref('')
const hoverStack = ref('')
const hoverCell = ref<string>('')

/**
 * 小球 hover 提示。
 *
 * 密集时环上名称会被隐藏（见 `computeRingDensity`），此时必须有另一条拿到
 * 「这个点是谁」的路径 —— 这就是它。同时顺带给出技术栈与能力数，
 * 比只有名字更有用。
 */
const hoverTip = ref<{ label: string; meta: string; x: number; y: number } | null>(null)
/** 提示需要相对舞台定位，故持有舞台元素引用。 */
const stageRef = ref<HTMLElement | null>(null)

const model = computed(() => buildPanorama2DModel(props.graphData, { domain: props.domain }))
const rings = computed(() => buildRingsLayout(model.value))
/**
 * 小球描边宽度。
 * 半径会随疏密收缩（最大 6.5 → 最小 3.4），若描边固定 2px，
 * 小球缩到最小后几乎全是描边、会被糊成一个实心点 —— 必须同步变细。
 */
const nodeStrokeWidth = computed(() => Math.max(1.2, rings.value.density.nodeRadius * 0.3))
const viewBox = computed(() => {
  const v = PANORAMA2D_VIEWBOX
  return `${v.width} ${v.height}`
})
/**
 * 外层 <svg> 只给了 viewBox、没给 width/height 属性时，浏览器按**替换元素默认高度
 * 150px** 渲染，而不是按 viewBox 比例算高度 —— 于是 `.p2d-svg{width:100%;height:auto}`
 * 会把整张图压成一条 150px 高的横条（实测 1076×150）。
 * 这里显式声明宽高比；比例的唯一来源仍是 PANORAMA2D_VIEWBOX，避免两处数字漂移。
 */
const svgAspectStyle = {
  aspectRatio: `${PANORAMA2D_VIEWBOX.width} / ${PANORAMA2D_VIEWBOX.height}`,
}
const matrix = computed(() => buildMatrixLayout(model.value))
const ego = computed(() => buildEgoNeighborhood(model.value, focusedPostName.value))
/** 全部岗位的聚合数据（未聚焦任何单个岗位时使用）。 */
const overview = computed(() => buildEgoOverview(model.value))
/** 聚焦视图的两种态：'' = 全部岗位（聚合），具体岗位名 = 聚焦该岗位。 */
const isAggregateScope = computed(() => !focusedPostName.value)

/** 技术栈名 → 内环节点坐标，用来画「技术栈 → 岗位」的连线。 */
const stackNodeByName = computed(() => {
  const map: Record<string, { x: number; y: number; color: string }> = {}
  rings.value.stacks.forEach(node => { map[node.stack] = { x: node.x, y: node.y, color: node.color } })
  return map
})

const currentViewMeta = computed(() => (
  PANORAMA2D_VIEWS.find(item => item.value === view.value) || PANORAMA2D_VIEWS[0]
))

const legendStacks = computed(() => model.value.stacks.slice(0, 12))

/**
 * 聚焦态只在「当前聚焦的岗位真的消失了」时清空，**不再自动落到第一个岗位**。
 *
 * 原实现是 `focusedPostName = posts[0].name`，本意是让右侧面板不出现空白。
 * 但代价很明显：筛选为「全部岗位」时，用户看到的是**某一个**岗位的邻域，
 * 与「我正在看全部岗位」的预期冲突 —— 用户反馈即「随机选了一个岗位展示」。
 * 现在两种态明确分开：'' = 全部岗位（走聚合面板），具体名称 = 聚焦该岗位；
 * 聚合态下右侧面板展示全局统计，因此不再需要「随便挑一个」来填空白。
 */
watch(model, value => {
  if (!value.posts.length) {
    focusedPostName.value = ''
    return
  }
  if (focusedPostName.value && !value.postByName[focusedPostName.value]) {
    focusedPostName.value = ''
  }
}, { immediate: true })

/** 切换视图时清掉悬浮残留：鼠标不动也会停在旧视图的位置，提示会「挂」在屏幕上。 */
watch(view, () => {
  hoverTip.value = null
  hoverStack.value = ''
})

function selectPost(name: string) {
  focusedPostName.value = name
  const post = model.value.postByName[name]
  if (post) emit('post-click', post)
}

/** 清空聚焦 → 回到「全部岗位」聚合态。 */
function clearFocus() {
  focusedPostName.value = ''
}

/* ---------- 小球 hover 提示 ---------- */

function showTip(event: MouseEvent, label: string, meta: string) {
  const host = stageRef.value
  if (!host) return
  const rect = host.getBoundingClientRect()
  hoverTip.value = { label, meta, x: event.clientX - rect.left, y: event.clientY - rect.top }
}

/** 岗位小球：既用于「同族高亮」，也给出名称提示（密集时名称是隐藏的）。 */
function enterPostNode(event: MouseEvent, name: string, stack: string) {
  hoverStack.value = stack
  const post = model.value.postByName[name]
  showTip(event, name, post ? `${stack} · ${post.abilities.length} 项能力` : stack)
}

/** 技术栈节点：技术栈多时环 1 的名称会被隐藏，提示里补上岗位数。 */
function enterStackNode(event: MouseEvent, name: string) {
  hoverStack.value = name
  showTip(event, name, `${stackPostCount(name)} 个岗位`)
}

function leaveNode() {
  hoverStack.value = ''
  hoverTip.value = null
}

/** 聚合态内环（枢纽能力）：提示里给出被多少个岗位需要。 */
function enterOverviewAbility(event: MouseEvent, name: string, hubs: number) {
  showTip(event, name, `${hubs} 个岗位需要`)
}

/** 聚合态外环（技术栈族）：提示里给出族内岗位数。 */
function enterOverviewStack(event: MouseEvent, name: string, postCount: number) {
  showTip(event, name, `${postCount} 个岗位`)
}

function sectorPath(a0: number, a1: number) {
  return wedgePath(PANORAMA2D_VIEWBOX.cx, PANORAMA2D_VIEWBOX.cy, 62, PANORAMA2D_VIEWBOX.r2 + 74, a0, a1)
}

function tintPath(a0: number, a1: number) {
  return wedgePath(PANORAMA2D_VIEWBOX.cx, PANORAMA2D_VIEWBOX.cy, 62, 66, a0, a1)
}

/** 标签落点：按环半径外推一小段，避免压到节点圆上。 */
function labelPoint(angle: number, radius: number) {
  return polarPoint(PANORAMA2D_VIEWBOX.cx, PANORAMA2D_VIEWBOX.cy, radius, angle)
}

function isStackDimmed(stack: string) {
  return !!hoverStack.value && hoverStack.value !== stack
}

function stackPostCount(stack: string) {
  return model.value.stacks.find(item => item.name === stack)?.postCount ?? 0
}

function abilitySharedCount(abilityName: string) {
  return (model.value.abilityPosts[abilityName] || []).length
}

function abilitySharedOthers(abilityName: string) {
  return (model.value.abilityPosts[abilityName] || []).filter(name => name !== focusedPostName.value)
}

/** 矩阵十字高亮：hover 到单元格时同时点亮所在行与列。 */
function cellKey(abilityName: string, postName: string) {
  return `${abilityName}\u0000${postName}`
}

function hoveredTuple() {
  if (!hoverCell.value) return null
  const [abilityName, postName] = hoverCell.value.split('\u0000')
  return { abilityName, postName }
}

function isAbilityRowActive(abilityName: string) {
  const hovered = hoveredTuple()
  return !!hovered && hovered.abilityName === abilityName
}

function isPostColumnActive(postName: string) {
  const hovered = hoveredTuple()
  return !!hovered && hovered.postName === postName
}

/* ---------- 邻域星图几何 ---------- */
const EGO_R1 = 132
const EGO_R2 = 244

const egoAbilities = computed(() => ego.value.abilities.map((ability, index, list) => {
  const angle = -Math.PI / 2 + (Math.PI * 2 * index) / Math.max(list.length, 1)
  const point = polarPoint(PANORAMA2D_VIEWBOX.cx, PANORAMA2D_VIEWBOX.cy, EGO_R1, angle)
  return { ...ability, angle, x: point.x, y: point.y }
}))

const egoNeighbors = computed(() => ego.value.neighbors.map((neighbor, index, list) => {
  const span = Math.PI * 2
  const angle = -Math.PI / 2 + span * (index + 0.5) / Math.max(list.length, 1)
  const point = polarPoint(PANORAMA2D_VIEWBOX.cx, PANORAMA2D_VIEWBOX.cy, EGO_R2, angle)
  return { ...neighbor, angle, x: point.x, y: point.y }
}))

/* ---------- 聚合态几何（全部岗位） ---------- */
const overviewAbilities = computed(() => overview.value.hubAbilities.map((ability, index, list) => {
  const angle = -Math.PI / 2 + (Math.PI * 2 * index) / Math.max(list.length, 1)
  const point = polarPoint(PANORAMA2D_VIEWBOX.cx, PANORAMA2D_VIEWBOX.cy, EGO_R1, angle)
  return { ...ability, angle, x: point.x, y: point.y }
}))

const overviewStacks = computed(() => overview.value.stacks.map((stack, index, list) => {
  const angle = -Math.PI / 2 + (Math.PI * 2) * (index + 0.5) / Math.max(list.length, 1)
  const point = polarPoint(PANORAMA2D_VIEWBOX.cx, PANORAMA2D_VIEWBOX.cy, EGO_R2, angle)
  return { ...stack, angle, x: point.x, y: point.y }
}))

/** 聚合态内环球半径：被越多岗位需要，球越大（7–14px）。 */
function overviewAbilityRadius(hubs: number) {
  const max = Math.max(1, overview.value.hubAbilities[0]?.hubs ?? 1)
  return 7 + (Math.max(0, hubs) / max) * 7
}
</script>

<template>
  <div class="p2d">
    <header class="p2d-head">
      <nav class="p2d-tabs" role="tablist" aria-label="全景图谱读法">
        <button
          v-for="item in PANORAMA2D_VIEWS"
          :key="item.value"
          type="button"
          role="tab"
          :aria-selected="view === item.value"
          class="p2d-tab"
          :class="{ 'is-active': view === item.value }"
          @click="view = item.value"
        >
          {{ item.label }}
        </button>
      </nav>
      <div class="p2d-legend">
        <template v-if="view === 'matrix'">
          <span>格色 = 归属技术栈</span>
          <span>深浅 = 要求等级</span>
          <span>格内数字 = 核心能力等级</span>
          <span>行尾数字 = 覆盖岗位数</span>
        </template>
        <template v-else-if="view === 'ego'">
          <span><i class="swatch" style="background:#1d4ed8" />核心能力</span>
          <span><i class="swatch" style="background:#60a5fa" />非核心能力</span>
          <span>线宽 = 权重</span>
          <span>外环 = 同样需要这些能力的岗位</span>
        </template>
        <template v-else>
          <span v-for="stack in legendStacks" :key="stack.name">
            <i class="swatch" :style="{ background: stack.color }" />{{ stack.name }}
          </span>
        </template>
      </div>
    </header>

    <p class="p2d-sub">{{ currentViewMeta.title }} —— {{ currentViewMeta.subtitle }}</p>

    <div v-if="model.empty" class="p2d-empty">
      暂无可展示的岗位图谱数据，请调整筛选条件后重试。
    </div>

    <div v-else class="p2d-body">
      <section ref="stageRef" class="p2d-stage">
        <!-- ============ 全景 · 分层同心环 ============ -->
        <svg
          v-if="view === 'rings'"
          class="p2d-svg"
          :viewBox="viewBox"
          :style="svgAspectStyle"
          role="img"
          aria-label="技术栈与岗位的分层同心环"
        >
          <g>
            <path
              v-for="(sector, index) in rings.sectors"
              :key="`sector-${sector.name}`"
              :d="sectorPath(sector.a0, sector.a1)"
              :fill="index % 2 ? '#f7fafe' : '#eef4ff'"
            />
            <path
              v-for="sector in rings.sectors"
              :key="`tint-${sector.name}`"
              :d="tintPath(sector.a0, sector.a1)"
              :fill="sector.color"
              opacity="0.5"
            />
          </g>

          <circle :cx="PANORAMA2D_VIEWBOX.cx" :cy="PANORAMA2D_VIEWBOX.cy" :r="PANORAMA2D_VIEWBOX.r1" fill="none" stroke="#e3eaf6" />
          <circle
            :cx="PANORAMA2D_VIEWBOX.cx"
            :cy="PANORAMA2D_VIEWBOX.cy"
            :r="PANORAMA2D_VIEWBOX.r2"
            fill="none"
            stroke="#e3eaf6"
            stroke-dasharray="3 5"
          />

          <circle
            :cx="PANORAMA2D_VIEWBOX.cx"
            :cy="PANORAMA2D_VIEWBOX.cy"
            r="46"
            fill="#eef4ff"
            stroke="#cfd9ee"
          />
          <text
            :x="PANORAMA2D_VIEWBOX.cx"
            :y="PANORAMA2D_VIEWBOX.cy - 4"
            text-anchor="middle"
            class="p2d-center-label"
          >{{ model.domain }}</text>
          <text
            :x="PANORAMA2D_VIEWBOX.cx"
            :y="PANORAMA2D_VIEWBOX.cy + 22"
            text-anchor="middle"
            class="p2d-center-sub"
          >{{ model.stacks.length }} 技术栈 · {{ model.posts.length }} 岗位</text>

          <g
            v-for="node in rings.stacks"
            :key="node.id"
            class="p2d-node"
            :class="{ 'is-dim': isStackDimmed(node.stack) }"
            @mouseenter="enterStackNode($event, node.stack)"
            @mouseleave="leaveNode()"
          >
            <line
              :x1="PANORAMA2D_VIEWBOX.cx"
              :y1="PANORAMA2D_VIEWBOX.cy"
              :x2="node.x"
              :y2="node.y"
              :stroke="node.color"
              opacity="0.35"
            />
            <circle :cx="node.x" :cy="node.y" r="24" :fill="node.color" />
            <!-- 技术栈多到放不下文字时隐藏名称，改由 hover 提示给出（见 enterStackNode） -->
            <template v-if="rings.density.showStackLabels">
              <text
                class="p2d-stack-label"
                :x="node.x"
                :y="node.y + 38"
                text-anchor="middle"
              >{{ node.label }}</text>
              <text
                class="p2d-count-label"
                :x="node.x"
                :y="node.y + 53"
                text-anchor="middle"
              >{{ stackPostCount(node.stack) }} 个岗位</text>
            </template>
          </g>

          <g
            v-for="node in rings.posts"
            :key="node.id"
            class="p2d-node p2d-node--post"
            :class="{ 'is-dim': isStackDimmed(node.stack), 'is-active': focusedPostName === node.label }"
            @mouseenter="enterPostNode($event, node.label, node.stack)"
            @mouseleave="leaveNode()"
            @click="selectPost(node.label)"
          >
            <line
              v-if="stackNodeByName[node.stack]"
              :x1="stackNodeByName[node.stack].x"
              :y1="stackNodeByName[node.stack].y"
              :x2="node.x"
              :y2="node.y"
              :stroke="node.color"
              opacity="0.28"
            />
            <circle
              :cx="node.x"
              :cy="node.y"
              :r="focusedPostName === node.label ? rings.density.nodeRadius + 1.8 : rings.density.nodeRadius"
              fill="#fff"
              :stroke="node.color"
              :stroke-width="focusedPostName === node.label ? nodeStrokeWidth + 1 : nodeStrokeWidth"
            />
            <!-- 名称只在宽松时全显；密集时靠 hover。但**当前选中的那个始终显示**，否则选中了却找不到它。 -->
            <text
              v-if="rings.density.showPostLabels || focusedPostName === node.label"
              class="p2d-post-label"
              :class="{ 'is-active': focusedPostName === node.label }"
              :x="labelPoint(node.angle, PANORAMA2D_VIEWBOX.r2 + 17).x"
              :y="labelPoint(node.angle, PANORAMA2D_VIEWBOX.r2 + 17).y + 4"
              :text-anchor="labelAnchor(node.angle)"
            >{{ node.label }}</text>
          </g>

          <!-- 密集时给出可见的说明，避免用户以为「岗位名丢了」 -->
          <text
            v-if="!rings.density.showPostLabels"
            class="p2d-density-hint"
            :x="PANORAMA2D_VIEWBOX.cx"
            :y="PANORAMA2D_VIEWBOX.height - 12"
            text-anchor="middle"
          >共 {{ model.posts.length }} 个岗位（每个扇区约 {{ rings.density.postsPerSector }} 个），名称已隐藏 —— 悬浮小球可查看</text>
        </svg>

        <!-- ============ 关联 · 能力矩阵 ============ -->
        <div v-else-if="view === 'matrix'" class="p2d-matrix-wrap">
          <p v-if="matrix.truncated.abilities || matrix.truncated.posts" class="p2d-matrix-note">
            已截断显示：另有 {{ matrix.truncated.abilities }} 项能力、{{ matrix.truncated.posts }} 个岗位未展示。
            可用左侧筛选收窄范围。
          </p>
          <table class="p2d-matrix">
            <thead>
              <tr>
                <th class="mx-corner">能力 \ 岗位</th>
                <th
                  v-for="post in matrix.columns"
                  :key="post.id"
                  class="mx-posthead"
                  :class="{ 'is-active': isPostColumnActive(post.name) }"
                  :title="`${post.name}（${post.stack}）`"
                >
                  <i :style="{ background: post.color }" />
                  <span>{{ post.name }}</span>
                </th>
                <th class="mx-tailhead">岗位数</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="row in matrix.rows"
                :key="row.name"
                :class="{ 'is-active': isAbilityRowActive(row.name) }"
              >
                <th class="mx-rowhead" :title="row.name">
                  <b>{{ row.name }}</b>
                </th>
                <td
                  v-for="post in matrix.columns"
                  :key="`${row.name}-${post.id}`"
                  class="mx-cell"
                  :class="{ 'is-hovered': hoverCell === cellKey(row.name, post.name) }"
                  @mouseenter="hoverCell = cellKey(row.name, post.name)"
                  @mouseleave="hoverCell = ''"
                  @click="selectPost(post.name)"
                >
                  <template v-if="findPostAbility(post, row.name)">
                    <div
                      class="mx-chip"
                      :style="{
                        background: post.color,
                        opacity: 0.28 + (findPostAbility(post, row.name)!.level / 5) * 0.72,
                      }"
                    >{{ findPostAbility(post, row.name)!.core ? `L${findPostAbility(post, row.name)!.level}` : '' }}</div>
                  </template>
                  <div v-else class="mx-chip mx-chip--empty" />
                </td>
                <td class="mx-tail">{{ row.hubs }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- ============ 聚焦 · 邻域星图 ============ -->
        <svg
          v-else
          class="p2d-svg"
          :viewBox="viewBox"
          :style="svgAspectStyle"
          role="img"
          aria-label="单个岗位的能力邻域"
        >
          <circle
            :cx="PANORAMA2D_VIEWBOX.cx"
            :cy="PANORAMA2D_VIEWBOX.cy"
            :r="EGO_R1"
            fill="none"
            stroke="#e3eaf6"
            stroke-dasharray="3 5"
          />
          <circle
            :cx="PANORAMA2D_VIEWBOX.cx"
            :cy="PANORAMA2D_VIEWBOX.cy"
            :r="EGO_R2"
            fill="none"
            stroke="#e3eaf6"
            stroke-dasharray="3 5"
          />

          <g v-if="ego.focus">
            <line
              v-for="ability in egoAbilities"
              :key="`edge-${ability.name}`"
              :x1="PANORAMA2D_VIEWBOX.cx"
              :y1="PANORAMA2D_VIEWBOX.cy"
              :x2="ability.x"
              :y2="ability.y"
              :stroke="ability.core ? '#1d4ed8' : '#60a5fa'"
              :stroke-width="1 + (ability.weight / 100) * 2.2"
              opacity="0.45"
            />
            <line
              v-for="neighbor in egoNeighbors"
              :key="`outer-${neighbor.name}`"
              :x1="PANORAMA2D_VIEWBOX.cx"
              :y1="PANORAMA2D_VIEWBOX.cy"
              :x2="neighbor.x"
              :y2="neighbor.y"
              :stroke="neighbor.color"
              stroke-dasharray="4 4"
              opacity="0.3"
            />

            <circle
              :cx="PANORAMA2D_VIEWBOX.cx"
              :cy="PANORAMA2D_VIEWBOX.cy"
              r="52"
              :fill="ego.focus.color"
            />
            <text
              class="p2d-ego-center"
              :x="PANORAMA2D_VIEWBOX.cx"
              :y="PANORAMA2D_VIEWBOX.cy + 4"
              text-anchor="middle"
            >{{ ego.focus.name }}</text>

            <g v-for="ability in egoAbilities" :key="`ab-${ability.name}`" class="p2d-node">
              <circle
                :cx="ability.x"
                :cy="ability.y"
                :r="ability.core ? 11 : 8"
                :fill="ability.core ? '#1d4ed8' : '#60a5fa'"
              />
              <circle
                v-if="ability.core"
                :cx="ability.x"
                :cy="ability.y"
                r="16"
                fill="none"
                stroke="#1d4ed8"
                opacity="0.35"
              />
              <text
                class="p2d-post-label"
                :x="labelPoint(ability.angle, EGO_R1 + 16).x"
                :y="labelPoint(ability.angle, EGO_R1 + 16).y + 4"
                :text-anchor="labelAnchor(ability.angle)"
              >{{ ability.name }}</text>
            </g>

            <g
              v-for="neighbor in egoNeighbors"
              :key="`nb-${neighbor.name}`"
              class="p2d-node p2d-node--post"
              :class="{ 'is-active': focusedPostName === neighbor.name }"
              @click="selectPost(neighbor.name)"
            >
              <circle
                :cx="neighbor.x"
                :cy="neighbor.y"
                r="7"
                fill="#fff"
                :stroke="neighbor.color"
                stroke-width="2"
              />
              <text
                class="p2d-post-label"
                :x="labelPoint(neighbor.angle, EGO_R2 + 16).x"
                :y="labelPoint(neighbor.angle, EGO_R2 + 16).y + 4"
                :text-anchor="labelAnchor(neighbor.angle)"
              >{{ neighbor.name }}</text>
            </g>
          </g>

          <!-- ============ 全部岗位 · 聚合态 ============ -->
          <!--
            没有聚焦任何单个岗位时展示**全量聚合**，而不是「随便挑一个岗位顶上」：
            中心 = 领域，内环 = 枢纽能力（被最多岗位需要），外环 = 技术栈族。
            这是「筛选为全部岗位」时与用户预期一致的读法。
          -->
          <g v-else>
            <line
              v-for="ability in overviewAbilities"
              :key="`ov-edge-${ability.name}`"
              :x1="PANORAMA2D_VIEWBOX.cx"
              :y1="PANORAMA2D_VIEWBOX.cy"
              :x2="ability.x"
              :y2="ability.y"
              stroke="#93b4f7"
              opacity="0.5"
            />
            <line
              v-for="stack in overviewStacks"
              :key="`ov-stedge-${stack.name}`"
              :x1="PANORAMA2D_VIEWBOX.cx"
              :y1="PANORAMA2D_VIEWBOX.cy"
              :x2="stack.x"
              :y2="stack.y"
              :stroke="stack.color"
              stroke-dasharray="4 4"
              opacity="0.25"
            />

            <circle :cx="PANORAMA2D_VIEWBOX.cx" :cy="PANORAMA2D_VIEWBOX.cy" r="64" fill="#1d4ed8" />
            <text
              class="p2d-center-label"
              :x="PANORAMA2D_VIEWBOX.cx"
              :y="PANORAMA2D_VIEWBOX.cy - 6"
              text-anchor="middle"
            >{{ overview.domain }}</text>
            <text
              class="p2d-center-sub"
              :x="PANORAMA2D_VIEWBOX.cx"
              :y="PANORAMA2D_VIEWBOX.cy + 14"
              text-anchor="middle"
            >全部 {{ overview.postCount }} 个岗位</text>
            <text
              class="p2d-center-sub"
              :x="PANORAMA2D_VIEWBOX.cx"
              :y="PANORAMA2D_VIEWBOX.cy + 30"
              text-anchor="middle"
            >{{ overview.stackCount }} 个技术栈</text>

            <g
              v-for="ability in overviewAbilities"
              :key="`ov-ab-${ability.name}`"
              class="p2d-node"
              @mouseenter="enterOverviewAbility($event, ability.name, ability.hubs)"
              @mouseleave="leaveNode()"
            >
              <circle
                :cx="ability.x"
                :cy="ability.y"
                :r="overviewAbilityRadius(ability.hubs)"
                fill="#4f7bd6"
              />
              <text
                class="p2d-post-label"
                :x="labelPoint(ability.angle, EGO_R1 + 17).x"
                :y="labelPoint(ability.angle, EGO_R1 + 17).y + 4"
                :text-anchor="labelAnchor(ability.angle)"
              >{{ ability.name }}</text>
            </g>

            <g
              v-for="stack in overviewStacks"
              :key="`ov-st-${stack.name}`"
              class="p2d-node"
              @mouseenter="enterOverviewStack($event, stack.name, stack.postCount)"
              @mouseleave="leaveNode()"
            >
              <circle :cx="stack.x" :cy="stack.y" r="12" :fill="stack.color" />
              <text
                v-if="rings.density.showStackLabels"
                class="p2d-post-label"
                :x="labelPoint(stack.angle, EGO_R2 + 17).x"
                :y="labelPoint(stack.angle, EGO_R2 + 17).y + 4"
                :text-anchor="labelAnchor(stack.angle)"
              >{{ stack.name }}</text>
            </g>
          </g>
        </svg>

        <!-- 小球 hover 提示：跟随鼠标，补上「名称被隐藏」时的信息 -->
        <p
          v-if="hoverTip"
          class="p2d-tip"
          :style="{ left: `${hoverTip.x + 14}px`, top: `${hoverTip.y + 14}px` }"
        >
          <b>{{ hoverTip.label }}</b>
          <span>{{ hoverTip.meta }}</span>
        </p>
      </section>

      <aside class="p2d-panel">
        <template v-if="ego.focus">
          <h2 class="p2d-panel-title">{{ ego.focus.name }}</h2>
          <div class="p2d-chips">
            <span class="p2d-chip" :style="{ background: `${ego.focus.color}1a`, color: ego.focus.color }">
              {{ ego.focus.stack }}
            </span>
            <span class="p2d-chip">{{ ego.focus.abilities.length }} 项能力</span>
            <span class="p2d-chip">{{ ego.focus.abilities.filter(a => a.core).length }} 项核心</span>
          </div>

          <div class="p2d-sec">
            <div class="p2d-sec-title">
              能力要求
              <button type="button" class="p2d-link" @click="view = 'ego'">查看邻域</button>
            </div>
            <div
              v-for="ability in ego.focus.abilities"
              :key="ability.name"
              class="p2d-ability"
            >
              <div class="p2d-ability-top">
                <span class="p2d-ability-name">{{ ability.name }}</span>
                <span v-if="ability.core" class="p2d-chip p2d-chip--core">核心</span>
                <span class="p2d-ability-level">L{{ ability.level }}</span>
              </div>
              <div class="p2d-bar">
                <i :style="{ width: `${ability.weight}%`, background: ego.focus.color }" />
              </div>
              <div class="p2d-ability-meta">
                <span>权重 {{ ability.weight }}%</span>
                <span>
                  {{ abilitySharedOthers(ability.name).length
                    ? `另有 ${abilitySharedOthers(ability.name).length} 个岗位需要`
                    : '该岗位独有' }}
                </span>
              </div>
              <div v-if="abilitySharedOthers(ability.name).length" class="p2d-tags">
                <span
                  v-for="other in abilitySharedOthers(ability.name)"
                  :key="other"
                  class="p2d-tag"
                >{{ other }}</span>
              </div>
            </div>
          </div>

          <div class="p2d-sec">
            <div class="p2d-sec-title">技术栈族（{{ ego.focus.stack }}）</div>
            <div
              v-for="post in model.posts.filter(item => item.stack === ego.focus!.stack)"
              :key="post.id"
              class="p2d-stackrow"
            >
              <i :style="{ background: post.color }" />
              <b>{{ post.name }}</b>
              <em>{{ post.abilities.length }} 项</em>
            </div>
          </div>

          <div class="p2d-sec">
            <div class="p2d-sec-title">枢纽能力 Top 5</div>
            <div v-for="ability in model.abilities.slice(0, 5)" :key="ability.name" class="p2d-kv">
              <span>{{ ability.name }}</span>
              <strong>{{ ability.hubs }} 个岗位</strong>
            </div>
          </div>
        </template>
        <!--
          聚合面板：未聚焦单个岗位时的替代内容。
          没有它时这里只能给出「请选择一个岗位」，而筛选为「全部岗位」的用户
          并不想选 —— 面板本身就该回答「全部岗位长什么样」。
        -->
        <template v-else>
          <h2 class="p2d-panel-title">{{ overview.domain }}<span class="p2d-panel-scope">全部岗位</span></h2>
          <div class="p2d-chips">
            <span class="p2d-chip">{{ overview.postCount }} 个岗位</span>
            <span class="p2d-chip">{{ overview.stackCount }} 个技术栈</span>
            <span class="p2d-chip">{{ model.abilities.length }} 项能力</span>
          </div>
          <p class="p2d-panel-hint">点击图中任一岗位小球，可查看该岗位的能力详情。</p>

          <div class="p2d-sec">
            <div class="p2d-sec-title">枢纽能力（被最多岗位需要）</div>
            <div v-for="ability in overview.hubAbilities" :key="ability.name" class="p2d-kv">
              <span>{{ ability.name }}</span>
              <strong>{{ ability.hubs }} 个岗位 · 均 L{{ ability.avgLevel }}</strong>
            </div>
            <p v-if="overview.truncatedAbilities" class="p2d-sec-note">
              另有 {{ overview.truncatedAbilities }} 项能力未列出（未进入前 {{ overview.hubAbilities.length }}）。
            </p>
          </div>

          <div class="p2d-sec">
            <div class="p2d-sec-title">技术栈族分布</div>
            <div v-for="stack in overview.stacks" :key="stack.name" class="p2d-stackrow">
              <i :style="{ background: stack.color }" />
              <b>{{ stack.name }}</b>
              <em>{{ stack.postCount }} 个</em>
            </div>
          </div>
        </template>
      </aside>
    </div>
  </div>
</template>

<style scoped lang="scss">
/*
 * 本组件在页面里是「正常文档流的一块内容」，不是铺满全屏的浮层：
 * 页面本身负责滚动，这里只负责给出自己的表面（边框/圆角/底色）。
 * 之前写成 position:absolute + inset:0 是为了配合 3D 那种全屏浮层版式，
 * 嵌进 page-shell 后会让自身脱离文档流、把页面高度压成 0。
 */
.p2d {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 420px;
  overflow: hidden;
  border: 1px solid var(--app-border, #e3eaf6);
  border-radius: var(--app-radius-lg, 14px);
  background: var(--app-bg, #f4f7fd);
  box-shadow: var(--app-shadow-sm, 0 2px 10px rgba(22, 34, 63, 0.04));
}

/* ---------- 头部 ---------- */
.p2d-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  padding: 14px 20px 0;
}

.p2d-tabs {
  display: flex;
  gap: 6px;
  padding: 4px;
  border: 1px solid var(--app-border, #e3eaf6);
  border-radius: var(--app-radius-md, 10px);
  background: #fff;
}

.p2d-tab {
  border: 0;
  padding: 7px 16px;
  border-radius: var(--app-radius-sm, 8px);
  background: transparent;
  color: #58668a;
  font: inherit;
  font-size: 12.5px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;

  &:hover { background: #f6f9fe; color: var(--app-text-strong, #16223f); }
  &.is-active { background: var(--app-primary, #2f6bff); color: #fff; font-weight: 500; }
}

.p2d-legend {
  display: flex;
  align-items: center;
  gap: 11px;
  flex-wrap: wrap;
  color: #58668a;
  font-size: 11.5px;

  span { display: inline-flex; align-items: center; gap: 5px; }
}

.swatch {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 3px;
}

.p2d-sub {
  margin: 8px 20px 10px;
  color: #8b97b5;
  font-size: 12px;
}

/* ---------- 主体 ---------- */
/*
 * 不能写 flex:1 —— 那是 flex-basis:0，只在「父级高度固定」的浮层里成立。
 * 现在是正常文档流、高度由内容决定：flex-basis:0 会让本区块被压到
 * min-height 的剩余空间里，图被塞进一个几百像素高的内部滚动条。
 * 因此改为按内容撑开（矩阵有自己的 max-height + 内部滚动，见 .p2d-matrix-wrap）。
 */
.p2d-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 336px;
  gap: 16px;
  align-items: start;
  padding: 0 20px 18px;

  @media (max-width: 1180px) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.p2d-stage {
  /* hover 提示以本容器为定位基准（.p2d-tip 是它的绝对定位子元素） */
  position: relative;
  min-width: 0;
  border: 1px solid var(--app-border, #e3eaf6);
  border-radius: var(--app-radius-lg, 14px);
  background: #fff;
  padding: 8px 10px;
}

.p2d-svg {
  display: block;
  width: 100%;
  height: auto;
}

.p2d-empty {
  margin: 40px 20px;
  padding: 40px;
  border: 1px dashed var(--app-border-strong, #cfd9ee);
  border-radius: var(--app-radius-lg, 14px);
  background: #fff;
  color: #8b97b5;
  text-align: center;
  font-size: 13px;
}

/* ---------- SVG 节点 ---------- */
.p2d-node {
  transition: opacity 0.18s;

  &.is-dim { opacity: 0.22; }
}

.p2d-node--post { cursor: pointer; }

.p2d-center-label {
  font-size: 13px;
  font-weight: 600;
  fill: var(--app-text-strong, #16223f);
}

.p2d-center-sub {
  font-size: 10.5px;
  fill: #8b97b5;
}

.p2d-stack-label {
  font-size: 12.5px;
  font-weight: 500;
  fill: var(--app-text-strong, #16223f);
}

.p2d-count-label {
  font-size: 10.5px;
  fill: #8b97b5;
}

.p2d-post-label {
  font-size: 11.5px;
  fill: #2a3550;

  &.is-active { font-weight: 700; fill: var(--app-text-strong, #16223f); }
}

.p2d-ego-center {
  font-size: 13px;
  font-weight: 700;
  fill: #fff;
}

/* ---------- 疏密提示与 hover 提示 ---------- */
.p2d-density-hint {
  font-size: 11px;
  fill: #8b97b5;
}

/*
 * 小球 hover 提示。
 * `pointer-events: none` 是必须的：提示紧贴光标出现，若能接收指针事件，
 * 光标一进入提示就触发下面小球的 mouseleave、提示消失、光标又落回小球 —— 无限闪烁。
 */
.p2d-tip {
  position: absolute;
  z-index: 6;
  max-width: 260px;
  margin: 0;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(22, 34, 63, 0.94);
  color: #fff;
  font-size: 11.5px;
  line-height: 1.5;
  pointer-events: none;
  box-shadow: 0 4px 14px rgba(22, 34, 63, 0.22);

  b {
    display: block;
    font-weight: 600;
  }

  span {
    color: rgba(255, 255, 255, 0.75);
  }
}

/* ---------- 矩阵 ---------- */
.p2d-matrix-wrap {
  max-height: 640px;
  overflow: auto;
}

.p2d-matrix-note {
  margin: 0 0 8px;
  padding: 7px 10px;
  border-radius: var(--app-radius-sm, 8px);
  background: #fff8ec;
  color: #b45309;
  font-size: 11.5px;
}

.p2d-matrix {
  border-collapse: separate;
  border-spacing: 0;
  font-size: 11.5px;

  th,
  td { padding: 0; }

  .mx-corner {
    position: sticky;
    top: 0;
    left: 0;
    z-index: 3;
    padding: 0 10px 6px;
    background: #fff;
    border-right: 1px solid #edf2fa;
    border-bottom: 1px solid #edf2fa;
    color: #8b97b5;
    font-weight: 600;
    text-align: left;
    white-space: nowrap;
  }

  /*
   * 列头（岗位）改为**横排**。
   *
   * 原实现是 `width: 34px` + `writing-mode: vertical-rl; transform: rotate(180deg)`：
   * 34px 宽的中文列头只能一个字一行竖着排，用户反馈就是「关联矩阵不要竖向显示」。
   * 横排后列宽由内容决定，表格整体变宽 → 由 .p2d-matrix-wrap 横向滚动承载；
   * 同时行上限从 60 降到 30（见 PANORAMA2D_MATRIX_LIMITS），
   * 两者合起来把「竖着滚很久」变成「横着扫一眼」。
   */
  .mx-posthead {
    position: sticky;
    top: 0;
    z-index: 2;
    min-width: 92px;
    padding: 6px 8px;
    background: #fff;
    border-bottom: 1px solid #edf2fa;
    vertical-align: bottom;

    &.is-active { background: var(--app-primary-soft, #e9f0ff); }

    i {
      display: inline-block;
      width: 8px;
      height: 8px;
      margin: 0 4px 0 0;
      border-radius: 2px;
      vertical-align: middle;
    }

    span {
      display: inline-block;
      color: var(--app-text-strong, #16223f);
      font-size: 11px;
      font-weight: 500;
      white-space: nowrap;
      vertical-align: middle;
    }
  }

  .mx-tailhead {
    position: sticky;
    top: 0;
    z-index: 2;
    padding: 0 8px 6px;
    background: #fff;
    border-bottom: 1px solid #edf2fa;
    border-left: 1px solid #edf2fa;
    color: #8b97b5;
    font-weight: 600;
    text-align: right;
    white-space: nowrap;
  }

  .mx-rowhead {
    position: sticky;
    left: 0;
    z-index: 1;
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 0 10px 0 8px;
    background: #fff;
    border-right: 1px solid #edf2fa;
    border-bottom: 1px solid #f4f7fd;
    text-align: left;
    font-weight: 400;

    b {
      max-width: 132px;
      overflow: hidden;
      color: var(--app-text, #2a3550);
      font-size: 11.5px;
      font-weight: 400;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  tr.is-active .mx-rowhead { background: var(--app-primary-soft, #e9f0ff); }

  .mx-cell {
    /* 与横排后的列头同宽，保证每列对齐（列头 min-width 92px） */
    width: 92px;
    height: 20px;
    border-right: 1px solid #f7faff;
    border-bottom: 1px solid #f4f7fd;
    text-align: center;
    cursor: pointer;

    &.is-hovered { outline: 1.5px solid var(--app-primary, #2f6bff); outline-offset: -1.5px; }
  }

  .mx-chip {
    /* 格子变宽了（92px），色块同步放大才有「柱子」的观感 */
    width: 58px;
    height: 13px;
    margin: 0 auto;
    border-radius: 2px;
    color: #fff;
    font-size: 9px;
    font-weight: 600;
    line-height: 13px;

    &--empty { background: #f2f5fb; }
  }

  .mx-tail {
    padding: 0 8px;
    border-bottom: 1px solid #f4f7fd;
    border-left: 1px solid #edf2fa;
    color: #8b97b5;
    text-align: right;
    font-variant-numeric: tabular-nums;
  }
}

/* ---------- 右侧面板 ---------- */
.p2d-panel {
  min-width: 0;
  padding: 16px 18px;
  border: 1px solid var(--app-border, #e3eaf6);
  border-radius: var(--app-radius-lg, 14px);
  background: #fff;
}

.p2d-panel-title {
  margin: 0 0 10px;
  color: var(--app-text-strong, #16223f);
  font-size: 14px;
  font-weight: 600;
}

.p2d-panel-empty {
  margin: 0;
  color: #8b97b5;
  font-size: 12.5px;
}

/** 面板标题上的「全部岗位」角标：把聚合态与单岗位态在视觉上分开。 */
.p2d-panel-scope {
  margin-left: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--app-primary-soft, #e9f0ff);
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 500;
  vertical-align: middle;
}

/** 面板顶部的一句操作提示（聚合态下引导用户点球）。 */
.p2d-panel-hint {
  margin: 0 0 12px;
  color: #8b97b5;
  font-size: 11.5px;
  line-height: 1.6;
}

/** 分区末尾的补充说明（如「另有 N 项能力未列出」）。 */
.p2d-sec-note {
  margin: 8px 0 0;
  color: #8b97b5;
  font-size: 11px;
  line-height: 1.5;
}

.p2d-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.p2d-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--app-primary-soft, #e9f0ff);
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 500;

  &--core { background: #fff3e6; color: #b45309; }
}

.p2d-sec {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #edf2fa;

  &:first-of-type { margin-top: 0; padding-top: 0; border-top: 0; }
}

.p2d-sec-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 9px;
  color: #8b97b5;
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.p2d-link {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--app-primary, #2f6bff);
  font: inherit;
  font-size: 11px;
  cursor: pointer;

  &:hover { color: var(--app-primary-hover, #1f57e0); text-decoration: underline; }
}

.p2d-ability {
  margin-bottom: 9px;
  padding: 9px 11px;
  border: 1px solid var(--app-border, #e3eaf6);
  border-radius: var(--app-radius-md, 10px);
}

.p2d-ability-top {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 6px;
}

.p2d-ability-name {
  color: var(--app-text-strong, #16223f);
  font-size: 12.5px;
  font-weight: 500;
}

.p2d-ability-level {
  margin-left: auto;
  color: #8b97b5;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.p2d-bar {
  height: 4px;
  border-radius: 2px;
  background: #edf2fa;
  overflow: hidden;

  i { display: block; height: 100%; border-radius: 2px; }
}

.p2d-ability-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 5px;
  color: #8b97b5;
  font-size: 11px;
}

.p2d-tags {
  margin-top: 5px;
}

.p2d-tag {
  display: inline-block;
  margin: 3px 4px 0 0;
  padding: 1px 5px;
  border: 1px solid var(--app-border, #e3eaf6);
  border-radius: 4px;
  background: #f6f9fe;
  color: #58668a;
  font-size: 10.5px;
}

.p2d-stackrow {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 0;
  font-size: 12px;

  i { flex: none; width: 9px; height: 9px; border-radius: 3px; }
  b { flex: 1; color: var(--app-text, #2a3550); font-weight: 400; }
  em { color: #8b97b5; font-style: normal; font-variant-numeric: tabular-nums; }
}

.p2d-kv {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 6px 0;
  border-bottom: 1px solid #edf2fa;
  font-size: 12px;

  &:last-of-type { border-bottom: 0; }

  span { color: #8b97b5; }
  strong { color: var(--app-text-strong, #16223f); font-weight: 500; }
}
</style>
