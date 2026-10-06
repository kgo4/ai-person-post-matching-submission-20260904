import type { PanoramaGraphData } from '@/api/post-panorama'

/**
 * 岗位全景图谱 · 2D 呈现的数据与几何（纯逻辑，无 DOM）。
 *
 * 为什么把几何也放进来：三种视图（同心环 / 能力矩阵 / 邻域星图）的坐标全部是
 * 「输入数据 → 确定位置」的纯计算，与 Vue 无关。放在这里就能用 .mjs 直接断言，
 * 组件只负责把算好的坐标画出来 —— 这也是这套视图能替代 3D 力导向的基础：
 * 位置可复现、可测试，不再依赖迭代求解。
 */

/* ===================== 类型 ===================== */

export interface Panorama2DAbility {
  name: string
  /** 要求等级 1-5 */
  level: number
  /** 权重百分比 0-100 */
  weight: number
  core: boolean
  /**
   * 能力自身归属的技术栈类别。
   * 保留它是因为技术栈节点缺失时（旧后端 / 关系投影未生成），岗位所属技术栈只能靠它推导。
   */
  category?: string
}

export interface Panorama2DPost {
  id: string
  name: string
  stack: string
  color: string
  abilities: Panorama2DAbility[]
}

export interface Panorama2DStack {
  name: string
  color: string
  postCount: number
}

export interface Panorama2DAbilityStat {
  name: string
  /** 需要它的岗位名（跨岗位复用是这套图的核心信息） */
  posts: string[]
  /** 覆盖岗位数，越大越接近「枢纽能力」 */
  hubs: number
  /** 平均要求等级，用于矩阵里判断深浅 */
  avgLevel: number
}

export interface Panorama2DModel {
  domain: string
  stacks: Panorama2DStack[]
  posts: Panorama2DPost[]
  /** 按覆盖岗位数降序；越靠前越是跨岗位枢纽能力 */
  abilities: Panorama2DAbilityStat[]
  abilityPosts: Record<string, string[]>
  postByName: Record<string, Panorama2DPost>
  stackColor: Record<string, string>
  empty: boolean
}

export interface Panorama2DViewport {
  width: number
  height: number
  cx: number
  cy: number
  r1: number
  r2: number
}

export interface Panorama2DRingSector {
  name: string
  color: string
  angle: number
  a0: number
  a1: number
  postCount: number
}

export interface Panorama2DRingNode {
  id: string
  label: string
  color: string
  angle: number
  x: number
  y: number
  stack: string
}

export interface Panorama2DRingsLayout {
  view: Panorama2DViewport
  sectors: Panorama2DRingSector[]
  stacks: Panorama2DRingNode[]
  posts: Panorama2DRingNode[]
  /** 环上疏密 → 小球半径与名称显隐（见 `computeRingDensity`） */
  density: Panorama2DRingDensity
}

/**
 * 环上节点的疏密程度，决定小球半径与名称显隐。
 *
 * 为什么要有它：岗位数可以到几十上百，而扇区数（技术栈族）通常十几个。
 * 每扇区岗位数一上去，沿弧排列的小球就挨在一起、名称互相压字，
 * 整个环糊成一团 —— 这时**缩小球体 + 隐去文字**才是对的，
 * 「看清每个岗位叫什么」交给 hover 提示，而不是硬把所有文字塞进环里。
 */
export interface Panorama2DRingDensity {
  /** 最拥挤扇区的岗位数（= 决定疏密的那个量） */
  busiestSector: number
  /** 每扇区平均岗位数（保留一位小数，供界面说明用） */
  postsPerSector: number
  /** 岗位小球半径，密时更小 */
  nodeRadius: number
  /** 是否显示岗位名称 */
  showPostLabels: boolean
  /** 是否显示技术栈名称 */
  showStackLabels: boolean
}

export interface Panorama2DMatrixLayout {
  rows: Panorama2DAbilityStat[]
  columns: Panorama2DPost[]
  /** 被截断的能力数 / 岗位数（0 表示没截断） */
  truncated: { abilities: number; posts: number }
}

export interface Panorama2DEgoNeighbor {
  name: string
  stack: string
  color: string
  /** 与中心岗位共同需要的能力名 */
  shared: string[]
}

export interface Panorama2DEgoLayout {
  focus: Panorama2DPost | null
  /** 环 1：中心岗位所需能力 */
  abilities: { name: string; level: number; weight: number; core: boolean; sharedWith: Panorama2DPost[] }[]
  /** 环 2：同样需要这些能力的其他岗位（转岗 / 能力复用路径） */
  neighbors: Panorama2DEgoNeighbor[]
}

/* ===================== 配色 ===================== */

/**
 * 技术栈族配色（本页唯一来源）。
 *
 * 项目的 `WORKBENCH_CHART` 只有一条蓝色梯度 + 中性灰，够工作台的强弱分档用，
 * 但技术栈是**无序类别**、且数量可达十几个，必须用可区分的色相。
 * 这里以项目主色 `--app-primary` 为起点，按色相拉开，保证同一族在任何视图里同色。
 */
export const PANORAMA2D_PALETTE = [
  '#1d4ed8',
  '#2563eb',
  '#4f7bd6',
  '#0f6e56',
  '#6d7cf0',
  '#b45309',
  '#0891b2',
  '#7c3aed',
  '#059669',
  '#db2777',
  '#ca8a04',
  '#0284c7',
] as const

export const PANORAMA2D_FALLBACK_STACK = '通用工程能力'

/* ===================== 归一化 ===================== */

const POST_TYPES = new Set(['post', 'POST'])
const STACK_TYPES = new Set(['techStack', 'TECH_STACK'])
const ABILITY_TYPES = new Set([
  'skillPoint',
  'SKILL_POINT',
  'postAbility',
  'postAbilityFact',
  'unnormalizedPostAbilityFact',
  'ability',
  'ABILITY',
  'ABILITY_FACT',
])

export function isPostNodeType(type?: string): boolean {
  return !!type && POST_TYPES.has(type)
}

export function isStackNodeType(type?: string): boolean {
  return !!type && STACK_TYPES.has(type)
}

export function isAbilityNodeType(type?: string): boolean {
  return !!type && ABILITY_TYPES.has(type)
}

/** 等级：缺失或非法一律回落 3（与 3D 视图同口径），并夹到 1-5。 */
export function normalize2DLevel(value?: number): number {
  if (!Number.isFinite(value)) return 3
  return Math.max(1, Math.min(5, Math.round(value as number)))
}

/** 权重：数据里既有 0-1 也有 0-100 两种口径，统一成百分比。 */
export function normalize2DWeight(value?: number): number {
  if (!Number.isFinite(value)) return 0
  const raw = value as number
  const percent = raw > 1 ? raw : raw * 100
  return Math.max(0, Math.min(100, Math.round(percent)))
}

function stackColorOf(index: number): string {
  return PANORAMA2D_PALETTE[index % PANORAMA2D_PALETTE.length]
}

/* ===================== 建图 ===================== */

/**
 * 把后端的标准图结构归一成 2D 展示模型。
 *
 * 需要兼容两种数据来源（页面在关系投影未生成时会从 overview 重建）：
 *  · 图接口：有 TECH_STACK 节点与 TECH_STACK_POST / POST_TECH_STACK 边；
 *  · overview 兜底：技术栈由能力节点的 category 推导，岗位→能力靠 REQUIRES 边。
 * 因此这里的策略是「先按边解析、边缺失再按节点自带字段兜底」，而不是假设只有一种形状。
 */
export function buildPanorama2DModel(
  data: PanoramaGraphData | null | undefined,
  options: { domain?: string } = {},
): Panorama2DModel {
  const domain = options.domain || '新一代信息技术'
  const nodes = data?.nodes || []
  const edges = data?.edges || []

  const postNodes = nodes.filter(node => isPostNodeType(node.type))
  const stackNodes = nodes.filter(node => isStackNodeType(node.type))
  const abilityNodes = nodes.filter(node => isAbilityNodeType(node.type))

  const postIds = new Set(postNodes.map(node => node.id))
  const abilityNodeById = new Map(abilityNodes.map(node => [node.id, node]))

  // ---- 岗位 → 技术栈 ----
  const stackByPostId = new Map<string, string>()
  for (const edge of edges) {
    if (edge.type === 'TECH_STACK_POST' && postIds.has(edge.target) && !postIds.has(edge.source)) {
      const stackNode = nodes.find(node => node.id === edge.source && isStackNodeType(node.type))
      if (stackNode) stackByPostId.set(edge.target, stackNode.label)
    } else if (edge.type === 'POST_TECH_STACK' && postIds.has(edge.source)) {
      const stackNode = nodes.find(node => node.id === edge.target && isStackNodeType(node.type))
      if (stackNode && !stackByPostId.has(edge.source)) stackByPostId.set(edge.source, stackNode.label)
    }
  }

  // ---- 岗位 → 能力（任意连接「岗位节点」与「能力节点」的边都接受）----
  const abilityIdsByPost = new Map<string, Set<string>>()
  for (const edge of edges) {
    const sourceIsPost = postIds.has(edge.source)
    const targetIsPost = postIds.has(edge.target)
    const sourceIsAbility = abilityNodeById.has(edge.source)
    const targetIsAbility = abilityNodeById.has(edge.target)
    if (sourceIsPost && targetIsAbility) {
      addToSet(abilityIdsByPost, edge.source, edge.target)
    } else if (targetIsPost && sourceIsAbility) {
      addToSet(abilityIdsByPost, edge.target, edge.source)
    }
  }
  // overview 兜底数据里能力节点自带 meta.postId，边可能不全，这里补一遍
  for (const node of abilityNodes) {
    const postId = node.meta?.postId
    if (postId === undefined || postId === null) continue
    const key = `POST:${postId}`
    if (postIds.has(key)) addToSet(abilityIdsByPost, key, node.id)
  }

  // ---- 岗位 ----
  const posts: Panorama2DPost[] = postNodes.map(node => {
    const abilityIds = [...(abilityIdsByPost.get(node.id) || [])]
    const abilities = dedupeAbilities(abilityIds
      .map(id => abilityNodeById.get(id))
      .filter((item): item is NonNullable<typeof item> => !!item)
      .map(item => ({
        name: item.label?.trim() || '(未命名能力)',
        level: normalize2DLevel(item.level),
        weight: normalize2DWeight(item.weight),
        core: item.meta?.isCore === true,
        category: item.category,
      })))

    return {
      id: node.id,
      name: node.label?.trim() || node.id,
      stack: '', // 下面统一回填，保证技术栈清单把所有岗位都算进去
      color: stackColorOf(0),
      abilities,
    }
  })

  // ---- 技术栈：图里的节点清单 ∪ 岗位实际引用到的类别 ∪ 能力自带类别 ----
  const stackNames = new Set<string>(stackNodes.map(node => node.label?.trim()).filter(Boolean) as string[])
  for (const post of posts) {
    const fromEdge = stackByPostId.get(post.id)
    if (fromEdge) {
      post.stack = fromEdge.trim()
    } else {
      const fromAbility = mostFrequent(post.abilities.map(item => item.category))
      post.stack = (fromAbility || '').trim()
    }
    if (post.stack) stackNames.add(post.stack)
  }
  if (stackNames.size === 0 && posts.length > 0) {
    posts.forEach(post => { post.stack = PANORAMA2D_FALLBACK_STACK })
    stackNames.add(PANORAMA2D_FALLBACK_STACK)
  }

  const stackList: Panorama2DStack[] = [...stackNames]
    .sort((a, b) => a.localeCompare(b, 'zh-CN'))
    .map((name, index) => ({
      name,
      color: stackColorOf(index),
      postCount: posts.filter(post => post.stack === name).length,
    }))
  const stackColor: Record<string, string> = {}
  stackList.forEach(stack => { stackColor[stack.name] = stack.color })
  posts.forEach(post => {
    post.color = stackColor[post.stack] || stackColorOf(0)
  })

  // ---- 能力统计：跨岗位共享是这套图的价值所在 ----
  const abilityPosts: Record<string, string[]> = {}
  const levelSum: Record<string, { total: number; count: number }> = {}
  for (const post of posts) {
    for (const ability of post.abilities) {
      const list = abilityPosts[ability.name] || (abilityPosts[ability.name] = [])
      if (!list.includes(post.name)) list.push(post.name)
      const stat = levelSum[ability.name] || (levelSum[ability.name] = { total: 0, count: 0 })
      stat.total += ability.level
      stat.count += 1
    }
  }
  const abilities: Panorama2DAbilityStat[] = Object.keys(abilityPosts)
    .map(name => {
      const stat = levelSum[name]
      return {
        name,
        posts: abilityPosts[name],
        hubs: abilityPosts[name].length,
        avgLevel: stat && stat.count ? stat.total / stat.count : 0,
      }
    })
    .sort((a, b) => b.hubs - a.hubs || a.name.localeCompare(b.name, 'zh-CN'))

  const postByName: Record<string, Panorama2DPost> = {}
  posts.forEach(post => { postByName[post.name] = post })

  return {
    domain,
    stacks: stackList,
    posts,
    abilities,
    abilityPosts,
    postByName,
    stackColor,
    empty: posts.length === 0,
  }
}

function addToSet(map: Map<string, Set<string>>, key: string, value: string): void {
  const set = map.get(key) || new Set<string>()
  set.add(value)
  map.set(key, set)
}

/** 取出现次数最多的类别；并列时按中文序取第一个，保证结果可复现。 */
function mostFrequent(values: (string | undefined)[]): string | undefined {
  const counter = new Map<string, number>()
  for (const value of values) {
    if (!value) continue
    counter.set(value, (counter.get(value) || 0) + 1)
  }
  if (!counter.size) return undefined
  return [...counter.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], 'zh-CN'))[0][0]
}

/**
 * 同一个岗位下重名能力合并：保留更高等级 / 更大权重。
 * 数据里同名能力可能同时来自「标准能力」和「未归一事实」，不合并会在矩阵里出现重复行。
 */
function dedupeAbilities(
  items: { name: string; level: number; weight: number; core: boolean; category?: string }[],
): Panorama2DAbility[] {
  const merged = new Map<string, Panorama2DAbility>()
  for (const item of items) {
    const existing = merged.get(item.name)
    if (!existing) {
      merged.set(item.name, {
        name: item.name,
        level: item.level,
        weight: item.weight,
        core: item.core,
        category: item.category,
      })
      continue
    }
    existing.level = Math.max(existing.level, item.level)
    existing.weight = Math.max(existing.weight, item.weight)
    existing.core = existing.core || item.core
    existing.category = existing.category || item.category
  }
  return [...merged.values()]
}

/* ===================== 几何：分层同心环 ===================== */

export const PANORAMA2D_VIEWBOX: Panorama2DViewport = {
  width: 880,
  height: 660,
  cx: 430,
  cy: 332,
  r1: 116,
  r2: 232,
}

export function polarPoint(cx: number, cy: number, radius: number, angle: number): { x: number; y: number } {
  return { x: cx + radius * Math.cos(angle), y: cy + radius * Math.sin(angle) }
}

/* ---------- 疏密自适应 ---------- */

/** 稀疏时的岗位小球半径。 */
export const PANORAMA2D_NODE_RADIUS_MAX = 6.5
/** 最拥挤时的岗位小球半径（再小就看不清了）。 */
export const PANORAMA2D_NODE_RADIUS_MIN = 3.4
/** 扇区内岗位数 ≤ 此值算「宽松」：满尺寸、名称全显示。 */
export const PANORAMA2D_DENSITY_COMFORT = 3
/** 扇区内岗位数 ≥ 此值算「极限」：最小尺寸、名称全隐藏。 */
export const PANORAMA2D_DENSITY_CROWDED = 9
/** 扇区内岗位数 > 此值就隐藏岗位名称（开始互相压字）。 */
export const PANORAMA2D_LABEL_LIMIT = 4
/** 技术栈数 > 此值就隐藏环 1 的技术栈名称（扇区弧长已放不下文字），靠图例与 hover 识别。 */
export const PANORAMA2D_STACK_LABEL_LIMIT = 10

function round1(value: number): number {
  return Math.round(value * 10) / 10
}

/**
 * 按「最拥挤扇区的岗位数」决定小球半径与名称显隐。
 *
 * 之所以看**最拥挤扇区**而不是平均值：一个技术栈塞了 20 个岗位、其余栈各 1 个时，
 * 平均值很小、看起来很宽松，但那个扇区其实已经糊成一团了。
 * 因此取 `max(最拥挤, 平均)` 作为「压力」——
 * 平均大说明整体挤，最挤大说明局部糊，两者都要触发收缩。
 */
export function computeRingDensity(postsPerStack: number[], stackCount: number): Panorama2DRingDensity {
  const sectors = Math.max(stackCount, 1)
  const counts = postsPerStack.map(count => Math.max(0, Number.isFinite(count) ? count : 0))
  const total = counts.reduce((sum, count) => sum + count, 0)
  const busiest = counts.reduce((max, count) => Math.max(max, count), 0)
  const average = total / sectors

  // 压力值：越接近 CROWDED 越挤
  const pressure = Math.max(busiest, average)
  const span = PANORAMA2D_DENSITY_CROWDED - PANORAMA2D_DENSITY_COMFORT
  const ratio = Math.max(0, Math.min(1, (pressure - PANORAMA2D_DENSITY_COMFORT) / span))
  const nodeRadius = round1(
    PANORAMA2D_NODE_RADIUS_MAX - (PANORAMA2D_NODE_RADIUS_MAX - PANORAMA2D_NODE_RADIUS_MIN) * ratio,
  )

  return {
    busiestSector: busiest,
    postsPerSector: round1(average),
    nodeRadius,
    // 球形还能靠颜色/位置读结构，文字一挤就彻底没法看 → 宁可先隐藏文字
    showPostLabels: busiest <= PANORAMA2D_LABEL_LIMIT,
    showStackLabels: stackCount <= PANORAMA2D_STACK_LABEL_LIMIT,
  }
}

/** 圆环扇形路径。a0/a1 为弧度，r1 > r0。 */
export function wedgePath(
  cx: number,
  cy: number,
  r0: number,
  r1: number,
  a0: number,
  a1: number,
): string {
  const p0 = polarPoint(cx, cy, r0, a0)
  const p1 = polarPoint(cx, cy, r1, a0)
  const p2 = polarPoint(cx, cy, r1, a1)
  const p3 = polarPoint(cx, cy, r0, a1)
  const large = Math.abs(a1 - a0) > Math.PI ? 1 : 0
  const sweep = a1 > a0 ? 1 : 0
  return [
    `M${p0.x} ${p0.y}`,
    `L${p1.x} ${p1.y}`,
    `A${r1} ${r1} 0 ${large} ${sweep} ${p2.x} ${p2.y}`,
    `L${p3.x} ${p3.y}`,
    `A${r0} ${r0} 0 ${large} ${sweep ? 0 : 1} ${p0.x} ${p0.y}`,
    'Z',
  ].join('')
}

/** 文字锚点：按角度决定左对齐 / 居中 / 右对齐，避免文字压到节点上。 */
export function labelAnchor(angle: number): 'start' | 'middle' | 'end' {
  const cosine = Math.cos(angle)
  if (cosine > 0.32) return 'start'
  if (cosine < -0.32) return 'end'
  return 'middle'
}

/**
 * 分层同心环布局：中心为领域根，环 1 按扇区放技术栈，环 2 把岗位铺在**所属技术栈的扇区**里。
 * 同族岗位因此永远聚在一起 —— 这是力导向做不到、而业务又必须读出来的结构。
 */
export function buildRingsLayout(
  model: Panorama2DModel,
  view: Panorama2DViewport = PANORAMA2D_VIEWBOX,
): Panorama2DRingsLayout {
  const { cx, cy, r1, r2 } = view
  const total = Math.max(model.stacks.length, 1)
  const sector = (Math.PI * 2) / total
  const origin = -Math.PI / 2
  // 疏密先算出来：小球半径与名称显隐都由它决定，环上排布本身不变。
  const density = computeRingDensity(model.stacks.map(stack => stack.postCount), model.stacks.length)

  const sectors: Panorama2DRingSector[] = model.stacks.map((stack, index) => {
    const angle = origin + sector * index
    return {
      name: stack.name,
      color: stack.color,
      angle,
      a0: angle - sector / 2,
      a1: angle + sector / 2,
      postCount: stack.postCount,
    }
  })

  const stacks: Panorama2DRingNode[] = sectors.map(item => {
    const point = polarPoint(cx, cy, r1, item.angle)
    return {
      id: `stack:${item.name}`,
      label: item.name,
      color: item.color,
      angle: item.angle,
      x: point.x,
      y: point.y,
      stack: item.name,
    }
  })

  const posts: Panorama2DRingNode[] = []
  model.stacks.forEach((stack, index) => {
    const mine = model.posts.filter(post => post.stack === stack.name)
    if (!mine.length) return
    const center = origin + sector * index
    // 只占扇区的 60%，留出族与族之间的视觉间隙
    const span = sector * 0.6
    mine.forEach((post, order) => {
      const angle = mine.length === 1
        ? center
        : center - span / 2 + (span * order) / (mine.length - 1)
      const point = polarPoint(cx, cy, r2, angle)
      posts.push({
        id: post.id,
        label: post.name,
        color: post.color,
        angle,
        x: point.x,
        y: point.y,
        stack: stack.name,
      })
    })
  })

  return { view, sectors, stacks, posts, density }
}

/* ===================== 几何：能力矩阵 ===================== */

/**
 * 矩阵默认展示上限。
 *
 * 行（能力）从 60 压到 30：60 行 × 18px 要竖着滚很久，而矩阵要回答的是
 * 「哪些能力是跨岗位枢纽」—— 按覆盖岗位数降序后，前 30 行已经覆盖结论，
 * 后面的长尾只增加滚动成本。截断数量仍照常返回、由界面说明，不静默丢数据。
 * 列（岗位）保持 40，但表头改为**横向书写**（见组件样式），横向滚动比竖向好读。
 */
export const PANORAMA2D_MATRIX_LIMITS = { abilities: 30, posts: 40 } as const

/**
 * 能力 × 岗位矩阵的取数。
 *
 * 行按「覆盖岗位数」降序 → 越靠上越是跨岗位枢纽能力，这是这张图要回答的核心问题。
 * 行列都给上限：`limit` 参数最大可到 360 个节点，全量铺成矩阵会让 DOM 直接卡死，
 * 截断数量单独返回，由界面给出提示而不是静默丢数据。
 */
export function buildMatrixLayout(
  model: Panorama2DModel,
  options: { maxAbilities?: number; maxPosts?: number } = {},
): Panorama2DMatrixLayout {
  const maxAbilities = Math.max(1, options.maxAbilities ?? PANORAMA2D_MATRIX_LIMITS.abilities)
  const maxPosts = Math.max(1, options.maxPosts ?? PANORAMA2D_MATRIX_LIMITS.posts)

  const columns = [...model.posts]
    .sort((a, b) => a.stack.localeCompare(b.stack, 'zh-CN') || a.name.localeCompare(b.name, 'zh-CN'))
    .slice(0, maxPosts)

  const columnNames = new Set(columns.map(post => post.name))
  const rows = model.abilities
    // 只保留在展示列里真正出现的能力，否则矩阵会有一堆整行空白
    .filter(ability => ability.posts.some(name => columnNames.has(name)))
    .slice(0, maxAbilities)

  return {
    rows,
    columns,
    truncated: {
      abilities: Math.max(0, model.abilities.length - rows.length),
      posts: Math.max(0, model.posts.length - columns.length),
    },
  }
}

export function findPostAbility(post: Panorama2DPost | undefined, abilityName: string): Panorama2DAbility | undefined {
  return post?.abilities.find(item => item.name === abilityName)
}

/* ===================== 几何：邻域星图 ===================== */

/**
 * 单个岗位的能力邻域。
 * 环 1 = 它要的能力；环 2 = **同样需要这些能力的其他岗位** —— 后者就是转岗/能力复用路径，
 * 也是「岗位全景」相对普通关系图多出来的那层信息。
 */
export function buildEgoNeighborhood(model: Panorama2DModel, postName: string): Panorama2DEgoLayout {
  const focus = model.postByName[postName] || null
  if (!focus) return { focus: null, abilities: [], neighbors: [] }

  const abilities = [...focus.abilities]
    .sort((a, b) => b.weight - a.weight || b.level - a.level || a.name.localeCompare(b.name, 'zh-CN'))
    .map(ability => ({
      ...ability,
      sharedWith: (model.abilityPosts[ability.name] || [])
        .filter(name => name !== focus.name)
        .map(name => model.postByName[name])
        .filter((item): item is Panorama2DPost => !!item),
    }))

  const neighborMap = new Map<string, { post: Panorama2DPost; shared: string[] }>()
  for (const ability of abilities) {
    for (const other of ability.sharedWith) {
      const entry = neighborMap.get(other.name) || { post: other, shared: [] }
      if (!entry.shared.includes(ability.name)) entry.shared.push(ability.name)
      neighborMap.set(other.name, entry)
    }
  }
  const neighbors: Panorama2DEgoNeighbor[] = [...neighborMap.values()]
    .sort((a, b) => b.shared.length - a.shared.length || a.post.name.localeCompare(b.post.name, 'zh-CN'))
    .map(entry => ({
      name: entry.post.name,
      stack: entry.post.stack,
      color: entry.post.color,
      shared: entry.shared,
    }))

  return { focus, abilities, neighbors }
}

/* ===================== 聚焦视图 · 全部岗位聚合态 ===================== */

/**
 * 未聚焦任何单个岗位时的聚合数据。
 *
 * 存在的理由（用户反馈）：筛选为「全部岗位」时，聚焦视图原先会自动落到
 * `posts[0]` —— 用户看到的是**某一个**岗位的邻域，与「我正在看全部岗位」
 * 的预期不符，体感就是「随机选了一个岗位」。
 * 正确语义是：全部岗位时展示**全量聚合**（枢纽能力 + 技术栈族），
 * 由用户点选某个岗位后才切换到单岗位邻域。
 */
export interface Panorama2DEgoOverviewAbility {
  name: string
  hubs: number
  avgLevel: number
}

export interface Panorama2DEgoOverviewStack {
  name: string
  color: string
  postCount: number
  posts: string[]
}

export interface Panorama2DEgoOverview {
  /** 恒为 true —— 让调用方能靠一个字面量把两种态分开 */
  aggregate: true
  domain: string
  postCount: number
  stackCount: number
  hubAbilities: Panorama2DEgoOverviewAbility[]
  stacks: Panorama2DEgoOverviewStack[]
  truncatedAbilities: number
}

export const PANORAMA2D_OVERVIEW_LIMITS = { abilities: 12 } as const

export function buildEgoOverview(
  model: Panorama2DModel,
  options: { maxAbilities?: number } = {},
): Panorama2DEgoOverview {
  const maxAbilities = Math.max(1, options.maxAbilities ?? PANORAMA2D_OVERVIEW_LIMITS.abilities)
  // model.abilities 已按覆盖岗位数降序 → 取前 N 就是「最枢纽的 N 项能力」
  const hubAbilities = model.abilities.slice(0, maxAbilities).map(item => ({
    name: item.name,
    hubs: item.hubs,
    avgLevel: round1(item.avgLevel),
  }))

  return {
    aggregate: true,
    domain: model.domain,
    postCount: model.posts.length,
    stackCount: model.stacks.length,
    hubAbilities,
    stacks: model.stacks.map(stack => ({
      name: stack.name,
      color: stack.color,
      postCount: stack.postCount,
      posts: model.posts.filter(post => post.stack === stack.name).map(post => post.name),
    })),
    truncatedAbilities: Math.max(0, model.abilities.length - hubAbilities.length),
  }
}

/* ===================== 视图定义 ===================== */

export type Panorama2DViewMode = 'rings' | 'matrix' | 'ego'

export const PANORAMA2D_VIEWS: { value: Panorama2DViewMode; label: string; title: string; subtitle: string }[] = [
  {
    value: 'rings',
    label: '全景',
    title: '技术栈 → 岗位 的分层同心环',
    subtitle: '每个扇区是一个技术栈族，扇形内的岗位同族。层级固定，打开即知结构。',
  },
  {
    value: 'matrix',
    label: '关联',
    title: '能力 × 岗位 矩阵',
    subtitle: '行按「被几个岗位需要」排序 —— 越靠上越是跨岗位的枢纽能力；格内为要求等级。',
  },
  {
    value: 'ego',
    label: '聚焦',
    title: '岗位能力邻域（未选岗位时为全部岗位聚合）',
    subtitle: '全部岗位时：内环是跨岗位枢纽能力，外环是技术栈族；点选某个岗位后切换为它的能力邻域，外环变为同样需要这些能力的岗位（转岗 / 复用路径）。',
  },
]
