import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
// 本文件在 frontend/src/ 下；nginx.conf 在 frontend/ 下
const frontendDir = join(currentDir, '..')
const nginxConf = readFileSync(join(frontendDir, 'nginx.conf'), 'utf8')

/*
 * /uploads/ 必须用 `^~` 前缀匹配，否则上传的图片在生产环境全部 404。
 *
 * 回归背景（2026-09-04）：线上"上传头像成功后不显示"，数据库里路径正确、文件也在磁盘上，
 * 但 <img src="/uploads/avatars/xxx.png"> 拿到 404。原因是 nginx 的匹配优先级：
 *
 *   精确 `=`  →  `^~` 前缀  →  正则 `~` / `~*`  →  普通前缀
 *
 * 配置里有一条 `location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|...)$` 给静态资源加缓存头，
 * 而 `location /uploads/` 是普通前缀 —— 于是 .png/.jpg 的请求先被正则 location 截走，
 * 去 SPA 的 html 根目录（/usr/share/nginx/html/uploads/...）找文件，必然 404。
 *
 * 加 `^~` 后：命中最长前缀就跳过正则检查，请求才会真正 proxy_pass 到后端。
 * 这个 bug 同样影响学习资源封面等一切走 /uploads/ 的图片，所以必须守住。
 */
const uploadsLocations = nginxConf.match(/location\s+\S*\s*\/uploads\/\s*\{/g) ?? []
assert.ok(uploadsLocations.length > 0, 'nginx.conf 里必须有 /uploads/ 的 location')

for (const block of uploadsLocations) {
  assert.ok(
    block.includes('^~'),
    `location /uploads/ 必须写成 "location ^~ /uploads/ { "，当前是 "${block.trim()}" —— ` +
      '否则会被下面的静态资源正则 location 抢先匹配，上传的图片全部 404',
  )
}

// 反向校验：确实存在那条会抢匹配的正则 location，说明这条约束不是多余的
assert.match(
  nginxConf,
  /location ~\* \\?\.\(js\|css\|png\|jpg\|jpeg/,
  '静态资源正则 location 应仍然存在（它正是 ^~ 要规避的匹配顺序问题）',
)

// 后端静态资源映射要求 /uploads/** 存在（SecurityConfig 里 permitAll），双端一致
assert.match(nginxConf, /location \^~ \/uploads\/ \{[\s\S]*?proxy_pass http:\/\/backend:8080;/,
  '/uploads/ 必须 proxy_pass 到后端')

console.log('nginx uploads routing tests passed')
