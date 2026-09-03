import assert from 'node:assert/strict'
import {
  buildTagPathLabel,
  countDirectChildren,
  shouldExpandNodeByDefault,
} from './tag-directory-panel.ts'

const tree = [
  {
    id: 1,
    tagName: '技术能力',
    children: [
      {
        id: 2,
        tagName: '后端',
        children: [{ id: 3, tagName: 'Java', children: [] }],
      },
    ],
  },
]

assert.equal(countDirectChildren(tree[0]), 1)
assert.equal(buildTagPathLabel(tree, 3), '技术能力 / 后端 / Java')
assert.equal(
  shouldExpandNodeByDefault({ level: 1, isInSelectedPath: false }),
  true,
)
assert.equal(
  shouldExpandNodeByDefault({ level: 2, isInSelectedPath: false }),
  false,
)
assert.equal(
  shouldExpandNodeByDefault({ level: 3, isInSelectedPath: true }),
  true,
)

console.log('tag-directory-panel tests passed')
