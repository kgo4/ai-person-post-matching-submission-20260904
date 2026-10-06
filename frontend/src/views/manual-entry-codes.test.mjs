import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const root = join(currentDir, '..')

function read(relativePath) {
  return readFileSync(join(root, relativePath), 'utf8')
}

const employeePage = read('views/employee/list/index.vue')
const postPage = read('views/post/list/index.vue')
const templatePage = read('views/post/template/edit.vue')

for (const [name, source, codeField] of [
  ['employee', employeePage, 'empCode'],
  ['post', postPage, 'postCode'],
  ['post template', templatePage, 'templateCode'],
]) {
  assert.match(source, new RegExp(`const \{ ${codeField}: _${codeField}, \.\.\.createPayload \} = form`), `${name} creation should omit ${codeField}`)
  assert.match(source, new RegExp(`v-if="isEdit"[\\s\\S]{0,240}v-model="form\\.${codeField}"[\\s\\S]{0,160}readonly`), `${name} should render ${codeField} read-only only while editing`)
  assert.doesNotMatch(source, new RegExp(`${codeField}: \\[\{ required: true`), `${name} should not require ${codeField}`)
}

// 能力标签的两条断言（新建不填 tagCode / 编辑时只读展示）随 2026-09-04 下线
// 「能力标签治理」页一并移除：composables/useTagDirectory.ts 与
// views/system/ability-tag/components/TagEditorPanel.vue 已删除，
// 文件不存在时 read() 会抛 ENOENT 直接中断整个用例文件。
// 后端标签能力（ability_tag / /api/system/ability-tag/**）保持不变；
// 将来若有页面重新提供标签编辑，需要在新页面上重建同一条约束。

console.log('manual entry code form tests passed')
