import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

vi.mock('@/api', () => ({
  pageEmployees: vi.fn(),
  pagePosts: vi.fn(),
}))

import { pageEmployees, pagePosts } from '@/api'
import { useGlobalSearch } from './useGlobalSearch'

function deferred() {
  let resolve!: (value: unknown) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('useGlobalSearch', () => {
  it('较慢的第一次搜索不能覆盖较快的第二次响应', async () => {
    const first = deferred()
    const second = deferred()
    vi.mocked(pageEmployees)
      .mockReturnValueOnce(first.promise as never)
      .mockReturnValueOnce(second.promise as never)
    vi.mocked(pagePosts).mockResolvedValue({ code: 200, message: 'ok', data: { records: [] } } as never)

    const { keyword, results, onSearchInput } = useGlobalSearch()
    keyword.value = 'first'
    onSearchInput()
    await new Promise((r) => setTimeout(r, 350))
    keyword.value = 'second'
    onSearchInput()
    await new Promise((r) => setTimeout(r, 350))

    // 第一次响应晚到
    first.resolve({
      code: 200,
      message: 'ok',
      data: { records: [{ id: 1, realName: '旧员工', empCode: 'E001' }] },
    })
    second.resolve({
      code: 200,
      message: 'ok',
      data: { records: [{ id: 2, realName: '新员工', empCode: 'E002' }] },
    })
    await nextTick()
    await nextTick()

    expect(results.value).toHaveLength(1)
    expect(results.value[0].name).toBe('新员工')
  })

  it('取消不产生错误提示（静默）', async () => {
    const slow = deferred()
    vi.mocked(pageEmployees).mockReturnValueOnce(slow.promise as never)
    vi.mocked(pagePosts).mockResolvedValue({ code: 200, message: 'ok', data: { records: [] } } as never)

    const { keyword, results, loading, onSearchInput, clearResults } = useGlobalSearch()
    keyword.value = 'query'
    onSearchInput()
    await new Promise((r) => setTimeout(r, 350))

    clearResults()
    slow.resolve({
      code: 200,
      message: 'ok',
      data: { records: [{ id: 3, realName: 'X', empCode: 'X' }] },
    })
    await nextTick()

    expect(results.value).toHaveLength(0)
    expect(loading.value).toBe(false)
  })

  it('卸载后不应用过期响应', async () => {
    const slow = deferred()
    vi.mocked(pageEmployees).mockReturnValueOnce(slow.promise as never)
    vi.mocked(pagePosts).mockResolvedValue({ code: 200, message: 'ok', data: { records: [] } } as never)

    const { keyword, results, onSearchInput } = useGlobalSearch()
    keyword.value = 'q'
    onSearchInput()
    await new Promise((r) => setTimeout(r, 350))

    slow.resolve({
      code: 200,
      message: 'ok',
      data: { records: [{ id: 4, realName: 'Z', empCode: 'Z' }] },
    })
    await nextTick()

    expect(results.value).toHaveLength(0)
  })

  it('空白输入清空结果', async () => {
    const { keyword, results, onSearchInput } = useGlobalSearch()
    keyword.value = '   '
    onSearchInput()
    await new Promise((r) => setTimeout(r, 350))

    expect(results.value).toEqual([])
  })
})
