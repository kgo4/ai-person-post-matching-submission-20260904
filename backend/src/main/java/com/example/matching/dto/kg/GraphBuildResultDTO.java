package com.example.matching.dto.kg;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 图谱构建结果DTO
 *
 * @author system
 */
@Data
public class GraphBuildResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否成功 */
    private boolean success;

    /** 节点总数 */
    private int nodeCount;

    /** 边总数 */
    private int edgeCount;

    /** 按节点类型统计 */
    private Map<String, Integer> nodeTypeCounts;

    /** 按边类型统计 */
    private Map<String, Integer> edgeTypeCounts;

    /** 消息 */
    private String message;

    /** 本次全量构图版本 */
    private String graphVersion;

    public static GraphBuildResultDTO success(int nodeCount, int edgeCount,
                                               Map<String, Integer> nodeTypeCounts,
                                               Map<String, Integer> edgeTypeCounts) {
        GraphBuildResultDTO result = new GraphBuildResultDTO();
        result.setSuccess(true);
        result.setNodeCount(nodeCount);
        result.setEdgeCount(edgeCount);
        result.setNodeTypeCounts(nodeTypeCounts);
        result.setEdgeTypeCounts(edgeTypeCounts);
        result.setMessage("图谱构建成功");
        return result;
    }

    public static GraphBuildResultDTO failure(String message) {
        GraphBuildResultDTO result = new GraphBuildResultDTO();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }
}
