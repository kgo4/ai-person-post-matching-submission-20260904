package com.example.matching.service.agent.impl;

import com.example.matching.agent.dto.person.PersonAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.agent.dto.post.PostAbilityExtractionResult;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.dto.governance.GovernanceAdmission;
import com.example.matching.event.KnowledgeGraphRebuildRequestedEvent;
import com.example.matching.event.PostModelChangeEvent;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.service.agent.AgentBusinessApplyService;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.ability.PersonAbilityClaimAdmissionService;
import com.example.matching.service.governance.GovernedAdmissionService;
import com.example.matching.service.post.PostAbilityWeightNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent 业务应用服务实现
 * <p>
 * 所有 AI 能力声明写入正式事实表之前，必须通过 GovernedAdmissionService 取得可验证准入凭证：
 * - PASS 才能写正式能力/岗位事实
 * - REVIEW 只写待审候选记录
 * - BLOCK/RETRY 不写任何业务结果
 *
 * @author system
 */
@Slf4j
@Service
public class AgentBusinessApplyServiceImpl implements AgentBusinessApplyService {

    private final GovernedAdmissionService governedAdmissionService;
    private final PersonAbilityClaimAdmissionService personAbilityClaimAdmissionService;
    private final AbilityEvidenceIngestionService abilityEvidenceIngestionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PostAbilityModelMapper postAbilityModelMapper;

    public AgentBusinessApplyServiceImpl(
            GovernedAdmissionService governedAdmissionService,
            PersonAbilityClaimAdmissionService personAbilityClaimAdmissionService,
            AbilityEvidenceIngestionService abilityEvidenceIngestionService,
            ApplicationEventPublisher eventPublisher,
            PostAbilityModelMapper postAbilityModelMapper,
            com.example.matching.service.common.VectorRecallCacheEpoch vectorRecallCacheEpoch,
            com.example.matching.agent.service.AgentClaimConflictDetector conflictDetector) {
        this.governedAdmissionService = governedAdmissionService;
        this.personAbilityClaimAdmissionService = personAbilityClaimAdmissionService;
        this.abilityEvidenceIngestionService = abilityEvidenceIngestionService;
        this.eventPublisher = eventPublisher;
        this.postAbilityModelMapper = postAbilityModelMapper;
        this.vectorRecallCacheEpoch = vectorRecallCacheEpoch;
        this.conflictDetector = conflictDetector;
    }

    private final com.example.matching.service.common.VectorRecallCacheEpoch vectorRecallCacheEpoch;
    private final com.example.matching.agent.service.AgentClaimConflictDetector conflictDetector;

    @Override
    @Transactional
    public PersonAbilityApplyResult applyPersonAbilities(PersonAbilityExtractionResult extractionResult) {
        return applyPersonAbilities(extractionResult, false);
    }

    @Override
    @Transactional
    public PersonAbilityApplyResult applyPersonAbilities(PersonAbilityExtractionResult extractionResult,
                                                          boolean coalesceEmployeeRefresh) {
        if (extractionResult == null || extractionResult.getClaims() == null) {
            return new PersonAbilityApplyResult(0, 0, 0, 0, 0);
        }

        List<PersonAbilityClaim> claims = extractionResult.getClaims();
        java.util.Set<String> conflictingKeys = new java.util.HashSet<>(
                conflictDetector.detectPersonClaimConflicts(claims));
        int passCount = 0;
        int reviewCount = 0;
        int blockCount = 0;
        int errorCount = 0;
        Set<Long> employeesWithPassedClaims = new HashSet<>();

        for (PersonAbilityClaim claim : claims) {
            try {
                // 矛盾声明强制 REVIEW：同标签同来源出现不兼容等级，绝不自动 PASS
                if (isConflicting(claim.getAbilityTagId(), claim.getSourceType(), conflictingKeys)) {
                    reviewCount++;
                    continue;
                }
                GovernanceAdmission admission = governedAdmissionService.admitPersonAbility(claim);
                if (admission.isAdmitted()) {
                    passCount++;
                    if (admission.getBusinessTargetId() != null) {
                        abilityEvidenceIngestionService.ingestEmployeeAbility(
                                admission.getBusinessTargetId(), "EMP_ABILITY");
                    }
                    if (coalesceEmployeeRefresh) {
                        if (claim.getEmpId() != null) {
                            employeesWithPassedClaims.add(claim.getEmpId());
                        }
                    } else {
                        personAbilityClaimAdmissionService.completeBatchForEmployee(claim.getEmpId());
                    }
                } else if (admission.isPendingReview()) {
                    reviewCount++;
                } else if (admission.isBlocked()) {
                    blockCount++;
                } else {
                    errorCount++;
                }
            } catch (Exception e) {
                log.error("处理人员能力声明失败: {}", claim.getAbilityName(), e);
                errorCount++;
            }
        }

        if (coalesceEmployeeRefresh) {
            employeesWithPassedClaims.forEach(personAbilityClaimAdmissionService::completeBatchForEmployee);
        }

        if (passCount > 0) {
            triggerGraphRefresh();
        }

        return new PersonAbilityApplyResult(claims.size(), passCount, reviewCount, blockCount, errorCount);
    }

    private boolean isConflicting(Long tagId, String sourceType, java.util.Set<String> conflictingKeys) {
        if (tagId == null) {
            return false;
        }
        return conflictingKeys.contains(tagId + "|" + (sourceType != null ? sourceType : "UNKNOWN"));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisCacheNames.POST_MODEL, allEntries = true)
    })
    public PostAbilityApplyResult applyPostAbilities(PostAbilityExtractionResult extractionResult) {
        if (extractionResult == null || extractionResult.getClaims() == null) {
            return new PostAbilityApplyResult(0, 0, 0, 0, 0);
        }

        List<PostAbilityClaim> claims = extractionResult.getClaims();
        int passCount = 0;
        int reviewCount = 0;
        int blockCount = 0;
        int errorCount = 0;

        for (PostAbilityClaim claim : claims) {
            try {
                if (!claim.isValid()) {
                    blockCount++;
                    continue;
                }
                Long modelId = upsertValidatedPostAbility(claim);
                passCount++;
                eventPublisher.publishEvent(new com.example.matching.event.PostAbilityEvidenceIngestionRequestedEvent(
                        modelId, "POST_ABILITY_MODEL"));
                eventPublisher.publishEvent(new PostModelChangeEvent(this, "MODEL_CONFIG", claim.getPostId()));
            } catch (Exception e) {
                log.error("处理岗位能力声明失败: {}", claim.getAbilityName(), e);
                errorCount++;
            }
        }

        if (passCount > 0) {
            vectorRecallCacheEpoch.advance();
        }

        return new PostAbilityApplyResult(claims.size(), passCount, reviewCount, blockCount, errorCount);
    }

    private Long upsertValidatedPostAbility(PostAbilityClaim claim) {
        String abilityName = claim.getAbilityName().trim();
        String skillPointKey = skillPointKey(abilityName);
        LambdaQueryWrapper<PostAbilityModel> query = new LambdaQueryWrapper<PostAbilityModel>()
                .eq(PostAbilityModel::getPostId, claim.getPostId())
                .eq(PostAbilityModel::getSkillPointKey, skillPointKey)
                .eq(PostAbilityModel::getIsDeleted, 0)
                .last("LIMIT 1");
        PostAbilityModel model = postAbilityModelMapper.selectOne(query);
        if (model == null) {
            model = new PostAbilityModel();
            model.setPostId(claim.getPostId());
            model.setIsDeleted(0);
        }
        model.setTagId(claim.getAbilityTagId());
        model.setAbilityName(abilityName);
        model.setTechStack(resolveTechStack(claim.getTechStack(), abilityName));
        model.setSkillPointKey(skillPointKey);
        model.setMinRequiredLevel(claim.getRequiredLevel() != null ? claim.getRequiredLevel() : 3);
        model.setWeight(PostAbilityWeightNormalizer.toPercentage(claim.getWeight(), new java.math.BigDecimal("5")));
        model.setIsCore(Boolean.TRUE.equals(claim.getIsCore()) ? 1 : 0);
        model.setIsRequired(Boolean.TRUE.equals(claim.getIsRequired()) ? 1 : 0);
        model.setRemark(claim.getEvidenceText());
        model.setSourceType(claim.getSourceType());
        model.setGovernanceAdmissionId(null);
        if (model.getId() == null) {
            postAbilityModelMapper.insert(model);
        } else {
            postAbilityModelMapper.updateById(model);
        }
        return model.getId();
    }

    private String skillPointKey(String abilityName) {
        return abilityName.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String resolveTechStack(String techStack, String abilityName) {
        if (techStack != null && !techStack.isBlank()) {
            return techStack.trim();
        }
        String text = abilityName.toLowerCase(Locale.ROOT);
        if (text.contains("spring")) return "Spring";
        if (text.contains("java")) return "Java";
        if (text.contains("mysql") || text.contains("sql") || text.contains("数据库")) return "数据存储";
        if (text.contains("redis")) return "Redis";
        if (text.contains("rabbitmq") || text.contains("kafka") || text.contains("消息")) return "消息队列";
        if (text.contains("docker") || text.contains("kubernetes") || text.contains("k8s")) return "云原生";
        return "通用工程能力";
    }

    /**
     * 触发图谱刷新（带 10 秒冷却，避免频繁重建）
     */
    private final AtomicLong lastGraphRefreshAt = new AtomicLong(0);

    private void triggerGraphRefresh() {
        long now = System.currentTimeMillis();
        while (true) {
            long previous = lastGraphRefreshAt.get();
            if (now - previous < 10_000) {
                return;
            }
            if (lastGraphRefreshAt.compareAndSet(previous, now)) {
                eventPublisher.publishEvent(new KnowledgeGraphRebuildRequestedEvent());
                return;
            }
        }
    }
}
