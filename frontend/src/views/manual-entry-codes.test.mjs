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
const tagDirectory = read('composables/useTagDirectory.ts')
const tagEditor = read('views/system/ability-tag/components/TagEditorPanel.vue')
const tagEditPage = read('views/system/ability-tag/edit.vue')

for (const [name, source, codeField] of [
  ['employee', employeePage, 'empCode'],
  ['post', postPage, 'postCode'],
  ['post template', templatePage, 'templateCode'],
]) {
  assert.match(source, new RegExp(`const \{ ${codeField}: _${codeField}, \.\.\.createPayload \} = form`), `${name} creation should omit ${codeField}`)
  assert.match(source, new RegExp(`v-if="isEdit"[\\s\\S]{0,240}v-model="form\\.${codeField}"[\\s\\S]{0,160}readonly`), `${name} should render ${codeField} read-only only while editing`)
  assert.doesNotMatch(source, new RegExp(`${codeField}: \\[\{ required: true`), `${name} should not require ${codeField}`)
}

assert.match(tagDirectory, /const \{ tagCode: _tagCode, \.\.\.createPayload \} = form/, 'tag creation should omit tagCode')
assert.doesNotMatch(tagDirectory, /tagCode: \[\{ required: true/, 'tag creation should not require tagCode')
assert.match(tagEditor, /v-if="form\.id"[\s\S]{0,240}v-model="form\.tagCode"[\s\S]{0,160}readonly/, 'tag editor should show tagCode read-only only for an existing tag')
assert.doesNotMatch(tagEditPage, /prop="code"/, 'legacy tag edit page should not render an editable code field')
assert.doesNotMatch(tagEditPage, /code: \[\{ required: true/, 'legacy tag edit page should not require a code')

console.log('manual entry code form tests passed')
