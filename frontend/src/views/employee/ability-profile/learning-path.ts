/**
 * 「学习路径建议」展示归一化（纯逻辑，独立于组件便于测试）。
 *
 * <p><b>背景（2026-09-04 员工反馈「学习路径建议显示成了原始 json」）</b>：
 * 该字段在后端是**结构化对象数组** —— `LearningPathSuggestion`
 * `{ tagId, abilityName, currentLevel, targetLevel, suggestion, priority }`，
 * 而报告组件把它当成 `string[]` 直接插值渲染，于是每个元素都被当作对象打印出来
 * （能力名、等级、建议全部挤在一坨里，员工看到的就是原始 JSON 片段）。
 *
 * <p>这里只做一件事：把「任意形状的 learningPathSuggestions」归一化成可渲染条目，
 * 且**永不把原始 JSON 透给用户**；同时兼容 AI 只给一句话（字符串元素）的降级形态 ——
 * 「解析不出来」也不能变成「把脏数据糊到脸上」。
 */

export interface LearningPathEntry {
  /** 能力名；字符串形态（AI 只给了一句话）时为 null */
  abilityName: string | null
  /** 当前等级（1-5）；取不到为 null */
  currentLevel: number | null
  /** 目标等级（1-5）；取不到为 null */
  targetLevel: number | null
  /** 建议正文 */
  suggestion: string
  /** 优先级原文（未识别的值原样保留，便于排查） */
  priority: string | null
}

/** 宽松取数字：兼容字符串型等级；取不到返回 null（不回落 0，0 会被读成「L0」） */
function toLevel(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (trimmed !== '' && Number.isFinite(Number(trimmed))) return Number(trimmed)
  }
  return null
}

/** 宽松取文本：空串按「没有」处理，避免渲染出一行空白 */
function toText(value: unknown): string | null {
  if (typeof value === 'string') {
    const trimmed = value.trim()
    return trimmed === '' ? null : trimmed
  }
  if (typeof value === 'number' && Number.isFinite(value)) return String(value)
  return null
}

/**
 * 归一化学习路径建议。
 *
 * @param raw JSON.parse 之后的任意值（可能是 null / 对象 / 数组 / 脏数据）
 * @returns 可渲染条目；无法识别的元素直接丢弃，而不是渲染成「{}」
 */
export function normalizeLearningPath(raw: unknown): LearningPathEntry[] {
  if (!Array.isArray(raw)) {
    return []
  }
  const entries: LearningPathEntry[] = []
  for (const item of raw) {
    // 形态一：AI 只给了一句话（**纯字符串元素**）—— 保留为纯文本条目。
    // 注意这里只认真正的 string：`0`、`false` 这类标量元素是脏数据，
    // 走 toText 会被转成「0」渲染成一条莫名其妙的建议。
    if (typeof item === 'string') {
      const plain = item.trim()
      if (plain === '') {
        continue
      }
      entries.push({
        abilityName: null,
        currentLevel: null,
        targetLevel: null,
        suggestion: plain,
        priority: null,
      })
      continue
    }
    if (!item || typeof item !== 'object' || Array.isArray(item)) {
      continue
    }
    const record = item as Record<string, unknown>
    const abilityName = toText(record.abilityName) ?? toText(record.ability)
    const suggestion = toText(record.suggestion)
      ?? toText(record.content)
      ?? toText(record.text)
      ?? ''
    // 既没能力名也没建议 → 脏数据（例如 `{}`），丢弃，别渲染成一行空
    if (!abilityName && !suggestion) {
      continue
    }
    entries.push({
      abilityName,
      currentLevel: toLevel(record.currentLevel),
      targetLevel: toLevel(record.targetLevel),
      suggestion,
      priority: toText(record.priority),
    })
  }
  return entries
}

export interface LearningPathPriorityMeta {
  text: string
  tagType: 'danger' | 'warning' | 'info'
}

/**
 * 优先级 → 中文标签 + 颜色。
 *
 * <p>后端约定 `HIGH / MEDIUM / LOW`，但 AI 也可能给出 `P0` 或中文；
 * 未识别的值**回显原文**（而不是吞掉）—— 展示错总比静默丢失信息好排查。
 */
export function learningPathPriorityMeta(priority?: string | null): LearningPathPriorityMeta | null {
  const raw = (priority ?? '').trim()
  if (!raw) {
    return null
  }
  const key = raw.toUpperCase()
  if (key === 'HIGH' || key === 'P0' || raw === '高') {
    return { text: '高优先级', tagType: 'danger' }
  }
  if (key === 'MEDIUM' || key === 'P1' || raw === '中') {
    return { text: '中优先级', tagType: 'warning' }
  }
  if (key === 'LOW' || key === 'P2' || raw === '低') {
    return { text: '低优先级', tagType: 'info' }
  }
  return { text: raw, tagType: 'info' }
}
