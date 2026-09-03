package com.example.matching.service.matching.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.enums.AbilitySourceCredibility;
import com.example.matching.dto.matching.MatchDetailDTO;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.dto.matching.PostRecommendDTO;
import com.example.matching.dto.matching.PostRecommendDTO.HardConditionDetail;
import com.example.matching.dto.matching.PostRecommendDTO.PostRecommendation;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.post.PostHardConditionRuleService;
import com.example.matching.service.matching.EmployeePostRecommendService;
import com.example.matching.service.matching.MatchingAlgorithmService;
import com.example.matching.service.matching.MatchingProfileTextBuilder;
import com.example.matching.service.matching.MatchingSnapshotAssembler;
import com.example.matching.service.matching.MatchingTrainingWeightProfileStore;
import com.example.matching.service.matching.MatchingScoreService;
import com.example.matching.service.matching.FeedbackCalibrationService;
import com.example.matching.service.matching.MatchingEvidenceScoreCalculator;
import com.example.matching.service.matching.MatchScoreInput;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.vector.MilvusVectorService;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.resilience.AiServiceResilience;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 员工推荐岗位服务实现
 * <p>
 * 流程：读取员工能力画像 → 向量召回候选岗位 Top K → L2预评分 → 返回推荐卡片
 * <p>
 * 推荐分公式（M7：与执行评分共用 MatchingScoreService 输入构建，权重取自当前
 * WeightProfile 字段如 noLlmAbilityWeight/noLlmSemanticWeight/noLlmEvidenceWeight，
 * 不写死比例；质量分与校准值使用岗位真实值）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeePostRecommendServiceImpl implements EmployeePostRecommendService {

    private final MilvusVectorService milvusVectorService;
    private final MatchingAlgorithmService matchingAlgorithmService;
    private final PostHardConditionRuleService postHardConditionRuleService;
    private final EmpEmployeeMapper empEmployeeMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final PostPostMapper postPostMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final EmpResumeParseMapper empResumeParseMapper;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MatchingProfileTextBuilder matchingProfileTextBuilder;
    private final MatchingTrainingWeightProfileStore weightProfileStore;
    private final MatchingScoreService matchingScoreService;
    private final PostAbilityModelService postAbilityModelService;
    private final FeedbackCalibrationService feedbackCalibrationService;
    private final AiServiceResilience aiServiceResilience;

    private static final long CANDIDATE_RECALL_TIMEOUT_SECONDS = 12L;

    // 公开推荐分始终由 MatchingScoreService 计算；L2 只提供能力兼容度特征。

    @Override
    public PostRecommendDTO.Response recommendPostsForEmployee(PostRecommendDTO.Request request) {
        Long empId = request.getEmpId();
        int topK = normalizeTopK(request.getTopK());

        // ===== 1. 加载员工数据 =====
        EmpEmployee emp = empEmployeeMapper.selectById(empId);
        if (emp == null) {
            throw new IllegalArgumentException("员工不存在: empId=" + empId);
        }

        List<EmpAbility> empAbilities = empAbilityMapper.selectList(
                Wrappers.<EmpAbility>lambdaQuery().eq(EmpAbility::getEmpId, empId));

        // 加载简历解析数据
        Map<String, Object> resumeBasicInfo = loadResumeBasicInfo(empId);

        // 加载能力标签名称映射
        Map<Long, String> tagNameMap = loadTagNameMap(empAbilities);

        // ===== 2. 构建员工画像文本并进行向量召回（Redis 缓存） =====
        String empText = matchingProfileTextBuilder.buildFormalEmployeeAbilityRecallText(empAbilities, tagNameMap);
        String vectorCacheKey = RedisCacheNames.EMP_VECTOR + ":" + employeeVectorCacheEpoch() + ":" + empId;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vectorResults;
        try {
            Object cached = redisTemplate.opsForValue().get(vectorCacheKey);
            if (cached instanceof List) {
                vectorResults = (List<Map<String, Object>>) cached;
                log.debug("员工向量召回命中Redis缓存: empId={}", empId);
            } else {
                vectorResults = recallPosts(empText, topK);
                if (!vectorResults.isEmpty()) {
                    redisTemplate.opsForValue().set(vectorCacheKey, vectorResults, 1, TimeUnit.HOURS);
                }
            }
        } catch (Exception e) {
            log.warn("Redis读取员工向量缓存失败，回源查询: empId={}, error={}", empId, e.getMessage());
            vectorResults = recallPosts(empText, topK);
        }

        boolean vectorFallback = false;
        if (vectorResults.isEmpty()) {
            log.warn("向量召回未返回任何岗位结果，empId={}", empId);
            vectorResults = loadFallbackPostCandidates(topK);
            vectorFallback = true;
            if (vectorResults.isEmpty()) {
                return buildEmptyResponse(empId, emp.getRealName());
            }
        }

        // ===== 3. 批量预加载候选岗位数据（消除 N+1 查询） =====
        List<Long> candidatePostIds = vectorResults.subList(0, Math.min(vectorResults.size(), topK)).stream()
                .map(r -> Long.parseLong(String.valueOf(r.get("refId"))))
                .toList();

        // 批量加载岗位信息
        Map<Long, PostPost> postMap = new HashMap<>();
        List<PostPost> posts = vectorFallback
                ? postPostMapper.selectList(Wrappers.<PostPost>lambdaQuery()
                        .in(PostPost::getId, candidatePostIds)
                        .eq(PostPost::getStatus, 1))
                : postPostMapper.selectBatchIds(candidatePostIds);
        for (PostPost p : posts) {
            if (p.getStatus() == 1) {
                postMap.put(p.getId(), p);
            }
        }

        // 批量加载岗位能力模型
        List<PostAbilityModel> allModels = postAbilityModelMapper.selectList(
                Wrappers.<PostAbilityModel>lambdaQuery().in(PostAbilityModel::getPostId, candidatePostIds));
        Map<Long, List<PostAbilityModel>> modelMap = allModels.stream()
                .collect(Collectors.groupingBy(PostAbilityModel::getPostId));

        // 批量加载所有岗位要求的标签名称
        Set<Long> allPostTagIds = allModels.stream()
                .map(PostAbilityModel::getTagId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> missingTagIds = allPostTagIds.stream()
                .filter(id -> !tagNameMap.containsKey(id)).collect(Collectors.toSet());
        if (!missingTagIds.isEmpty()) {
            List<AbilityTag> extraTags = abilityTagMapper.selectBatchIds(new ArrayList<>(missingTagIds));
            for (AbilityTag tag : extraTags) {
                tagNameMap.put(tag.getId(), tag.getTagName());
            }
        }

        // ===== 4. 对召回岗位进行L2预评分 =====
        List<PostRecommendation> recommendations = new ArrayList<>();

        // M-12：在推荐服务边界将 Entity 转换为匹配专用 DTO，算法层只消费 DTO
        MatchingEmployeeProfile empProfile = MatchingSnapshotAssembler.toEmployeeProfile(emp);
        List<MatchingAbilitySnapshot> abilitySnapshots = toAbilitySnapshots(empAbilities, tagNameMap);

        for (Map<String, Object> vectorResult : vectorResults.subList(0, Math.min(vectorResults.size(), topK))) {
            Long postId = Long.parseLong(String.valueOf(vectorResult.get("refId")));
            BigDecimal vectorScore = extractVectorScore(vectorResult.get("score"));

            try {
                PostPost postEntity = postMap.get(postId);
                List<PostAbilityModel> modelEntities = modelMap.get(postId);
                MatchingPostProfile postProfile = MatchingSnapshotAssembler.toPostProfile(
                        postEntity, toRequirementSnapshots(modelEntities, tagNameMap));
                // M7：预览读取真实岗位模型质量分与反馈校准值，不伪造 100/0
                BigDecimal modelQualityScore = postAbilityModelService.calculateQualityScore(postId);
                BigDecimal feedbackCalibration = feedbackCalibrationService.calculateCalibration(postId);
                PostRecommendation rec = buildPostRecommendation(
                        empId, empProfile, abilitySnapshots, resumeBasicInfo, tagNameMap,
                        postId, vectorScore, request, postProfile, toRequirementSnapshots(modelEntities, tagNameMap),
                        modelQualityScore, feedbackCalibration);
                if (rec != null) {
                    recommendations.add(rec);
                }
            } catch (Exception e) {
                log.warn("构建岗位推荐卡片失败，跳过。postId={}, error={}", postId, e.getMessage());
            }
        }

        // 按推荐分降序排序，取Top K
        recommendations.sort((a, b) -> b.getRecommendScore().compareTo(a.getRecommendScore()));
        if (recommendations.size() > topK) {
            recommendations = recommendations.subList(0, topK);
        }

        // ===== 5. 构建响应 =====
        PostRecommendDTO.Response response = new PostRecommendDTO.Response();
        response.setEmpId(empId);
        response.setEmpName(emp.getRealName());
        response.setRecommendations(recommendations);
        return response;
    }

    private int normalizeTopK(Integer requestedTopK) {
        if (requestedTopK == null) {
            return 5;
        }
        return Math.max(1, Math.min(requestedTopK, 20));
    }

    private List<Map<String, Object>> loadFallbackPostCandidates(int topK) {
        List<PostPost> posts = postPostMapper.selectList(
                Wrappers.<PostPost>lambdaQuery()
                        .eq(PostPost::getStatus, 1)
                        .last("LIMIT " + topK));
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }
        return posts.stream()
                .map(post -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("refId", post.getId());
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
            log.warn("无法解析岗位推荐向量分，按语义缺失处理：rawScore={}", rawScore);
            return null;
        }
    }

    private int countCoreRequirements(List<MatchingRequirementSnapshot> postRequirements) {
        if (postRequirements == null || postRequirements.isEmpty()) {
            return 0;
        }
        return (int) postRequirements.stream()
                .filter(req -> req.isCore() != null && req.isCore() == 1)
                .count();
    }

    /**
     * 构建单个岗位的推荐卡片（使用预加载数据）
     */
    private PostRecommendation buildPostRecommendation(
            Long empId, MatchingEmployeeProfile emp, List<MatchingAbilitySnapshot> empAbilities,
            Map<String, Object> resumeBasicInfo, Map<Long, String> tagNameMap,
            Long postId, BigDecimal vectorScore, PostRecommendDTO.Request request,
            MatchingPostProfile post, List<MatchingRequirementSnapshot> postRequirements,
            BigDecimal modelQualityScore, BigDecimal feedbackCalibration) {

        if (post == null) {
            return null;
        }

        if (postRequirements == null) {
            postRequirements = List.of();
        }

        boolean postModelComplete = !postRequirements.isEmpty();

        PostRecommendation rec = new PostRecommendation();
        rec.setPostId(postId);
        rec.setPostName(post.postName());
        rec.setPostCode(post.postCode());
        rec.setPostLevel(post.postLevel());
        rec.setVectorScore(vectorScore);
        rec.setPostModelComplete(postModelComplete);

        // 硬性条件预览
        String hardConditionStatus = "PASS";
        List<HardConditionDetail> hardConditionDetails = null;
        if (request.isEnableHardConditionPreview() || request.isStrictHardConditionMode()) {
            var hardConditions = postHardConditionRuleService.toHardConditions(postId);
            if (hardConditions != null && !hardConditions.isEmpty()) {
                var hcResult = matchingAlgorithmService.checkHardConditions(emp, hardConditions, resumeBasicInfo);
                hardConditionStatus = hcResult.isPassed() ? "PASS" : "FAIL";
                hardConditionDetails = new ArrayList<>();
                for (var detail : hcResult.getDetails()) {
                    HardConditionDetail hcd = new HardConditionDetail();
                    hcd.setField(detail.getField());
                    hcd.setLabel(detail.getLabel());
                    hcd.setOperator(detail.getOperator());
                    hcd.setExpectedValue(detail.getExpectedValue());
                    hcd.setActualValue(detail.getActualValue());
                    hcd.setPassed(detail.isPassed());
                    hcd.setSource(detail.getSource());
                    hardConditionDetails.add(hcd);
                    // 如果有任何条件不通过，标记为FAIL
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

        // L2预评分
        BigDecimal l2PreviewScore = BigDecimal.ZERO;
        boolean canRunL2Preview = request.isEnableL2Preview() && !empAbilities.isEmpty();
        int coreHitCount = 0;
        int coreTotalCount = 0;
        List<String> gapSummary = new ArrayList<>();
        String evidenceConfidence = "WEAK";

        if (canRunL2Preview && postModelComplete) {
            // 能力融合
            Map<Long, BigDecimal> fusedLevels = matchingAlgorithmService.fuseAbilityLevel(empAbilities);

            // 语义匹配
            List<MatchDetailDTO> matchDetails = matchingAlgorithmService.performSemanticMatchingForPreview(
                    fusedLevels, empAbilities, postRequirements);

            // 计算L2得分
            l2PreviewScore = matchingAlgorithmService.calculateAbilityCompatibilityScore(
                    matchDetails, postRequirements);
            rec.setL2PreviewScore(l2PreviewScore);

            // 统计核心能力命中
            for (int i = 0; i < postRequirements.size(); i++) {
                MatchingRequirementSnapshot req = postRequirements.get(i);
                MatchDetailDTO detail = matchDetails.get(i);

                if (req.isCore() != null && req.isCore() == 1) {
                    coreTotalCount++;
                    if (detail.isPassed()) {
                        coreHitCount++;
                    }
                }

                // 收集能力差距（最多保留5条）
                if (!detail.isPassed() && req.isRequired() != null && req.isRequired() == 1) {
                    if (gapSummary.size() < 5) {
                        String tagName = tagNameMap.getOrDefault(req.tagId(), "标签#" + req.tagId());
                        BigDecimal empLevel = detail.getEmployeeRawLevel();
                        gapSummary.add(tagName + " 低于岗位要求 " + req.minRequiredLevel() + " 级"
                                + (empLevel != null && empLevel.compareTo(BigDecimal.ZERO) > 0
                                ? "（当前 " + empLevel.intValue() + " 级）" : "（无相关能力）"));
                    }
                }
            }

            rec.setCoreAbilityHitCount(coreHitCount);
            rec.setCoreAbilityTotalCount(coreTotalCount);
            rec.setCoreAbilityHitRate(coreTotalCount > 0 ? (double) coreHitCount / coreTotalCount : 0.0);

            // 证据置信度评估
            evidenceConfidence = evaluateEvidenceConfidence(empAbilities, postRequirements);
        } else if (!postModelComplete) {
            rec.setL2PreviewScore(BigDecimal.ZERO);
            rec.setCoreAbilityHitCount(0);
            rec.setCoreAbilityTotalCount(0);
            rec.setCoreAbilityHitRate(0.0);
            gapSummary.add("岗位能力模型不完整，无法进行精确匹配");
        } else if (empAbilities.isEmpty()) {
            rec.setL2PreviewScore(BigDecimal.ZERO);
            rec.setCoreAbilityHitCount(0);
            rec.setCoreAbilityTotalCount(countCoreRequirements(postRequirements));
            rec.setCoreAbilityHitRate(0.0);
            gapSummary.add("员工暂无能力档案，无法进行能力模型预评分");
        } else {
            rec.setL2PreviewScore(BigDecimal.ZERO);
            rec.setCoreAbilityHitCount(0);
            rec.setCoreAbilityTotalCount(0);
            rec.setCoreAbilityHitRate(0.0);
        }

        rec.setEvidenceConfidence(evidenceConfidence);
        rec.setGapSummary(gapSummary);

        // 计算综合推荐分（M7：与执行评分共用 MatchingScoreService 输入构建，读取真实质量分/校准值）
        BigDecimal recommendScore = calculateRecommendScore(
                vectorScore, l2PreviewScore, evidenceConfidence, modelQualityScore, feedbackCalibration);
        rec.setRecommendScore(recommendScore);
        // M7：预览缺少执行输入（向量分缺失）时显式标记 approximate，不伪造 100/0
        rec.setApproximate(vectorScore == null);

        // 生成推荐理由
        rec.setReason(generateRecommendReason(rec, post, empAbilities, tagNameMap));

        return rec;
    }
    /**
     * 计算综合推荐分
     * <p>
     * 与执行评分共用 {@link MatchingScoreService#score(MatchScoreInput)}：权重取自当前
     * {@link MatchingTrainingWeightProfileStore.WeightProfile} 的统一四维权重，
     * 不再写死局部比例，也不使用岗位质量或反馈作为额外加减分。
     */
    private BigDecimal calculateRecommendScore(BigDecimal vectorScore, BigDecimal l2PreviewScore,
                                                String evidenceConfidence,
                                                BigDecimal modelQualityScore, BigDecimal feedbackCalibration) {

        BigDecimal evidenceScore = switch (evidenceConfidence) {
            case "STRONG" -> MatchingEvidenceScoreCalculator.STRONG_THRESHOLD;
            case "MEDIUM" -> MatchingEvidenceScoreCalculator.MEDIUM_THRESHOLD;
            default -> MatchingEvidenceScoreCalculator.WEAK_THRESHOLD;
        };

        return matchingScoreService.score(MatchScoreInput.deterministic(
                l2PreviewScore, vectorScore, evidenceScore, weightProfileStore.currentProfile())).finalScore()
                .setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 评估证据置信度
     * <p>
     * - STRONG：多个来源一致
     * - MEDIUM：有非简历来源
     * - WEAK：仅有简历解析
     */
    private String evaluateEvidenceConfidence(List<MatchingAbilitySnapshot> empAbilities,
                                              List<MatchingRequirementSnapshot> postRequirements) {
        List<MatchingRequirementSnapshot> requiredRequirements = postRequirements.stream()
                .filter(r -> r.isRequired() != null && r.isRequired() == 1)
                .toList();

        int hasMultipleSources = 0;
        int hasNonResumeSource = 0;
        int totalRequired = requiredRequirements.size();

        for (MatchingRequirementSnapshot requirement : requiredRequirements) {
            List<MatchingAbilitySnapshot> tagAbilities = empAbilities.stream()
                    .filter(ability -> matchesRequirement(requirement, ability))
                    .toList();

            if (tagAbilities.size() > 1) {
                hasMultipleSources++;
            }
            if (tagAbilities.stream().anyMatch(a -> !"RESUME_PARSE".equals(a.sourceType()))) {
                hasNonResumeSource++;
            }
        }

        if (totalRequired == 0) return "MEDIUM";
        if (hasMultipleSources >= totalRequired / 2) return "STRONG";
        if (hasNonResumeSource >= totalRequired / 3) return "MEDIUM";
        return "WEAK";
    }

    private List<Map<String, Object>> recallPosts(String empText, int topK) {
        try {
            return aiServiceResilience.callWithResilienceOrThrow("matching-candidate-post-recall",
                    () -> milvusVectorService.searchPostsForEmployee(empText, topK), CANDIDATE_RECALL_TIMEOUT_SECONDS);
        } catch (Exception e) {
            log.warn("岗位候选向量召回超时或不可用，降级数据库候选池: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 标签库是可选元数据。能力两侧都带标签时优先按标签匹配；任一侧没有标签时，
     * 只能按非空能力名称精确匹配，绝不能把全部 null 标签当作同一能力。
     */
    static boolean matchesRequirement(MatchingRequirementSnapshot requirement,
                                      MatchingAbilitySnapshot ability) {
        if (requirement == null || ability == null) {
            return false;
        }
        if (requirement.tagId() != null && requirement.tagId().equals(ability.tagId())) {
            return true;
        }
        return requirement.abilityName() != null && ability.abilityName() != null
                && requirement.abilityName().trim().equalsIgnoreCase(ability.abilityName().trim());
    }

    /**
     * 生成推荐理由
     */
    private String generateRecommendReason(PostRecommendation rec, MatchingPostProfile post,
                                            List<MatchingAbilitySnapshot> empAbilities, Map<Long, String> tagNameMap) {
        StringBuilder reason = new StringBuilder();

        if (rec.getCoreAbilityHitCount() != null && rec.getCoreAbilityTotalCount() != null
                && rec.getCoreAbilityTotalCount() > 0) {
            double hitRate = rec.getCoreAbilityHitRate();
            if (hitRate >= 0.8) {
                reason.append("核心能力高度匹配");
            } else if (hitRate >= 0.5) {
                reason.append("核心能力部分匹配");
            } else {
                reason.append("核心能力匹配度较低");
            }
        }

        if ("PASS".equals(rec.getHardConditionStatus())) {
            reason.append("，硬性条件全部通过");
        } else if ("FAIL".equals(rec.getHardConditionStatus())) {
            reason.append("，存在硬性条件风险");
        }

        if (rec.getL2PreviewScore() != null && rec.getL2PreviewScore().doubleValue() >= 80) {
            reason.append("，能力模型评分优秀");
        } else if (rec.getL2PreviewScore() != null && rec.getL2PreviewScore().doubleValue() >= 60) {
            reason.append("，能力模型评分良好");
        }

        if ("STRONG".equals(rec.getEvidenceConfidence())) {
            reason.append("，多来源证据一致");
        }

        return reason.toString();
    }

    /**
     * M-12：Entity -> 匹配专用 DTO 转换（推荐服务边界）
     */
    private List<MatchingAbilitySnapshot> toAbilitySnapshots(List<EmpAbility> empAbilities,
                                                             Map<Long, String> tagNameMap) {
        if (empAbilities == null) {
            return List.of();
        }
        List<MatchingAbilitySnapshot> snapshots = new ArrayList<>();
        for (EmpAbility ability : empAbilities) {
            snapshots.add(MatchingSnapshotAssembler.toAbilitySnapshot(
                    ability, tagNameMap.get(ability.getTagId())));
        }
        return snapshots;
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

    /**
     * 构建员工画像文本（用于向量搜索）
     */
    private String buildEmployeeProfileText(EmpEmployee emp, List<EmpAbility> abilities,
                                             Map<Long, String> tagNameMap) {
        StringBuilder sb = new StringBuilder();
        sb.append(emp.getRealName()).append(" ");
        if (emp.getLevel() != null) {
            sb.append(emp.getLevel()).append(" ");
        }
        for (EmpAbility a : abilities) {
            String tagName = tagNameMap.getOrDefault(a.getTagId(), "ability" + a.getTagId());
            sb.append(tagName).append(":").append(a.getMasteryLevel()).append("级; ");
        }
        return sb.toString();
    }

    /**
     * 加载简历解析数据
     */
    private Map<String, Object> loadResumeBasicInfo(Long empId) {
        try {
            EmpResumeParse resume = empResumeParseMapper.selectOne(
                    Wrappers.<EmpResumeParse>lambdaQuery()
                            .eq(EmpResumeParse::getEmpId, empId)
                            .eq(EmpResumeParse::getStatus, 2)
                            .orderByDesc(EmpResumeParse::getCreatedTime)
                            .last("LIMIT 1"));
            if (resume != null && resume.getAiAnalysisResult() != null) {
                Map<String, Object> analysis = objectMapper.readValue(
                        resume.getAiAnalysisResult(),
                        new TypeReference<Map<String, Object>>() {});
                Object basicInfo = analysis.get("basicInfo");
                if (basicInfo instanceof Map) {
                    return (Map<String, Object>) basicInfo;
                }
            }
        } catch (Exception e) {
            log.debug("解析简历basicInfo失败: empId={}", empId);
        }
        return Collections.emptyMap();
    }

    /**
     * 加载能力标签名称映射
     */
    private Map<Long, String> loadTagNameMap(List<EmpAbility> empAbilities) {
        if (empAbilities.isEmpty()) {
            return new HashMap<>();
        }
        List<Long> tagIds = empAbilities.stream()
                .map(EmpAbility::getTagId)
                .distinct()
                .toList();
        List<AbilityTag> tags = abilityTagMapper.selectList(
                Wrappers.<AbilityTag>lambdaQuery().in(AbilityTag::getId, tagIds));
        Map<Long, String> map = new HashMap<>();
        for (AbilityTag tag : tags) {
            map.put(tag.getId(), tag.getTagName());
        }
        return map;
    }

    /**
     * 构建空响应
     */
    private PostRecommendDTO.Response buildEmptyResponse(Long empId, String empName) {
        PostRecommendDTO.Response response = new PostRecommendDTO.Response();
        response.setEmpId(empId);
        response.setEmpName(empName);
        response.setRecommendations(Collections.emptyList());
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
