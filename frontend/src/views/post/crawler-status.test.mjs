/**
 * 爬虫采集状态映射的回归测试。
 *
 * 重点锁死：
 * 1. 「未知状态」必须按「仍在推进」处理，否则命令还没跑完就停止轮询，用户看到永远不变的「待领取」；
 * 2. 终态集合必须完整，新增终态时这里先红，提醒同步页面轮询逻辑；
 * 3. 未知来源代码回显原值，不吞成空串。
 */
import assert from 'node:assert/strict'
import {
  BATCH_CHANNELS,
  BATCH_CHANNEL_OPTIONS,
  MARKET_JD_ANALYSIS_STATUS,
  TERMINAL_COMMAND_STATUSES,
  analysisTagType,
  batchResultTagType,
  channelLabel,
  channelTagType,
  commandTagType,
  describeBatchDelete,
  describeBatchJdDelete,
  describeSingleJdDelete,
  isCommandActive,
  isAnalysisActive,
  marketJdAnalysisStatusMeta,
  sourceLabel,
  sourceLabels,
  summarizeCommands,
} from './crawler-status.ts'

// ---- 终态集合 ----
assert.deepEqual(
  [...TERMINAL_COMMAND_STATUSES].sort(),
  ['CANCELLED', 'EXPIRED', 'FAILED', 'SUCCEEDED'],
  '命令终态集合发生变化时必须同步检查页面轮询条件',
)

// ---- isCommandActive ----
for (const status of TERMINAL_COMMAND_STATUSES) {
  assert.equal(isCommandActive(status), false, `${status} 是终态，不应继续轮询`)
}
for (const status of ['PENDING', 'DISPATCHED', 'RUNNING']) {
  assert.equal(isCommandActive(status), true, `${status} 尚未结束，应继续轮询`)
}
assert.equal(isCommandActive(null), true, '状态未知时按仍在推进处理，避免过早停止轮询')
assert.equal(isCommandActive(''), true, '空状态同样按仍在推进处理')

// ---- tag 颜色 ----
assert.equal(commandTagType('SUCCEEDED'), 'success')
assert.equal(commandTagType('FAILED'), 'danger')
assert.equal(commandTagType('RUNNING'), 'warning')
assert.equal(commandTagType('CANCELLED'), 'info')
assert.equal(commandTagType(undefined), 'info')

assert.equal(batchResultTagType('OK'), 'success')
assert.equal(batchResultTagType('PARTIAL_FAILED'), 'warning')
assert.equal(batchResultTagType('REJECTED'), 'danger')
assert.equal(batchResultTagType('UNAUTHORIZED'), 'danger')
assert.equal(batchResultTagType('WHATEVER'), 'info')

assert.equal(analysisTagType('SUCCEEDED'), 'success')
assert.equal(analysisTagType('FAILED'), 'danger')
assert.equal(analysisTagType('QUEUED'), 'warning')
assert.equal(analysisTagType('SKIPPED'), 'info')

// ---- 来源映射 ----
assert.equal(sourceLabel('jd'), '京东 / 招聘 JD')
assert.equal(sourceLabel('remoteok'), 'RemoteOK')
assert.equal(sourceLabel('unknown-source'), 'unknown-source', '未知来源应回显原值')
assert.equal(sourceLabel(null), '—')
assert.deepEqual(sourceLabels(['jd', 'themuse']), ['京东 / 招聘 JD', 'The Muse'])
assert.deepEqual(sourceLabels([]), [])
assert.deepEqual(sourceLabels(null), [])

// ---- 批次来源通道（爬虫 vs 人工上传） ----
// 通道取值必须与后端 MarketJdData.CHANNEL_* 一一对应；后端加了新通道这里会先红。
assert.deepEqual(
  [...BATCH_CHANNELS].sort(),
  ['CRAWLER', 'MANUAL_UPLOAD', 'POST_IMPORT'],
  '批次通道集合发生变化时必须同步后端 MarketJdData.CHANNEL_* 与筛选下拉',
)

// 筛选项必须覆盖全部通道，且第一项是「不筛选」
assert.equal(BATCH_CHANNEL_OPTIONS[0].value, '', '筛选项第一项必须是「全部来源」（空串 = 不筛选）')
for (const channel of BATCH_CHANNELS) {
  assert.ok(
    BATCH_CHANNEL_OPTIONS.some(option => option.value === channel),
    `筛选下拉必须包含通道 ${channel}，否则该来源的批次无法被单独筛出`,
  )
}

assert.equal(channelLabel('CRAWLER'), '爬虫推送')
assert.equal(channelLabel('MANUAL_UPLOAD'), '人工上传')
assert.equal(channelLabel('POST_IMPORT'), '岗位导入')
assert.equal(channelLabel('PARTNER_FEED'), 'PARTNER_FEED', '未知通道回显原值，避免被误读成「没有来源」')
assert.equal(channelLabel(null), '—')
assert.equal(channelLabel(undefined), '—')
assert.equal(channelLabel(''), '—')

// 爬虫批次必须与人工批次在视觉上可区分，否则「分类」等于没做
assert.notEqual(
  channelTagType('CRAWLER'),
  channelTagType('MANUAL_UPLOAD'),
  '爬虫推送与人工上传必须用不同颜色的 tag，否则运维无法一眼分开',
)
assert.equal(channelTagType('CRAWLER'), 'primary')
assert.equal(channelTagType('MANUAL_UPLOAD'), 'warning')
assert.equal(channelTagType('POST_IMPORT'), 'info')
assert.equal(channelTagType(null), 'info')

// ---- 批次删除结论 ----
assert.equal(
  describeBatchDelete({ batchNo: 'b1', marketJdRows: 12, versionSnapshots: 2, batchLogs: 1 }),
  '批次已删除：市场 JD 12 条、历史版本 2 条、批次登记 1 条',
)
assert.equal(
  describeBatchDelete({ batchNo: 'b1' }),
  '批次已不存在，无数据被删除',
  '后端报 0/0/0 时不能编造「已删除 0 条」的假结论，要明说没有数据被动过',
)
assert.equal(
  describeBatchDelete(null),
  '批次已删除',
  '后端未返回明细时不能编造删除条数',
)

// ---- 单条 / 批量 JD 删除结论（数据治理） ----
assert.equal(
  describeSingleJdDelete({ id: 7, marketJdRows: 1, versionSnapshots: 3 }),
  '已删除该 JD，同时清理历史版本 3 条',
)
assert.equal(
  describeSingleJdDelete({ id: 7, marketJdRows: 0, versionSnapshots: 0 }),
  '该 JD 已不存在，无需删除',
  '记录被并发删掉时不能说「删除成功」，否则用户以为自己刚删的那条还在',
)
assert.equal(
  describeSingleJdDelete(null),
  '该 JD 已不存在，无需删除',
  '后端未返回明细时不能编造删除结论',
)

assert.equal(
  describeBatchJdDelete({ requested: 3, marketJdRows: 3, versionSnapshots: 5, missingIds: [] }),
  '已删除 3 条 JD，清理历史版本 5 条',
)
assert.equal(
  describeBatchJdDelete({ requested: 4, marketJdRows: 2, versionSnapshots: 0, missingIds: [11, 12] }),
  '已删除 2 条 JD，清理历史版本 0 条；另有 2 条已不存在（可能已被他人删除）',
  'missingIds 必须体现在结论里：勾选时还在、提交后已被他人删除的情况不能被吞成「全部成功」',
)
assert.equal(
  describeBatchJdDelete({ requested: 2, marketJdRows: 0, versionSnapshots: 0, missingIds: [1, 2] }),
  '所选 JD 均已不存在，无需删除',
)
assert.equal(describeBatchJdDelete(null), '批量删除已完成')

// ---- 汇总 ----
assert.deepEqual(summarizeCommands(null), { total: 0, active: 0, succeeded: 0, failed: 0 })
assert.deepEqual(
  summarizeCommands([
    { status: 'PENDING' },
    { status: 'RUNNING' },
    { status: 'SUCCEEDED' },
    { status: 'FAILED' },
    { status: 'CANCELLED' },
  ]),
  { total: 5, active: 2, succeeded: 1, failed: 1 },
  '已取消/已过期既不算进行中，也不算成功或失败',
)

// ---- 在途解析判定（页面是否该继续轮询） ----
//
// 修复前页面的轮询条件只看「有没有未结束的采集命令」，而采集命令入口默认关闭，
// 于是爬虫自己推送导致批次进入「解析中」时页面完全不轮询，标签一直不变。
for (const state of ['QUEUED', 'RUNNING']) {
  assert.equal(isAnalysisActive(state), true, `${state} 表示解析仍在推进，页面必须继续轮询`)
}
for (const state of ['SUCCEEDED', 'FAILED', 'SKIPPED', 'NOT_TRIGGERED', undefined, null, '']) {
  assert.equal(isAnalysisActive(state), false, `${state} 是终态/未触发，不应继续轮询`)
}

// ===== 市场 JD 行级分析状态（曾经把「已分析」显示成「分析中」）=====
//
// 背景：市场 JD（MarketJdData）的口径是 0 待分析 / 1 已分析 / 2 跳过，
// 与 Excel 导入（PostImportItem）的 0 待分析 / 1 分析中 / 2 成功 / 3 失败 完全不同。
// 页面上曾按后者渲染，导致每一条分析成功的 JD 都显示成「分析中」——线上表现为
// 「爬虫推来数据、点完解析一直卡在解析中」。这一组断言就是这个 bug 的回归锁。

assert.deepEqual(
  MARKET_JD_ANALYSIS_STATUS,
  { PENDING: 0, ANALYZED: 1, SKIPPED: 2 },
  '市场 JD 行级状态取值必须与后端 MarketJdData.analysisStatus 一致（0/1/2），不要改成 Excel 那套',
)

// 1 是「已分析」而不是「分析中」—— 这一条是本 bug 的核心
assert.equal(
  marketJdAnalysisStatusMeta(MARKET_JD_ANALYSIS_STATUS.ANALYZED).text,
  '已分析',
  'analysisStatus=1 表示后端已提取完成并入准入流程，绝不是「分析中」',
)
assert.equal(marketJdAnalysisStatusMeta(MARKET_JD_ANALYSIS_STATUS.ANALYZED).tone, 'success')

// 2 是「跳过/阻断」而不是「已分析」
assert.equal(
  marketJdAnalysisStatusMeta(MARKET_JD_ANALYSIS_STATUS.SKIPPED).text,
  '跳过',
  'analysisStatus=2 是清洗阻断或去重跳过（后端 MarketJdQueryPortAdapter 据此排除证据），不是成功',
)
assert.equal(marketJdAnalysisStatusMeta(MARKET_JD_ANALYSIS_STATUS.SKIPPED).tone, 'warning')

assert.equal(marketJdAnalysisStatusMeta(MARKET_JD_ANALYSIS_STATUS.PENDING).text, '待分析')
assert.equal(marketJdAnalysisStatusMeta(null).text, '待分析', '状态缺失按待分析处理，不能显示成已分析')
assert.equal(marketJdAnalysisStatusMeta(undefined).text, '待分析')

// 去重命中的 JD 会被刻意跳过分析、状态永远停在 0：必须说清是「重复跳过」，
// 否则用户会以为系统还欠它一次解析，反复点「重新解析」也不会变。
assert.equal(
  marketJdAnalysisStatusMeta(MARKET_JD_ANALYSIS_STATUS.PENDING, 1).text,
  '重复跳过',
  '重复项的状态必须区别于普通待分析项',
)
assert.equal(
  marketJdAnalysisStatusMeta(MARKET_JD_ANALYSIS_STATUS.ANALYZED, 1).text,
  '已分析',
  '重复标记不应覆盖已经真实分析完成的状态',
)
assert.equal(marketJdAnalysisStatusMeta(MARKET_JD_ANALYSIS_STATUS.PENDING, 0).text, '待分析')

// 未知取值回显原码：静默归一化会让后端新增状态无人发现
assert.equal(marketJdAnalysisStatusMeta(9).text, '未知(9)')
assert.equal(marketJdAnalysisStatusMeta(9).tone, 'info')

console.log('crawler status tests passed')
