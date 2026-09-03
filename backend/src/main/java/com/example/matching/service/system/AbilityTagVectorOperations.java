package com.example.matching.service.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.system.AbilityTagMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 能力标签向量操作服务
 * <p>
 * 从 AbilityTagServiceImpl 中抽取，负责向量嵌入生成、canonical_id 初始化和余弦相似度计算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbilityTagVectorOperations extends ServiceImpl<AbilityTagMapper, AbilityTag> {

    private final VectorEmbeddingService vectorEmbeddingService;

    @Transactional
    public int batchGenerateVectors() {
        List<AbilityTag> tagsWithoutVector = list(Wrappers.<AbilityTag>lambdaQuery()
                .eq(AbilityTag::getStatus, 1)
                .isNull(AbilityTag::getEmbeddingVector));

        int count = 0;
        for (AbilityTag tag : tagsWithoutVector) {
            try {
                List<Float> vector = vectorEmbeddingService.embed(tag.getTagName());
                tag.setEmbeddingVector(vector);
                updateById(tag);
                count++;
            } catch (Exception e) {
                log.warn("生成向量失败: tagId={}, tagName={}, error={}",
                        tag.getId(), tag.getTagName(), e.getMessage());
            }
        }

        log.info("批量生成向量完成: total={}, success={}", tagsWithoutVector.size(), count);
        return count;
    }

    @Transactional
    public int batchInitCanonicalTagIds() {
        List<AbilityTag> tagsWithoutCanonical = list(Wrappers.<AbilityTag>lambdaQuery()
                .isNull(AbilityTag::getCanonicalTagId));

        int count = 0;
        for (AbilityTag tag : tagsWithoutCanonical) {
            tag.setCanonicalTagId(tag.getId());
            updateById(tag);
            count++;
        }

        log.info("批量初始化canonical_tag_id完成: total={}", count);
        return count;
    }

    /**
     * 计算两个标签名称的余弦相似度
     *
     * @return 相似度值，计算失败返回 null
     */
    public Float calculateSimilarity(String tagName, AbilityTag targetTag) {
        try {
            List<Float> queryVector = vectorEmbeddingService.embed(tagName);
            if (queryVector == null || queryVector.isEmpty() || targetTag.getEmbeddingVector() == null) {
                return null;
            }
            return vectorEmbeddingService.cosineSimilarity(queryVector, targetTag.getEmbeddingVector());
        } catch (Exception e) {
            log.warn("计算相似度失败: tagName={}, targetTag={}", tagName, targetTag.getTagName(), e);
            return null;
        }
    }
}
