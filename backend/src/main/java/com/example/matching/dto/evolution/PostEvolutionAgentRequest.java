package com.example.matching.dto.evolution;

import lombok.Data;

import java.util.List;

/**
 * 岗位演化 Agent 请求
 *
 * @author system
 */
@Data
public class PostEvolutionAgentRequest {

    /** 目标岗位ID */
    private Long postId;

    /** 岗位名称（可选，用于日志） */
    private String postName;

    /** 行业 */
    private String industry;

    /** 业务领域 */
    private String businessDomain;

    /** 指定的知识源文档ID列表 */
    private List<Long> sourceDocumentIds;

    /** 指定的资料类型过滤 */
    private List<String> sourceTypes;

    /** 触发类型：MANUAL_UPLOAD/MANUAL_RUN/SCHEDULED/CLOUD_SYNC/MARKET_JD_IMPORT */
    private String triggerType;

    /** 是否包含市场JD */
    private Boolean includeMarketJd;

    /** 是否包含知乎外部趋势（仅作为演化证据，不自动应用） */
    private Boolean includeZhihu;

    /** 是否包含云知识库 */
    private Boolean includeCloudKnowledge;

    /** 是否包含行业白皮书 */
    private Boolean includeWhitepaper;

    /** 操作人ID */
    private Long operatorId;
}
