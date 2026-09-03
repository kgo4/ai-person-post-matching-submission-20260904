package com.example.matching.port.system;

/**
 * 系统数据规模统计端口：聚合各域实体数量，供"能力大脑"健康检查等跨域统计使用。
 * <p>
 * 单个域统计失败时该字段为 null（不中断整体统计），调用方按需降级。
 */
public interface SystemDataStatsPort {

    /**
     * 统计各域实体数量。
     *
     * @return 各域计数快照，单个域失败时对应字段为 null
     */
    DataStatsSnapshot countSnapshot();

    record DataStatsSnapshot(
            Long postCount,
            Long employeeCount,
            Long abilityTagCount,
            Long matchingRecordCount,
            Long evidenceCount,
            Long ragDocumentCount,
            Long graphNodeCount,
            Long graphEdgeCount,
            Long evolutionTaskCount,
            Long learningResourceCount
    ) {
    }
}
