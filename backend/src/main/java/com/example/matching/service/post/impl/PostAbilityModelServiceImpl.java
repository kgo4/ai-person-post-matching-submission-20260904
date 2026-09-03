package com.example.matching.service.post.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostModelQuality;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.service.system.AbilityTagHierarchy;
import com.example.matching.event.PostModelChangeEvent;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostModelQualityMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.post.PostAbilityWeightNormalizer;
import com.example.matching.vo.post.PostAbilityModelVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostAbilityModelServiceImpl extends ServiceImpl<PostAbilityModelMapper, PostAbilityModel> implements PostAbilityModelService {

    private final PostPostMapper postPostMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final PostModelQualityMapper postModelQualityMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    private final com.example.matching.service.common.VectorRecallCacheEpoch vectorRecallCacheEpoch;
    private final com.example.matching.converter.post.PostPostConverter postPostConverter;

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.POST_MODEL, key = "#dto.postId")
    })
    public void saveConfig(PostAbilityModelConfigDTO dto) {
        normalizeOptionalTag(dto);
        validateSingleAbilityIdentity(dto);
        Long modelId = null;
        if (dto.getId() == null) {
            PostAbilityModel model = new PostAbilityModel();
            BeanUtils.copyProperties(dto, model);
            applyPanoramaMetadata(dto, model);
            save(model);
            modelId = model.getId();
        } else {
            PostAbilityModel model = getById(dto.getId());
            if (model != null) {
                BeanUtils.copyProperties(dto, model, "id");
                applyPanoramaMetadata(dto, model);
                updateById(model);
                modelId = model.getId();
            }
        }
        abilityEvidenceIngestionService.ingestPostAbilityModel(modelId, "POST_ABILITY_MODEL");
        // 发布岗位模型变更事件，触发向量同步
        vectorRecallCacheEpoch.advance();
        eventPublisher.publishEvent(new PostModelChangeEvent(this, "MODEL_CONFIG", dto.getPostId()));
    }

    @Override
    public PostAbilityModelVO getPostAbilityModel(Long postId) {
        PostPost post = postPostMapper.selectById(postId);
        if (post == null) {
            return null;
        }
        // M17：DTO 收口——基础字段由 MapStruct 生成的 PostPostConverter 映射
        PostAbilityModelVO vo = postPostConverter.toAbilityModelVO(post);

        List<PostAbilityModel> models = listByPostId(postId);
        vo.setAbilityRequirements(models.stream().map(m -> {
            PostAbilityModelVO.AbilityRequirementDetail detail = new PostAbilityModelVO.AbilityRequirementDetail();
            detail.setModelId(m.getId());
            detail.setTagId(m.getTagId());
            // 岗位能力编辑直接以岗位能力表为准；标签库名称仅是可选辅助，不能影响展示。
            AbilityTag tag = m.getTagId() != null ? abilityTagMapper.selectById(m.getTagId()) : null;
            String abilityName = StringUtils.hasText(m.getAbilityName()) ? m.getAbilityName().trim() : null;
            detail.setAbilityName(abilityName);
            detail.setTechStack(m.getTechStack());
            detail.setTagName(tag != null ? tag.getTagName()
                    : (StringUtils.hasText(abilityName) ? abilityName : "未命名能力（模型#" + m.getId() + "）"));
            detail.setMinRequiredLevel(m.getMinRequiredLevel());
            detail.setWeight(m.getWeight());
            detail.setIsRequired(m.getIsRequired());
            detail.setIsCore(m.getIsCore());
            return detail;
        }).collect(Collectors.toList()));

        return vo;
    }

    @Override
    @Cacheable(cacheNames = RedisCacheNames.POST_MODEL, key = "#postId", sync = true)
    public List<PostAbilityModel> listByPostId(Long postId) {
        List<PostAbilityModel> models = list(Wrappers.<PostAbilityModel>lambdaQuery().eq(PostAbilityModel::getPostId, postId));
        normalizePersistedWeights(models);
        return models;
    }

    /** 统一修复历史岗位模型权重，保持相对比例并将总和收敛到100%。 */
    private void normalizePersistedWeights(List<PostAbilityModel> models) {
        if (models == null || models.isEmpty()) return;
        BigDecimal total = models.stream().map(PostAbilityModel::getWeight)
                .filter(Objects::nonNull).filter(w -> w.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0 || (total.compareTo(new BigDecimal("95")) >= 0
                && total.compareTo(new BigDecimal("105")) <= 0)) return;
        BigDecimal assigned = BigDecimal.ZERO;
        PostAbilityModel last = null;
        for (PostAbilityModel model : models) {
            if (model.getWeight() == null) continue;
            last = model;
            BigDecimal normalized = model.getWeight().multiply(new BigDecimal("100"))
                    .divide(total, 2, RoundingMode.HALF_UP);
            model.setWeight(normalized);
            assigned = assigned.add(normalized);
        }
        if (last == null) return;
        last.setWeight(last.getWeight().add(new BigDecimal("100").subtract(assigned))
                .setScale(2, RoundingMode.HALF_UP));
        models.forEach(model -> baseMapper.updateById(model));
        log.warn("岗位能力权重已自动规范化: postId={}, 原总和={}, 新总和=100", models.get(0).getPostId(), total);
    }

    @Override
    public Set<Long> listConfiguredPostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptySet();
        }
        return list(Wrappers.<PostAbilityModel>lambdaQuery()
                .in(PostAbilityModel::getPostId, postIds)
                .select(PostAbilityModel::getPostId))
                .stream()
                .map(PostAbilityModel::getPostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.POST_MODEL, key = "#list[0].postId")
    })
    public void batchConfig(List<PostAbilityModelConfigDTO> list) {
        if (list == null || list.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "batchConfig参数列表不能为空");
        }

        for (PostAbilityModelConfigDTO dto : list) {
            if (dto.getPostId() == null) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "batchConfig参数中包含postId为null的项");
            }
        }

        Long postId = list.get(0).getPostId();
        for (PostAbilityModelConfigDTO dto : list) {
            if (!postId.equals(dto.getPostId())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                        "batchConfig要求所有项属于同一postId，发现不一致: 预期" + postId + "，实际" + dto.getPostId());
            }
        }

        // 兼容历史/AI 使用的 0-1 相对权重，正式模型统一存储为百分比。
        PostAbilityWeightNormalizer.normalizeLegacyRelativeWeights(list);

        // ===== 1. 校验配置合法性 =====
        list.forEach(this::normalizeOptionalTag);
        validateModelConfig(list);

        // ===== 2. 生成模型版本号 =====
        String modelVersion = "v" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        // ===== 3. 先物理删除该岗位旧配置（避免唯一约束冲突）, 验证postId确保不误删 =====
        if (postId == null) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "batchConfig: postId为空，拒绝执行物理删除");
        }
        baseMapper.physicalDeleteByPostId(postId);

        // ===== 4. 写入新配置（带版本号） =====
        List<PostAbilityModel> models = list.stream().map(dto -> {
            PostAbilityModel model = new PostAbilityModel();
            BeanUtils.copyProperties(dto, model);
            model.setId(null); // 确保是新增
            model.setModelVersion(modelVersion);
            applyPanoramaMetadata(dto, model);
            return model;
        }).collect(Collectors.toList());
        saveBatch(models);
        models.forEach(model -> abilityEvidenceIngestionService.ingestPostAbilityModel(model.getId(), "POST_ABILITY_MODEL"));

        // ===== 5. 计算并保存质量评分 =====
        try {
            calculateAndSaveQualityScore(postId, modelVersion, list);
        } catch (Exception e) {
            log.warn("计算岗位模型质量评分失败: postId={}, error={}", postId, e.getMessage());
        }

        // ===== 6. 发布变更事件 =====
        vectorRecallCacheEpoch.advance();
        eventPublisher.publishEvent(new PostModelChangeEvent(this, "MODEL_CONFIG", postId));

        log.info("岗位能力模型批量配置完成: postId={}, version={}, count={}", postId, modelVersion, models.size());
    }

    @Override
    @Transactional
    public void deleteModel(Long modelId) {
        PostAbilityModel model = getById(modelId);
        if (model == null) {
            return;
        }
        Long postId = model.getPostId();
        removeById(modelId);
        if (postId != null) {
            vectorRecallCacheEpoch.advance();
            eventPublisher.publishEvent(new PostModelChangeEvent(this, "MODEL_CONFIG", postId));
        }
    }

    @Override
    public BigDecimal calculateQualityScore(Long postId) {
        List<PostAbilityModel> models = listByPostId(postId);
        if (models.isEmpty()) {
            return BigDecimal.ZERO;
        }

        PostPost post = postPostMapper.selectById(postId);

        BigDecimal weightCompleteness = calculateWeightCompleteness(models);
        BigDecimal coreClarity = calculateCoreClarity(models);
        BigDecimal coverageScore = calculateCoverageScore(models);
        boolean jdExists = post != null && post.getJobDescription() != null && !post.getJobDescription().isBlank();

        BigDecimal jdScore = jdExists ? new BigDecimal("100") : BigDecimal.ZERO;
        BigDecimal qualityScore = weightCompleteness.multiply(new BigDecimal("0.35"))
                .add(coreClarity.multiply(new BigDecimal("0.30")))
                .add(coverageScore.multiply(new BigDecimal("0.25")))
                .add(jdScore.multiply(new BigDecimal("0.10")))
                .setScale(2, RoundingMode.HALF_UP);

        return qualityScore;
    }

    /**
     * 校验岗位能力模型配置
     */
    private void validateModelConfig(List<PostAbilityModelConfigDTO> list) {
        List<Long> requestedTagIds = list.stream().map(PostAbilityModelConfigDTO::getTagId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, AbilityTag> tagsById = requestedTagIds.isEmpty()
                ? new HashMap<>()
                : abilityTagMapper.selectBatchIds(requestedTagIds).stream()
                .collect(Collectors.toMap(AbilityTag::getId, tag -> tag, (a, b) -> a));
        for (PostAbilityModelConfigDTO dto : list) {
            if (dto.getTagId() != null && !AbilityTagHierarchy.isAssessable(tagsById.get(dto.getTagId()))) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                        "岗位能力模型只能配置启用的 L2 可评估能力: tagId=" + dto.getTagId());
            }
            if (!StringUtils.hasText(dto.getAbilityName())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                        "岗位能力模型项必须提供可验证的技能点名称");
            }
        }
        // 1. 同一岗位不能重复配置同一正式标签或同名未挂标签能力。
        Set<String> abilityKeys = new HashSet<>();
        for (PostAbilityModelConfigDTO dto : list) {
            String abilityKey = skillPointKey(dto.getAbilityName());
            if (!abilityKeys.add(abilityKey)) {
                throw new BusinessException(ErrorCodeEnum.POST_MODEL_TAG_DUPLICATE,
                        "岗位能力重复配置: " + abilityKey);
            }
        }

        // 2. 权重总和必须在 95-105 范围内
        for (PostAbilityModelConfigDTO dto : list) {
            if (dto.getWeight() != null && dto.getWeight().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ErrorCodeEnum.POST_MODEL_WEIGHT_INVALID,
                        "能力项权重不能为负数，tagId=" + dto.getTagId());
            }
        }
        BigDecimal totalWeight = list.stream()
                .map(d -> d.getWeight() != null ? d.getWeight() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(new BigDecimal("95")) < 0 || totalWeight.compareTo(new BigDecimal("105")) > 0) {
            throw new BusinessException(ErrorCodeEnum.POST_MODEL_WEIGHT_INVALID,
                    "当前权重总和为 " + totalWeight + "，应在95-105之间");
        }

        // 3. 必填能力不能权重为 0
        for (PostAbilityModelConfigDTO dto : list) {
            if (dto.getIsRequired() != null && dto.getIsRequired() == 1) {
                if (dto.getWeight() == null || dto.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException(ErrorCodeEnum.POST_MODEL_REQUIRED_WEIGHT_ZERO,
                            "必填能力项(tagId=" + dto.getTagId() + ")的权重不能为0");
                }
            }
        }

        // 5. 核心项建议权重不低于 15（警告，不阻断）
        for (PostAbilityModelConfigDTO dto : list) {
            if (dto.getIsCore() != null && dto.getIsCore() == 1) {
                if (dto.getWeight() != null && dto.getWeight().compareTo(new BigDecimal("15")) < 0) {
                    log.warn("核心能力项(tagId={})权重{}低于建议值15", dto.getTagId(), dto.getWeight());
                }
            }
        }
    }

    private void validateSingleAbilityIdentity(PostAbilityModelConfigDTO dto) {
        if (!StringUtils.hasText(dto.getAbilityName())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "岗位能力模型项必须提供技能点名称");
        }
        if (dto.getTagId() != null && !AbilityTagHierarchy.isAssessable(abilityTagMapper.selectById(dto.getTagId()))) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "岗位能力模型只能配置启用的 L2 可评估能力: tagId=" + dto.getTagId());
        }
    }

    /** 标签库只是可选关联；失效标签不能阻断岗位正式能力配置。 */
    private void normalizeOptionalTag(PostAbilityModelConfigDTO dto) {
        if (dto == null || dto.getTagId() == null) {
            return;
        }
        AbilityTag tag = abilityTagMapper.selectById(dto.getTagId());
        if (!AbilityTagHierarchy.isAssessable(tag)) {
            log.warn("岗位能力标签关联无效，按未关联标签保存: postId={}, tagId={}, abilityName={}",
                    dto.getPostId(), dto.getTagId(), dto.getAbilityName());
            dto.setTagId(null);
        }
    }

    private void applyPanoramaMetadata(PostAbilityModelConfigDTO dto, PostAbilityModel model) {
        model.setAbilityName(dto.getAbilityName().trim());
        model.setTechStack(resolveTechStack(dto.getTechStack(), dto.getAbilityName()));
        model.setSkillPointKey(skillPointKey(dto.getAbilityName()));
    }

    private String skillPointKey(String abilityName) {
        return abilityName.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String resolveTechStack(String techStack, String abilityName) {
        if (StringUtils.hasText(techStack)) return techStack.trim();
        String text = abilityName == null ? "" : abilityName.toLowerCase(Locale.ROOT);
        if (text.contains("spring")) return "Spring";
        if (text.contains("java")) return "Java";
        if (text.contains("mysql") || text.contains("sql") || text.contains("数据库")) return "数据存储";
        if (text.contains("redis")) return "Redis";
        if (text.contains("rabbitmq") || text.contains("kafka") || text.contains("消息")) return "消息队列";
        if (text.contains("docker") || text.contains("kubernetes") || text.contains("k8s")) return "云原生";
        return "通用工程能力";
    }

    /**
     * 计算并保存质量评分
     */
    private void calculateAndSaveQualityScore(Long postId, String modelVersion, List<PostAbilityModelConfigDTO> configList) {
        List<PostAbilityModel> models = configList.stream().map(dto -> {
            PostAbilityModel m = new PostAbilityModel();
            BeanUtils.copyProperties(dto, m);
            return m;
        }).collect(Collectors.toList());

        PostPost post = postPostMapper.selectById(postId);

        BigDecimal weightCompleteness = calculateWeightCompleteness(models);
        BigDecimal coreClarity = calculateCoreClarity(models);
        BigDecimal coverageScore = calculateCoverageScore(models);
        boolean jdExists = post != null && post.getJobDescription() != null && !post.getJobDescription().isBlank();

        BigDecimal jdScore = jdExists ? new BigDecimal("100") : BigDecimal.ZERO;
        BigDecimal qualityScore = weightCompleteness.multiply(new BigDecimal("0.35"))
                .add(coreClarity.multiply(new BigDecimal("0.30")))
                .add(coverageScore.multiply(new BigDecimal("0.25")))
                .add(jdScore.multiply(new BigDecimal("0.10")))
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("totalWeight", models.stream().map(PostAbilityModel::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        detail.put("itemCount", models.size());
        detail.put("coreCount", models.stream().filter(m -> m.getIsCore() != null && m.getIsCore() == 1).count());
        detail.put("requiredCount", models.stream().filter(m -> m.getIsRequired() != null && m.getIsRequired() == 1).count());
        detail.put("jdExists", jdExists);

        PostModelQuality quality = new PostModelQuality();
        quality.setPostId(postId);
        quality.setModelVersion(modelVersion);
        quality.setQualityScore(qualityScore);
        quality.setWeightCompleteness(weightCompleteness);
        quality.setCoreClarity(coreClarity);
        quality.setCoverageScore(coverageScore);
        quality.setJdExists(jdExists ? 1 : 0);
        try {
            quality.setQualityDetail(objectMapper.writeValueAsString(detail));
        } catch (Exception e) {
            quality.setQualityDetail("{}");
        }
        postModelQualityMapper.insert(quality);
    }

    /**
     * 权重完整度评分：权重总和越接近100分越高
     */
    private BigDecimal calculateWeightCompleteness(List<PostAbilityModel> models) {
        BigDecimal totalWeight = models.stream()
                .map(m -> m.getWeight() != null ? m.getWeight() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diff = totalWeight.subtract(new BigDecimal("100")).abs();
        // 偏差越大分数越低，满分100，偏差5分扣到0
        BigDecimal score = new BigDecimal("100").subtract(diff.multiply(new BigDecimal("20")))
                .max(BigDecimal.ZERO).min(new BigDecimal("100"));
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 核心项清晰度评分：有核心项且核心项权重合理得高分
     */
    private BigDecimal calculateCoreClarity(List<PostAbilityModel> models) {
        long coreCount = models.stream().filter(m -> m.getIsCore() != null && m.getIsCore() == 1).count();
        if (coreCount == 0) return BigDecimal.ZERO;

        BigDecimal avgCoreWeight = models.stream()
                .filter(m -> m.getIsCore() != null && m.getIsCore() == 1)
                .map(m -> m.getWeight() != null ? m.getWeight() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(coreCount), 2, RoundingMode.HALF_UP);

        // 核心项平均权重 >= 20 为满分，< 10 为 50 分
        BigDecimal score = avgCoreWeight.multiply(new BigDecimal("2.5"))
                .add(new BigDecimal("50"))
                .min(new BigDecimal("100"));
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 覆盖度评分：能力标签是否覆盖技术/业务/软技能三大类
     */
    private BigDecimal calculateCoverageScore(List<PostAbilityModel> models) {
        Set<String> categories = new HashSet<>();
        for (PostAbilityModel m : models) {
            AbilityTag tag = abilityTagMapper.selectById(m.getTagId());
            if (tag != null && tag.getTagCategory() != null) {
                categories.add(tag.getTagCategory());
            }
        }
        return switch (categories.size()) {
            case 3 -> new BigDecimal("100");
            case 2 -> new BigDecimal("70");
            case 1 -> new BigDecimal("40");
            default -> BigDecimal.ZERO;
        };
    }
}
