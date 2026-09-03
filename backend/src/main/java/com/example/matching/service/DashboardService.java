package com.example.matching.service;

import com.example.matching.config.RedisCacheNames;
import com.example.matching.dto.matching.DashboardStatsSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dashboard 数据统计服务
 * <p>
 * 聚合统计员工数、岗位数、匹配记录分布等，供前端"匹配驾驶舱"页面使用。
 * 统计计算与缓存委托给 {@link DashboardStatsCacheService}（独立 Bean，缓存代理有效），
 * 本类负责把快照 DTO 转成前端所需的 Map 结构，前端接口保持不变。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardStatsCacheService statsCacheService;

    /**
     * 获取 Dashboard 统计数据（缓存 5 分钟）
     *
     * @return 包含员工数、岗位数、匹配记录数、分数分布、状态分布、最近记录的 Map
     */
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

    /**
     * 清除 Dashboard 缓存（员工/岗位/匹配记录变更时调用）
     */
    @CacheEvict(cacheNames = RedisCacheNames.DASHBOARD_STATS, key = "'all'")
    public void evictDashboardStats() {
        log.debug("清除Dashboard缓存");
    }
}
