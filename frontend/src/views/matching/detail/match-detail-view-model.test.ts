import { describe, expect, it } from 'vitest'
import {
  buildScoreParts,
  getAiScoreEmptyText,
  parseHardConditionDetails,
  getMatchStatusText,
  getApprovalStatusText,
  getScreeningLevelText,
  isPassed,
  getGapText,
  getRiskLevelType,
} from './match-detail-view-model'
import type { MatchingRecord } from '@/api/matching/types'

describe('match-detail-view-model', () => {
  it('buildScoreParts: Milvus 不可用时语义分显示占位文本', () => {
    const record = {
      postModelScore: 80,
      vectorScore: null,
      profileSemanticScore: null,
      evidenceScore: 60,
      aiScore: null,
      llmScore: null,
      finalMatchScore: 75,
      screeningLevel: 2,
    } as unknown as MatchingRecord

    const parts = buildScoreParts(record)

    expect(parts).toHaveLength(5)
    const semantic = parts.find((p) => p.label === '整体语义分')!
    expect(semantic.emptyText).toBe('Milvus 不可用')
  })

  it('buildScoreParts: AI 未触发时显示未触发', () => {
    const record = {
      postModelScore: 80,
      evidenceScore: 60,
      aiScore: null,
      finalMatchScore: 75,
      screeningLevel: 2,
    } as unknown as MatchingRecord

    const parts = buildScoreParts(record)
    const ai = parts.find((p) => p.label === 'AI 建议分')!
    expect(ai.emptyText).toBe('未触发')
  })

  it('buildScoreParts: 空记录返回空数组', () => {
    expect(buildScoreParts(null)).toEqual([])
    expect(buildScoreParts(undefined)).toEqual([])
  })

  it('parseHardConditionDetails: 解析 JSON 详情', () => {
    const details = parseHardConditionDetails(
      JSON.stringify({ details: [{ name: '学历', passed: true }] }),
    )
    expect(details).toHaveLength(1)
    expect(details[0].name).toBe('学历')
  })

  it('parseHardConditionDetails: 空/畸形输入返回空数组', () => {
    expect(parseHardConditionDetails(null)).toEqual([])
    expect(parseHardConditionDetails('not-json')).toEqual([])
  })

  it('getMatchStatusText / getApprovalStatusText 提供可读文案', () => {
    expect(getMatchStatusText(90)).toBeTruthy()
    expect(getApprovalStatusText('APPROVED')).toBe('已通过')
  })

  it('getScreeningLevelText 区分 L1/L2/L3', () => {
    expect(getScreeningLevelText(1)).toContain('硬条件')
    expect(getScreeningLevelText(2)).toContain('量化')
    expect(getScreeningLevelText(3)).toContain('AI')
  })

  it('isPassed / getGapText / getRiskLevelType 容错', () => {
    expect(isPassed({ passed: true })).toBe(true)
    expect(isPassed({})).toBe(false)
    expect(getGapText(2)).toContain('差距')
    expect(getGapText(-1)).toBe('已达标')
    expect(getRiskLevelType('HIGH')).toBe('danger')
  })
})
