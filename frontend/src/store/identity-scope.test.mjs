import assert from 'node:assert/strict'
import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { clearIdentityScopedStorage, IDENTITY_SCOPED_PERSIST_KEYS } from './identity-scope.ts'

const currentDir = dirname(fileURLToPath(import.meta.url))
const modulesDir = join(currentDir, 'modules')

/* ============ clearIdentityScopedStorage ============ */

function fakeStorage(initialKeys) {
  const map = new Map(initialKeys.map(k => [k, 'x']))
  return {
    removeItem: key => map.delete(key),
    keys: () => [...map.keys()].sort(),
  }
}

// 身份相关的键被清掉
{
  const storage = fakeStorage(['tasks', 'matching-tasks', 'user', 'unrelated'])
  clearIdentityScopedStorage(storage)
  assert.deepEqual(storage.keys(), ['unrelated', 'user'], '只应清身份相关的键')
}

// 空 storage 不报错
clearIdentityScopedStorage(fakeStorage([]))

// 清单本身不得包含 user（user store 自己清字段，pinia 会写回空值）
assert.equal(
  IDENTITY_SCOPED_PERSIST_KEYS.includes('user'),
  false,
  'user 键不应在身份清理清单里（由 user store 自身清理）',
)
// 键不得重复
assert.equal(
  new Set(IDENTITY_SCOPED_PERSIST_KEYS).size,
  IDENTITY_SCOPED_PERSIST_KEYS.length,
  '清单不得有重复键',
)

/* ============ 自动核对：所有持久化 store 都得登记 ============ */

/**
 * 扫描 `store/modules/**` 里所有 `persist: { key: 'xxx' }` 的 key，
 * 断言它们要么在身份清理清单里、要么是 `user`。
 *
 * 这条断言是为了防止「新增了一个持久化 store，却忘了登记」——
 * 那正是 2026-09-04 跨身份泄漏的成因，而且新 store 加进来时不会有任何报错。
 */
const declaredKeys = []
for (const file of readdirSync(modulesDir)) {
  if (!file.endsWith('.ts') || file.endsWith('.test.ts')) continue
  const source = readFileSync(join(modulesDir, file), 'utf8')
  for (const match of source.matchAll(/persist:\s*\{[\s\S]*?key:\s*'([^']+)'/g)) {
    declaredKeys.push({ key: match[1], file })
  }
}

assert.ok(declaredKeys.length > 0, '未扫描到任何持久化 store，正则可能失效了')

const allowed = new Set([...IDENTITY_SCOPED_PERSIST_KEYS, 'user'])
for (const { key, file } of declaredKeys) {
  assert.ok(
    allowed.has(key),
    `${file} 的持久化键 '${key}' 未登记在 identity-scope.ts；` +
      '若它随身份变化，请加入 IDENTITY_SCOPED_PERSIST_KEYS，否则说明为何无需清理',
  )
}

// 清单里的键必须真的对应一个存在的持久化 store（防清单里留死键）
const declaredKeySet = new Set(declaredKeys.map(d => d.key))
for (const key of IDENTITY_SCOPED_PERSIST_KEYS) {
  assert.ok(declaredKeySet.has(key), `identity-scope.ts 里的 '${key}' 不对应任何持久化 store，应删除`)
}

console.log(`identity scope tests passed（核对 ${declaredKeys.length} 个持久化 store）`)
