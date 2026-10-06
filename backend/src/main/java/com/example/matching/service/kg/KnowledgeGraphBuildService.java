package com.example.matching.service.kg;

import com.example.matching.dto.kg.GraphBuildResultDTO;

/**
 * 知识图谱构建服务接口
 *
 * @author system
 */
public interface KnowledgeGraphBuildService {

    /**
     * 全量重建图谱
     * <p>
     * 清空现有节点和边，从所有源表重新构建
     *
     * @return 构建结果
     */
    GraphBuildResultDTO rebuildFullGraph();
}
