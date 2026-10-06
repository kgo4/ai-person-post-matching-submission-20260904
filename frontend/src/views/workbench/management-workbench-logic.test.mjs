/**
 * 管理端角色工作台纯逻辑单测（node 直跑）。
 *
 * 覆盖 4 个角色的装配 + 公共工具函数。
 * 重点断言「取不到数据时不得伪造 0」这一约定（ratioPercent / progressOf）。
 */
import assert from 'node:assert/strict'
import {
  buildAbilityHeat,
  buildHrWorkbench,
  buildJobArchitectWorkbench,
  buildPlatformAdminWorkbench,
  buildPostAbilityScale,
  countRunningTasks,
  logsToNotices,
  progressOf,
  ratioPercent,
  recordTrend,
  recordsOf,
  tasksToNotices,
  totalOf,
} from './management-workbench-logic.ts'

/* ============================ 公共工具 ============================ */

assert.equal(totalOf({ total: 12 }), 12)
assert.equal(totalOf({ total: null }), null)
assert.equal(totalOf(null), null)
assert.equal(totalOf('nope'), null)

assert.deepEqual(recordsOf({ records: [1, 2] }), [1, 2])
assert.deepEqual(recordsOf({ records: null }), [])
assert.deepEqual(recordsOf(null), [])

// 分母为 0 / 缺失 → null（表示"暂无口径"，不是 0%）
assert.equal(ratioPercent(5, 0), null)
assert.equal(ratioPercent(5, null), null)
assert.equal(ratioPercent(null, null), null)
assert.equal(ratioPercent(0, 10), 0, '0/10 是合法口径，应为 0%')
assert.equal(ratioPercent(1, 3), 33)
assert.equal(ratioPercent(2, 3), 67)

assert.equal(countRunningTasks([{ status: 1 }, { status: 2 }, { status: 1 }]), 2)
assert.equal(countRunningTasks([]), 0)

// 趋势：时间倒序入参，输出按时间正序
const trend = recordTrend([
  { createdTime: '2026-09-03', aiMatchScore: 90, finalMatchScore: 88 },
  { createdTime: '2026-09-02', aiMatchScore: 80 },
  { createdTime: '2026-09-01', aiMatchScore: 70 },
])
assert.deepEqual(trend.map(p => p.label), ['09-01', '09-02', '09-03'])
assert.equal(trend[1].secondary, 80, '缺 finalMatchScore 时回落 aiMatchScore，而不是 0')

// 趋势限长：只保留最近 N 条
const many = Array.from({ length: 20 }, (_, i) => ({ createdTime: `2026-09-${String(i + 1).padStart(2, '0')}`, aiMatchScore: i }))
assert.equal(recordTrend(many).length, 8)

// 任务通知：状态映射
const notices = tasksToNotices([
  { id: 1, status: 3, refName: 'A', createdTime: '2026-09-04 10:00:00' },
  { id: 2, status: 2 },
  { id: 3, status: 1 },
])
assert.equal(notices[0].kind, 'warning')
assert.equal(notices[1].kind, 'success')
assert.equal(notices[2].kind, 'info')
assert.equal(notices[1].title, '2', '无名称时回落 id')

assert.deepEqual(logsToNotices([]), [])
assert.equal(logsToNotices([{ realName: '张三', operationDesc: '修改权限' }])[0].desc, '张三 执行了该操作')

// progressOf：percent 为 null 时 value 取 0，但 status 必须说明"暂无口径"
const p = progressOf('完成率', null, '完成 0 / 共 0', '/x', 'matching')
assert.equal(p.value, 0)
assert.ok(p.status.includes('暂无口径'))
assert.equal(progressOf('完成率', 50, '一半', '/x', 'matching').status, '一半')

/* ============================== HR ============================== */

const hr = buildHrWorkbench({
  employeeTotal: 100,
  enabledTotal: 80,
  postTotal: 30,
  matching: { total: 50, status1: 3, status2: 20, pendingPublish: 6, recent: [{ aiMatchScore: 88, finalMatchScore: 90 }] },
  tasks: [{ status: 1 }, { status: 2 }],
  outcomeReview: { pending: 4, total: 10 },
})

assert.equal(hr.stats.length, 5, '指标卡固定 5 张，与参考图一致')
assert.deepEqual(hr.stats.map(s => s.key), ['employees', 'posts', 'matching', 'running', 'outcomes'])
assert.equal(hr.stats[0].value, '100')
// 【2026-09-04】HR 工作台不再展示「学习计划」（那是员工自己的进度），
// 改为「待复核学习成果」——才是真正落在这个角色头上的待办。
assert.equal(hr.stats[4].label, '待复核学习成果')
assert.equal(hr.stats[4].value, '4')
assert.equal(hr.stats[4].path, '/learning/outcome-review')
assert.equal(hr.trend.points.length, 1)
assert.equal(hr.progresses[0].value, 40, '20/50 = 40%')
assert.equal(hr.progresses[1].value, 80, '80/100 = 80%')
assert.equal(hr.progresses[2].label, '学习成果复核处理率')
assert.equal(hr.progresses[2].value, 60, '(10-4)/10 = 60%')
assert.equal(hr.notices.length, 2)
assert.ok(hr.primaryActionPath.startsWith('/matching'))

// 【2026-09-04】待办 = 有真实未处理状态的事项；「发起人岗匹配 / 维护人员档案」
// 这类常驻入口已从待办移除（改由 actions 承载）。
assert.deepEqual(
  hr.todos.map(todo => todo.title),
  ['确认并发布匹配结果', '有匹配任务正在执行', '复核员工学习成果'],
  '待发布结果、执行中的任务、待复核学习成果都是真实待办',
)
assert.equal(hr.todos[0].urgent, true, '有待推送结果时标记为紧急')
assert.equal(hr.todos[2].path, '/learning/outcome-review', '学习成果待办必须直达复核页')

// 空数据：不得伪造 0，展示 '--' 且进度标注"暂无口径"
const hrEmpty = buildHrWorkbench({
  employeeTotal: null,
  enabledTotal: null,
  postTotal: null,
  matching: null,
  tasks: [],
  outcomeReview: null,
})
assert.equal(hrEmpty.stats[0].value, '--')
assert.equal(hrEmpty.stats[2].value, '--')
assert.equal(hrEmpty.stats[4].value, '--', '复核队列取不到时显示 -- 而不是 0')
assert.ok(hrEmpty.progresses[0].status.includes('暂无口径'))
assert.deepEqual(hrEmpty.trend.points, [])
assert.deepEqual(hrEmpty.notices, [])
// 确实没事可做时保持为空 —— 拿常驻入口凑数会让待办面板看起来是假的
assert.deepEqual(hrEmpty.todos, [], '没有真实待办时不得用常驻入口填充')

// 只有执行中的任务、没有待推送结果时，只出一条待办
const hrRunningOnly = buildHrWorkbench({
  employeeTotal: 10,
  enabledTotal: 10,
  postTotal: 3,
  matching: { total: 5, status1: 1, status2: 4, pendingPublish: 0, recent: [] },
  tasks: [{ status: 1 }],
  outcomeReview: { pending: 0, total: 0 },
})
assert.deepEqual(hrRunningOnly.todos.map(todo => todo.title), ['有匹配任务正在执行'], '待复核为 0 时不产生学习成果待办')

/* ========================= 岗位体系管理员 ========================= */

// 能力热度聚合：按引用次数降序，空名跳过，同热度按名称稳定排序
assert.deepEqual(
  buildAbilityHeat([
    { tagName: 'Python' }, { tagName: 'Java' }, { tagName: 'Python' },
    { tagName: '  ' }, { tagName: null }, { tagName: 'Java' }, { tagName: 'Python' },
  ]),
  [{ name: 'Python', value: 3 }, { name: 'Java', value: 2 }],
  '热度 = 被多少个岗位能力项引用，空名能力不得上榜',
)
assert.deepEqual(buildAbilityHeat([{ tagName: 'A' }, { tagName: 'B' }]), [{ name: 'A', value: 1 }, { name: 'B', value: 1 }])
assert.deepEqual(buildAbilityHeat(null), [], '无数据时返回空数组而不是抛错')
assert.equal(buildAbilityHeat(Array.from({ length: 40 }, (_, i) => ({ tagName: `A${i}` }))).length, 30, '词云最多 30 个词')

/* ==================== 岗位能力规模（按岗位聚合，非时间序列） ==================== */
// 「岗位能力规模」原先 points 恒为空 → 岗位体系管理员的工作台永远只有一张空图。
// 改为按岗位横向聚合：primary = 能力项数，secondary = 其中的核心能力数。
assert.deepEqual(
  buildPostAbilityScale(
    [{ id: 1, postName: '后端工程师' }, { id: 2, postName: '前端工程师' }],
    [
      { postId: 1, isCore: true }, { postId: 1 }, { postId: 1 },
      { postId: 2, isCore: true },
    ],
  ),
  [
    { label: '后端工程师', primary: 3, secondary: 1 },
    { label: '前端工程师', primary: 1, secondary: 1 },
  ],
  '按能力项数降序，核心能力单独成一条序列',
)

// 岗位名取不到时回落到「岗位 {id}」，但点必须保留（不能因为缺名字就把数据丢掉）
assert.deepEqual(buildPostAbilityScale(null, [{ postId: 7 }]), [{ label: '岗位 7', primary: 1, secondary: 0 }])

// 无 postId 的能力项无法归属任何岗位 → 不参与聚合，返回空数组交给视图层空态
assert.deepEqual(buildPostAbilityScale([{ id: 1, postName: 'A' }], [{ tagName: 'Python' }, { tagName: 'Java' }]), [])
assert.deepEqual(
  buildPostAbilityScale([{ id: 1, postName: 'A' }], [{ tagName: 'Python', postId: null }]),
  [],
  'postId 为 null 时不得被 Number(null)=0 归到「岗位 0」上',
)
assert.deepEqual(buildPostAbilityScale(null, null), [], '无数据时返回空数组而不是抛错')

// 只取规模最大的前 N 个岗位
assert.equal(
  buildPostAbilityScale(
    Array.from({ length: 10 }, (_, i) => ({ id: i + 1, postName: `P${i + 1}` })),
    Array.from({ length: 10 }, (_, i) => Array.from({ length: i + 1 }, () => ({ postId: i + 1 }))).flat(),
  ).length,
  6,
  '最多 6 个岗位',
)

// 同规模时按名称稳定排序：同一份数据每次渲染顺序必须一致
assert.deepEqual(
  buildPostAbilityScale([], [{ postId: 2 }, { postId: 1 }]).map(point => point.label),
  ['岗位 1', '岗位 2'],
)

const ja = buildJobArchitectWorkbench({
  postTotal: 12,
  panorama: { stats: { abilityCount: 200, skillPointCount: 340 }, abilities: [{ tagName: 'Python' }, { tagName: 'Python' }, { tagName: 'Java' }] },
  inspection: { abilityCount: 210, riskyCount: 21, aiSourceCount: 42 },
})
assert.deepEqual(ja.stats.map(s => s.key), ['posts', 'abilities', 'skills', 'risky', 'aiSource'])
// inspection 优先于 panorama
assert.equal(ja.stats[1].value, '210')
assert.equal(ja.stats[2].value, '340')
assert.equal(ja.progresses[0].value, 10, '21/210 = 10%')
assert.deepEqual(ja.notices, [], '岗位端无系统通知来源')
// 待办只保留「有待确认的 AI 来源能力」与「有高风险能力」两条真实项
assert.deepEqual(ja.todos.map(todo => todo.title), ['审核 AI 抽取的岗位能力', '处理高风险能力'])
assert.equal(ja.todos[1].urgent, true, '存在高风险能力时标记为紧急')
// 【2026-09-04】岗位工作台顶部不再放主行动按钮（原「+ 导入 JD」）。
// 置空 label 是唯一开关：RoleWorkbench 用 v-if="primaryActionLabel" 决定是否渲染。
assert.equal(ja.primaryActionLabel, '', '岗位工作台顶部不得出现主行动按钮')
assert.equal(ja.primaryActionPath, '/ai-governance/records', 'path 仍须保留给「业务进度」的查看全部')
// 词云由岗位能力明细聚合，不依赖标签库统计快照
assert.deepEqual(ja.abilityCloud, [{ name: 'Python', value: 2 }, { name: 'Java', value: 1 }])
// 该用例的 abilities 没有 postId → 无法归属岗位，趋势落到空态（而不是造假点）
assert.deepEqual(ja.trend.points, [])

// 有岗位归属时，「岗位能力规模」必须真的出数据（回归：原先恒为空图）
const jaScale = buildJobArchitectWorkbench({
  postTotal: 12,
  panorama: {
    stats: { abilityCount: 200, skillPointCount: 340 },
    posts: [{ id: 1, postName: '后端工程师' }, { id: 2, postName: '前端工程师' }],
    abilities: [
      { tagName: 'Java', postId: 1, isCore: true },
      { tagName: 'MySQL', postId: 1 },
      { tagName: 'Vue', postId: 2, isCore: true },
    ],
  },
  inspection: null,
})
assert.equal(jaScale.trend.title, '岗位能力规模')
assert.deepEqual(jaScale.trend.points, [
  { label: '后端工程师', primary: 2, secondary: 1 },
  { label: '前端工程师', primary: 1, secondary: 1 },
])
assert.equal(jaScale.trend.primaryName, '能力项')
assert.equal(jaScale.trend.secondaryName, '核心能力')

/* ========================== 岗位趋势待审待办 ========================== */

// 有 PENDING 候选 → 占待办位，且拆出新岗位/能力变更两类数量，管理端一眼知道要审什么
const jaTrend = buildJobArchitectWorkbench({
  postTotal: 12,
  panorama: null,
  inspection: null,
  trendPending: { pendingCandidateCount: 8, pendingNewPostCount: 3, pendingChangeCount: 5, awaitingTaskCount: 2 },
})
const trendTodo = jaTrend.todos.find(todo => todo.title === '审核岗位趋势候选')
assert.ok(trendTodo, '有 PENDING 候选时必须出现趋势待办')
assert.equal(trendTodo.desc, '8 个候选待确认（新岗位 3 · 能力变更 5）')
assert.equal(trendTodo.path, '/post/trend-discovery', '待办必须直达趋势发现页')
assert.equal(trendTodo.urgent, undefined, '建岗节奏由管理员掌握，不标紧急')
// 常驻入口里也要有趋势发现的入口（待办会消失，入口不会）
assert.ok(
  jaTrend.actions.some(action => action.path === '/post/trend-discovery'),
  '岗位体系管理员的常驻入口应包含岗位趋势发现',
)

// 候选全审完（计数为 0）→ 待办必须消失，不能变成永远清不掉的静态入口
assert.equal(
  buildJobArchitectWorkbench({
    postTotal: 12,
    panorama: null,
    inspection: null,
    trendPending: { pendingCandidateCount: 0, pendingNewPostCount: 0, pendingChangeCount: 0, awaitingTaskCount: 0 },
  }).todos.some(todo => todo.title === '审核岗位趋势候选'),
  false,
  '计数为 0 时不得留待办',
)

// 接口取不到（null）→ 同样不出现，不得伪造 0 或强行展示
assert.equal(
  buildJobArchitectWorkbench({ postTotal: 12, panorama: null, inspection: null, trendPending: null })
    .todos.some(todo => todo.title === '审核岗位趋势候选'),
  false,
  '汇总接口失败时不应出现趋势待办',
)
assert.equal(
  buildJobArchitectWorkbench({ postTotal: 12, panorama: null, inspection: null })
    .todos.some(todo => todo.title === '审核岗位趋势候选'),
  false,
  '缺省 trendPending 时不应出现趋势待办',
)

// 计数存在但分类字段缺失（老后端）时不能渲染成 NaN
const jaTrendPartial = buildJobArchitectWorkbench({
  postTotal: 12,
  panorama: null,
  inspection: null,
  trendPending: { pendingCandidateCount: 2 },
})
const partialTodo = jaTrendPartial.todos.find(todo => todo.title === '审核岗位趋势候选')
assert.equal(partialTodo.desc, '2 个候选待确认（新岗位 0 · 能力变更 0）')

const jaEmpty = buildJobArchitectWorkbench({ postTotal: null, panorama: null, inspection: null })
assert.equal(jaEmpty.stats[1].value, '--')
assert.deepEqual(jaEmpty.todos, [], '巡检口径缺失时不得凭空造待办')
assert.ok(jaEmpty.progresses[0].status.includes('暂无口径'))
// 巡检口径缺失时，panorama 的 abilityCount 也不存在，不能凭空调出 0%
assert.equal(jaEmpty.progresses[0].value, 0)
assert.deepEqual(jaEmpty.abilityCloud, [], '无岗位能力明细时词云为空，由视图层整块隐藏')

/* ========================== 平台管理员 ========================== */
// 原「AI 配置管理员」段已删除：该构建器随 2026-09-04 角色合并被移除
// （数据源 /api/rag/** 归岗位体系管理员，平台管理员调用会 403）。

const sa = buildPlatformAdminWorkbench({
  userTotal: 40,
  enabledRoles: ['A', 'B', 'C'],
  roleTotal: 5,
  logTotal: 900,
  logs: [{ realName: '李四', operationDesc: '停用账号', operationTime: '2026-09-04 09:00:00' }],
})
assert.deepEqual(sa.stats.map(s => s.key), ['users', 'roles', 'logs', 'recent'])
assert.equal(sa.stats[1].value, '3', '启用角色优先取列表长度而非分页 total')
assert.equal(sa.stats[3].value, '1')
assert.equal(sa.progresses[0].value, 100)
assert.equal(sa.notices.length, 1)
// 账号/角色/审计都是查阅型数据，没有可判定的未处理状态 → 待办为空，
// 由视图层展示"当前没有待处理事项"（原三条"常规"事项已改为快捷入口）
assert.deepEqual(sa.todos, [], '平台端无可判定待办时不得拿常驻事项凑数')

const saEmpty = buildPlatformAdminWorkbench({
  userTotal: null,
  enabledRoles: null,
  roleTotal: null,
  logTotal: null,
  logs: [],
})
assert.equal(saEmpty.stats[0].value, '--')
assert.equal(saEmpty.stats[1].value, '--')
assert.equal(saEmpty.stats[3].value, '--')
assert.ok(saEmpty.progresses[0].status.includes('暂无口径'))

// enabledRoles 为空数组但 roleTotal 有值时，回落 roleTotal
const saFallback = buildPlatformAdminWorkbench({
  userTotal: 1,
  enabledRoles: [],
  roleTotal: 6,
  logTotal: 1,
  logs: [],
})
assert.equal(saFallback.stats[1].value, '6')

console.log('management-workbench-logic.test.mjs passed')
