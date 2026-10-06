package com.example.matching.service.rag;

/**
 * RAG 知识分层。sourceType 表示业务来源，knowledgeLayer 表示检索策略边界，二者不能混用。
 */
public enum RagKnowledgeLayer {
    FACT,
    EVIDENCE,
    DOMAIN,
    TREND
}
