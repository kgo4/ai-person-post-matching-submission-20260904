package com.example.matching.service.workbench.impl;

import com.example.matching.dto.workbench.WorkbenchMetricDelta;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.entity.post.PostPost;
import com.example.matching.service.employee.EmpEmployeeService;
import com.example.matching.service.matching.MatchingRecordService;
import com.example.matching.service.post.PostPostService;
import com.example.matching.service.workbench.WorkbenchMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 工作台指标环比服务实现。
 *
 * <p>统一按"自然日新增量"计算：本期为今天 00:00 至今，对比期为昨天整日。
 * 之所以用新增量而非存量环比，是因为存量（如人员总数）日间变化幅度小，且基数为 0 时
 * 百分比无意义；新增量能真实反映当日业务活跃度，与工作台"较昨日 ↑N"的语义一致。</p>
 *
 * @see WorkbenchMetricsService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkbenchMetricsServiceImpl implements WorkbenchMetricsService {

    private final EmpEmployeeService empEmployeeService;
    private final PostPostService postPostService;
    private final MatchingRecordService matchingRecordService;

    @Override
    public WorkbenchMetricDelta dailyDelta(String metric) {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();

        Long current = countCreated(metric, todayStart, null);
        Long previous = countCreated(metric, yesterdayStart, todayStart);
        return WorkbenchMetricDelta.of(current, previous);
    }

    /**
     * 统计某时间窗内的新增条数。
     *
     * @param from 起点（含）
     * @param to   终点（不含），null 表示不设上界
     * @return 条数；未知指标返回 null，由上层隐藏该卡的环比行
     */
    private Long countCreated(String metric, LocalDateTime from, LocalDateTime to) {
        boolean hasUpperBound = to != null;
        switch (metric) {
            case METRIC_EMPLOYEE:
                return empEmployeeService.lambdaQuery()
                        .ge(EmpEmployee::getCreatedTime, from)
                        .lt(hasUpperBound, EmpEmployee::getCreatedTime, to)
                        .count();
            case METRIC_POST:
                return postPostService.lambdaQuery()
                        .ge(PostPost::getCreatedTime, from)
                        .lt(hasUpperBound, PostPost::getCreatedTime, to)
                        .count();
            case METRIC_MATCHING_RECORD:
                return matchingRecordService.lambdaQuery()
                        .ge(MatchingRecord::getCreatedTime, from)
                        .lt(hasUpperBound, MatchingRecord::getCreatedTime, to)
                        .eq(MatchingRecord::getIsDeleted, 0)
                        .count();
            default:
                // 未知指标不抛异常：工作台是聚合展示页，单个指标不可用不应导致整页失败，
                // 返回 null 让该卡片隐藏环比行即可。
                log.warn("未知的工作台指标环比类型: {}", metric);
                return null;
        }
    }

    @Override
    public Map<Long, Long> recentMatchingCountByEmployee(List<Long> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return Map.of();
        }
        LocalDateTime since = LocalDateTime.now().minusDays(ACTIVE_WINDOW_DAYS);
        // 只 select 主键与 empId：分组计数不需要匹配明细，
        // 避免把 JSON 报告等大字段拉进工作台首屏。
        List<MatchingRecord> rows = matchingRecordService.lambdaQuery()
                .select(MatchingRecord::getId, MatchingRecord::getEmpId)
                .in(MatchingRecord::getEmpId, empIds)
                .ge(MatchingRecord::getCreatedTime, since)
                .eq(MatchingRecord::getIsDeleted, 0)
                .list();
        if (rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream()
                .map(MatchingRecord::getEmpId)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(empId -> empId, Collectors.counting()));
    }
}


