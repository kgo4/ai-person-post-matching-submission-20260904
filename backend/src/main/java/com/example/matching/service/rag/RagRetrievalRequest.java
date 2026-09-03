package com.example.matching.service.rag;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * RAG 检索请求
 */
@Data
@Builder
public class RagRetrievalRequest {

    /** 查询文本 */
    private String queryText;

    /** 场景枚举 */
    private RagScenarioEnum scenario;

    /** 返回数量（覆盖场景默认值） */
    private Integer topK;

    /** 最低相似度（覆盖场景默认值） */
    private Double minSimilarity;

    /** 业务关联ID（如员工ID、岗位ID） */
    private Long refId;

    /** 业务关联类型（如 EMPLOYEE、POST） */
    private String refType;

    /** 是否强制使用云知识库（覆盖场景默认值） */
    private Boolean forceCloud;

    /** 额外过滤条件（JSON格式） */
    private String extraFilter;

    /** 业务来源白名单；为空时使用场景默认来源范围 */
    private List<String> sourceTypes;
}
