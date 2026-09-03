package com.example.matching.dto.kg;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 图谱边DTO
 *
 * @author system
 */
@Data
public class GraphEdgeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 边ID，格式为 {sourceNodeKey}-{edgeType}-{targetNodeKey} */
    private String id;

    /** 源节点ID */
    private String source;

    /** 目标节点ID */
    private String target;

    /** 边类型 */
    private String type;

    /** 边权重 */
    private BigDecimal weight;

    /** 置信度 0-100 */
    private BigDecimal confidence;

    /** 元数据 */
    private Map<String, Object> metadata;
}
