package com.example.matching.agent.dto;

import lombok.Data;

import java.util.List;

/**
 * 证据治理Agent请求DTO
 *
 * @author system
 */
@Data
public class EvidenceGovernanceAgentRequest {
    /** 场景 */
    private String scenario;

    /** 声明类型 */
    private String claimType;

    /** 声明文本 */
    private String claimText;

    /** 证据文本 */
    private String evidenceText;

    /** 来源类型 */
    private String sourceType;

    /** 来源引用ID */
    private Long sourceRefId;

    /** 来源引用列表 */
    private List<String> sourceRefs;

    /** RAG分块ID列表 */
    private List<Long> ragChunkIds;

    /** 匹配的标签ID */
    private Long matchedTagId;

    /** 相似标签ID */
    private Long similarTagId;
}
