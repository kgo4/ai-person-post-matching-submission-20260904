/**
 * 工作台 API。
 *
 * 只提供工作台首屏需要的聚合读模型：KPI 卡环比 + 团队成员。
 * 各角色的业务明细仍走各自域的接口，工作台不重复暴露业务入口。
 */
import { get } from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

/** 指标环比，对应 KPI 卡右下角的「较昨日 ↑12」 */
export interface WorkbenchMetricDeltaVO {
  current: number
  previous: number
  /** up=增长 down=下降 flat=持平 */
  direction: 'up' | 'down' | 'flat'
  diff: number
  /** 对比期文案，例如「较昨日」 */
  basis: string
}

/** 单张 KPI 卡的指标数据 */
export interface WorkbenchMetricCardVO {
  /** 指标键，与前端 WorkbenchStat.key 对应 */
  key: string
  /** 当期值；null 表示取不到 */
  value: number | null
  /** 环比；null 表示无基线数据，前端隐藏环比行 */
  delta: WorkbenchMetricDeltaVO | null
}

/** 团队成员摘要 */
export interface WorkbenchTeamMemberVO {
  id: number
  name: string
  role: string
  meta: string
  active: boolean
}

export interface WorkbenchMetricsVO {
  cards: WorkbenchMetricCardVO[]
  members: WorkbenchTeamMemberVO[]
}

/**
 * 获取当前角色工作台指标。
 *
 * 员工角色后端不返回团队成员（members 为空数组），
 * 视图层按空区块处理，不做本地补数据。
 */
export function getWorkbenchMetrics(): Promise<ApiResponse<WorkbenchMetricsVO>> {
  return get<WorkbenchMetricsVO>('/workbench/metrics')
}
