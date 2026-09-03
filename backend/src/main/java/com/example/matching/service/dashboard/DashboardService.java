package com.example.matching.service.dashboard;

import java.util.Map;

/**
 * Dashboard 数据统计服务接口。
 * <p>
 * 遵循项目 Interface + Impl 约定，聚合统计员工数、岗位数、匹配记录分布等，
 * 供前端"匹配驾驶舱"页面使用。
 */
public interface DashboardService {

    /**
     * 获取 Dashboard 统计数据（缓存 5 分钟）
     *
     * @return 包含员工数、岗位数、匹配记录数、分数分布、状态分布、最近记录的 Map
     */
    Map<String, Object> getDashboardStats();

    /**
     * 清除 Dashboard 缓存（员工/岗位/匹配记录变更时调用）
     */
    void evictDashboardStats();
}
