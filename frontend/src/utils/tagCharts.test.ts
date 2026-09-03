import { describe, expect, it } from 'vitest'
import { buildBubbleOption, buildGraphOption } from './tagCharts'

describe('tag charts', () => {
  it('excludes unnamed usage statistics from the bubble chart', () => {
    const option = buildBubbleOption([
      { id: 1, tagId: 1, tagName: 'Java', usedByPostCount: 3, usedByEmpCount: 2, heatScore: 8, statDate: '2026-08-16' },
      { id: 2, tagId: 2, tagName: '  ', usedByPostCount: 9, usedByEmpCount: 8, heatScore: 25, statDate: '2026-08-16' },
    ])

    expect((option.series as any[])[0].data).toHaveLength(1)
    expect((option.series as any[])[0].data[0].name).toBe('Java')
  })

  it('only draws active relations whose two labels can be resolved', () => {
    const option = buildGraphOption([
      { id: 1, sourceTagId: 1, targetTagId: 2, sourceTagName: 'Java', targetTagName: 'Spring', relationType: 'SIMILAR', similarityScore: 0.9, status: 'CONFIRMED', evidenceSource: '', remark: '', createdTime: '2026-08-16' },
      { id: 2, sourceTagId: 3, targetTagId: 4, sourceTagName: '', targetTagName: 'Vue', relationType: 'SIMILAR', similarityScore: 0.8, status: 'PENDING', evidenceSource: '', remark: '', createdTime: '2026-08-16' },
      { id: 3, sourceTagId: 5, targetTagId: 6, sourceTagName: 'Go', targetTagName: 'Rust', relationType: 'SIMILAR', similarityScore: 0.8, status: 'REJECTED', evidenceSource: '', remark: '', createdTime: '2026-08-16' },
    ])
    const series = (option.series as any[])[0]

    expect(series.data.map((node: any) => node.name)).toEqual(['Java', 'Spring'])
    expect(series.links).toHaveLength(1)
  })
})
