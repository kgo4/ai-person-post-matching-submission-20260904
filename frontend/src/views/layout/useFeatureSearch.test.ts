import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { getSidebarModules } from '@/config/sidebar-menu'
import { buildFeatureIndex, searchFeatures, useFeatureSearch } from './useFeatureSearch'

/** HR 真实持有的权限码子集（见 V147/V162 授权矩阵） */
const HR_PERMISSIONS = [
  'EMPLOYEE:READ',
  'ASSESSMENT:MANAGE',
  'MATCHING:EXECUTE',
  'MATCHING:APPROVE',
  'MATCHING:CONFIG',
  'MATCHING:READ',
  'NOTIFICATION:SELF',
  'LEARNING:SELF',
]

/** 员工只持有 self 域权限 */
const EMPLOYEE_PERMISSIONS = ['NOTIFICATION:SELF', 'ASSESSMENT:SELF', 'LEARNING:SELF', 'MATCHING:SELF']

function hrIndex() {
  return buildFeatureIndex(getSidebarModules(), HR_PERMISSIONS, 'HR_SPECIALIST')
}

function employeeIndex() {
  return buildFeatureIndex(getSidebarModules(), EMPLOYEE_PERMISSIONS, 'EMPLOYEE')
}

describe('buildFeatureIndex', () => {
  it('索引只包含当前角色可见的功能（搜得到 = 进得去）', () => {
    const employeePaths = employeeIndex().map(item => item.path)

    expect(employeePaths).toContain('/matching/my-result')
    // 员工没有 MATCHING:EXECUTE，HR 的「发起匹配」不能出现在员工索引里
    expect(employeePaths).not.toContain('/matching/execute')
    // 员工的自助发起匹配是独立页面（MATCHING:SELF），必须搜得到 —— 搜得到就进得去
    expect(employeePaths).toContain('/matching/self-execute')
    // 员工没有 EMPLOYEE:READ，员工档案入口同理
    expect(employeePaths).not.toContain('/employee/list')
  })

  it('同一份菜单按角色裁出不同的功能集合', () => {
    const hrPaths = hrIndex().map(item => item.path)
    const employeePaths = employeeIndex().map(item => item.path)

    expect(hrPaths).toContain('/employee/list')
    expect(hrPaths).toContain('/matching/execute')
    expect(hrPaths).toContain('/matching/tasks')

    // 员工专属入口不应出现在 HR 索引里
    expect(hrPaths).not.toContain('/matching/my-result')
    expect(hrPaths).not.toContain('/matching/self-execute')
    expect(employeePaths).not.toContain('/matching/result')
  })

  it('每一份权限都可能出现在功能列表里', () => {
    const menu = getSidebarModules()
    // 平台管理员：账号/角色/审计 + AI 基础设施
    const adminIndex = buildFeatureIndex(menu, ['USER:MANAGE', 'ROLE:MANAGE', 'AUDIT:READ', 'AI:CONFIG', 'ASSESSMENT:CONFIG'], 'PLATFORM_ADMIN')
    const paths = adminIndex.map(item => item.path)

    expect(paths).toContain('/system/user')
    expect(paths).toContain('/system/role')
    expect(paths).toContain('/system/operation-log')
    // 无 POST:MANAGE，业务知识入口不应出现
    expect(paths).not.toContain('/rag/knowledge')
  })

  it('结果带模块名，便于在结果里区分同名页面', () => {
    const item = hrIndex().find(entry => entry.path === '/matching/result')
    expect(item?.moduleLabel).toBe('人岗匹配')
  })
})

describe('searchFeatures', () => {
  it('空白输入不返回任何结果', () => {
    expect(searchFeatures(hrIndex(), '')).toEqual([])
    expect(searchFeatures(hrIndex(), '   ')).toEqual([])
  })

  it('完全相等的页面名排在前缀/包含命中之前', () => {
    const index = [
      { moduleKey: 'matching', moduleLabel: '人岗匹配', label: '匹配结果', path: '/matching/result' },
      { moduleKey: 'matching', moduleLabel: '人岗匹配', label: '我的匹配结果', path: '/matching/my-result' },
      { moduleKey: 'matching', moduleLabel: '人岗匹配', label: '发起匹配', path: '/matching/execute' },
    ]

    expect(searchFeatures(index, '匹配结果').map(item => item.label)).toEqual([
      '匹配结果',
      '我的匹配结果',
    ])
  })

  it('页面名不命中时回退到模块名匹配', () => {
    const results = searchFeatures(hrIndex(), '人岗匹配')
    expect(results.length).toBeGreaterThan(0)
    expect(results.every(item => item.moduleLabel === '人岗匹配')).toBe(true)
  })

  it('忽略大小写与空白', () => {
    expect(searchFeatures(hrIndex(), ' JD ').length).toBe(
      searchFeatures(hrIndex(), 'jd').length,
    )
  })

  it('最多返回 limit 条', () => {
    expect(searchFeatures(hrIndex(), '匹配', 2)).toHaveLength(2)
  })

  it('搜不到时不返回结果，而不是返回全部', () => {
    expect(searchFeatures(hrIndex(), '这个功能不存在')).toEqual([])
  })
})

/*
 * 面板状态机。上一版这里是 `layout-search-timer.test.ts` —— 它在测试体内
 * 又抄了一遍 setTimeout 逻辑再断言自己抄的那份，**根本没有 import 被测实现**，
 * 所以实现怎么改它都绿。这里改成对真实 composable 断言（含面板关闭的防误触延迟）。
 */
describe('useFeatureSearch 面板状态', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  function setup() {
    return useFeatureSearch(ref('HR_SPECIALIST'), ref(HR_PERMISSIONS))
  }

  it('输入命中时展开面板；空白输入收起且清空结果', () => {
    const { keyword, results, showPanel, onSearchInput } = setup()

    keyword.value = '匹配'
    onSearchInput()
    expect(showPanel.value).toBe(true)
    expect(results.value.length).toBeGreaterThan(0)

    keyword.value = '   '
    onSearchInput()
    expect(showPanel.value).toBe(false)
    expect(results.value).toEqual([])
  })

  it('搜不到也展开面板，用于提示「未找到相关功能」', () => {
    const { keyword, results, showPanel, onSearchInput } = setup()
    keyword.value = '不存在的功能'
    onSearchInput()
    expect(showPanel.value).toBe(true)
    expect(results.value).toEqual([])
  })

  it('失焦关闭有延迟：点击结果项的那一下不会被吃掉', () => {
    const { keyword, showPanel, onSearchInput, closeSearchPanel } = setup()
    keyword.value = '匹配'
    onSearchInput()

    closeSearchPanel()
    // 点击结果项在 blur 之后才触发，此刻面板必须还在
    expect(showPanel.value).toBe(true)

    vi.advanceTimersByTime(200)
    expect(showPanel.value).toBe(false)
  })

  it('关闭计时器挂着时再次输入，面板不会被延迟关闭', () => {
    const { keyword, showPanel, onSearchInput, closeSearchPanel } = setup()
    keyword.value = '匹配'
    onSearchInput()

    closeSearchPanel()
    keyword.value = '匹配结果'
    onSearchInput()
    vi.advanceTimersByTime(500)

    expect(showPanel.value).toBe(true)
  })

  it('clearResults 立即收起面板并清空关键字', () => {
    const { keyword, results, showPanel, onSearchInput, clearResults } = setup()
    keyword.value = '匹配'
    onSearchInput()

    clearResults()
    vi.advanceTimersByTime(500)

    expect(showPanel.value).toBe(false)
    expect(results.value).toEqual([])
    expect(keyword.value).toBe('')
  })

  it('索引随权限实时变化：换角色后搜同一关键字的结果跟着变', () => {
    const role = ref('EMPLOYEE')
    const permissions = ref(EMPLOYEE_PERMISSIONS)
    const { keyword, results, onSearchInput } = useFeatureSearch(role, permissions)

    keyword.value = '员工档案'
    onSearchInput()
    expect(results.value).toEqual([])

    role.value = 'HR_SPECIALIST'
    permissions.value = HR_PERMISSIONS
    onSearchInput()
    expect(results.value.map(item => item.path)).toContain('/employee/list')
  })
})
