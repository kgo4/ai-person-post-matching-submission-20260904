package com.example.matching.service.contest.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.constant.SourceRefConstants;
import com.example.matching.dto.contest.EvidenceCreateDTO;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.port.matching.MatchingQueryPort;
import com.example.matching.port.matching.MatchingQueryPort.MatchingFeedbackDTO;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.post.PostQueryPort.JdImportTaskDTO;
import com.example.matching.port.post.PostQueryPort.PostAbilityDTO;
import com.example.matching.port.post.PostQueryPort.PostDTO;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.tag.TagQueryPort.TagDTO;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.port.talent.TalentQueryPort.EmployeeAbilityDTO;
import com.example.matching.port.talent.TalentQueryPort.EmployeeDTO;
import com.example.matching.port.talent.TalentQueryPort.ResumeParseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

/**
 * 证据中心回填：从 JD 导入、简历解析、匹配反馈、员工能力、岗位模型批量生成证据项。
 * <p>
 * 从 EvidenceCenterServiceImpl（570 行）中拆分的回填组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EvidenceBackfillService {

    private final ContestEvidenceItemMapper evidenceItemMapper;
    private final PostQueryPort postQueryPort;
    private final TalentQueryPort talentQueryPort;
    private final MatchingQueryPort matchingQueryPort;
    private final TagQueryPort tagQueryPort;
    private final AiTrustHarnessService aiTrustHarnessService;

    private static final DateTimeFormatter CODE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicLong SEQUENCE = new AtomicLong(0);
    public int backfillEvidence(String sourceType, int limit) {
        return switch (sourceType) {
            case "JD_IMPORT" -> backfillFromJdImport(limit);
            case "RESUME_PARSE" -> backfillFromResumeParse(limit);
            case "MATCHING_FEEDBACK" -> backfillFromMatchingFeedback(limit);
            case "EMP_ABILITY" -> backfillFromEmpAbility(limit);
            case "POST_ABILITY_MODEL" -> backfillFromPostAbilityModel(limit);
            default -> throw new com.example.matching.common.exception.BusinessException(
                    com.example.matching.common.exception.ErrorCodeEnum.INTERNAL_ERROR,
                    "不支持的回填来源类型: " + sourceType);
        };
    }

    public int backfillFromJdImport(int limit) {
        List<JdImportTaskDTO> tasks = postQueryPort.listAnalyzedJdImportTasks(limit);

        List<EvidenceCreateDTO> pendingDtos = new ArrayList<>();
        for (JdImportTaskDTO task : tasks) {
            // 去重：同来源+同目标不重复创建
            if (existsBySourceAndTarget("JD_IMPORT", task.id(), "POST_ABILITY_MODEL")) {
                continue;
            }
            EvidenceCreateDTO dto = new EvidenceCreateDTO();
            dto.setSourceType("JD_IMPORT");
            dto.setSourceRefId(task.id());
            dto.setSourceTitle("JD导入任务#" + task.id());
            dto.setSourceText(task.jdRawText());
            dto.setTargetType("POST_ABILITY_MODEL");
            dto.setTargetRefId(task.postId());
            dto.setConfidenceScore(new BigDecimal("80"));
            dto.setCredibilityScore(new BigDecimal("85"));
            // 以岗位首个能力模型作为正式标签锚点，供 harness 自动准入
            List<PostAbilityDTO> models = postQueryPort.listRequirementsByPostId(task.postId());
            if (!models.isEmpty()) {
                dto.setTagId(models.get(0).tagId());
            }
            pendingDtos.add(dto);
        }
        return insertWithHarnessReview(pendingDtos);
    }

    /**
     * 从简历解析回填证据
     */
    public int backfillFromResumeParse(int limit) {
        List<ResumeParseDTO> parses = talentQueryPort.listCompletedResumeParses(limit);

        List<EvidenceCreateDTO> pendingDtos = new ArrayList<>();
        for (ResumeParseDTO parse : parses) {
            if (existsBySourceAndTarget("RESUME_PARSE", parse.id(), "EMP_ABILITY")) {
                continue;
            }
            EvidenceCreateDTO dto = new EvidenceCreateDTO();
            dto.setSourceType("RESUME_PARSE");
            dto.setSourceRefId(parse.id());
            dto.setSourceTitle(parse.fileName());
            dto.setSourceText(parse.parsedContent());
            dto.setTargetType("EMP_ABILITY");
            dto.setTargetRefId(parse.empId());
            dto.setConfidenceScore(new BigDecimal("75"));
            dto.setCredibilityScore(new BigDecimal("80"));
            // 以员工首项能力作为正式标签锚点，供 harness 自动准入
            List<EmployeeAbilityDTO> abilities = talentQueryPort.listAbilitiesByEmpId(parse.empId());
            if (!abilities.isEmpty()) {
                dto.setTagId(abilities.get(0).tagId());
            }
            pendingDtos.add(dto);
        }
        return insertWithHarnessReview(pendingDtos);
    }

    /**
     * 从匹配反馈回填证据
     */
    public int backfillFromMatchingFeedback(int limit) {
        List<MatchingFeedbackDTO> feedbacks = matchingQueryPort.listRecentFeedback(limit);

        List<EvidenceCreateDTO> pendingDtos = new ArrayList<>();
        for (MatchingFeedbackDTO feedback : feedbacks) {
            if (existsBySourceAndTarget("MATCHING_FEEDBACK", feedback.id(), "MATCHING_RECORD")) {
                continue;
            }
            EvidenceCreateDTO dto = new EvidenceCreateDTO();
            dto.setSourceType("MATCHING_FEEDBACK");
            dto.setSourceRefId(feedback.id());
            dto.setSourceTitle("匹配反馈#" + feedback.id());
            dto.setSourceText(feedback.feedbackComment());
            dto.setTargetType("MATCHING_RECORD");
            dto.setTargetRefId(feedback.matchingRecordId());
            dto.setConfidenceScore(new BigDecimal("90"));
            dto.setCredibilityScore(new BigDecimal("70"));
            pendingDtos.add(dto);
        }
        return insertWithHarnessReview(pendingDtos);
    }

    /**
     * 检查是否已存在相同来源和目标的证据（去重）
     */
    public int backfillFromEmpAbility(int limit) {
        List<EmployeeAbilityDTO> abilities = talentQueryPort.listActiveAbilities(limit);

        List<EvidenceCreateDTO> pendingDtos = new ArrayList<>();
        for (EmployeeAbilityDTO ability : abilities) {
            if (existsBySourceAndTarget("EMP_ABILITY", ability.id(), "EMP_ABILITY")) {
                continue;
            }
            EmployeeDTO employee = talentQueryPort.getEmployeeById(ability.empId());
            TagDTO tag = ability.tagId() != null
                    ? tagQueryPort.getTagById(ability.tagId())
                    : null;
            String employeeName = employee != null ? employee.realName() : "员工#" + ability.empId();
            String abilityName = StringUtils.hasText(ability.abilityName())
                    ? ability.abilityName()
                    : (tag != null && StringUtils.hasText(tag.tagName()) ? tag.tagName() : null);
            if (!StringUtils.hasText(abilityName)) {
                log.warn("跳过无名称人员能力证据: abilityId={}, empId={}", ability.id(), ability.empId());
                continue;
            }

            EvidenceCreateDTO dto = new EvidenceCreateDTO();
            dto.setSourceType("EMP_ABILITY");
            dto.setSourceRefId(ability.id());
            dto.setSourceTitle("员工能力来源：" + employeeName + " - " + abilityName);
            dto.setSourceText(buildEmpAbilityEvidenceText(ability, employeeName, abilityName));
            dto.setTargetType("EMP_ABILITY");
            dto.setTargetRefId(ability.id());
            dto.setAbilityName(abilityName);
            dto.setTagId(ability.tagId());
            dto.setConfidenceScore(scoreFromLevel(ability.masteryLevel()));
            dto.setCredibilityScore(scoreFromWeight(ability.sourceWeight()));
            pendingDtos.add(dto);
        }
        return insertWithHarnessReview(pendingDtos);
    }

    public int backfillFromPostAbilityModel(int limit) {
        List<PostAbilityDTO> models = postQueryPort.listActivePostAbilityModels(limit);

        List<EvidenceCreateDTO> pendingDtos = new ArrayList<>();
        for (PostAbilityDTO model : models) {
            if (existsBySourceAndTarget("POST_ABILITY_MODEL", model.id(), "POST_ABILITY_MODEL")) {
                continue;
            }
            PostDTO post = postQueryPort.getPostById(model.postId());
            TagDTO tag = model.tagId() != null
                    ? tagQueryPort.getTagById(model.tagId())
                    : null;
            if (post == null || !StringUtils.hasText(post.postName())) continue;
            String postName = post.postName();
            String abilityName = StringUtils.hasText(model.abilityName())
                    ? model.abilityName()
                    : (tag != null && StringUtils.hasText(tag.tagName()) ? tag.tagName() : null);
            if (!StringUtils.hasText(abilityName)) {
                log.warn("跳过无名称岗位能力证据: modelId={}, postId={}", model.id(), model.postId());
                continue;
            }

            EvidenceCreateDTO dto = new EvidenceCreateDTO();
            dto.setSourceType("POST_ABILITY_MODEL");
            dto.setSourceRefId(model.id());
            dto.setSourceTitle("岗位能力模型来源：" + postName + " - " + abilityName);
            dto.setSourceText(buildPostAbilityModelEvidenceText(model, postName, abilityName));
            dto.setTargetType("POST_ABILITY_MODEL");
            dto.setTargetRefId(model.id());
            dto.setAbilityName(abilityName);
            dto.setTagId(model.tagId());
            dto.setConfidenceScore(scoreFromLevel(model.minRequiredLevel()));
            dto.setCredibilityScore(new BigDecimal("85"));
            pendingDtos.add(dto);
        }
        return insertWithHarnessReview(pendingDtos);
    }

    public List<ContestEvidenceItem> loadEvidenceForTarget(String targetType, Long targetRefId) {
        return evidenceItemMapper.selectList(
                new LambdaQueryWrapper<ContestEvidenceItem>()
                        .eq(ContestEvidenceItem::getTargetType, targetType)
                        .eq(ContestEvidenceItem::getTargetRefId, targetRefId)
                        .eq(ContestEvidenceItem::getIsDeleted, 0)
                        .orderByDesc(ContestEvidenceItem::getCredibilityScore)
                        .orderByDesc(ContestEvidenceItem::getCreatedTime));
    }

    public Map<String, Object> buildChainResult(String subjectType, Long subjectId, String subjectCode,
                                                 String subjectName, List<Map<String, Object>> abilities,
                                                 List<ContestEvidenceItem> allEvidence) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subjectType", subjectType);
        result.put("subjectId", subjectId);
        result.put("subjectCode", subjectCode);
        result.put("subjectName", subjectName);
        result.put("abilityCount", abilities.size());
        result.put("evidenceCount", allEvidence.size());
        result.put("averageConfidence", averageScore(allEvidence, true));
        result.put("averageCredibility", averageScore(allEvidence, false));
        result.put("sourceTypeDistribution", sourceTypeDistribution(allEvidence));
        result.put("abilities", abilities);
        return result;
    }

    public List<Map<String, Object>> toEvidenceCards(List<ContestEvidenceItem> evidences) {
        List<Map<String, Object>> cards = new ArrayList<>();
        for (ContestEvidenceItem evidence : evidences) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", evidence.getId());
            card.put("evidenceCode", evidence.getEvidenceCode());
            card.put("sourceType", evidence.getSourceType());
            card.put("sourceRefId", evidence.getSourceRefId());
            card.put("sourceTitle", evidence.getSourceTitle());
            card.put("sourceText", evidence.getSourceText());
            card.put("abilityName", evidence.getAbilityName());
            card.put("confidenceScore", evidence.getConfidenceScore());
            card.put("credibilityScore", evidence.getCredibilityScore());
            card.put("evidenceStatus", evidence.getEvidenceStatus());
            card.put("createdTime", evidence.getCreatedTime());
            cards.add(card);
        }
        return cards;
    }

    public Map<String, Long> sourceTypeDistribution(List<ContestEvidenceItem> evidences) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (ContestEvidenceItem evidence : evidences) {
            distribution.merge(evidence.getSourceType(), 1L, Long::sum);
        }
        return distribution;
    }

    public BigDecimal averageScore(List<ContestEvidenceItem> evidences, boolean confidence) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (ContestEvidenceItem evidence : evidences) {
            BigDecimal value = confidence ? evidence.getConfidenceScore() : evidence.getCredibilityScore();
            if (value != null) {
                total = total.add(value);
                count++;
            }
        }
        if (count == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return total.divide(new BigDecimal(count), 2, java.math.RoundingMode.HALF_UP);
    }


    public boolean existsBySourceAndTarget(String sourceType, Long sourceRefId, String targetType) {
        Long count = evidenceItemMapper.selectCount(
                new LambdaQueryWrapper<ContestEvidenceItem>()
                        .eq(ContestEvidenceItem::getSourceType, sourceType)
                        .eq(ContestEvidenceItem::getSourceRefId, sourceRefId)
                        .eq(ContestEvidenceItem::getTargetType, targetType));
        return count != null && count > 0;
    }

    /**
     * 生成证据编码：EVD_yyyyMMddHHmmss_SSS
     */
    private String buildEmpAbilityEvidenceText(EmployeeAbilityDTO ability, String employeeName, String abilityName) {
        return "员工：" + employeeName + "\n"
                + "能力：" + abilityName + "\n"
                + "掌握等级：" + safe(ability.masteryLevel()) + "\n"
                + "评价来源：" + safe(ability.evaluationSource()) + "\n"
                + "来源权重：" + safe(ability.sourceWeight()) + "\n"
                + "评价日期：" + safe(ability.evaluationDate()) + "\n"
                + "备注：" + safe(ability.remark());
    }

    private String buildPostAbilityModelEvidenceText(PostAbilityDTO model, String postName, String abilityName) {
        return "岗位：" + postName + "\n"
                + "能力：" + abilityName + "\n"
                + "最低要求等级：" + safe(model.minRequiredLevel()) + "\n"
                + "权重：" + safe(model.weight()) + "\n"
                + "是否必需：" + safe(model.isRequired()) + "\n"
                + "是否核心：" + safe(model.isCore()) + "\n"
                + "模型版本：" + safe(model.modelVersion()) + "\n"
                + "备注：" + safe(model.remark());
    }

    private BigDecimal scoreFromLevel(Integer level) {
        if (level == null) {
            return BigDecimal.ZERO;
        }
        return clampScore(BigDecimal.valueOf(level).multiply(new BigDecimal("20")));
    }

    private BigDecimal scoreFromWeight(BigDecimal weight) {
        if (weight == null) {
            return new BigDecimal("80");
        }
        BigDecimal normalized = weight.compareTo(BigDecimal.ONE) <= 0
                ? weight.multiply(new BigDecimal("100"))
                : weight;
        return clampScore(normalized);
    }

    public String safe(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    public ContestEvidenceItem insertEvidence(EvidenceCreateDTO dto) {
        return insertEvidence(dto, "PENDING", null);
    }

    /**
     * 插入证据并指定审核状态与审核备注（默认 PENDING，兼容手动创建路径）。
     *
     * @param dto           证据数据
     * @param status        证据状态：VERIFIED / PENDING / REJECTED
     * @param reviewComment 审核备注（如 harness 自动审核结果）
     */
    public ContestEvidenceItem insertEvidence(EvidenceCreateDTO dto, String status, String reviewComment) {
        ContestEvidenceItem item = new ContestEvidenceItem();
        item.setEvidenceCode(generateEvidenceCode());
        item.setSourceType(dto.getSourceType());
        item.setSourceRefId(dto.getSourceRefId());
        item.setSourceTitle(dto.getSourceTitle());
        item.setSourceText(dto.getSourceText());
        item.setTargetType(dto.getTargetType());
        item.setTargetRefId(dto.getTargetRefId());
        item.setAbilityName(dto.getAbilityName());
        item.setTagId(dto.getTagId());
        item.setConfidenceScore(clampScore(dto.getConfidenceScore()));
        item.setCredibilityScore(clampScore(dto.getCredibilityScore()));
        item.setEvidenceStatus(status != null ? status : "PENDING");
        item.setReviewComment(reviewComment);

        evidenceItemMapper.insert(item);
        return item;
    }

    // ==================== Harness 自动审核 ====================

    /**
     * 批量插入回填证据，并交由 AI Trust Harness 自动审核：
     * PASS → VERIFIED（自动通过）；REVIEW → PENDING（harness 建议复核，人工仅抽查少量）；
     * BLOCK / RETRY → REJECTED（自动拒绝）；harness 异常 → PENDING（fail-safe，不阻断回填）。
     * <p>
     * 审核结果（decision / riskLevel / supportScore / reasons）写入 reviewComment 形成可追溯记录，
     * 人工无需逐条审核——harness 是回填证据的唯一自动审核者。
     */
    private int insertWithHarnessReview(List<EvidenceCreateDTO> dtos) {
        if (dtos.isEmpty()) {
            return 0;
        }
        List<AiHarnessClaimDTO> claims = new ArrayList<>(dtos.size());
        for (EvidenceCreateDTO dto : dtos) {
            claims.add(buildHarnessClaim(dto));
        }

        List<AiHarnessDecisionDTO> decisions;
        try {
            decisions = aiTrustHarnessService.verifyBatch(claims);
        } catch (Exception e) {
            log.warn("harness 批量审核回填证据失败，降级为待审核: {}", e.getMessage());
            decisions = Collections.nCopies(claims.size(), null);
        }

        int created = 0;
        for (int i = 0; i < dtos.size(); i++) {
            AiHarnessDecisionDTO decision = i < decisions.size() ? decisions.get(i) : null;
            insertEvidence(dtos.get(i), mapDecisionToStatus(decision), buildReviewNote(decision));
            created++;
        }
        return created;
    }

    private AiHarnessClaimDTO buildHarnessClaim(EvidenceCreateDTO dto) {
        AiHarnessClaimDTO claim = new AiHarnessClaimDTO();
        claim.setSourceType(dto.getSourceType());
        claim.setSourceRefId(dto.getSourceRefId());
        claim.setClaimText(StringUtils.hasText(dto.getAbilityName()) ? dto.getAbilityName() : dto.getSourceTitle());
        claim.setEvidenceText(dto.getSourceText());
        claim.setMatchedTagId(dto.getTagId());
        claim.setBusinessTargetType(dto.getTargetType());
        claim.setBusinessTargetId(dto.getTargetRefId());

        if ("EMP_ABILITY".equals(dto.getTargetType())) {
            claim.setScenario("PERSON_ABILITY");
            claim.setClaimType("EMP_ABILITY");
            if (dto.getTargetRefId() != null) {
                claim.setSourceRefs(List.of(SourceRefConstants.empAbilityFactRef(dto.getTargetRefId())));
            }
        } else if ("POST_ABILITY_MODEL".equals(dto.getTargetType())) {
            claim.setScenario("POST_ABILITY");
            claim.setClaimType("POST_ABILITY_MODEL");
            if (dto.getTargetRefId() != null) {
                claim.setSourceRefs(List.of(SourceRefConstants.postAbilityModelFactRef(dto.getTargetRefId())));
            }
        } else if ("MATCHING_RECORD".equals(dto.getTargetType())) {
            claim.setScenario("POST_ABILITY");
            claim.setClaimType("MATCHING_RECORD");
            if (dto.getTargetRefId() != null) {
                claim.setSourceRefs(List.of(SourceRefConstants.matchingRecordRef(dto.getTargetRefId())));
            }
        } else {
            claim.setScenario("PERSON_ABILITY");
            claim.setClaimType(dto.getTargetType());
        }
        return claim;
    }

    private String mapDecisionToStatus(AiHarnessDecisionDTO decision) {
        if (decision == null || decision.getDecision() == null) {
            return "PENDING";
        }
        return switch (decision.getDecision()) {
            case "PASS" -> "VERIFIED";
            case "REVIEW" -> "PENDING";
            case "BLOCK", "RETRY" -> "REJECTED";
            default -> "PENDING";
        };
    }

    private String buildReviewNote(AiHarnessDecisionDTO decision) {
        if (decision == null) {
            return "harness 自动审核失败，保留待审核";
        }
        StringBuilder sb = new StringBuilder("[harness] ");
        sb.append("decision=").append(decision.getDecision())
                .append(" risk=").append(decision.getRiskLevel())
                .append(" supportScore=").append(decision.getSupportScore());
        if (decision.getReasons() != null && !decision.getReasons().isEmpty()) {
            sb.append(" reasons=").append(String.join("; ", decision.getReasons()));
        }
        return sb.length() > 500 ? sb.substring(0, 500) : sb.toString();
    }

    private String generateEvidenceCode() {
        String timestamp = LocalDateTime.now().format(CODE_FORMATTER);
        long seq = SEQUENCE.incrementAndGet() % 1000;
        return String.format("EVD_%s_%03d", timestamp, seq);
    }

    private BigDecimal clampScore(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO;
        }
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (score.compareTo(new BigDecimal("100")) > 0) {
            return new BigDecimal("100");
        }
        return score;
    }
}
