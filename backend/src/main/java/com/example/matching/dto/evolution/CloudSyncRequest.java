package com.example.matching.dto.evolution;

import lombok.Data;

import java.util.List;

/**
 * 云知识库同步请求
 *
 * @author system
 */
@Data
public class CloudSyncRequest {

    /** 知识库编码 */
    private String knowledgeBaseCode;

    /** 业务领域 */
    private String businessDomain;

    /** 同步的资料类型范围 */
    private List<String> sourceTypes;

    /** 操作人ID */
    private Long operatorId;
}
