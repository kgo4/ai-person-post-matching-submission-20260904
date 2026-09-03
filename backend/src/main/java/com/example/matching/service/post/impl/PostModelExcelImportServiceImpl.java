package com.example.matching.service.post.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.listener.PageReadListener;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.dto.post.PostModelExcelRowDTO;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.post.PostModelExcelImportService;
import com.example.matching.service.post.PostPostWriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 岗位能力模型Excel导入服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostModelExcelImportServiceImpl implements PostModelExcelImportService {

    private final PostPostMapper postPostMapper;
    private final PostPostWriteService postPostWriteService;
    private final AbilityTagMapper abilityTagMapper;
    private final PostAbilityModelService postAbilityModelService;
    private final PostCapabilityGenerationService capabilityGenerationService;

    @Override
    public List<PostModelExcelRowDTO> parseExcel(InputStream inputStream) {
        List<PostModelExcelRowDTO> rows = new ArrayList<>();
        try {
            EasyExcel.read(inputStream, PostModelExcelRowDTO.class,
                    new PageReadListener<PostModelExcelRowDTO>(dataList -> rows.addAll(dataList)))
                    .sheet()
                    .doRead();
        } catch (Exception e) {
            throw BusinessException.of(ErrorCodeEnum.IMPORT_ERROR, "Excel解析失败").put("operation", "parseExcel").build();
        }
        log.info("Excel解析完成，共{}行", rows.size());
        return rows;
    }

    @Override
    @Transactional
    public Map<Long, Integer> batchImportFromTemplateB(List<PostModelExcelRowDTO> rows) {
        Map<Long, Integer> result = new LinkedHashMap<>();

        // 按岗位编码分组
        Map<String, List<PostModelExcelRowDTO>> groupedByPost = rows.stream()
                .filter(r -> r.getPostCode() != null && !r.getPostCode().isBlank())
                .collect(Collectors.groupingBy(PostModelExcelRowDTO::getPostCode, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<PostModelExcelRowDTO>> entry : groupedByPost.entrySet()) {
            String postCode = entry.getKey();
            List<PostModelExcelRowDTO> postRows = entry.getValue();

            // 查找或创建岗位
            Long postId = findOrCreatePost(postCode, postRows.get(0).getPostName());

            // 构建能力模型配置
            List<PostAbilityModelConfigDTO> configList = new ArrayList<>();
            for (PostModelExcelRowDTO row : postRows) {
                if (row.getTagCode() == null || row.getTagCode().isBlank()) {
                    continue;
                }

                // 查找能力标签
                AbilityTag tag = findTagByCodeOrName(row.getTagCode(), row.getTagName());
                if (tag == null) {
                    log.warn("能力标签不存在: code={}, name={}, 跳过该行", row.getTagCode(), row.getTagName());
                    continue;
                }

                PostAbilityModelConfigDTO config = new PostAbilityModelConfigDTO();
                config.setPostId(postId);
                config.setTagId(tag.getId());
                config.setMinRequiredLevel(row.getMinRequiredLevel() != null ? row.getMinRequiredLevel() : 2);
                config.setWeight(row.getWeight() != null ? row.getWeight() : BigDecimal.TEN);
                config.setIsCore(row.getIsCore() != null ? row.getIsCore() : 0);
                config.setIsRequired(row.getIsRequired() != null ? row.getIsRequired() : 0);
                config.setRemark(row.getRemark());
                configList.add(config);
            }

            if (!configList.isEmpty()) {
                // 归一化权重到100
                normalizeConfigWeights(configList);
                // 批量配置
                postAbilityModelService.batchConfig(configList);
                result.put(postId, configList.size());
                log.info("岗位能力模型导入成功: postCode={}, postId={}, 能力项数={}", postCode, postId, configList.size());
            }
        }

        return result;
    }

    @Override
    @Transactional
    public Map<Long, Integer> batchImportFromTemplateA(List<PostModelExcelRowDTO> rows) {
        Map<Long, Integer> result = new LinkedHashMap<>();

        // 按岗位编码分组
        Map<String, List<PostModelExcelRowDTO>> groupedByPost = rows.stream()
                .filter(r -> r.getPostCode() != null && !r.getPostCode().isBlank())
                .collect(Collectors.groupingBy(PostModelExcelRowDTO::getPostCode, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<PostModelExcelRowDTO>> entry : groupedByPost.entrySet()) {
            String postCode = entry.getKey();
            List<PostModelExcelRowDTO> postRows = entry.getValue();

            // 查找或创建岗位
            PostModelExcelRowDTO firstRow = postRows.get(0);
            Long postId = findOrCreatePost(postCode, firstRow.getPostName());

            // 合并岗位描述
            StringBuilder description = new StringBuilder();
            for (PostModelExcelRowDTO row : postRows) {
                if (row.getPostDescription() != null && !row.getPostDescription().isBlank()) {
                    description.append(row.getPostDescription()).append("\n");
                }
            }

            if (description.length() == 0) {
                log.warn("岗位描述为空，无法进行AI分析: postCode={}", postCode);
                continue;
            }

            // 调用AI生成能力模型
            try {
                List<JdAbilityItemDTO> abilities = capabilityGenerationService.analyzePostText(
                        firstRow.getPostName(), description.toString());

                if (abilities != null && !abilities.isEmpty()) {
                    List<PostAbilityModelConfigDTO> configList = abilities.stream().map(ability -> {
                        PostAbilityModelConfigDTO config = new PostAbilityModelConfigDTO();
                        config.setPostId(postId);
                        // 使用匹配到的标签ID
                        if (ability.getMatchedTagId() != null) {
                            config.setTagId(ability.getMatchedTagId());
                        } else if (ability.getSuggestedName() != null) {
                            // 尝试通过建议名称查找
                            AbilityTag tag = findTagByCodeOrName(null, ability.getSuggestedName());
                            if (tag != null) {
                                config.setTagId(tag.getId());
                            }
                        }
                        config.setMinRequiredLevel(ability.getMinRequiredLevel() != null ? ability.getMinRequiredLevel() : 2);
                        config.setWeight(ability.getWeight() != null ? ability.getWeight() : BigDecimal.TEN);
                        config.setIsCore(ability.getIsCore() != null ? ability.getIsCore() : 0);
                        config.setIsRequired(ability.getIsRequired() != null ? ability.getIsRequired() : 0);
                        return config;
                    }).filter(c -> c.getTagId() != null).collect(Collectors.toList());

                    if (!configList.isEmpty()) {
                        normalizeConfigWeights(configList);
                        postAbilityModelService.batchConfig(configList);
                        result.put(postId, configList.size());
                        log.info("AI生成岗位能力模型成功: postCode={}, postId={}, 能力项数={}", postCode, postId, configList.size());
                    }
                }
            } catch (Exception e) {
                log.error("AI生成岗位能力模型失败: postCode={}, error={}", postCode, e.getMessage());
            }
        }

        return result;
    }

    @Override
    @Transactional
    public List<PostAbilityModelConfigDTO> normalizeWeights(Long postId) {
        List<PostAbilityModel> models = postAbilityModelService.listByPostId(postId);
        if (models.isEmpty()) {
            return Collections.emptyList();
        }

        BigDecimal totalWeight = models.stream()
                .map(m -> m.getWeight() != null ? m.getWeight() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return Collections.emptyList();
        }

        // 按比例归一化到100
        List<PostAbilityModelConfigDTO> configList = models.stream().map(m -> {
            PostAbilityModelConfigDTO config = new PostAbilityModelConfigDTO();
            config.setId(m.getId());
            config.setPostId(m.getPostId());
            config.setTagId(m.getTagId());
            config.setMinRequiredLevel(m.getMinRequiredLevel());
            config.setWeight(m.getWeight().multiply(new BigDecimal("100"))
                    .divide(totalWeight, 2, RoundingMode.HALF_UP));
            config.setIsRequired(m.getIsRequired());
            config.setIsCore(m.getIsCore());
            config.setRemark(m.getRemark());
            return config;
        }).collect(Collectors.toList());

        // 批量更新
        postAbilityModelService.batchConfig(configList);
        log.info("权重归一化完成: postId={}, 原总和={}, 项数={}", postId, totalWeight, configList.size());

        return configList;
    }

    @Override
    @Transactional
    public int copyPostModel(Long sourcePostId, Long targetPostId) {
        List<PostAbilityModel> sourceModels = postAbilityModelService.listByPostId(sourcePostId);
        if (sourceModels.isEmpty()) {
            throw BusinessException.of(ErrorCodeEnum.POST_NOT_FOUND, "源岗位没有能力模型配置")
                    .entity("POST", sourcePostId).operation("copyPostModel").build();
        }

        // 检查目标岗位是否存在
        PostPost targetPost = postPostMapper.selectById(targetPostId);
        if (targetPost == null) {
            throw BusinessException.of(ErrorCodeEnum.POST_NOT_FOUND, "目标岗位不存在: " + targetPostId)
                    .entity("POST", targetPostId).operation("copyPostModel").build();
        }

        List<PostAbilityModelConfigDTO> configList = sourceModels.stream().map(m -> {
            PostAbilityModelConfigDTO config = new PostAbilityModelConfigDTO();
            config.setPostId(targetPostId);
            config.setTagId(m.getTagId());
            config.setMinRequiredLevel(m.getMinRequiredLevel());
            config.setWeight(m.getWeight());
            config.setIsRequired(m.getIsRequired());
            config.setIsCore(m.getIsCore());
            config.setRemark("从岗位ID=" + sourcePostId + "复制");
            return config;
        }).collect(Collectors.toList());

        postAbilityModelService.batchConfig(configList);
        log.info("岗位模型复制完成: sourcePostId={}, targetPostId={}, 能力项数={}", sourcePostId, targetPostId, configList.size());

        return configList.size();
    }

    // ===== 内部方法 =====

    /**
     * 查找或创建岗位
     */
    private Long findOrCreatePost(String postCode, String postName) {
        PostPost existing = postPostMapper.selectOne(
                Wrappers.<PostPost>lambdaQuery().eq(PostPost::getPostCode, postCode));
        if (existing != null) {
            return existing.getId();
        }

        // 创建新岗位
        PostPost newPost = new PostPost();
        newPost.setPostCode(postCode);
        newPost.setPostName(postName != null ? postName : postCode);
        newPost.setStatus(1);
        postPostWriteService.save(newPost);
        log.info("自动创建岗位: postCode={}, postId={}", postCode, newPost.getId());
        return newPost.getId();
    }

    /**
     * 根据编码或名称查找能力标签
     */
    private AbilityTag findTagByCodeOrName(String tagCode, String tagName) {
        if (tagCode != null && !tagCode.isBlank()) {
            AbilityTag byCode = abilityTagMapper.selectOne(
                    Wrappers.<AbilityTag>lambdaQuery().eq(AbilityTag::getTagCode, tagCode));
            if (byCode != null) {
                return byCode;
            }
        }
        if (tagName != null && !tagName.isBlank()) {
            AbilityTag byName = abilityTagMapper.selectOne(
                    Wrappers.<AbilityTag>lambdaQuery().eq(AbilityTag::getTagName, tagName));
            if (byName != null) {
                return byName;
            }
        }
        return null;
    }

    /**
     * 归一化配置列表中的权重到100
     */
    private void normalizeConfigWeights(List<PostAbilityModelConfigDTO> configList) {
        BigDecimal totalWeight = configList.stream()
                .map(c -> c.getWeight() != null ? c.getWeight() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWeight.compareTo(BigDecimal.ZERO) > 0 && totalWeight.compareTo(new BigDecimal("100")) != 0) {
            BigDecimal scale = new BigDecimal("100").divide(totalWeight, 4, RoundingMode.HALF_UP);
            for (PostAbilityModelConfigDTO config : configList) {
                if (config.getWeight() != null) {
                    config.setWeight(config.getWeight().multiply(scale).setScale(2, RoundingMode.HALF_UP));
                }
            }
            log.info("权重自动归一化: 原始总和={}, 归一化后总和=100", totalWeight);
        }
    }
}
