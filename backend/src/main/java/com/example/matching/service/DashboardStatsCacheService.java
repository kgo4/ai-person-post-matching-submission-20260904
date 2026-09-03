package com.example.matching.service;

import com.example.matching.config.RedisCacheNames;
import com.example.matching.dto.matching.DashboardStatsSnapshot;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.matching.MatchingRecordMapper;
import com.example.matching.mapper.post.PostPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Dashboard 统计缓存服务（独立 Bean）
 * <p>
 * 独立 Bean 保证 {@code @Cacheable} 代理生效（避免同类内部调用导致代理失效）；
 * 缓存目标类型为不可变 DTO {@link DashboardStatsSnapshot}，字段类型明确，
 * 命中缓存后不会退化为 LinkedHashMap。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardStatsCacheService {

    private final EmpEmployeeMapper empEmployeeMapper;
    private final PostPostMapper postPostMapper;
    private final MatchingRecordMapper matchingRecordMapper;

    @Cacheable(cacheNames = RedisCacheNames.DASHBOARD_STATS, key = "'all'", sync = true)
    public DashboardStatsSnapshot load() {
        log.debug("缓存未命中，从DB查询Dashboard统计数据");

        Map<String, Long> matchingSummary = matchingRecordMapper.selectDashboardSummary();

        Map<String, Long> scoreDist = new HashMap<>();
        scoreDist.put("strong", matchingSummary.getOrDefault("score90", 0L));
        scoreDist.put("match", matchingSummary.getOrDefault("score75", 0L));
        scoreDist.put("observe", matchingSummary.getOrDefault("score60", 0L));
        scoreDist.put("reject", matchingSummary.getOrDefault("scoreBelow60", 0L));

        Map<String, Long> statusDist = new HashMap<>();
        statusDist.put("pending", matchingSummary.getOrDefault("status0", 0L));
        statusDist.put("strong", matchingSummary.getOrDefault("status1", 0L));
        statusDist.put("match", matchingSummary.getOrDefault("status2", 0L));
        statusDist.put("observe", matchingSummary.getOrDefault("status3", 0L));
        statusDist.put("reject", matchingSummary.getOrDefault("status4", 0L));

        return new DashboardStatsSnapshot(
                empEmployeeMapper.selectCount(null),
                postPostMapper.selectCount(null),
                matchingSummary.getOrDefault("totalCount", 0L),
                scoreDist,
                statusDist,
                matchingRecordMapper.selectRecentTen());
    }
}
