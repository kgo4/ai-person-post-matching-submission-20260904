import assert from 'node:assert/strict'
import {
  canDeleteTask,
  statusTagType,
  statusText,
  triggerTypeText,
} from './evolution-status.ts'

// 状态映射的取值就是页面上给用户看的字面量，改这里等于改文案，必须被测试拦住。
assert.equal(statusText('PENDING'), '待处理')
assert.equal(statusText('RUNNING'), '运行中')
assert.equal(statusText('WAIT_CONFIRM'), '待确认')
assert.equal(statusText('APPLIED'), '已应用')
assert.equal(statusText('FAILED'), '失败')

// 未知/空值不能渲染成空字符串：空状态回退 '—'，未知状态原样回显便于排查
assert.equal(statusText(null), '—')
assert.equal(statusText(undefined), '—')
assert.equal(statusText(''), '—')
assert.equal(statusText('SOMETHING_NEW'), 'SOMETHING_NEW')

assert.equal(statusTagType('APPLIED'), 'success')
assert.equal(statusTagType('FAILED'), 'danger')
assert.equal(statusTagType('不存在'), 'info')
assert.equal(statusTagType(null), 'info')

assert.equal(triggerTypeText('MANUAL_RUN'), '手动运行')
assert.equal(triggerTypeText('MARKET_DISCOVERY'), '市场发现线索')
assert.equal(triggerTypeText(null), '—')

// RUNNING 的任务不能删：后端还在写它的变更项与证据，删掉会留孤儿数据。
// taskStatus 与 progressStatus 都要看 —— 任务状态可能已推进但进度还在跑。
assert.equal(canDeleteTask({ taskStatus: 'PENDING' }), true)
assert.equal(canDeleteTask({ taskStatus: 'WAIT_CONFIRM', progressStatus: 'COMPLETED' }), true)
assert.equal(canDeleteTask({ taskStatus: 'RUNNING' }), false)
assert.equal(canDeleteTask({ taskStatus: 'PENDING', progressStatus: 'RUNNING' }), false)

console.log('post evolution status tests passed')
