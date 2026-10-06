export type ChartContainerLike = {
  clientWidth: number
  clientHeight: number
}

export function canInitializeChart(container: ChartContainerLike | null | undefined): boolean {
  if (!container) return false
  return container.clientWidth > 0 && container.clientHeight > 0
}
