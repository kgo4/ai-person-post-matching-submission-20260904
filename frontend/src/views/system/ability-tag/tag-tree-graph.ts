import type { EChartsOption } from 'echarts'
import type { AbilityTagTreeVO } from '@/api'
import { categoryColors } from '@/utils/tagCharts'

type TagTreeGraphNode = {
  id: string
  value: number
  name: string
  children: TagTreeGraphNode[]
  itemStyle: { color: string }
  symbolSize: number
}

export function buildTagTreeGraphOption(treeData: AbilityTagTreeVO[]): EChartsOption {
  return {
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        const node = params.data as TagTreeGraphNode
        return `${node.name}<br/>子标签：${node.children.length}`
      },
    },
    series: [{
      type: 'tree',
      data: treeData.map(toGraphNode),
      top: 28,
      left: 28,
      bottom: 28,
      right: 120,
      layout: 'orthogonal',
      orient: 'LR',
      roam: true,
      expandAndCollapse: true,
      initialTreeDepth: 2,
      symbol: 'circle',
      lineStyle: { color: '#cbd5e1', width: 1.5, curveness: 0.2 },
      label: {
        position: 'left',
        verticalAlign: 'middle',
        align: 'right',
        color: '#20364f',
        fontSize: 12,
        formatter: '{b}',
      },
      leaves: {
        label: { position: 'right', align: 'left' },
      },
      emphasis: { focus: 'descendant' },
      animationDurationUpdate: 250,
    }],
  }
}

function toGraphNode(tag: AbilityTagTreeVO): TagTreeGraphNode {
  const children = (tag.children || []).map(toGraphNode)
  return {
    id: String(tag.id),
    value: tag.id,
    name: tag.tagName?.trim() || `标签 #${tag.id}`,
    children,
    itemStyle: { color: categoryColors[tag.tagCategory] || '#64748b' },
    symbolSize: children.length > 0 ? 14 : 10,
  }
}
