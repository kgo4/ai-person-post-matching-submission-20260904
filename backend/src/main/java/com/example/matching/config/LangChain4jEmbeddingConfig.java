package com.example.matching.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 向量化模型配置
 * <p>
 * 复用 {@code spring.ai.openai.embedding.*} 的配置项（DashScope 千问 text-embedding-v1），
 * 与 LangChain4j 体系对齐。后续若需统一为单一一套 prefix，迁移至 {@code langchain4j.embedding.*} 即可。
 */
@Slf4j
@Configuration
public class LangChain4jEmbeddingConfig {

    @Value("${spring.ai.openai.embedding.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.embedding.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${spring.ai.openai.embedding.options.model:text-embedding-v1}")
    private String modelName;

    @Value("${spring.ai.openai.embedding.options.dimensions:0}")
    private Integer dimensions;

    /**
     * 单次 HTTP 请求的 connect/read 超时（秒）。
     * langchain4j 1.18 默认 connectTimeout=15s、readTimeout=60s；
     * 大模型推理（如 Qwen3-VL-Embedding-8B）处理长文本/大批量时可能接近该上限，
     * 网关侧一旦超时断开即表现为 Connection reset，故调大并提供配置覆盖。
     */
    @Value("${langchain4j.embedding.timeout-seconds:120}")
    private long timeoutSeconds;

    /**
     * 可重试异常（如 Connection reset / SocketTimeoutException）的最大尝试次数，默认 3。
     */
    @Value("${langchain4j.embedding.max-retries:5}")
    private int maxRetries;

    /**
     * 当配置了 api-key 才创建 LangChain4j 的 EmbeddingModel bean，避免开发环境无 key 也能启动。
     * bean 名沿用 {@code langchain4jEmbeddingModel}，以免与 Spring AI 的 {@code embeddingModel} 同名冲突。
     */
    @Bean
    @ConditionalOnProperty(name = "spring.ai.openai.embedding.api-key")
    public EmbeddingModel langchain4jEmbeddingModel() {
        log.info("初始化 LangChain4j EmbeddingModel: baseUrl={}, model={}, dimensions={}, timeout={}s, maxRetries={}",
                baseUrl, modelName, dimensions, timeoutSeconds, maxRetries);
        var builder = OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(Math.max(1L, timeoutSeconds)))
                .maxRetries(Math.max(1, maxRetries));
        if (dimensions != null && dimensions > 0) {
            builder.dimensions(dimensions);
        }
        return builder.build();
    }
}