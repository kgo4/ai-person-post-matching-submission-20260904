import { expect, it } from 'vitest'
import { buildTagTreeGraphOption } from './tag-tree-graph'

it('converts tag hierarchy into a clickable ECharts tree', () => {
  const option = buildTagTreeGraphOption([
    {
      id: 1,
      tagCode: 'ENGINEERING',
      tagName: 'Engineering',
      tagCategory: 'TECHNICAL',
      tagLevel: 0,
      children: [
        { id: 2, tagCode: 'JAVA', tagName: 'Java', tagCategory: 'TECHNICAL', tagLevel: 1, children: [] },
      ],
    },
  ])
  const series = option.series as any[]

  expect(series[0].type).toBe('tree')
  expect(series[0].data[0].children[0]).toMatchObject({
    id: '2',
    value: 2,
    itemStyle: { color: '#2563eb' },
  })
})

it('renders a stable fallback name for an invalid historical tag', () => {
  const option = buildTagTreeGraphOption([
    { id: 9, tagCode: 'INVALID', tagName: ' ', tagCategory: 'TECHNICAL', tagLevel: 0, children: [] },
  ])

  expect((option.series as any[])[0].data[0].name).toBe('标签 #9')
})
