package com.example.matching.service.kg;

import com.example.matching.entity.kg.KgGraphSnapshot;

import java.util.Map;

/**
 * 知识图谱快照服务接口
 *
 * @author system
 */
public interface KnowledgeGraphSnapshotService {

    /**
     * 创建图谱快照
     *
     * @param snapshotType 快照类型
     * @param snapshotName 快照名称
     * @param graphJson    图谱JSON
     * @param createdBy    创建人
     * @return 快照实体
     */
    KgGraphSnapshot createSnapshot(String snapshotType, String snapshotName, String graphJson, Long createdBy);

    /**
     * 查询快照列表
     *
     * @param snapshotType 快照类型
     * @param page         页码
     * @param size         每页数量
     * @return 快照列表
     */
    Map<String, Object> getSnapshotPage(String snapshotType, Integer page, Integer size);

    /**
     * 获取快照详情
     *
     * @param id 快照ID
     * @return 快照实体
     */
    KgGraphSnapshot getSnapshotById(Long id);

    String createPostAbilitySnapshot(String snapshotType, Long createdBy);

    Map<String, Object> diffPostAbilitySnapshots(String baselineSnapshotCode, String targetSnapshotCode);
}
