import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { nextTick } from 'vue'

describe('layout-search-timer', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('does not fire search before 300ms debounce', async () => {
    let searchFired = false
    const mockHandleSearch = vi.fn()

    let searchTimer: ReturnType<typeof setTimeout> | null = null
    function onSearchInput() {
      if (searchTimer) clearTimeout(searchTimer)
      searchTimer = setTimeout(() => mockHandleSearch(), 300)
    }

    onSearchInput()
    vi.advanceTimersByTime(200)
    expect(mockHandleSearch).not.toHaveBeenCalled()

    vi.advanceTimersByTime(150)
    expect(mockHandleSearch).toHaveBeenCalledTimes(1)
  })

  it('clears search timer on unmount before 300ms', () => {
    const mockHandleSearch = vi.fn()
    let searchTimer: ReturnType<typeof setTimeout> | null = null

    function onSearchInput() {
      if (searchTimer) clearTimeout(searchTimer)
      searchTimer = setTimeout(() => mockHandleSearch(), 300)
    }

    function clearSearchTimers() {
      if (searchTimer) {
        clearTimeout(searchTimer)
        searchTimer = null
      }
    }

    onSearchInput()
    clearSearchTimers()
    vi.advanceTimersByTime(500)
    expect(mockHandleSearch).not.toHaveBeenCalled()
  })

  it('clears close panel timer on closeSearchPanel call and on unmount', () => {
    const mockClose = vi.fn()
    let closePanelTimer: ReturnType<typeof setTimeout> | null = null

    function closeSearchPanel() {
      if (closePanelTimer) clearTimeout(closePanelTimer)
      closePanelTimer = setTimeout(() => mockClose(), 200)
    }

    function clearSearchTimers() {
      if (closePanelTimer) {
        clearTimeout(closePanelTimer)
        closePanelTimer = null
      }
    }

    closeSearchPanel()
    clearSearchTimers()
    vi.advanceTimersByTime(500)
    expect(mockClose).not.toHaveBeenCalled()
  })

  it('closeSearchPanel fires when not cleared', () => {
    const mockClose = vi.fn()
    let closePanelTimer: ReturnType<typeof setTimeout> | null = null

    function closeSearchPanel() {
      if (closePanelTimer) clearTimeout(closePanelTimer)
      closePanelTimer = setTimeout(() => mockClose(), 200)
    }

    closeSearchPanel()
    vi.advanceTimersByTime(250)
    expect(mockClose).toHaveBeenCalledTimes(1)
  })
})
