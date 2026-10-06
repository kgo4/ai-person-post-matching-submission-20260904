import assert from 'node:assert/strict'
import {
  analysisStatusMeta,
  bindBlockReason,
  canImportAbilities,
  employeeDisplayName,
  emptyRosterReason,
  importActionState,
  pmsDisplayName,
  rosterBindingMeta,
  rosterBindingState,
  rosterMatchesKeyword,
  selectableConflictEmpIds,
  summarizeSyncResult,
} from './pms-roster.ts'

/* ============ analysisStatusMeta ============ */
assert.deepEqual(analysisStatusMeta(2), { text: '待导入', tone: 'success' })
assert.deepEqual(analysisStatusMeta(6), { text: '已导入', tone: 'success' })
assert.deepEqual(analysisStatusMeta(null), { text: '未分析', tone: 'info' })
assert.deepEqual(analysisStatusMeta(undefined), { text: '未分析', tone: 'info' })
// 未知状态码不能静默成空白
assert.deepEqual(analysisStatusMeta(99), { text: '状态 99', tone: 'info' })

/* ============ rosterBindingState / rosterBindingMeta ============ */
const unbound = { bound: false, empId: null, empName: null, empCode: null, empHasAccount: null }
const boundNoAccount = { bound: true, empId: 7, empName: '张三', empCode: 'EMP001', empHasAccount: false }
const boundWithAccount = { bound: true, empId: 7, empName: '张三', empCode: 'EMP001', empHasAccount: true }

assert.equal(rosterBindingState(unbound), 'unbound')
assert.equal(rosterBindingState(boundNoAccount), 'bound-without-account')
assert.equal(rosterBindingState(boundWithAccount), 'bound-with-account')

// bound=true 但 empId 为空是脏数据，按未绑定处理，不能抛出
assert.equal(rosterBindingState({ bound: true, empId: null }), 'unbound')

assert.deepEqual(rosterBindingMeta(unbound), { state: 'unbound', text: '未绑定', tone: 'warning' })
assert.deepEqual(rosterBindingMeta(boundNoAccount), {
  state: 'bound-without-account',
  text: '已绑定 · 待注册',
  tone: 'info',
})
assert.deepEqual(rosterBindingMeta(boundWithAccount), {
  state: 'bound-with-account',
  text: '已绑定',
  tone: 'success',
})

/* ============ 显示名兜底 ============ */
assert.equal(employeeDisplayName({ empName: '张三' }), '张三')
assert.equal(employeeDisplayName({ empName: '   ', empCode: 'EMP001' }), 'EMP001')
assert.equal(employeeDisplayName({ empId: 7 }), '员工#7')
assert.equal(employeeDisplayName({}), '—')

assert.equal(pmsDisplayName({ pmsNickname: '李四', pmsUsername: 'lisi', pmsUserId: 3 }), '李四')
assert.equal(pmsDisplayName({ pmsNickname: '', pmsUsername: 'lisi', pmsUserId: 3 }), 'lisi')
assert.equal(pmsDisplayName({ pmsUserId: 3 }), 'PMS#3')

/* ============ canImportAbilities：口径落点 ============ */
// 「能分析，导入才要求绑定」——未绑定不得导入
assert.equal(canImportAbilities(unbound), false)
assert.equal(canImportAbilities(boundNoAccount), true)
assert.equal(canImportAbilities(boundWithAccount), true)

/* ============ importActionState：禁用必须带原因 ============ */
// 已绑定 + 分析成功(2) + 选了 2 项 → 可导入
assert.deepEqual(
  importActionState({ item: boundNoAccount, analysisStatus: 2, selectedCount: 2, abilityCount: 5 }),
  { disabled: false, hint: '已选 2 / 5 项' },
)

// 未绑定：能分析但不能导入（口径落点）
const blockedByBinding = importActionState({ item: unbound, analysisStatus: 2, selectedCount: 2, abilityCount: 5 })
assert.equal(blockedByBinding.disabled, true)
assert.ok(blockedByBinding.hint.includes('绑定'), blockedByBinding.hint)

// 一项没选
const noneSelected = importActionState({ item: boundWithAccount, analysisStatus: 2, selectedCount: 0, abilityCount: 5 })
assert.equal(noneSelected.disabled, true)
assert.ok(noneSelected.hint.includes('至少选择一项'), noneSelected.hint)

// 已导入过（6）：不能重复导入，且必须说清「怎么才能再来一次」
const alreadyImported = importActionState({ item: boundWithAccount, analysisStatus: 6, selectedCount: 3, abilityCount: 5 })
assert.equal(alreadyImported.disabled, true)
assert.ok(alreadyImported.hint.includes('重新导入'), alreadyImported.hint)

// 分析中(1) / 失败(3) / 未分析(null)：都要给原因，不能静默禁用
for (const status of [0, 1, 3, null]) {
  const state = importActionState({ item: boundWithAccount, analysisStatus: status, selectedCount: 3, abilityCount: 5 })
  assert.equal(state.disabled, true, `status=${status} 应禁用`)
  assert.ok(state.hint.length > 0, `status=${status} 必须给出原因`)
  assert.ok(!state.hint.includes('绑定'), `status=${status} 的原因不应误报成「未绑定」`)
}

// 未选中 PMS 人员
const noTarget = importActionState({ item: null, analysisStatus: 2, selectedCount: 1, abilityCount: 5 })
assert.equal(noTarget.disabled, true)
assert.ok(noTarget.hint.length > 0, noTarget.hint)

/* ============ 绑定弹窗冲突项 ============ */
// 当前行自己已绑的员工必须可选，否则 HR 打不开自己的绑定项、也改不了绑
assert.deepEqual(selectableConflictEmpIds([1, 2, 3], 2), [1, 3])
assert.deepEqual(selectableConflictEmpIds([1, 2, 3], null), [1, 2, 3])
assert.deepEqual(selectableConflictEmpIds([1, 2, 3], undefined), [1, 2, 3])

assert.equal(bindBlockReason({ pmsUserId: null, empId: 1, conflictEmpIds: [] }), '未选中 PMS 人员')
assert.equal(bindBlockReason({ pmsUserId: 5, empId: null, conflictEmpIds: [] }), '请选择要绑定的员工')
assert.equal(bindBlockReason({ pmsUserId: 5, empId: 1, conflictEmpIds: [1] }), '该员工已绑定其他 PMS 人员，请先解绑')
assert.equal(bindBlockReason({ pmsUserId: 5, empId: 1, conflictEmpIds: [2] }), null)

/* ============ summarizeSyncResult ============ */
const text = summarizeSyncResult({ newSynced: 3, totalPmsUsers: 10, alreadySynced: 7, autoBound: 2 })
assert.ok(text.includes('PMS 人员 10 人'), text)
assert.ok(text.includes('本次新同步 3 人'), text)
assert.ok(text.includes('此前已同步 7 人'), text)
assert.ok(text.includes('按工号自动绑定 2 人'), text)
// 旧实现会往人员库插影子档案，文案里绝不能出现「自动创建员工」
assert.ok(!text.includes('创建员工'), text)

const quiet = summarizeSyncResult({ newSynced: 0, totalPmsUsers: 0, alreadySynced: 0, autoBound: 0 })
assert.ok(!quiet.includes('此前已同步'), quiet)
assert.ok(!quiet.includes('自动绑定'), quiet)

/* ============ emptyRosterReason ============ */
assert.equal(emptyRosterReason('PMS 数据源未配置或不可用'), 'PMS 数据源未配置或不可用')
assert.ok(emptyRosterReason(null).length > 0)
assert.ok(emptyRosterReason('   ').length > 0)

/* ============ rosterMatchesKeyword ============ */
const row = {
  pmsNickname: '张三',
  pmsUsername: 'zhangsan',
  pmsEmployeeId: 'PMS-001',
  pmsRole: 'DEV',
  empName: '张三',
  empCode: 'EMP001',
}
assert.equal(rosterMatchesKeyword(row, ''), true, '空关键字应全量放行')
assert.equal(rosterMatchesKeyword(row, '   '), true)
assert.equal(rosterMatchesKeyword(row, '张'), true)
assert.equal(rosterMatchesKeyword(row, 'ZHANGSAN'), true, '应大小写不敏感')
assert.equal(rosterMatchesKeyword(row, 'emp001'), true, '应能按本系统工号搜到')
assert.equal(rosterMatchesKeyword(row, '李四'), false)
// 字段为 null 时不能把 "null" 拼进 haystack
assert.equal(rosterMatchesKeyword({ pmsNickname: null, pmsUserId: 1 }, 'ull'), false)

console.log('pms roster tests passed')
