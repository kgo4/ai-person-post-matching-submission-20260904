package com.example.matching.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量数据库配置属性（milvus.enabled=true 时激活）
 * <p>
 * 实际客户端由 {@link ResilientMilvusClient} 管理，支持自动重连。
 * 此类仅负责绑定 YAML 配置属性。
 */
@Slf4j
@Data
@Configuration
@ConditionalOnProperty(name = "milvus.enabled", havingValue = "true")
@ConditionalOnExpression("'${milvus.uri:}'.trim().length() > 0")
@ConfigurationProperties(prefix = "milvus")
public class MilvusConfig {

    private String uri;
    private String token;
    private String database;
    private String collectionName;
    private String profileCollectionName = "person_post_vector";
    private String ragCollectionName = "rag_knowledge_chunks";
    private int dimension = 1536;
}
