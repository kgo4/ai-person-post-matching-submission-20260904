/**
 * 源码契约测试工具：剥注释 + 取片段。
 *
 * 背景：契约测试会用 `includes()/toContain()` 断言「某个东西已经不存在了」，
 * 而项目约定又要求把**决策原因**写在被删元素旁边 —— 于是注释里会自然地出现
 * 被禁止的字符串，直接扫原文就会被自己的说明文字误伤：
 * **代码越对、注释越详尽，测试越红**。
 *
 * 已踩过三次，故统一收在这里：
 *   1. `AppSidebar.vue` 注释「不再显示系统名『多源异构岗位与能力图谱』」；
 *   2. `AppTopbar.vue` 同类；
 *   3. `matching-tasks.ts` 注释「不再写死 hasPermission('MATCHING:READ')」。
 *
 * 对 `.vue` / `.ts` / `.js` 源码一律适用（都是 HTML + 块 + 行三种注释）。
 */

/**
 * 去掉注释后的源码。
 *
 * 处理顺序不可颠倒：必须先去掉块注释再处理行注释，否则块注释内部的 `//`
 * 会把它后面的代码一起吃掉。
 *
 * 行注释用负向后行断言排除 `://`，避免误删 URL（注释里出现过 `https://...`
 * 与 `/uploads/**` 这类写法）。
 */
export function stripSfcComments(input: string): string {
  return input
    // HTML 注释（.vue 模板段）—— 最容易骗到断言的正是这种
    .replace(/<!--[\s\S]*?-->/g, '')
    // 块注释（脚本 / 样式段）
    .replace(/\/\*[\s\S]*?\*\//g, '')
    // 行注释；`(?<!:)` 保证 `https://`、`site://` 不被误伤
    .replace(/(?<!:)\/\/.*$/gm, '')
}

/**
 * 取出 `<template>` 段的源码（comments 已剥离）。
 *
 * 用 `lastIndexOf('</template>')` 而不是 `indexOf`：模板里只要有嵌套的
 * `<template v-else>` / `<template #dropdown>`，`indexOf` 就会在**第一个**
 * 闭合标签处截断，切片悄悄变短 —— 断言可能因为"内容没被切到"而假通过。
 */
export function extractTemplate(sfcSource: string): string {
  const start = sfcSource.indexOf('<template>')
  const end = sfcSource.lastIndexOf('</template>')
  if (start === -1 || end === -1) return ''
  return sfcSource.slice(start, end)
}
