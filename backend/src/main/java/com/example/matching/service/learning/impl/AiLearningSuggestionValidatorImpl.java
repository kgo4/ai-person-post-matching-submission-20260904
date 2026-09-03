package com.example.matching.service.learning.impl;

import com.example.matching.dto.kg.context.GraphLearningPrerequisiteContext;
import com.example.matching.dto.learning.AiLearningSuggestionDTO;
import com.example.matching.entity.learning.LearningResource;
import com.example.matching.common.util.AbilityNameNormalizer;
import com.example.matching.service.learning.AiLearningSuggestionValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI 学习建议校验器实现
 * <p>
 * 校验规则：
 * 1. resourceId 是否真实存在
 * 2. abilityTagId 是否来自诊断差距
 * 3. title/url/resourceType 是否和数据库一致
 * 4. sourceRefs 是否能追溯到资源或证据
 * 5. AI 是否生成了额外能力或链接
 *
 * @author system
 */
@Slf4j
@Service
public class AiLearningSuggestionValidatorImpl implements AiLearningSuggestionValidator {

    @Override
    public AiLearningSuggestionDTO.ValidationSummary validate(
            List<AiLearningSuggestionDTO.AbilitySuggestion> suggestions,
            Set<String> allowedAbilityNames,
            Map<Long, LearningResource> resourceMap) {

        AiLearningSuggestionDTO.ValidationSummary summary = new AiLearningSuggestionDTO.ValidationSummary();
        int totalSteps = 0;
        int validatedSteps = 0;
        int filteredSteps = 0;
        List<String> details = new ArrayList<>();

        for (AiLearningSuggestionDTO.AbilitySuggestion suggestion : suggestions) {
            // 校验 abilityName 是否来自差距诊断
            String normalizedName = AbilityNameNormalizer.normalize(suggestion.getAbilityName());
            boolean abilityAllowed = allowedAbilityNames.stream()
                    .anyMatch(name -> AbilityNameNormalizer.normalize(name).equals(normalizedName));

            if (!abilityAllowed) {
                suggestion.setInsufficientEvidence(true);
                suggestion.setSteps(List.of());
                filteredSteps++;
                details.add("能力「" + suggestion.getAbilityName() + "」不在差距诊断中，已过滤");
                log.warn("AI建议校验：能力不在差距诊断中，abilityName={}", suggestion.getAbilityName());
                continue;
            }

            // 校验每个步骤
            List<AiLearningSuggestionDTO.LearningStep> validSteps = new ArrayList<>();
            for (AiLearningSuggestionDTO.LearningStep step : suggestion.getSteps()) {
                totalSteps++;
                String failureReason = validateStep(step, resourceMap);
                if (failureReason == null) {
                    step.setValidated(true);
                    validSteps.add(step);
                    validatedSteps++;
                } else {
                    step.setValidated(false);
                    step.setValidationFailureReason(failureReason);
                    filteredSteps++;
                    details.add("步骤被过滤: resourceId=" + step.getResourceId()
                            + ", 原因=" + failureReason);
                    log.warn("AI建议校验：步骤被过滤，resourceId={}, reason={}",
                            step.getResourceId(), failureReason);
                }
            }

            suggestion.setSteps(validSteps);

            // 如果所有步骤都被过滤，标记为证据不足
            if (validSteps.isEmpty() && !suggestion.isInsufficientEvidence()) {
                suggestion.setInsufficientEvidence(true);
                details.add("能力「" + suggestion.getAbilityName() + "」所有步骤均校验失败，标记为证据不足");
            }
        }

        summary.setTotalSteps(totalSteps);
        summary.setValidatedSteps(validatedSteps);
        summary.setFilteredSteps(filteredSteps);
        summary.setHasInsufficientEvidence(
                suggestions.stream().anyMatch(AiLearningSuggestionDTO.AbilitySuggestion::isInsufficientEvidence));
        summary.setDetails(details);

        return summary;
    }

    @Override
    public AiLearningSuggestionDTO.ValidationSummary validate(
            List<AiLearningSuggestionDTO.AbilitySuggestion> suggestions,
            Set<String> allowedAbilityNames,
            Map<Long, LearningResource> resourceMap,
            Set<Long> allowedTagIds,
            GraphLearningPrerequisiteContext graphPrerequisites) {

        AiLearningSuggestionDTO.ValidationSummary summary = new AiLearningSuggestionDTO.ValidationSummary();
        int totalSteps = 0;
        int validatedSteps = 0;
        int filteredSteps = 0;
        List<String> details = new ArrayList<>();

        for (AiLearningSuggestionDTO.AbilitySuggestion suggestion : suggestions) {
            String normalizedName = AbilityNameNormalizer.normalize(suggestion.getAbilityName());
            boolean abilityAllowed = allowedAbilityNames.stream()
                    .anyMatch(name -> AbilityNameNormalizer.normalize(name).equals(normalizedName));

            if (!abilityAllowed) {
                suggestion.setInsufficientEvidence(true);
                suggestion.setSteps(List.of());
                filteredSteps++;
                details.add("能力「" + suggestion.getAbilityName() + "」不在差距诊断中，已过滤");
                log.warn("AI建议校验：能力不在差距诊断中，abilityName={}", suggestion.getAbilityName());
                continue;
            }

            // abilityName 是学习差距与人员正式能力的业务身份；tagId 仅用于图谱增强，缺失或过期均不得阻断学习建议。
            if (suggestion.getTagId() != null && allowedTagIds != null && !allowedTagIds.isEmpty()
                    && !allowedTagIds.contains(suggestion.getTagId())) {
                log.info("AI建议标签未命中当前差距，保留按能力名校验通过的建议: tagId={}, abilityName={}",
                        suggestion.getTagId(), suggestion.getAbilityName());
            }

            List<AiLearningSuggestionDTO.LearningStep> validSteps = new ArrayList<>();
            for (AiLearningSuggestionDTO.LearningStep step : suggestion.getSteps()) {
                totalSteps++;
                String failureReason = validateStep(step, resourceMap);
                if (failureReason == null) {
                    step.setValidated(true);
                    validSteps.add(step);
                    validatedSteps++;
                } else {
                    step.setValidated(false);
                    step.setValidationFailureReason(failureReason);
                    filteredSteps++;
                    details.add("步骤被过滤: resourceId=" + step.getResourceId()
                            + ", 原因=" + failureReason);
                    log.warn("AI建议校验：步骤被过滤，resourceId={}, reason={}",
                            step.getResourceId(), failureReason);
                }
            }

            suggestion.setSteps(validSteps);

            if (validSteps.isEmpty() && !suggestion.isInsufficientEvidence()) {
                suggestion.setInsufficientEvidence(true);
                details.add("能力「" + suggestion.getAbilityName() + "」所有步骤均校验失败，标记为证据不足");
            }
        }

        orderByGraphPrerequisites(suggestions, graphPrerequisites);

        summary.setTotalSteps(totalSteps);
        summary.setValidatedSteps(validatedSteps);
        summary.setFilteredSteps(filteredSteps);
        summary.setHasInsufficientEvidence(
                suggestions.stream().anyMatch(AiLearningSuggestionDTO.AbilitySuggestion::isInsufficientEvidence));
        summary.setDetails(details);

        return summary;
    }

    private void orderByGraphPrerequisites(List<AiLearningSuggestionDTO.AbilitySuggestion> suggestions,
                                           GraphLearningPrerequisiteContext graphPrerequisites) {
        if (suggestions == null || suggestions.size() < 2 || graphPrerequisites == null
                || graphPrerequisites.prerequisites() == null || graphPrerequisites.prerequisites().isEmpty()) {
            return;
        }

        Map<Long, Integer> originalOrder = new LinkedHashMap<>();
        for (int index = 0; index < suggestions.size(); index++) {
            Long tagId = suggestions.get(index).getTagId();
            if (tagId != null) {
                originalOrder.putIfAbsent(tagId, index);
            }
        }

        Map<Long, Set<Long>> dependentsByPrerequisite = new HashMap<>();
        Map<Long, Integer> indegree = new HashMap<>();
        originalOrder.keySet().forEach(tagId -> indegree.put(tagId, 0));
        for (GraphLearningPrerequisiteContext.PrerequisiteNode prerequisite : graphPrerequisites.prerequisites()) {
            Long abilityId = prerequisite.abilityId();
            Long prerequisiteId = prerequisite.prerequisiteAbilityId();
            if (!originalOrder.containsKey(abilityId) || !originalOrder.containsKey(prerequisiteId)
                    || abilityId.equals(prerequisiteId)) {
                continue;
            }
            if (dependentsByPrerequisite.computeIfAbsent(prerequisiteId, ignored -> new LinkedHashSet<>())
                    .add(abilityId)) {
                indegree.computeIfPresent(abilityId, (ignored, value) -> value + 1);
            }
        }

        PriorityQueue<Long> ready = new PriorityQueue<>(Comparator.comparingInt(originalOrder::get));
        indegree.forEach((tagId, degree) -> {
            if (degree == 0) {
                ready.add(tagId);
            }
        });

        Map<Long, Integer> sortedOrder = new HashMap<>();
        int position = 0;
        while (!ready.isEmpty()) {
            Long tagId = ready.poll();
            sortedOrder.put(tagId, position++);
            for (Long dependent : dependentsByPrerequisite.getOrDefault(tagId, Set.of())) {
                int remaining = indegree.computeIfPresent(dependent, (ignored, value) -> value - 1);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (sortedOrder.size() != originalOrder.size()) {
            log.warn("Learning prerequisite graph contains a cycle for tags {}, retaining model order", originalOrder.keySet());
            return;
        }
        suggestions.sort(Comparator.comparingInt(suggestion ->
                sortedOrder.getOrDefault(suggestion.getTagId(), Integer.MAX_VALUE)));
    }

    /**
     * 校验单个步骤
     *
     * @return null 表示通过校验，否则返回失败原因
     */
    private String validateStep(AiLearningSuggestionDTO.LearningStep step,
                                Map<Long, LearningResource> resourceMap) {
        // 1. resourceId 必须存在
        if (step.getResourceId() == null) {
            return "resourceId 为空";
        }

        // 2. resourceId 必须在系统资源库中
        LearningResource resource = resourceMap.get(step.getResourceId());
        if (resource == null) {
            return "resourceId=" + step.getResourceId() + " 在系统资源库中不存在";
        }

        // 3. title 必须与数据库一致
        if (step.getTitle() != null && !step.getTitle().isBlank()
                && !step.getTitle().equals(resource.getTitle())) {
            // 修正标题而不是过滤
            log.debug("AI建议校验：修正标题，AI={}, DB={}", step.getTitle(), resource.getTitle());
            step.setTitle(resource.getTitle());
        }

        // 4. url 必须与数据库一致（如果AI提供了url）
        if (step.getUrl() != null && !step.getUrl().isBlank()
                && resource.getUrl() != null && !step.getUrl().equals(resource.getUrl())) {
            log.debug("AI建议校验：修正URL，AI={}, DB={}", step.getUrl(), resource.getUrl());
            step.setUrl(resource.getUrl());
        }

        // 5. resourceType 必须与数据库一致
        if (step.getResourceType() != null && !step.getResourceType().isBlank()
                && resource.getResourceType() != null
                && !step.getResourceType().equals(resource.getResourceType())) {
            log.debug("AI建议校验：修正资源类型，AI={}, DB={}",
                    step.getResourceType(), resource.getResourceType());
            step.setResourceType(resource.getResourceType());
        }

        // 6. 补全缺失字段
        if (step.getTitle() == null || step.getTitle().isBlank()) {
            step.setTitle(resource.getTitle());
        }
        if (step.getUrl() == null || step.getUrl().isBlank()) {
            step.setUrl(resource.getUrl());
        }
        if (step.getResourceType() == null || step.getResourceType().isBlank()) {
            step.setResourceType(resource.getResourceType());
        }
        if (step.getDifficultyLevel() == null) {
            step.setDifficultyLevel(resource.getDifficultyLevel());
        }

        // 7. sourceRefs 校验
        if (step.getSourceRefs() != null) {
            List<String> validRefs = new ArrayList<>();
            for (String ref : step.getSourceRefs()) {
                if (ref != null && ref.startsWith("resource:")) {
                    try {
                        Long refId = Long.parseLong(ref.substring("resource:".length()));
                        if (resourceMap.containsKey(refId)) {
                            validRefs.add(ref);
                        }
                    } catch (NumberFormatException e) {
                        // 忽略无效引用
                    }
                } else if (ref != null && ref.startsWith("evidence:")) {
                    // 证据引用保留，后续可追溯
                    validRefs.add(ref);
                }
            }
            step.setSourceRefs(validRefs);
        }

        return null; // 校验通过
    }

}
