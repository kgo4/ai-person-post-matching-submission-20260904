import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { extractTemplate, stripSfcComments } from '../../utils/sfc-source.ts'

const currentDir = dirname(fileURLToPath(import.meta.url))
const configSource = readFileSync(join(currentDir, '../../config/sidebar-menu.ts'), 'utf8')

/**
 * 断言一律看**剥掉注释后**的源码。
 *
 * 直接扫原文会被注释骗到：本组件的注释里正写着「不再显示系统名
 * 『多源异构岗位与能力图谱』」来记录决策原因，而断言要求这串字
 * 不得出现 —— 于是代码越正确、注释越详细，测试越红（本文件踩过）。
 */
const source = stripSfcComments(readFileSync(join(currentDir, 'components/AppSidebar.vue'), 'utf8'))

/** 只取 `<template>` 段，用于「界面上是否真的渲染了某个东西」的断言 */
const templateSource = extractTemplate(source)

assert.equal(source.includes('const modules = computed'), true)
assert.equal(source.includes('getSidebarModules()'), true)
// 角色 + 权限双重过滤统一由 config/sidebar-menu.filterSidebarModules 提供，
// 侧边栏组件不再自己实现一份过滤规则（避免两处规则漂移）。
assert.equal(source.includes('filterSidebarModules'), true)
assert.equal(source.includes('const activeChildren = computed'), true)
assert.equal(source.includes('class="sidebar-subnav"'), true)
// 早期版本的角色切换 tab（sidebar-intro）已随参考图版式移除，这里断言它不会被重新引入
assert.equal(source.includes('class="sidebar-intro"'), false)

/* ============ 侧边栏结构与职责（2026-09-04 两轮调整后的现状） ============
 * 第一轮：删掉 300px 毛玻璃面板 + 深浅色切换 + 收起导航 + 左下角账号块，
 *         统一为「固定 200px 白色导航」，账号入口全部收进顶栏下拉。
 * 第二轮（本文件当前断言）：账号入口重新分工 ——
 *         顶栏右上角只剩头像（点击进个人中心），
 *         身份展示与退出登录回到侧边栏底部。
 *         「同一件事只有一个入口」不变式仍然成立：两个入口职责不重叠。 */

assert.equal(source.includes('layout-sidebar--collapsed'), false, '侧边栏折叠能力已移除')
assert.equal(/\.layout-sidebar \{[^}]*width: 300px/.test(source), false, '旧的 300px 毛玻璃面板样式应已移除')
assert.equal(source.includes('backdrop-filter'), false, '旧的毛玻璃面板样式应已移除')
assert.equal(source.includes('sidebar-theme-toggle'), false, '深浅模式切换已移除')
assert.equal(source.includes('sidebar-collapse-btn'), false, '收起导航按钮已移除')
// 旧的 class 名 'sidebar-user' 不复用：新的底部账号区叫 sidebar-account，
// 断言旧名不存在可以防止有人把老样式块（含药丸形容器）粘回来。
assert.equal(source.includes('sidebar-user'), false, '不复用旧的 sidebar-user 类名')

// 现版式：固定白色导航面板
assert.match(source, /\.layout-sidebar \{[^}]*position: sticky[^}]*top: 0[^}]*width: 200px/)

// 品牌图标：2026-09-04 起用品牌 Logo 图片（public/logo-mark.png），不再是 CSS 画的两个旋转方块。
// 现为正方形 40px，按 1x/2x/3x 预渲染并走 srcset —— 交一张大图让浏览器自己缩，滤波质量更差（曾发糊）。
assert.equal(source.includes('/logo-mark.png'), true, '侧边栏品牌图标应使用 Logo 图片')
assert.equal(source.includes('/logo-mark@2x.png'), true, '应提供 2x 图并走 srcset')
assert.equal(source.includes('/logo-mark@3x.png'), true, '应提供 3x 图并走 srcset')
assert.equal(source.includes('<span></span><i></i>'), false, '旧的 CSS 方块图标应已移除')
assert.match(source, /\.sidebar-logo-icon \{[^}]*width: 40px[^}]*height: 40px/)

/* 顶部品牌块：只显示图标 + 团队名（小字）。
 * 系统名「多源异构岗位与能力图谱」已移除 —— 200px 栏宽里 12 个汉字必然折行；
 * 系统名的展示由登录页承担。 */
assert.equal(templateSource.includes('sidebar-logo-icon'), true, '顶部应显示品牌图标')
assert.equal(templateSource.includes('sidebar-logo-team'), true, '图标下方应显示团队名')
assert.equal(templateSource.includes('KGOAIspace'), true, '团队名应保留')
assert.equal(templateSource.includes('多源异构岗位与能力图谱'), false, '侧边栏不再渲染系统名')
assert.equal(source.includes('sidebar-logo-system'), false, '系统名样式块应已移除')

/* 底部账号区：头像 + 姓名 + 角色，点击只弹退出登录。
 * 个人中心由顶栏头像进入，这里不得再放一个（否则又变成两处都能进）。 */
assert.equal(source.includes('sidebar-account'), true, '底部应有账号区')
assert.equal(source.includes('sidebar-account__avatar'), true, '账号区应显示头像')
assert.equal(source.includes('{{ userName }}'), true, '账号区应显示用户名')
assert.equal(source.includes('{{ userRoleLabel }}'), true, '用户名下方应显示角色')
assert.equal(source.includes('退出登录'), true, '底部账号区应提供退出登录')
assert.equal(source.includes("'/profile'"), false, '侧边栏不应再有个人中心入口（由顶栏头像负责）')
assert.equal(source.includes('defineEmits'), true, '退出登录需向上抛事件给布局页')
// 角色文案必须走统一映射，不能在本组件里再写死一份
assert.equal(source.includes("from '@/utils/role-label'"), true, '角色文案应复用 utils/role-label')

// 导航文案仍要展示模块名（summary 只是配置里的元数据，早已不渲染）
assert.equal(source.includes('{{ item.label }}'), true)
assert.equal(source.includes('{{ item.summary }}'), false)

const employeeModule = configSource.slice(configSource.indexOf("key: 'employee'"), configSource.indexOf("key: 'post'"))
assert.equal(employeeModule.includes("path: '/employee/ability-profile'"), true)
assert.equal(employeeModule.includes("path: '/employee/list'"), true)
// 员工在能力画像模块里看到的是“我的能力”，管理端命名只对 HR 开放
assert.equal(employeeModule.includes("label: '我的能力'"), true)
assert.equal(employeeModule.includes("roles: ['EMPLOYEE']"), true)

console.log('enterprise sidebar tests passed')
