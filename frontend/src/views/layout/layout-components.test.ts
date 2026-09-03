import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const layoutSource = readFileSync(join(currentDir, 'index.vue'), 'utf8')

function componentSource(name: string) {
  const path = join(currentDir, 'components', name)
  expect(existsSync(path)).toBe(true)
  return readFileSync(path, 'utf8')
}

describe('layout component boundaries', () => {
  it('composes navigation and topbar through dedicated components', () => {
    expect(layoutSource).toContain("import AppSidebar from './components/AppSidebar.vue'")
    expect(layoutSource).toContain("import AppTopbar from './components/AppTopbar.vue'")
    expect(layoutSource).toContain('<AppSidebar')
    expect(layoutSource).toContain('<AppTopbar')
    expect(layoutSource).not.toContain('class="sidebar-nav"')
    expect(layoutSource).not.toContain('class="layout-topbar"')
  })

  it('keeps component-local styles with the component that renders them', () => {
    const sidebarSource = componentSource('AppSidebar.vue')
    const topbarSource = componentSource('AppTopbar.vue')
    const taskPanelSource = componentSource('TaskNotificationPanel.vue')

    expect(sidebarSource).toContain('<style scoped>')
    expect(sidebarSource).toContain('.layout-sidebar')
    expect(topbarSource).toContain('<style scoped>')
    expect(topbarSource).toContain('.layout-topbar')
    expect(taskPanelSource).toContain('<style scoped>')
    expect(taskPanelSource).toContain('.task-panel')
  })
})
