package com.example.matching.dto.matching;

import com.example.matching.entity.matching.MatchingRecord;

import java.util.List;
import java.util.Map;

/**
 * Dashboard 统计快照（不可变 DTO）
 * <p>
 * 用于 Redis 缓存的目标类型。相比 {@code Map<String, Object>}，
 * 各字段类型明确，反序列化不会退化为 LinkedHashMap。
 */
public record DashboardStatsSnapshot(
        long employeeCount,
        long postCount,
        long recordCount,
        Map<String, Long> scoreDistribution,
        Map<String, Long> statusDistribution,
        List<MatchingRecord> recentRecords
) {
}
