import assert from 'node:assert/strict'
import {
  PANORAMA2D_FALLBACK_STACK,
  PANORAMA2D_NODE_RADIUS_MAX,
  PANORAMA2D_NODE_RADIUS_MIN,
  PANORAMA2D_STACK_LABEL_LIMIT,
  PANORAMA2D_VIEWBOX,
  buildEgoNeighborhood,
  buildEgoOverview,
  buildMatrixLayout,
  buildPanorama2DModel,
  buildRingsLayout,
  computeRingDensity,
  findPostAbility,
  labelAnchor,
  normalize2DLevel,
  normalize2DWeight,
  polarPoint,
  wedgePath,
} from './postPanorama2d.ts'

/**
 * 2D 全景图谱的纯逻辑契约。
 *
 * 这套视图取代了 3D 力导向作为主展示，所以「同族岗位必然同扇区」「枢纽能力排在最上」
 * 这类结构性质必须被断言锁住 —— 它们正是 3D 版读不出来的东西。任何一条失效，
 * 都意味着用户又回到了「看不懂这张图」的状态。
 */

const graph = {
  available: true,
  nodes: [
    { id: 'TECH_STACK:人工智能', type: 'TECH_STACK', label: '人工智能', category: 'TECH_STACK' },
    { id: 'TECH_STACK:大数据', type: 'TECH_STACK', label: '大数据', category: 'TECH_STACK' },
    { id: 'POST:1', type: 'POST', label: '算法工程师', category: 'P5' },
    { id: 'POST:2', type: 'POST', label: '计算机视觉工程师', category: 'P5' },
    { id: 'POST:3', type: 'POST', label: '数据开发工程师', category: 'P4' },
    { id: 'SKILL_POINT:11', type: 'skillPoint', label: '机器学习', level: 5, weight: 30, meta: { postId: 1 } },
    { id: 'SKILL_POINT:12', type: 'skillPoint', label: '深度学习', level: 5, weight: 25, meta: { postId: 1, isCore: true } },
    { id: 'SKILL_POINT:21', type: 'skillPoint', label: '机器学习', level: 4, weight: 25, meta: { postId: 2 } },
    { id: 'SKILL_POINT:31', type: 'skillPoint', label: 'SQL开发', level: 5, weight: 30, meta: { postId: 3 } },
  ],
  edges: [
    { id: 'e1', source: 'TECH_STACK:人工智能', target: 'POST:1', type: 'TECH_STACK_POST' },
    { id: 'e2', source: 'TECH_STACK:人工智能', target: 'POST:2', type: 'TECH_STACK_POST' },
    { id: 'e3', source: 'TECH_STACK:大数据', target: 'POST:3', type: 'TECH_STACK_POST' },
    { id: 'a1', source: 'POST:1', target: 'SKILL_POINT:11', type: 'REQUIRES' },
    { id: 'a2', source: 'POST:1', target: 'SKILL_POINT:12', type: 'CORE_REQUIRES' },
    { id: 'a3', source: 'POST:2', target: 'SKILL_POINT:21', type: 'REQUIRES' },
    { id: 'a4', source: 'POST:3', target: 'SKILL_POINT:31', type: 'REQUIRES' },
  ],
  stats: { nodeCount: 9, edgeCount: 7, postCount: 3, abilityCount: 4, skillPointCount: 4 },
}

/* ---------- 归一化 ---------- */

assert.equal(normalize2DLevel(undefined), 3, '等级缺失应回落 3（与 3D 视图同口径）')
assert.equal(normalize2DLevel(9), 5, '等级应夹到 5')
assert.equal(normalize2DLevel(0), 1, '等级应夹到 1')
assert.equal(normalize2DWeight(0.3), 30, '0-1 口径应换算成百分比')
assert.equal(normalize2DWeight(30), 30, '0-100 口径应原样保留')
assert.equal(normalize2DWeight(150), 100, '超界权重应夹到 100')
assert.equal(normalize2DWeight(undefined), 0, '权重缺失应为 0')

/* ---------- 建图 ---------- */

const model = buildPanorama2DModel(graph)

assert.equal(model.stacks.length, 2, '技术栈应来自 TECH_STACK 节点')
assert.deepEqual(model.stacks.map(s => s.name), ['大数据', '人工智能'], '技术栈按中文序排列，保证配色稳定')
assert.deepEqual(model.stacks.map(s => s.postCount), [1, 2], '技术栈应统计各自岗位数')
assert.equal(model.posts.length, 3)
assert.equal(model.empty, false)

const algo = model.postByName['算法工程师']
const vision = model.postByName['计算机视觉工程师']
const dataDev = model.postByName['数据开发工程师']

assert.equal(algo.stack, '人工智能', '岗位应从 TECH_STACK_POST 边解析出所属技术栈')
assert.equal(dataDev.stack, '大数据')
assert.equal(algo.color, vision.color, '同族岗位必须同色')
assert.notEqual(algo.color, dataDev.color, '不同族必须不同色')

assert.deepEqual(algo.abilities.map(a => a.name).sort(), ['机器学习', '深度学习'].sort())
assert.equal(algo.abilities.find(a => a.name === '深度学习').core, true, 'meta.isCore 应映射成核心能力')
assert.equal(findPostAbility(algo, '机器学习').level, 5)
assert.equal(findPostAbility(algo, '机器学习').weight, 30, '权重应归一成百分比')

/* 跨岗位共享：这是这套图的核心信息 */
const sharedAbility = model.abilities.find(a => a.name === '机器学习')
assert.equal(sharedAbility.hubs, 2, '「机器学习」应统计为被 2 个岗位需要')
assert.deepEqual([...sharedAbility.posts].sort(), ['算法工程师', '计算机视觉工程师'].sort())
assert.equal(model.abilities.find(a => a.name === 'SQL开发').hubs, 1)
assert.equal(model.abilities[0].name, '机器学习', '能力默认按覆盖岗位数降序 —— 枢纽能力排在最上')

/* 技术栈缺失时的兜底：从能力类别推导 */
const fallback = buildPanorama2DModel({
  available: true,
  nodes: [
    { id: 'POST:1', type: 'POST', label: '算法工程师' },
    { id: 'SKILL_POINT:11', type: 'skillPoint', label: '机器学习', category: '人工智能', level: 5, weight: 30, meta: { postId: 1 } },
    { id: 'SKILL_POINT:12', type: 'skillPoint', label: '深度学习', category: '人工智能', level: 4, weight: 20, meta: { postId: 1 } },
  ],
  edges: [],
  stats: { postCount: 1 },
})
assert.equal(fallback.posts[0].stack, '人工智能', '无技术栈节点时应从能力类别兜底')
assert.deepEqual(fallback.stacks.map(s => s.name), ['人工智能'])

/* 完全没有类别信息时的终极兜底 */
const noCategory = buildPanorama2DModel({
  available: true,
  nodes: [{ id: 'POST:1', type: 'POST', label: '某岗位' }],
  edges: [],
  stats: { postCount: 1 },
})
assert.equal(noCategory.posts[0].stack, PANORAMA2D_FALLBACK_STACK)

/* 重名能力合并：同名同时来自标准能力与未归一事实时，不能出现两行 */
const duplicated = buildPanorama2DModel({
  available: true,
  nodes: [
    { id: 'POST:1', type: 'POST', label: '算法工程师' },
    { id: 'SKILL_POINT:11', type: 'skillPoint', label: '机器学习', level: 3, weight: 10, meta: { postId: 1 } },
    { id: 'SKILL_POINT:12', type: 'postAbilityFact', label: '机器学习', level: 5, weight: 30, meta: { postId: 1 } },
  ],
  edges: [],
  stats: { postCount: 1 },
})
assert.equal(duplicated.posts[0].abilities.length, 1, '同岗位重名能力应合并成一项')
assert.equal(duplicated.posts[0].abilities[0].level, 5, '合并应保留更高等级')
assert.equal(duplicated.posts[0].abilities[0].weight, 30, '合并应保留更大权重')

/* 空数据 */
const emptyModel = buildPanorama2DModel(null)
assert.equal(emptyModel.empty, true)
assert.deepEqual(emptyModel.posts, [])
assert.deepEqual(emptyModel.stacks, [])
assert.deepEqual(buildRingsLayout(emptyModel).posts, [], '空模型不应抛错')

/* ---------- 同心环几何 ---------- */

const rings = buildRingsLayout(model)
assert.equal(rings.sectors.length, 2, '扇区数等于技术栈数')
assert.equal(rings.stacks.length, 2)
assert.equal(rings.posts.length, 3)

const centerDistance = node => Math.hypot(node.x - PANORAMA2D_VIEWBOX.cx, node.y - PANORAMA2D_VIEWBOX.cy)
rings.stacks.forEach(node => {
  assert.ok(Math.abs(centerDistance(node) - PANORAMA2D_VIEWBOX.r1) < 1e-6, '技术栈必须落在内环上')
})
rings.posts.forEach(node => {
  assert.ok(Math.abs(centerDistance(node) - PANORAMA2D_VIEWBOX.r2) < 1e-6, '岗位必须落在外环上')
})

/* 同族同扇区：3D 力导向做不到、而业务必须读出来的一点 */
const sector = (Math.PI * 2) / rings.sectors.length
rings.sectors.forEach(item => {
  const mine = rings.posts.filter(post => post.stack === item.name)
  assert.ok(mine.length > 0, `技术栈「${item.name}」应有岗位`)
  mine.forEach(post => {
    const delta = Math.abs(post.angle - item.angle)
    assert.ok(delta <= sector * 0.3 + 1e-9, `岗位「${post.label}」必须落在所属技术栈的扇区内`)
  })
})
const aiPosts = rings.posts.filter(post => post.stack === '人工智能')
assert.equal(aiPosts.length, 2)
assert.ok(Math.abs(aiPosts[0].angle - aiPosts[1].angle) > 1e-6, '同族多个岗位不应重叠在同一角度')

/* 极坐标与扇形路径 */
const point = polarPoint(0, 0, 10, 0)
assert.ok(Math.abs(point.x - 10) < 1e-9 && Math.abs(point.y) < 1e-9)
const path = wedgePath(0, 0, 10, 20, 0, Math.PI / 2)
assert.match(path, /^M/, '扇形路径应以 M 开头')
assert.match(path, /A20 20 0 0 1/, '外弧应为顺指针方向')
assert.match(path, /Z$/, '扇形路径应闭合')

/* 文字锚点：左右侧贴边不能被裁掉 */
assert.equal(labelAnchor(0), 'start')
assert.equal(labelAnchor(Math.PI), 'end')
assert.equal(labelAnchor(-Math.PI / 2), 'middle')

/* ---------- 能力矩阵 ---------- */

const matrix = buildMatrixLayout(model)
assert.equal(matrix.columns.length, 3)
assert.ok(matrix.rows.every(row => row.hubs >= 1))
for (let i = 1; i < matrix.rows.length; i += 1) {
  assert.ok(matrix.rows[i - 1].hubs >= matrix.rows[i].hubs, '矩阵行必须按覆盖岗位数降序')
}
assert.deepEqual(matrix.truncated, { abilities: 0, posts: 0 })

const tiny = buildMatrixLayout(model, { maxAbilities: 1, maxPosts: 1 })
assert.equal(tiny.rows.length, 1)
assert.equal(tiny.columns.length, 1)
assert.ok(tiny.truncated.abilities > 0, '截断必须被显式报告，不能静默丢数据')
assert.ok(tiny.truncated.posts > 0)

/* 截断后不能留下整行空白的能力 */
const wide = buildMatrixLayout(model, { maxPosts: 1 })
assert.ok(wide.rows.length > 0)
wide.rows.forEach(row => {
  const columnNames = wide.columns.map(post => post.name)
  assert.ok(row.posts.some(name => columnNames.includes(name)), `能力「${row.name}」在展示列里应有落点`)
})

/* ---------- 邻域星图 ---------- */

const ego = buildEgoNeighborhood(model, '算法工程师')
assert.equal(ego.focus.name, '算法工程师')
assert.deepEqual(ego.abilities.map(a => a.name), ['机器学习', '深度学习'], '内环能力按权重降序')
assert.deepEqual(ego.abilities[0].sharedWith.map(post => post.name), ['计算机视觉工程师'], '共享关系应排除自己')
assert.deepEqual(ego.neighbors.map(n => n.name), ['计算机视觉工程师'], '外环只放共享能力的其他岗位')
assert.deepEqual(ego.neighbors[0].shared, ['机器学习'])

const isolated = buildEgoNeighborhood(model, '数据开发工程师')
assert.deepEqual(isolated.neighbors, [], '没有共享能力时外环应为空，而不是塞进无关岗位')

assert.equal(buildEgoNeighborhood(model, '不存在的岗位').focus, null)

/* ---------- 疏密自适应：小球半径与名称显隐 ---------- */

// 宽松（最挤扇区 2 个）：满尺寸 + 名称全显示
const sparse = computeRingDensity([2, 1], 2)
assert.equal(sparse.busiestSector, 2)
assert.equal(sparse.showPostLabels, true)
assert.equal(sparse.nodeRadius, PANORAMA2D_NODE_RADIUS_MAX)

// 极限（某扇区 12 个）：缩到最小 + 隐藏名称
const crowded = computeRingDensity([12, 1], 2)
assert.equal(crowded.busiestSector, 12)
assert.equal(crowded.showPostLabels, false)
assert.equal(crowded.nodeRadius, PANORAMA2D_NODE_RADIUS_MIN)

// 中间态必须落在两端之间（是真插值，不是「两档硬切」）
const mid = computeRingDensity([6, 6], 2)
assert.ok(
  mid.nodeRadius < PANORAMA2D_NODE_RADIUS_MAX && mid.nodeRadius > PANORAMA2D_NODE_RADIUS_MIN,
  '中间密度必须给出中间半径',
)
assert.equal(mid.showPostLabels, false, '每扇区 6 个已经开始压字，应隐藏名称')

// 关键口径：看**最拥挤扇区**，不是平均值。
// [11,1,1,1] 平均只有 3.5、看起来宽松，但那个 11 的扇区已经糊成一团了。
const skewed = computeRingDensity([11, 1, 1, 1], 4)
assert.equal(skewed.busiestSector, 11)
assert.equal(skewed.showPostLabels, false, '平均值小也不能掩盖单扇区拥挤')

// 技术栈过多时隐藏环 1 的名称（扇区弧长已放不下文字）
assert.equal(computeRingDensity([1], PANORAMA2D_STACK_LABEL_LIMIT).showStackLabels, true)
assert.equal(computeRingDensity([1], PANORAMA2D_STACK_LABEL_LIMIT + 1).showStackLabels, false)

// 空数据：不抛错，并给「最宽松」而不是 NaN
const none = computeRingDensity([], 0)
assert.equal(none.nodeRadius, PANORAMA2D_NODE_RADIUS_MAX)
assert.ok(Number.isFinite(none.postsPerSector))

// 脏数据（NaN / 负数）：按 0 处理，不能污染半径
const dirty = computeRingDensity([Number.NaN, -3, 2], 3)
assert.ok(Number.isFinite(dirty.nodeRadius), '脏数据不得让半径变成 NaN')
assert.ok(dirty.nodeRadius <= PANORAMA2D_NODE_RADIUS_MAX)

// 布局必须把密度带出来，否则界面拿不到「该不该显示名称」
assert.equal(rings.density.busiestSector, 2)
assert.equal(rings.density.showPostLabels, true)

/* ---------- 全部岗位聚合态（聚焦视图的「全部岗位」） ---------- */

const ov = buildEgoOverview(model)
assert.equal(ov.aggregate, true, '聚合态必须可被字面量识别')
assert.equal(ov.postCount, 3)
assert.equal(ov.stackCount, 2)
assert.ok(ov.hubAbilities.length > 0)
for (let i = 1; i < ov.hubAbilities.length; i += 1) {
  assert.ok(ov.hubAbilities[i - 1].hubs >= ov.hubAbilities[i].hubs, '聚合态枢纽能力必须按覆盖岗位数降序')
}
const aiStack = ov.stacks.find(stack => stack.name === '人工智能')
assert.ok(aiStack, '聚合态要带出技术栈族')
assert.ok(aiStack.posts.length > 0, '技术栈族要带上族内岗位名，供面板直接列出')

// 截断必须显式报告，不能静默少给
const ovTiny = buildEgoOverview(model, { maxAbilities: 1 })
assert.equal(ovTiny.hubAbilities.length, 1)
assert.ok(ovTiny.truncatedAbilities > 0)

// 空模型不抛错
const ovEmpty = buildEgoOverview(emptyModel)
assert.equal(ovEmpty.postCount, 0)
assert.deepEqual(ovEmpty.hubAbilities, [])
assert.deepEqual(ovEmpty.stacks, [])

console.log('postPanorama2d tests passed')
