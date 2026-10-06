package com.example.matching.dto.workbench;

import java.util.List;

/**
 * 工作台指标视图（KPI 卡 + 环比 + 团队成员）。
 *
 * <p>这是工作台首屏的聚合读模型：一次性返回各角色 KPI 卡所需的当期值与环比，
 * 以及管理端需要的团队成员摘要，避免前端为每张卡片单独发一次请求。</p>
 *
 * <p>字段可为空表示"该角色无此指标 / 无权限取到"，前端按空状态处理，
 * 不得用 0 兜底。</p>
 */
public record WorkbenchMetricsVO(
        List<MetricCard> cards,
        List<TeamMember> members
) {

    /**
     * 单张 KPI 卡。
     *
     * @param key   指标键，与前端 WorkbenchStat.key 对应
     * @param value 当期值；null 表示取不到
     * @param delta 环比；null 表示无基线数据
     */
    public record MetricCard(String key, Long value, WorkbenchMetricDelta delta) {}

    /**
     * 团队成员摘要（对应参考图底部中栏）。
     *
     * @param active 是否有在手活跃业务，用于渲染在线/离线指示点
     */
    public record TeamMember(
            Long id,
            String name,
            String role,
            String meta,
            boolean active
    ) {}
}
