package com.example.matching.ai.validation;

import com.example.matching.dto.assessment.AssessmentScopeDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评估题目绑定校验器。
 * <p>
 * 不可变业务规则（见实施计划 §0 规则 4）：AI 测试和 AI 面试只能验证
 * {@link AssessmentScopeDTO} 中已有标签，不得新增、改名或输出无法映射的 tagId。
 * <p>
 * 逐题校验题目是否绑定到 scope 内能力标签与岗位要求，并校验来源简历 Claim。
 * 非法题被剔除（不得降级成泛化题）；非法题比例超过阈值时阶段为 INVALID_OUTPUT；
 * scope 能力未全部覆盖时阶段为 INSUFFICIENT_COVERAGE。
 */
@Component
public class AssessmentQuestionBindingValidator {

    /** 非法题比例阈值（百分比），超过则整体 INVALID_OUTPUT */
    public static final int MAX_INVALID_RATIO_PERCENT = 30;

    private static final List<String> ALLOWED_VERIFICATION_TYPES =
            List.of("CLAIM_RECALL", "POST_SCENARIO", "LEVEL_DISCRIMINATION");

    /**
     * 校验结果。
     *
     * @param outputValid           非法题比例未超阈值（否则阶段应置 INVALID_OUTPUT）
     * @param coverageSufficient    scope 内每个能力至少被一道合法题覆盖（否则 INSUFFICIENT_COVERAGE）
     * @param violations            违规明细
     * @param validQuestions        通过校验的合法题
     * @param rejectedCount         被剔除的非法题数
     * @param coveredAbilityTagIds  被合法题覆盖的能力标签 ID
     * @param uncoveredAbilityTagIds scope 内未被覆盖的能力标签 ID
     */
    public record BindingValidationResult(
            boolean outputValid,
            boolean coverageSufficient,
            List<String> violations,
            List<Map<String, Object>> validQuestions,
            int rejectedCount,
            List<Long> coveredAbilityTagIds,
            List<Long> uncoveredAbilityTagIds) {
    }

    /**
     * 逐题校验绑定关系。
     *
     * @param scope     评估范围（交集 + 岗位要求映射）
     * @param questions 标准化后的题目列表（每题 Map，含 abilityTagId/postRequirementId/sourceClaimIds）
     */
    public BindingValidationResult validate(AssessmentScopeDTO scope, List<Map<String, Object>> questions) {
        Set<Long> allowedTagIds = new LinkedHashSet<>();
        Map<Long, Long> postReqByTag = new HashMap<>();
        Map<Long, Set<Long>> claimIdsByTag = new HashMap<>();
        Set<Long> allowedPostReqIds = new HashSet<>();
        Set<Long> allowedClaimIds = new HashSet<>();
        for (AssessmentScopeDTO.AssessmentScopeItem item : scope.items()) {
            allowedTagIds.add(item.abilityTagId());
            if (item.postRequirementId() != null) {
                postReqByTag.put(item.abilityTagId(), item.postRequirementId());
                allowedPostReqIds.add(item.postRequirementId());
            }
            allowedClaimIds.addAll(item.resumeClaimIds());
            claimIdsByTag.put(item.abilityTagId(), new HashSet<>(item.resumeClaimIds()));
        }

        List<String> violations = new ArrayList<>();
        List<Map<String, Object>> validQuestions = new ArrayList<>();
        Set<Long> coveredTagIds = new LinkedHashSet<>();

        if (questions != null) {
            for (int i = 0; i < questions.size(); i++) {
                Map<String, Object> q = questions.get(i);
                List<String> itemViolations = validateItem(
                        q, i, allowedTagIds, postReqByTag, allowedPostReqIds, allowedClaimIds, claimIdsByTag);
                if (itemViolations.isEmpty()) {
                    validQuestions.add(q);
                    coveredTagIds.addAll(resolveAbilityTagIds(q));
                } else {
                    violations.addAll(itemViolations);
                }
            }
        }

        int rejected = questions == null ? 0 : questions.size() - validQuestions.size();
        boolean outputValid = questions != null && !questions.isEmpty()
                && (rejected * 100 / questions.size()) <= MAX_INVALID_RATIO_PERCENT;

        List<Long> uncoveredTagIds = new ArrayList<>();
        for (Long tagId : allowedTagIds) {
            if (!coveredTagIds.contains(tagId)) {
                uncoveredTagIds.add(tagId);
            }
        }
        boolean coverageSufficient = uncoveredTagIds.isEmpty();

        return new BindingValidationResult(outputValid, coverageSufficient, violations,
                validQuestions, rejected, new ArrayList<>(coveredTagIds), uncoveredTagIds);
    }

    private List<String> validateItem(Map<String, Object> q, int index,
                                      Set<Long> allowedTagIds,
                                       Map<Long, Long> postReqByTag,
                                       Set<Long> allowedPostReqIds,
                                       Set<Long> allowedClaimIds,
                                       Map<Long, Set<Long>> claimIdsByTag) {
        List<String> violations = new ArrayList<>();
        String prefix = "questions[" + index + "]";

        List<Long> abilityTagIds = resolveAbilityTagIds(q);
        if (abilityTagIds.isEmpty()) {
            violations.add(prefix + ": 未知 abilityTagId（缺失）");
        } else if (!allowedTagIds.containsAll(abilityTagIds)) {
            violations.add(prefix + ": 评估范围外 abilityTagIds=" + abilityTagIds);
        }

        Long postRequirementId = toLong(q.get("postRequirementId"));
        if (postRequirementId != null) {
            if (!allowedPostReqIds.contains(postRequirementId)) {
                violations.add(prefix + ": 未知 postRequirementId=" + postRequirementId);
            }
            if (!abilityTagIds.stream().anyMatch(tagId -> postRequirementId.equals(postReqByTag.get(tagId)))) {
                violations.add(prefix + ": postRequirementId=" + postRequirementId
                        + " 与 abilityTagIds=" + abilityTagIds + " 不匹配");
            }
        }

        List<Long> sourceClaimIds = toLongList(q.get("sourceClaimIds"));
        if (sourceClaimIds == null || sourceClaimIds.isEmpty()) {
            violations.add(prefix + ": 空 sourceClaimIds");
        } else if (!allowedClaimIds.containsAll(sourceClaimIds)) {
            violations.add(prefix + ": sourceClaimIds 含 scope 外 Claim=" + sourceClaimIds);
        } else {
            for (Long tagId : abilityTagIds) {
                boolean hasClaimForTag = scopeClaimIdsForTag(tagId, claimIdsByTag, allowedClaimIds, q, sourceClaimIds);
                if (!hasClaimForTag) {
                    violations.add(prefix + ": sourceClaimIds 未覆盖 abilityTagId=" + tagId);
                }
            }
        }

        Object verificationType = q.get("verificationType");
        if (verificationType != null && !String.valueOf(verificationType).isBlank()
                && !ALLOWED_VERIFICATION_TYPES.contains(String.valueOf(verificationType))) {
            violations.add(prefix + ": 非法 verificationType=" + verificationType);
        }

        return violations;
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Long> toLongList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Long> result = new ArrayList<>();
        for (Object item : list) {
            Long l = toLong(item);
            if (l == null) {
                return null;
            }
            result.add(l);
        }
        return result;
    }

    private static List<Long> resolveAbilityTagIds(Map<String, Object> question) {
        List<Long> multiple = toLongList(question.get("abilityTagIds"));
        if (multiple != null && !multiple.isEmpty()) {
            return multiple.stream().distinct().toList();
        }
        Long single = toLong(firstNonNull(question.get("assessmentAbilityId"),
                firstNonNull(question.get("abilityTagId"), question.get("tagId"))));
        return single == null ? List.of() : List.of(single);
    }

    private boolean scopeClaimIdsForTag(Long tagId, Map<Long, Set<Long>> claimIdsByTag, Set<Long> allowedClaimIds,
                                        Map<String, Object> question, List<Long> sourceClaimIds) {
        // Source claims are validated against the exact scope item in validate(), where the only
        // reliable mapping is supplied through per-tag sourceClaimIds when a question is aggregated.
        Object bindings = question.get("verificationBindings");
        if (bindings instanceof List<?> list) {
            for (Object binding : list) {
                if (binding instanceof Map<?, ?> map) {
                    Long bindingTagId = toLong(firstNonNull(map.get("abilityTagId"), map.get("assessmentAbilityId")));
                    if (!tagId.equals(bindingTagId)) {
                        continue;
                    }
                    List<Long> ids = toLongList(map.get("sourceClaimIds"));
                    return ids != null && !ids.isEmpty() && sourceClaimIds.containsAll(ids)
                            && allowedClaimIds.containsAll(ids)
                            && claimIdsByTag.getOrDefault(tagId, Set.of()).containsAll(ids);
                }
            }
            return false;
        }
        // Legacy single-tag contract remains valid. Aggregated questions must use bindings so
        // evidence cannot be accidentally attributed to an unrelated resume claim.
        return resolveAbilityTagIds(question).size() == 1 && !sourceClaimIds.isEmpty()
                && claimIdsByTag.getOrDefault(tagId, Set.of()).containsAll(sourceClaimIds);
    }
}
