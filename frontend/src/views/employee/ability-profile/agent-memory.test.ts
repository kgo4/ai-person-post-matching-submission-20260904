import { beforeEach, describe, expect, it, vi } from 'vitest'

const request = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  get: request.get,
  put: request.put,
}))

import {
  disableAgentMemory,
  enableAgentMemory,
  expireAgentMemory,
  pageAgentMemories,
  pageGovernanceEvents,
  updateAgentMemory,
} from '@/api/ability-governance'

describe('agent-memory-governance API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('requests a filtered memory page', () => {
    const params = { pageNum: 2, pageSize: 20, status: 'ACTIVE', scope: 'AI_INTERVIEW' }

    pageAgentMemories(params)

    expect(request.get).toHaveBeenCalledWith('/governance/agent-memory/page', params)
  })

  it('requests a filtered governance-event page', () => {
    const params = { pageNum: 1, pageSize: 10, modifyType: 'TAG_NORMALIZE', empId: 9 }

    pageGovernanceEvents(params)

    expect(request.get).toHaveBeenCalledWith('/governance/agent-memory/events/page', params)
  })

  it('sends the edit payload to the selected memory', () => {
    const payload = { title: 'Canonical Java rule', priority: 8, applicableScope: 'AI_INTERVIEW' }

    updateAgentMemory(7, payload)

    expect(request.put).toHaveBeenCalledWith('/governance/agent-memory/7', payload)
  })

  it.each([
    ['enable', enableAgentMemory, '/governance/agent-memory/7/enable'],
    ['disable', disableAgentMemory, '/governance/agent-memory/7/disable'],
    ['expire', expireAgentMemory, '/governance/agent-memory/7/expire'],
  ])('uses the %s endpoint', (_action, invoke, path) => {
    invoke(7)

    expect(request.put).toHaveBeenCalledWith(path)
  })
})
