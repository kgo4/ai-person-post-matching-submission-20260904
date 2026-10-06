package com.example.matching.application.workbench;

import com.example.matching.application.common.SelfScopeSupport;
import com.example.matching.dto.workbench.WorkbenchMetricDelta;
import com.example.matching.dto.workbench.WorkbenchMetricsVO;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.service.employee.EmpEmployeeService;
import com.example.matching.service.workbench.WorkbenchMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工作台指标装配。
 *
 * <p>工作台 KPI 卡上的环比（「较昨日 ↑N」）与团队成员区块都由这里装配：
 * 环比与在手业务量统一来自 {@link WorkbenchMetricsService} 的计数能力，
 * 团队成员来自人员档案真实数据，不引用参考图里的示例内容。</p>
 *
 * <p>设计约束：</p>
 * <ol>
 *   <li>只注入 Service 接口，不注入 Mapper，符合分层架构约束。</li>
 *   <li>环比取不到时返回 {@code null} 字段而非 0，前端隐藏该行，
 *       禁止把"没有基线数据"显示成"↑0%"。</li>
 *   <li>团队成员对"仅本人"调用方一律返回空列表——员工不感知他人档案，
 *       这里的收口不依赖前端是否隐藏区块。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class WorkbenchMetricsApiFacade {

    /** KPI 指标键 → 环比统计口径。键与前端 WorkbenchStat.key 一一对应。 */
    private static final Map<String, String> METRIC_OF_STAT = Map.of(
            "employees", WorkbenchMetricsService.METRIC_EMPLOYEE,
            "posts", WorkbenchMetricsService.METRIC_POST,
            "matching", WorkbenchMetricsService.METRIC_MATCHING_RECORD
    );

    /** 团队成员区块展示条数上限，避免首屏拉全量人员档案。 */
    private static final int MEMBER_LIMIT = 6;

    private final WorkbenchMetricsService workbenchMetricsService;
    private final EmpEmployeeService empEmployeeService;
    private final SelfScopeSupport selfScopeSupport;

    /**
     * 装配当前角色工作台所需的指标数据。
     *
     * @return KPI 环比 + 团队成员；团队成员在员工侧为空列表
     */
    public WorkbenchMetricsVO metrics() {
        List<WorkbenchMetricsVO.TeamMember> members =
                selfScopeSupport.isSelfOnlyCaller() ? List.of() : teamMembers();
        return new WorkbenchMetricsVO(kpiCards(), members);
    }

    /**
     * 按预置的 KPI 键装配环比。
     *
     * <p>环比对象自带当期值，因此不再单独查询存量计数；无环比口径的键在装配
     * 阶段就不会出现在这里，由前端按缺省项渲染为无环比。</p>
     */
    private List<WorkbenchMetricsVO.MetricCard> kpiCards() {
        List<WorkbenchMetricsVO.MetricCard> cards = new ArrayList<>(METRIC_OF_STAT.size());
        METRIC_OF_STAT.forEach((key, metric) -> {
            WorkbenchMetricDelta delta = workbenchMetricsService.dailyDelta(metric);
            cards.add(new WorkbenchMetricsVO.MetricCard(
                    key,
                    delta == null ? null : delta.current(),
                    delta));
        });
        return cards;
    }

    /**
     * 团队成员摘要。
     *
     * <p>取最近建档的启用人员，并标注其近 30 天的匹配记录条数作为在手业务量。
     * 这是管理端协作视角的只读摘要，不涉及写操作，也不替代人员档案页面。</p>
     */
    private List<WorkbenchMetricsVO.TeamMember> teamMembers() {
        List<EmpEmployee> employees = empEmployeeService.lambdaQuery()
                .eq(EmpEmployee::getStatus, 1)
                .orderByDesc(EmpEmployee::getCreatedTime)
                .last("limit " + MEMBER_LIMIT)
                .list();
        if (employees.isEmpty()) {
            return List.of();
        }

        List<Long> empIds = employees.stream().map(EmpEmployee::getId).toList();
        Map<Long, Long> activeCount = workbenchMetricsService.recentMatchingCountByEmployee(empIds);

        List<WorkbenchMetricsVO.TeamMember> members = new ArrayList<>(employees.size());
        for (EmpEmployee employee : employees) {
            long count = activeCount.getOrDefault(employee.getId(), 0L);
            members.add(new WorkbenchMetricsVO.TeamMember(
                    employee.getId(),
                    employee.getRealName(),
                    roleTextOf(employee),
                    count > 0 ? count + " 条匹配记录" : "暂无匹配记录",
                    count > 0
            ));
        }
        return members;
    }

    /** 人员档案的职责文案：优先等级，其次工号，最后兜底。 */
    private String roleTextOf(EmpEmployee employee) {
        if (employee.getLevel() != null && !employee.getLevel().isBlank()) {
            return employee.getLevel();
        }
        return employee.getEmpCode() == null || employee.getEmpCode().isBlank()
                ? "在职员工"
                : employee.getEmpCode();
    }
}
