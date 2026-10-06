import assert from 'node:assert/strict'
import {
  ROLE_LABELS,
  normalizeRoleCode,
  primaryRoleLabel,
  roleLabelOf,
} from './role-label.ts'

/* ============ normalizeRoleCode ============ */
assert.equal(normalizeRoleCode('ROLE_HR_SPECIALIST'), 'HR_SPECIALIST')
assert.equal(normalizeRoleCode('hr_specialist'), 'HR_SPECIALIST')
assert.equal(normalizeRoleCode('  ROLE_EMPLOYEE  '), 'EMPLOYEE')
assert.equal(normalizeRoleCode(''), '')
assert.equal(normalizeRoleCode(null), '')
assert.equal(normalizeRoleCode(undefined), '')

/* ============ roleLabelOf ============ */
// 带前缀 / 不带前缀必须得到同一个中文名（调用方不该关心给的是哪种）
assert.equal(roleLabelOf('ROLE_HR_SPECIALIST'), 'HR')
assert.equal(roleLabelOf('HR_SPECIALIST'), 'HR')
assert.equal(roleLabelOf('employee'), '员工')
assert.equal(roleLabelOf('JOB_ARCHITECT'), '岗位体系管理员')
assert.equal(roleLabelOf('PLATFORM_ADMIN'), '平台管理员')

// V162 合并掉的旧码仍要给可读名，而不是英文码
assert.equal(roleLabelOf('ROLE_AI_CONFIG_MANAGER'), '平台管理员（角色已合并）')
assert.equal(roleLabelOf('ROLE_SECURITY_ADMIN'), '平台管理员（角色已合并）')

// 未登记的码原样展示（便于定位新角色），不吞成「未知角色」
assert.equal(roleLabelOf('DATA_STEWARD'), 'DATA_STEWARD')

// 空值才回落
assert.equal(roleLabelOf(''), '未知角色')
assert.equal(roleLabelOf(null), '未知角色')
assert.equal(roleLabelOf(undefined), '未知角色')
assert.equal(roleLabelOf('', 'WORKSPACE'), 'WORKSPACE')

/* ============ primaryRoleLabel ============ */
// 主角色 = 数组首位（后端按优先级下发）
assert.equal(primaryRoleLabel(['ROLE_HR_SPECIALIST', 'ROLE_EMPLOYEE']), 'HR')
assert.equal(primaryRoleLabel(['EMPLOYEE']), '员工')
// 空数组 / undefined 回落
assert.equal(primaryRoleLabel([]), '未知角色')
assert.equal(primaryRoleLabel(undefined), '未知角色')
// 首位是空串时跳过它去找下一个可用的，而不是直接回落
assert.equal(primaryRoleLabel(['', 'JOB_ARCHITECT']), '岗位体系管理员')

/* ============ 映射表自身的约定 ============ */
// 键必须是归一化后的形态（无 ROLE_ 前缀、全大写），否则永远命中不了
for (const key of Object.keys(ROLE_LABELS)) {
  assert.equal(key, normalizeRoleCode(key), `ROLE_LABELS 的键 ${key} 必须已归一化`)
}
// 四个在用角色必须都有中文名
for (const code of ['EMPLOYEE', 'HR_SPECIALIST', 'JOB_ARCHITECT', 'PLATFORM_ADMIN']) {
  assert.notEqual(ROLE_LABELS[code], undefined, `缺少角色 ${code} 的中文名`)
  assert.notEqual(ROLE_LABELS[code], code, `角色 ${code} 不应展示成英文码`)
}

console.log('role label tests passed')
