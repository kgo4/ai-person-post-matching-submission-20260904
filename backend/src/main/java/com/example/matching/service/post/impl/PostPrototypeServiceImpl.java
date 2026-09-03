package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.dto.post.PostPrototypeSaveDTO;
import com.example.matching.dto.post.PostPrototypeVO;
import com.example.matching.entity.post.PostPrototype;
import com.example.matching.entity.post.PostPrototypeTag;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.post.PostPrototypeMapper;
import com.example.matching.mapper.post.PostPrototypeTagMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.post.PostPrototypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 岗位原型服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostPrototypeServiceImpl extends ServiceImpl<PostPrototypeMapper, PostPrototype> implements PostPrototypeService {

    private final PostPrototypeTagMapper prototypeTagMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final PostAbilityModelService postAbilityModelService;

    @Override
    public IPage<PostPrototype> pagePrototypes(IPage<PostPrototype> page, String keyword, String industry, String category) {
        var wrapper = Wrappers.<PostPrototype>lambdaQuery()
                .eq(PostPrototype::getStatus, 1);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PostPrototype::getPrototypeName, keyword)
                    .or().like(PostPrototype::getDescription, keyword));
        }
        if (StringUtils.hasText(industry)) {
            wrapper.eq(PostPrototype::getIndustry, industry);
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(PostPrototype::getCategory, category);
        }
        wrapper.orderByDesc(PostPrototype::getCreatedTime);
        return page(page, wrapper);
    }

    @Override
    public List<PostPrototype> listEnabled() {
        return list(Wrappers.<PostPrototype>lambdaQuery()
                .eq(PostPrototype::getStatus, 1)
                .orderByDesc(PostPrototype::getCreatedTime));
    }

    @Override
    public PostPrototypeVO getDetail(Long id) {
        PostPrototype prototype = getById(id);
        if (prototype == null) {
            return null;
        }
        return convertToVO(prototype);
    }

    @Override
    @Transactional
    public void savePrototype(PostPrototypeSaveDTO dto) {
        PostPrototype prototype;
        if (dto.getId() != null) {
            prototype = getById(dto.getId());
            if (prototype == null) {
                throw BusinessException.of(ErrorCodeEnum.POST_NOT_FOUND, "原型不存在: " + dto.getId())
                        .entity("POST_PROTOTYPE", dto.getId()).build();
            }
        } else {
            prototype = new PostPrototype();
        }

        prototype.setPrototypeName(dto.getPrototypeName());
        prototype.setIndustry(dto.getIndustry());
        prototype.setCategory(dto.getCategory());
        prototype.setDescription(dto.getDescription());
        prototype.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);

        // 生成向量嵌入
        try {
            String textForEmbedding = dto.getPrototypeName() + " " + (dto.getDescription() != null ? dto.getDescription() : "");
            List<Float> vector = vectorEmbeddingService.embed(textForEmbedding.trim());
            prototype.setEmbeddingVector(vector);
        } catch (Exception e) {
            log.warn("生成原型向量失败: {}", e.getMessage());
        }

        if (dto.getId() != null) {
            updateById(prototype);
        } else {
            save(prototype);
        }

        // 保存标签关联
        if (dto.getTags() != null) {
            // 删除旧关联
            prototypeTagMapper.delete(Wrappers.<PostPrototypeTag>lambdaQuery()
                    .eq(PostPrototypeTag::getPrototypeId, prototype.getId()));

            // 插入新关联
            for (int i = 0; i < dto.getTags().size(); i++) {
                PostPrototypeSaveDTO.PrototypeTagItem tagItem = dto.getTags().get(i);
                PostPrototypeTag pt = new PostPrototypeTag();
                pt.setPrototypeId(prototype.getId());
                pt.setTagId(tagItem.getTagId());
                pt.setWeight(tagItem.getWeight());
                pt.setMinRequiredLevel(tagItem.getMinRequiredLevel() != null ? tagItem.getMinRequiredLevel() : 2);
                pt.setIsCore(tagItem.getIsCore() != null ? tagItem.getIsCore() : 0);
                pt.setIsRequired(tagItem.getIsRequired() != null ? tagItem.getIsRequired() : 0);
                pt.setSortOrder(tagItem.getSortOrder() != null ? tagItem.getSortOrder() : i);
                prototypeTagMapper.insert(pt);
            }
        }

        log.info("保存岗位原型: id={}, name={}", prototype.getId(), prototype.getPrototypeName());
    }

    @Override
    @Transactional
    public void deletePrototype(Long id) {
        // 删除标签关联
        prototypeTagMapper.delete(Wrappers.<PostPrototypeTag>lambdaQuery()
                .eq(PostPrototypeTag::getPrototypeId, id));
        // 删除原型
        removeById(id);
    }

    @Override
    public List<PostPrototypeVO> recallByDescription(String description, int topN) {
        try {
            List<Float> queryVector = vectorEmbeddingService.embed(description);
            if (queryVector == null || queryVector.isEmpty()) {
                return Collections.emptyList();
            }

            List<PostPrototype> allEnabled = listEnabled();

            // 计算相似度并排序
            List<PostPrototypeVO> results = new ArrayList<>();
            for (PostPrototype p : allEnabled) {
                if (p.getEmbeddingVector() == null || p.getEmbeddingVector().isEmpty()) {
                    continue;
                }
                Float similarity = vectorEmbeddingService.cosineSimilarity(queryVector, p.getEmbeddingVector());
                if (similarity != null && similarity > 0.6) {
                    PostPrototypeVO vo = convertToVO(p);
                    vo.setSimilarityScore(similarity);
                    results.add(vo);
                }
            }

            // 按相似度降序排序后取 topN
            return results.stream()
                    .sorted((a, b) -> Float.compare(
                            b.getSimilarityScore() != null ? b.getSimilarityScore() : 0f,
                            a.getSimilarityScore() != null ? a.getSimilarityScore() : 0f))
                    .limit(topN)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("原型向量召回失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional
    public void applyPrototypeToPost(Long prototypeId, Long postId) {
        List<PostPrototypeTag> protoTags = prototypeTagMapper.selectList(
                Wrappers.<PostPrototypeTag>lambdaQuery()
                        .eq(PostPrototypeTag::getPrototypeId, prototypeId)
                        .orderByAsc(PostPrototypeTag::getSortOrder));

        if (protoTags.isEmpty()) {
            log.warn("原型没有关联标签: prototypeId={}", prototypeId);
            return;
        }

        List<PostAbilityModelConfigDTO> configList = new ArrayList<>();
        for (PostPrototypeTag pt : protoTags) {
            PostAbilityModelConfigDTO config = new PostAbilityModelConfigDTO();
            config.setPostId(postId);
            config.setTagId(pt.getTagId());
            config.setMinRequiredLevel(pt.getMinRequiredLevel());
            config.setWeight(pt.getWeight());
            config.setIsCore(pt.getIsCore());
            config.setIsRequired(pt.getIsRequired());
            configList.add(config);
        }

        postAbilityModelService.batchConfig(configList);
        log.info("原型已应用到岗位: prototypeId={}, postId={}, tagCount={}", prototypeId, postId, configList.size());
    }

    // ===== 内部方法 =====

    private PostPrototypeVO convertToVO(PostPrototype prototype) {
        PostPrototypeVO vo = new PostPrototypeVO();
        vo.setId(prototype.getId());
        vo.setPrototypeName(prototype.getPrototypeName());
        vo.setIndustry(prototype.getIndustry());
        vo.setCategory(prototype.getCategory());
        vo.setDescription(prototype.getDescription());
        vo.setStatus(prototype.getStatus());
        vo.setCreatedTime(prototype.getCreatedTime());

        // 加载关联标签
        List<PostPrototypeTag> protoTags = prototypeTagMapper.selectList(
                Wrappers.<PostPrototypeTag>lambdaQuery()
                        .eq(PostPrototypeTag::getPrototypeId, prototype.getId())
                        .orderByAsc(PostPrototypeTag::getSortOrder));

        List<PostPrototypeVO.PrototypeTagVO> tagVOs = new ArrayList<>();
        for (PostPrototypeTag pt : protoTags) {
            AbilityTag tag = abilityTagMapper.selectById(pt.getTagId());
            PostPrototypeVO.PrototypeTagVO tagVO = new PostPrototypeVO.PrototypeTagVO();
            tagVO.setId(pt.getId());
            tagVO.setTagId(pt.getTagId());
            tagVO.setTagName(tag != null ? tag.getTagName() : "未知标签");
            tagVO.setTagCategory(tag != null ? tag.getTagCategory() : null);
            tagVO.setWeight(pt.getWeight());
            tagVO.setMinRequiredLevel(pt.getMinRequiredLevel());
            tagVO.setIsCore(pt.getIsCore());
            tagVO.setIsRequired(pt.getIsRequired());
            tagVO.setSortOrder(pt.getSortOrder());
            tagVOs.add(tagVO);
        }
        vo.setTags(tagVOs);

        return vo;
    }
}
