/**
 * 材料库展示口径（纯函数，无副作用，便于单测）。
 *
 * 为什么单独抽出来：索引状态 → 界面文案/颜色的映射，材料库列表、上传结果提示、
 * 以及后续任何需要展示「这份材料能不能用来解析」的地方都要用。
 * 散落在组件里会很快分叉出「列表说索引失败、按钮却允许解析」这类自相矛盾的界面。
 */

/** 材料能否参与趋势解析 —— 与后端 `readyForAnalysis` 同一判据，前端只做展示兜底 */
export function isMaterialReady(readyForAnalysis: boolean | undefined): boolean {
  return readyForAnalysis === true
}

export type MaterialTagType = 'success' | 'warning' | 'danger' | 'info'

export interface MaterialStatusMeta {
  /** 标签文案 */
  label: string
  /** Element Plus 标签色型 */
  type: MaterialTagType
  /** 悬浮/下方说明：讲清「为什么是这个状态、能不能用」 */
  hint: string
}

/**
 * 索引状态 → 展示口径。
 *
 * 注意 `INDEXED` 也要看 `readyForAnalysis`：索引成功但片段数为 0（扫描件、纯图片 PDF）
 * 时，后端会把 `readyForAnalysis` 置为 false。这时界面上不能显示成「可用」，
 * 否则管理员点「开始解析」会拿到空结果，还以为是模型没抽出来。
 */
export function materialStatusMeta(
  indexStatus: string | undefined,
  readyForAnalysis: boolean | undefined
): MaterialStatusMeta {
  if (indexStatus === 'FAILED') {
    return {
      label: '索引失败',
      type: 'danger',
      hint: '这份材料没能建立检索索引，无法参与解析；可点「重建索引」重试',
    }
  }
  if (indexStatus === 'PENDING' || !indexStatus) {
    return {
      label: '待索引',
      type: 'warning',
      hint: '材料已入库但还没建立检索索引，可点「重建索引」立即处理',
    }
  }
  if (!isMaterialReady(readyForAnalysis)) {
    return {
      label: '无可用片段',
      type: 'warning',
      hint: '索引完成但没有提取到文本片段（常见于扫描件或纯图片 PDF），需要先 OCR 再上传',
    }
  }
  return {
    label: '可解析',
    type: 'success',
    hint: '已建立检索索引，可用于岗位趋势解析',
  }
}

/** 「加入本次解析」按不出可为空的原因，便于直接展示成按钮的 tooltip */
export function pickBlockReason(material: {
  readyForAnalysis?: boolean
  ephemeral?: boolean
}): string {
  if (material.ephemeral) {
    return '这是仅试算材料，不会被复用给新的解析，请重新上传'
  }
  if (!isMaterialReady(material.readyForAnalysis)) {
    return '这份材料没有可用的检索片段，无法参与解析；可先重建索引或补充 OCR 文本'
  }
  return ''
}

/** 材料是否已经在本轮待提交清单里 */
export function isAlreadyPicked(documentId: number, pendingIds: readonly number[]): boolean {
  return pendingIds.includes(documentId)
}

/** 上传时间展示：后端返回 ISO 串，截到分钟即可，不需要秒级精度 */
export function formatUploadedTime(value?: string | null): string {
  if (!value) return '—'
  return value.length >= 16 ? value.substring(0, 16).replace('T', ' ') : value
}
