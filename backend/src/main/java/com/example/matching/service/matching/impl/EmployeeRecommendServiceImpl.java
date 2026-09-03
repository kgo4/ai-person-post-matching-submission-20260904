package com.example.matching.service.matching.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.enums.AbilitySourceCredibility;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.dto.matching.EmployeeRecommendDTO;
import com.example.matching.dto.matching.MatchDetailDTO;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition;
import com.example.matching.dto.matching.PostRecommendDTO;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.port.employee.EmployeeAbilityReadPort;
import com.example.matching.service.post.PostHardConditionRuleService;
import com.example.matching.service.matching.EmployeeRecommendService;
import com.example.matching.service.matching.MatchingAlgorithmService;
import com.example.matching.service.matching.MatchingProfileTextBuilder;
import com.example.matching.service.matching.MatchingSnapshotAssembler;
import com.example.matching.service.matching.MatchingTrainingWeightProfileStore;
import com.example.matching.service.matching.MatchingScoreService;
import com.example.matching.service.matching.MatchingEvidenceScoreCalculator;
import com.example.matching.service.matching.MatchScoreInput;
import com.example.matching.vector.MilvusVectorService;
import com.example.matching.resilience.AiServiceResilience;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 岗位推荐员工服务实现。
 * 统一流程：
 * 1. 先做 Milvus 向量召回
 * 2. 召回缺失时回退到可评分候选集，但语义分记为 missing
 * 3. 再做硬规则过滤、L2 预评分和统一重排
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeRecommendServiceImpl implements EmployeeRecommendService {

    private final MilvusVectorService milvusVectorService;
    private final MatchingAlgorithmService matchingAlgorithmService;
    private final PostHardConditionRuleService postHardConditionRuleService;
    private final EmpEmployeeMapper empEmployeeMapper;
    private final PostPostMapper postPostMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MatchingProfileTextBuilder matchingProfileTextBuilder;
    private final MatchingTrainingWeightProfileStore weightProfileStore;
    private final MatchingScoreService matchingScoreService;
    private final EmployeeAbilityReadPort employeeAbilityReadPort;
    private final AiServiceResilience aiServiceResilience;

    private static final long CANDIDATE_RECALL_TIMEOUT_SECONDS = 12L;

    @Override
    public EmployeeRecommendDTO.Response recommendEmployeesForPost(EmployeeRecommendDTO.Request request) {
        Long postId = request.getPostId();
        int topK = normalizeTopK(request.getTopK());

        PostPost post = postPostMapper.selectById(postId);
        if (post == null || post.getStatus() != 1) {
            throw new IllegalArgumentException("岗位不存在或已禁用 postId=" + postId);
        }

        List<PostAbilityModel> postRequirements = postAbilityModelMapper.selectList(
                Wrappers.<PostAbilityModel>lambdaQuery().eq(PostAbilityModel::getPostId, postId));
        Map<Long, String> tagNameMap = loadTagNameMap(postRequirements);

        String postText = matchingProfileTextBuilder.buildFormalPostAbilityRecallText(postRequirements, tagNameMap);
        String vectorCacheKey = RedisCacheNames.EMP_VECTOR + ":" + employeeVectorCacheEpoch() + ":post:" + postId;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vectorResults;
        try {
            Object cached = redisTemplate.opsForValue().get(vectorCacheKey);
            if (cached instanceof List) {
                vectorResults = (List<Map<String, Object>>) cached;
                log.debug("岗位向量召回命中 Redis 缓存: postId={}", postId);
            } else {
                vectorResults = recallEmployees(postText, topK);
                if (!vectorResults.isEmpty()) {
                    redisTemplate.opsForValue().set(vectorCacheKey, vectorResults, 1, TimeUnit.HOURS);
                }
            }
        } catch (Exception e) {
            log.warn("岗位向量缓存读取失败，回源检索: postId={}, error={}", postId, e.getMessage());
            vectorResults = recallEmployees(postText, topK);
        }

        boolean vectorFallback = false;
        if (vectorResults.isEmpty()) {
            log.warn("岗位推荐员工未拿到向量召回结果，切换到兜底候选集: postId={}", postId);
            vectorResults = loadFallbackEmployeeCandidates(topK);
            vectorFallback = true;
            if (vectorResults.isEmpty()) {
                return buildEmptyResponse(postId, post.getPostName());
            }
        }

        List<Long> candidateEmpIds = vectorResults.subList(0, Math.min(vectorResults.size(), topK)).stream()
                .map(item -> Long.parseLong(String.valueOf(item.get("refId"))))
                .toList();

        Map<Long, EmpEmployee> empMap = new HashMap<>();
        List<EmpEmployee> employees = vectorFallback
                ? empEmployeeMapper.selectList(Wrappers.<EmpEmployee>lambdaQuery().in(EmpEmployee::getId, candidateEmpIds))
                : empEmployeeMapper.selectBatchIds(candidateEmpIds);
        for (EmpEmployee employee : employees) {
            empMap.put(employee.getId(), employee);
        }

        // 权威正式能力（person_ability_profile 优先，回退 emp_ability），
        // 与匹配/预检口径一致；无正式能力且无待确立能力的员工不参与推荐（禁止匹配）。
        Map<Long, List<MatchingAbilitySnapshot>> authoritativeMap =
                employeeAbilityReadPort.loadAuthoritativeAbilities(candidateEmpIds);

        Set<Long> empTagIds = authoritativeMap.values().stream()
                .flatMap(List::stream)
                .map(MatchingAbilitySnapshot::tagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> missingTagIds = empTagIds.stream()
                .filter(id -> !tagNameMap.containsKey(id))
                .collect(Collectors.toSet());
        if (!missingTagIds.isEmpty()) {
            List<AbilityTag> extraTags = abilityTagMapper.selectBatchIds(new ArrayList<>(missingTagIds));
            for (AbilityTag tag : extraTags) {
                tagNameMap.put(tag.getId(), tag.getTagName());
            }
        }

        List<EmployeeRecommendDTO.EmployeeRecommendation> recommendations = new ArrayList<>();
        // M-12：在推荐服务边界将 Entity 转换为匹配专用 DTO，算法层只消费 DTO
        MatchingPostProfile postProfile = MatchingSnapshotAssembler.toPostProfile(
                post, toRequirementSnapshots(postRequirements, tagNameMap));
        var hardConditions = (request.isEnableHardConditionPreview() || request.isStrictHardConditionMode())
                ? postHardConditionRuleService.toHardConditions(postId)
                : List.<HardCondition>of();
        for (Map<String, Object> vectorResult : vectorResults.subList(0, Math.min(vectorResults.size(), topK))) {
            Long empId = Long.parseLong(String.valueOf(vectorResult.get("refId")));
            BigDecimal vectorScore = extractVectorScore(vectorResult.get("score"));

            try {
                List<MatchingAbilitySnapshot> empAbilities =
                        authoritativeMap.getOrDefault(empId, List.of());
                // 无正式能力且无待确立能力：禁止匹配，不出现在推荐列表
                if (empAbilities.isEmpty()) {
                    log.debug("员工无正式能力，跳过推荐: empId={}", empId);
                    continue;
                }
                EmpEmployee emp = empMap.get(empId);
                MatchingEmployeeProfile empProfile = MatchingSnapshotAssembler.toEmployeeProfile(emp);
                EmployeeRecommendDTO.EmployeeRecommendation recommendation = buildEmployeeRecommendation(
                        empId,
                        vectorScore,
                        request,
                        postProfile,
                        toRequirementSnapshots(postRequirements, tagNameMap),
                        empProfile,
                        empAbilities,
                        tagNameMap,
                        hardConditions
                );
                if (recommendation != null) {
                    recommendations.add(recommendation);
                }
            } catch (Exception e) {
                log.warn("构建员工推荐卡片失败，跳过 empId={}, error={}", empId, e.getMessage());
            }
        }

        recommendations.sort((left, right) -> right.getRecommendScore().compareTo(left.getRecommendScore()));

        EmployeeRecommendDTO.Response response = new EmployeeRecommendDTO.Response();
        response.setPostId(postId);
        response.setPostName(post.getPostName());
        response.setRecommendations(recommendations);
        return response;
    }

    private int normalizeTopK(Integer requestedTopK) {
        if (requestedTopK == null) {
            return 5;
        }
        return Math.max(1, Math.min(requestedTopK, 50));
    }

    private List<Map<String, Object>> recallEmployees(String postText, int topK) {
        try {
            return aiServiceResilience.callWithResilienceOrThrow("matching-candidate-employee-recall",
                    () -> milvusVectorService.searchEmployeesForPost(postText, topK), CANDIDATE_RECALL_TIMEOUT_SECONDS);
        } catch (Exception e) {
            log.warn("员工候选向量召回超时或不可用，降级数据库候选池: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> loadFallbackEmployeeCandidates(int topK) {
        List<EmpEmployee> employees = empEmployeeMapper.selectList(
                Wrappers.<EmpEmployee>lambdaQuery()
                        .eq(EmpEmployee::getStatus, 1)
                        .eq(EmpEmployee::getIsLocked, 0)
                        .last("LIMIT " + topK));
        if (employees == null || employees.isEmpty()) {
            return Collections.emptyList();
        }
        return employees.stream()
                .map(employee -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("refId", employee.getId());
                    return result;
                })
                .toList();
    }

    private BigDecimal extractVectorScore(Object rawScore) {
        if (rawScore == null) {
            return null;
        }
        if (rawScore instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (rawScore instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(String.valueOf(rawScore));
        } catch (NumberFormatException ex) {
            log.warn("无法解析员工推荐向量分，按语义缺失处理: rawScore={}", rawScore);
            return null;
        }
    }

    private EmployeeRecommendDTO.EmployeeRecommendation buildEmployeeRecommendation(
            Long empId,
            BigDecimal vectorScore,
            EmployeeRecommendDTO.Request request,
            MatchingPostProfile post,
            List<MatchingRequirementSnapshot> postRequirements,
            MatchingEmployeeProfile emp,
            List<MatchingAbilitySnapshot> empAbilities,
            Map<Long, String> tagNameMap,
            List<HardCondition> hardConditions
    ) {
        if (emp == null) {
            return null;
        }

        EmployeeRecommendDTO.EmployeeRecommendation rec = new EmployeeRecommendDTO.EmployeeRecommendation();
        rec.setEmpId(empId);
        rec.setEmpName(emp.realName());
        rec.setEmpCode(emp.empCode());
        rec.setVectorScore(vectorScore);

        boolean canRunL2 = request.isEnableL2Preview() && !empAbilities.isEmpty() && !postRequirements.isEmpty();

        String hardConditionStatus = "PASS";
        List<PostRecommendDTO.HardConditionDetail> hardConditionDetails = null;
        if (request.isEnableHardConditionPreview() || request.isStrictHardConditionMode()) {
            if (hardConditions != null && !hardConditions.isEmpty()) {
                Map<String, Object> resumeBasicInfo = new HashMap<>();
                var hcResult = matchingAlgorithmService.checkHardConditions(emp, hardConditions, resumeBasicInfo);
                hardConditionStatus = hcResult.isPassed() ? "PASS" : "FAIL";
                hardConditionDetails = new ArrayList<>();
                for (var detail : hcResult.getDetails()) {
                    PostRecommendDTO.HardConditionDetail hcd = new PostRecommendDTO.HardConditionDetail();
                    hcd.setField(detail.getField());
                    hcd.setLabel(detail.getLabel());
                    hcd.setOperator(detail.getOperator());
                    hcd.setExpectedValue(detail.getExpectedValue());
                    hcd.setActualValue(detail.getActualValue());
                    hcd.setPassed(detail.isPassed());
                    hcd.setSource(detail.getSource());
                    hardConditionDetails.add(hcd);
                    if (!detail.isPassed()) {
                        hardConditionStatus = "FAIL";
                    }
                }
            }
        }
        rec.setHardConditionStatus(hardConditionStatus);
        rec.setHardConditionDetails(hardConditionDetails);
        if (request.isStrictHardConditionMode() && "FAIL".equals(hardConditionStatus)) {
            return null;
        }

        BigDecimal l2PreviewScore = BigDecimal.ZERO;
        int coreHitCount = 0;
        int coreTotalCount = 0;
        List<String> gapSummary = new ArrayList<>();
        String evidenceConfidence = "WEAK";

        if (canRunL2) {
            Map<Long, BigDecimal> fusedLevels = matchingAlgorithmService.fuseAbilityLevel(empAbilities);
            List<MatchDetailDTO> matchDetails = matchingAlgorithmService.performSemanticMatchingForPreview(
                    fusedLevels, empAbilities, postRequirements);

            l2PreviewScore = matchingAlgorithmService.calculateAbilityCompatibilityScore(
                    matchDetails, postRequirements);

            for (int i = 0; i < postRequirements.size(); i++) {
                MatchingRequirementSnapshot req = postRequirements.get(i);
                MatchDetailDTO detail = matchDetails.get(i);

                if (req.isCore() != null && req.isCore() == 1) {
                    coreTotalCount++;
                    if (detail.isPassed()) {
                        coreHitCount++;
                    }
                }

                if (!detail.isPassed() && req.isRequired() != null && req.isRequired() == 1 && gapSummary.size() < 5) {
                    String tagName = tagNameMap.getOrDefault(req.tagId(), "标签#" + req.tagId());
                    BigDecimal empLevel = detail.getEmployeeRawLevel();
                    gapSummary.add(tagName + " 低于岗位要求 " + req.minRequiredLevel() + " 级"
                            + (empLevel != null && empLevel.compareTo(BigDecimal.ZERO) > 0
                            ? "（当前" + empLevel.intValue() + "级）"
                            : "（无相关能力）"));
                }
            }

            evidenceConfidence = evaluateEvidenceConfidence(empAbilities, postRequirements);
        }

        rec.setL2PreviewScore(l2PreviewScore.setScale(2, RoundingMode.HALF_UP));
        rec.setCoreAbilityHitCount(coreHitCount);
        rec.setCoreAbilityTotalCount(coreTotalCount);
        rec.setCoreAbilityHitRate(coreTotalCount > 0 ? (double) coreHitCount / coreTotalCount : 0.0);
        rec.setEvidenceConfidence(evidenceConfidence);
        rec.setGapSummary(gapSummary);

        BigDecimal recommendScore = calculateRecommendScore(
                vectorScore,
                l2PreviewScore,
                evidenceConfidence
        );
        rec.setRecommendScore(recommendScore);
        rec.setReason(generateRecommendReason(rec, postRequirements));

        return rec;
    }

    private BigDecimal calculateRecommendScore(
            BigDecimal vectorScore,
            BigDecimal l2PreviewScore,
            String evidenceConfidence
    ) {
        BigDecimal evidenceScore = switch (evidenceConfidence) {
            case "STRONG" -> MatchingEvidenceScoreCalculator.STRONG_THRESHOLD;
            case "MEDIUM" -> MatchingEvidenceScoreCalculator.MEDIUM_THRESHOLD;
            default -> MatchingEvidenceScoreCalculator.WEAK_THRESHOLD;
        };
        return matchingScoreService.score(MatchScoreInput.deterministic(
                l2PreviewScore, vectorScore, evidenceScore, weightProfileStore.currentProfile())).finalScore();
    }

    private String evaluateEvidenceConfidence(List<MatchingAbilitySnapshot> empAbilities,
                                              List<MatchingRequirementSnapshot> postRequirements) {
        if (empAbilities.isEmpty() || postRequirements.isEmpty()) {
            return "WEAK";
        }
        long highCredCount = empAbilities.stream()
                .filter(ability -> AbilitySourceCredibility.getWeightBySource(ability.sourceType()) >= 0.8)
                .count();
        double ratio = (double) highCredCount / empAbilities.size();
        if (ratio >= 0.7) {
            return "STRONG";
        }
        if (ratio >= 0.4) {
            return "MEDIUM";
        }
        return "WEAK";
    }

    private String generateRecommendReason(
            EmployeeRecommendDTO.EmployeeRecommendation rec,
            List<MatchingRequirementSnapshot> postRequirements
    ) {
        StringBuilder reason = new StringBuilder();
        if (rec.getCoreAbilityTotalCount() != null && rec.getCoreAbilityTotalCount() > 0) {
            double hitRate = rec.getCoreAbilityHitRate();
            if (hitRate >= 0.8) {
                reason.append("核心能力高度匹配");
            } else if (hitRate >= 0.5) {
                reason.append("核心能力部分匹配");
            } else {
                reason.append("核心能力匹配度较低");
            }
        } else {
            reason.append("岗位未定义核心能力");
        }
        if ("FAIL".equals(rec.getHardConditionStatus())) {
            reason.append("，硬性条件未通过");
        } else {
            reason.append("，硬性条件通过");
        }
        return reason.toString();
    }

    private Map<Long, String> loadTagNameMap(List<PostAbilityModel> postRequirements) {
        if (postRequirements.isEmpty()) {
            return new HashMap<>();
        }
        List<Long> tagIds = postRequirements.stream()
                .map(PostAbilityModel::getTagId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        // tagId 是可选辅助字段；岗位能力名称来自岗位能力表，空集合不得生成 IN ()。
        if (tagIds.isEmpty()) {
            return new HashMap<>();
        }
        List<AbilityTag> tags = abilityTagMapper.selectBatchIds(tagIds);
        Map<Long, String> map = new HashMap<>();
        for (AbilityTag tag : tags) {
            map.put(tag.getId(), tag.getTagName());
        }
        return map;
    }

    private List<MatchingRequirementSnapshot> toRequirementSnapshots(List<PostAbilityModel> postRequirements,
                                                                     Map<Long, String> tagNameMap) {
        if (postRequirements == null) {
            return List.of();
        }
        List<MatchingRequirementSnapshot> snapshots = new ArrayList<>();
        for (PostAbilityModel requirement : postRequirements) {
            snapshots.add(MatchingSnapshotAssembler.toRequirementSnapshot(
                    requirement, requirement.getAbilityName() != null ? requirement.getAbilityName()
                            : tagNameMap.get(requirement.getTagId())));
        }
        return snapshots;
    }

    private EmployeeRecommendDTO.Response buildEmptyResponse(Long postId, String postName) {
        EmployeeRecommendDTO.Response response = new EmployeeRecommendDTO.Response();
        response.setPostId(postId);
        response.setPostName(postName);
        response.setRecommendations(List.of());
        return response;
    }

    private String employeeVectorCacheEpoch() {
        try {
            Object epoch = redisTemplate.opsForValue().get(RedisCacheNames.EMP_VECTOR_CACHE_EPOCH);
            return epoch == null ? "0" : String.valueOf(epoch);
        } catch (Exception e) {
            log.warn("Failed to load employee vector cache epoch: {}", e.getMessage());
            return "0";
        }
    }
}
