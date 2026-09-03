import assert from 'node:assert/strict'
import { readGraphRouteSelection, isBuildPollingExpired } from './graphAtlasState.ts'

// readGraphRouteSelection
assert.deepEqual(readGraphRouteSelection({ employeeId: '7', postId: '9' }),
  { employeeId: 7, postId: 9 })
assert.equal(readGraphRouteSelection({ employeeId: 'bad' }).employeeId, undefined)
assert.equal(readGraphRouteSelection({}).employeeId, undefined)
assert.deepEqual(readGraphRouteSelection({ employeeId: '7' }),
  { employeeId: 7, postId: undefined })

// isBuildPollingExpired
assert.equal(isBuildPollingExpired(30_001, 30_000), true)
assert.equal(isBuildPollingExpired(29_999, 30_000), false)
assert.equal(isBuildPollingExpired(30_000, 30_000), false)
assert.equal(isBuildPollingExpired(30_001), true)

console.log('All graphAtlasState tests passed.')
