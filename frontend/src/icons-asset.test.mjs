/**
 * 站点图标与品牌图的尺寸契约测试。
 *
 * 为什么需要它：图标糊掉的根因不是「图不够大」，而是**导错了尺寸** ——
 *   ① 只给一张 32×32，浏览器在 16px 场景只能自己降采样；
 *   ② 把 848px 的大图塞进 44px 的框，让浏览器实时缩 19 倍，滤波质量不如预先导出；
 *   ③ 把横版图形硬塞进正方形，主体只剩一小块。
 * 这些都是「肉眼才能发现」的问题，所以把尺寸钉成断言。
 *
 * 纯本地读取文件字节头，不依赖任何图像库。
 */
import { readFileSync, existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'

const here = dirname(fileURLToPath(import.meta.url))
const publicDir = join(here, '..', 'public')
const indexPath = join(here, '..', 'index.html')

/** 读取 PNG 的宽高与颜色类型（IHDR 位于固定偏移） */
function readPng(file) {
  const buf = readFileSync(file)
  const signature = buf.subarray(0, 8).toString('hex')
  assert.equal(signature, '89504e470d0a1a0a', `${file} 不是合法 PNG`)
  assert.equal(buf.subarray(12, 16).toString('ascii'), 'IHDR', `${file} 缺少 IHDR`)
  return {
    width: buf.readUInt32BE(16),
    height: buf.readUInt32BE(20),
    // 0 灰度 / 2 RGB / 3 调色板 / 4 灰度+alpha / 6 RGBA
    colorType: buf[25],
  }
}

/** 读取 ICO 内嵌的各帧尺寸（宽高字节为 0 表示 256） */
function readIcoSizes(file) {
  const buf = readFileSync(file)
  assert.equal(buf.readUInt16LE(0), 0, `${file} ICO 保留位应为 0`)
  assert.equal(buf.readUInt16LE(2), 1, `${file} 应为 ICO 类型(1)`)
  const count = buf.readUInt16LE(4)
  const sizes = []
  for (let i = 0; i < count; i++) {
    const entry = 6 + i * 16
    sizes.push([buf[entry] || 256, buf[entry + 1] || 256])
  }
  return sizes
}

/* ------------------------- index.html 的引用 ------------------------- */

const indexHtml = readFileSync(indexPath, 'utf8')

for (const href of ['/favicon-16.png', '/favicon-32.png', '/favicon.ico', '/apple-touch-icon.png']) {
  assert.equal(indexHtml.includes(href), true, `index.html 应引用 ${href}`)
}
// 上一版只给了一张 32×32 的 favicon.png，就是模糊的来源之一，不应回退
assert.equal(indexHtml.includes('/favicon.png"'), false, '不应再引用单一的 favicon.png')
assert.equal(indexHtml.includes('favicon.svg'), false, '已移除的 favicon.svg 不应再被引用')

/* ------------------------- 文件存在性与像素尺寸 ------------------------- */

const expectedPng = {
  'favicon-16.png': [16, 16],
  'favicon-32.png': [32, 32],
  'apple-touch-icon.png': [180, 180],
  'logo-mark.png': [40, 40],
  'logo-mark@2x.png': [80, 80],
  'logo-mark@3x.png': [120, 120],
}

for (const [name, [w, h]] of Object.entries(expectedPng)) {
  const file = join(publicDir, name)
  assert.equal(existsSync(file), true, `缺少图标文件 public/${name}`)
  const png = readPng(file)
  assert.deepEqual(
    [png.width, png.height],
    [w, h],
    `public/${name} 应为 ${w}×${h}，实际 ${png.width}×${png.height}`,
  )
}

/* ------------------------- apple-touch-icon 必须不透明 ------------------------- */

// iOS 会把透明区域填成黑色：若这里出现 alpha 通道（colorType 4/6），
// 主屏图标会出现黑底或黑边 —— 这是「导出了透明版」的典型翻车方式。
const apple = readPng(join(publicDir, 'apple-touch-icon.png'))
assert.ok(
  apple.colorType === 2 || apple.colorType === 3,
  `apple-touch-icon 不能带透明通道（colorType=${apple.colorType}），iOS 会填黑`,
)

/* ------------------------- .ico 必须是多尺寸合一 ------------------------- */

const icoSizes = readIcoSizes(join(publicDir, 'favicon.ico'))
for (const [w, h] of [[16, 16], [32, 32], [48, 48]]) {
  assert.ok(
    icoSizes.some(([iw, ih]) => iw === w && ih === h),
    `favicon.ico 应内嵌 ${w}×${h}，实际包含 ${JSON.stringify(icoSizes)}`,
  )
}

console.log('icons-asset: 站点图标尺寸契约全部通过')
