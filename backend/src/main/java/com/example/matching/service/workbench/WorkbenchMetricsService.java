package com.example.matching.service.workbench;

import com.example.matching.dto.workbench.WorkbenchMetricDelta;

import java.util.List;
import java.util.Map;

/**
 * 工作台指标统计服务。
 *
 * <p>工作台 KPI 卡需要展示「较昨日 ↑N」这类环比，而现有各域统计接口只返回当期的
 * 绝对值。本服务提供统一的"某日新增量 / 对比日新增量"计算能力，供各角色装载器复用，
 * 避免每个角色各写一遍时间窗查询。</p>
 *
 * <p>设计约束：</p>
 * <ol>
 *   <li>只做计数，不返回业务明细，避免把无关数据引入工作台装配链路。</li>
 *   <li>无法计算环比时返回 {@code null}（而非 0），由前端隐藏环比行，
 *       防止把"没有基线数据"显示成"↑0%"。</li>
 * </ol>
 */
public interface WorkbenchMetricsService {

    /** 「在手活跃」判定窗口天数：近 N 天有匹配记录即视为有在手业务。 */
    int ACTIVE_WINDOW_DAYS = 30;

    /**
     * 计算某张表按创建时间统计的日环比。
     *
     * @param metric  指标名（仅用于日志与可读性），见本类常量
     * @return 环比；当对比日为 0 或数据不足以构成基线时返回 null
     */
    WorkbenchMetricDelta dailyDelta(String metric);

    /**
     * 统计一批人员在活跃窗口内的匹配记录条数。
     *
     * <p>供工作台「团队成员」区块标注在手业务量。只做分组计数，
     * 不返回匹配明细；未命中任何记录的人员不会出现在返回值中，
     * 调用方按 0 处理。</p>
     *
     * @param empIds 人员档案 ID 列表；为空时返回空 Map
     * @return empId → 记录条数
     */
    Map<Long, Long> recentMatchingCountByEmployee(List<Long> empIds);

    /** 人员档案新增量日环比。 */
    String METRIC_EMPLOYEE = "employee";

    /** 岗位新增量日环比。 */
    String METRIC_POST = "post";

    /** 匹配记录新增量日环比。 */
    String METRIC_MATCHING_RECORD = "matchingRecord";
}
