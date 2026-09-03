import { readdirSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { resolve, extname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(fileURLToPath(import.meta.url), '..', '..')
const srcDir = resolve(root, 'src')

function findMjsTests(dir) {
  const results = []
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = resolve(dir, entry.name)
    if (entry.isDirectory() && entry.name !== 'node_modules' && !entry.name.startsWith('.')) {
      results.push(...findMjsTests(full))
    } else if (entry.isFile() && extname(entry.name) === '.mjs' && entry.name.endsWith('.test.mjs')) {
      results.push(full)
    }
  }
  return results
}

const testFiles = findMjsTests(srcDir)

let failed = 0
let passed = 0

for (const file of testFiles) {
  const rel = file.slice(root.length + 1)
  const result = spawnSync('node', ['--experimental-strip-types', file], {
    cwd: root,
    stdio: 'inherit',
    timeout: 30_000,
  })
  if (result.status !== 0) {
    console.error(`FAIL: ${rel}`)
    failed++
  } else {
    passed++
  }
}

console.log(`\n.mjs tests: ${passed} passed, ${failed} failed`)
if (failed > 0) process.exit(1)
