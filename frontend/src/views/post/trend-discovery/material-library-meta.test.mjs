import assert from 'node:assert/strict'
import {
  formatUploadedTime,
  isAlreadyPicked,
  isMaterialReady,
  materialStatusMeta,
  pickBlockReason,
} from './material-library-meta.ts'

/* ============ materialStatusMeta ============ */

// FAILED 优先于一切：片段数可能是 0，但「失败」比「没有片段」更该被说出来
assert.deepEqual(materialStatusMeta('FAILED', false).label, '索引失败')
assert.deepEqual(materialStatusMeta('FAILED', true).label, '索引失败')
assert.equal(materialStatusMeta('FAILED', false).type, 'danger')

// PENDING / 缺失都算「还没索引」
assert.equal(materialStatusMeta('PENDING', false).label, '待索引')
assert.equal(materialStatusMeta(undefined, false).label, '待索引')

// 索引成功但无片段（扫描件）：不能说成可用，否则用户点解析只会拿到空结果
assert.equal(materialStatusMeta('INDEXED', false).label, '无可用片段')
assert.equal(materialStatusMeta('INDEXED', false).type, 'warning')
assert.match(materialStatusMeta('INDEXED', false).hint, /OCR/)

// 正常可解析
assert.equal(materialStatusMeta('INDEXED', true).label, '可解析')
assert.equal(materialStatusMeta('INDEXED', true).type, 'success')

// readyForAnalysis 缺省（老接口没返回该字段）时按「不可解析」处理，宁可保守
assert.equal(materialStatusMeta('INDEXED', undefined).label, '无可用片段')

/* ============ isMaterialReady ============ */

assert.equal(isMaterialReady(true), true)
assert.equal(isMaterialReady(false), false)
// undefined 必须为 false：断言 `=== true`，不能写成真值判断，否则 undefined/0 都会漏过
assert.equal(isMaterialReady(undefined), false)

/* ============ pickBlockReason ============ */

const ready = { readyForAnalysis: true, ephemeral: false }
assert.equal(pickBlockReason(ready), '')

// 仅试算材料不进复用池：即使索引完好也不能拿来做新的正式解析
assert.match(pickBlockReason({ readyForAnalysis: true, ephemeral: true }), /仅试算/)
// 试算原因优先于索引原因（两个问题同时存在时，先说根本原因）
assert.match(pickBlockReason({ readyForAnalysis: false, ephemeral: true }), /仅试算/)

assert.match(pickBlockReason({ readyForAnalysis: false, ephemeral: false }), /没有可用的检索片段/)
assert.match(pickBlockReason({}), /没有可用的检索片段/)

/* ============ isAlreadyPicked ============ */

assert.equal(isAlreadyPicked(7, [7, 8]), true)
assert.equal(isAlreadyPicked(9, [7, 8]), false)
assert.equal(isAlreadyPicked(7, []), false)

/* ============ formatUploadedTime ============ */

assert.equal(formatUploadedTime('2026-09-04T11:22:42'), '2026-09-04 11:22')
assert.equal(formatUploadedTime('2026-09-04'), '2026-09-04')
assert.equal(formatUploadedTime(''), '—')
assert.equal(formatUploadedTime(null), '—')
assert.equal(formatUploadedTime(undefined), '—')

console.log('material-library-meta: all assertions passed')
