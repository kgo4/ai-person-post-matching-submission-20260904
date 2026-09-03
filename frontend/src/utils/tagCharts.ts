import type { EChartsOption } from 'echarts'
import type { AbilityTagUsageStat, AbilityTagRelation } from '@/api/tag-governance'
import { shouldShowBubbleLabel } from '@/views/system/ability-tag/bubbleLabeling'

export const categoryColors: Record<string, string> = {
  TECHNICAL: '#2563eb',
  SOFT: '#059669',
  BUSINESS: '#d97706',
}

export function buildCategoryPieOption(categoryStats: Record<string, number>): EChartsOption {
  const total = categoryStats.TECHNICAL + categoryStats.SOFT + categoryStats.BUSINESS
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: '#47617f' } },
    graphic: [
      { type: 'text', left: 'center', top: '38%', style: { text: String(total), fontSize: 28, fontWeight: 800, fill: '#03152c' } },
      { type: 'text', left: 'center', top: '50%', style: { text: '标签总数', fontSize: 12, fill: '#7690ad' } },
    ],
    series: [{
      type: 'pie',
      radius: ['50%', '72%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      data: [
        { value: categoryStats.TECHNICAL, name: '技术能力', itemStyle: { color: categoryColors.TECHNICAL } },
        { value: categoryStats.SOFT, name: '软技能', itemStyle: { color: categoryColors.SOFT } },
        { value: categoryStats.BUSINESS, name: '业务能力', itemStyle: { color: categoryColors.BUSINESS } },
      ],
    }],
  }
}

export function buildWordCloudOption(
  usageStats: AbilityTagUsageStat[],
): EChartsOption {
  return {
    tooltip: { show: true },
    series: [{
      type: 'wordCloud',
      shape: 'circle',
      keepAspect: false,
      left: 'center',
      top: 'center',
      width: '90%',
      height: '90%',
      sizeRange: [14, 42],
      rotationRange: [-30, 30],
      rotationStep: 15,
      gridSize: 10,
      drawOutOfBound: false,
      shrinkToFit: true,
      layoutAnimation: true,
      textStyle: {
        fontFamily: 'Inter, PingFang SC, Microsoft YaHei, sans-serif',
        fontWeight: 700,
      },
      emphasis: {
        focus: 'self',
        textStyle: { textShadowBlur: 3, textShadowColor: '#333' },
      },
      data: usageStats
        .filter((stat) => hasTagName(stat.tagName))
        .slice(0, 30)
        .map((stat) => ({
          name: stat.tagName!,
          value: Number(stat.heatScore) || 0,
          textStyle: { color: categoryColors[stat.tagCategory || 'TECHNICAL'] || '#2563eb' },
        })),
    }] as any,
  } as EChartsOption
}

export function buildBubbleOption(usageStats: AbilityTagUsageStat[]): EChartsOption {
  const data = usageStats
    .filter((stat) => hasTagName(stat.tagName))
    .slice(0, 20)
    .map((stat, index) => ({
    value: [stat.usedByPostCount || 0, stat.usedByEmpCount || 0, Number(stat.heatScore) || 0],
    name: stat.tagName || '',
    itemStyle: { color: categoryColors[stat.tagCategory || 'TECHNICAL'] || '#2563eb' },
    label: {
      show: shouldShowBubbleLabel(stat, index),
      formatter: stat.tagName || '',
      position: 'top' as const,
      distance: 10,
      fontSize: 11,
      color: '#47617f',
      backgroundColor: 'rgba(255, 255, 255, 0.72)',
      padding: [2, 6],
      borderRadius: 999,
    },
    }))
  return {
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => `${p.name}<br/>岗位引用: ${p.value[0]}<br/>员工引用: ${p.value[1]}<br/>热度: ${p.value[2]}`,
    },
    grid: { left: 50, right: 28, top: 20, bottom: 64 },
    xAxis: { name: '岗位引用', nameTextStyle: { color: '#7690ad' }, axisLine: { lineStyle: { color: '#e2e8f0' } }, axisLabel: { color: '#47617f' } },
    yAxis: { name: '员工引用', nameTextStyle: { color: '#7690ad' }, axisLine: { lineStyle: { color: '#e2e8f0' } }, axisLabel: { color: '#47617f' } },
    series: [{
      type: 'scatter',
      symbolSize: (val: number[]) => Math.max(8, Math.sqrt(val[2]) * 4),
      data,
      emphasis: {
        label: {
          show: true,
          formatter: '{b}',
          position: 'top' as const,
          color: '#03152c',
        },
      },
    }],
  }
}

export function buildGraphOption(relations: AbilityTagRelation[]): EChartsOption {
  const visibleRelations = relations.filter((relation) =>
    (relation.status === 'PENDING' || relation.status === 'CONFIRMED')
      && hasTagName(relation.sourceTagName)
      && hasTagName(relation.targetTagName),
  )
  const tagNameMap = new Map<number, string>()
  for (const rel of visibleRelations) {
    if (rel.sourceTagName) tagNameMap.set(rel.sourceTagId, rel.sourceTagName)
    if (rel.targetTagName) tagNameMap.set(rel.targetTagId, rel.targetTagName)
  }

  const categories = [
    { name: '标签', itemStyle: { color: '#2563eb' } },
  ]
  const nodes = Array.from(tagNameMap.entries()).map(([id, name]) => ({
    id: String(id),
    name,
    symbolSize: 30,
    category: 0,
  }))

  const links = visibleRelations.map(rel => ({
    source: String(rel.sourceTagId),
    target: String(rel.targetTagId),
    lineStyle: {
      type: (rel.status === 'PENDING' ? 'dashed' : 'solid') as 'dashed' | 'solid',
      color: rel.relationType === 'SAME_AS' ? '#2563eb' : '#059669',
      width: rel.status === 'CONFIRMED' ? 2 : 1,
      opacity: 0.7,
    },
  }))

  return {
    tooltip: { trigger: 'item' },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      categories,
      data: nodes,
      links,
      force: { repulsion: 200, edgeLength: 120 },
      label: { show: true, fontSize: 11 },
      lineStyle: { curveness: 0.1 },
    }],
  }
}

function hasTagName(name: string | undefined): name is string {
  return Boolean(name?.trim())
}
