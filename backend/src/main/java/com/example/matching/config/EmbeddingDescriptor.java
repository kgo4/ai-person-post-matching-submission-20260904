package com.example.matching.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 嵌入模型溯源描述（不可变）。
 */
@Component
public class EmbeddingDescriptor {

    private final String modelName;
    private final Integer dimension;

    public EmbeddingDescriptor(
            @Value("${spring.ai.openai.embedding.options.model:text-embedding-v1}") String modelName,
            @Value("${spring.ai.openai.embedding.options.dimensions:0}") Integer dimension) {
        this.modelName = modelName;
        this.dimension = (dimension != null && dimension > 0) ? dimension : null;
    }

    public String modelName() {
        return modelName;
    }

    public Integer dimension() {
        return dimension;
    }

    @Override
    public String toString() {
        return modelName + (dimension != null ? ":" + dimension : "");
    }
}
