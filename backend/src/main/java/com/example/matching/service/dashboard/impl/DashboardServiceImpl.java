package com.example.matching.service.dashboard.impl;

import com.example.matching.config.RedisCacheNames;
import com.example.matching.dto.matching.DashboardStatsSnapshot;
import com.example.matching.service.DashboardStatsCacheService;
import com.example.matching.service.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dashboard 数据统计服务实现。
 * <p>
 * 统计计算与缓存委托给 {@link DashboardStatsCacheService}（独立 Bean，缓存代理有效），
 * 本类负责把快照 DTO 转成前端所需的 Map 结构，前端接口保持不变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardStatsCacheService statsCacheService;

    @Override
    public Map<String, Object> getDashboardStats() {
        DashboardStatsSnapshot snapshot = statsCacheService.load();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("employeeCount", snapshot.employeeCount());
        stats.put("postCount", snapshot.postCount());
        stats.put("recordCount", snapshot.recordCount());
        stats.put("scoreDistribution", new HashMap<>(snapshot.scoreDistribution()));
        stats.put("statusDistribution", new HashMap<>(snapshot.statusDistribution()));
        stats.put("recentRecords", snapshot.recentRecords());
        return stats;
    }

    @Override
    @CacheEvict(cacheNames = RedisCacheNames.DASHBOARD_STATS, key = "'all'")
    public void evictDashboardStats() {
        log.debug("清除Dashboard缓存");
    }
}
