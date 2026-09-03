package com.example.matching.dto.kg;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 图谱节点DTO
 *
 * @author system
 */
@Data
public class GraphNodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点ID，格式为 {nodeType}:{refId}，如 POST:1 */
    private String id;

    /** 显示标签 */
    private String label;

    /** 节点类型 */
    private String type;

    /** 分类或领域 */
    private String category;

    /** 可视化权重 */
    private BigDecimal weight;

    /** 状态 */
    private String status;

    /** 元数据 */
    private Map<String, Object> metadata;
}
