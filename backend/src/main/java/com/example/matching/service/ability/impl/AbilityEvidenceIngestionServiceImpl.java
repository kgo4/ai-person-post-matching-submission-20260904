package com.example.matching.service.ability.impl;

import com.example.matching.port.contest.ContestQueryPort;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.post.PostQueryPort.PostAbilityDTO;
import com.example.matching.port.post.PostQueryPort.PostDTO;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.tag.TagQueryPort.TagDTO;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.port.talent.TalentQueryPort.EmployeeAbilityDTO;
import com.example.matching.port.talent.TalentQueryPort.EmployeeDTO;
import com.example.matching.service.ability.AbilityCrossValidationService;
import com.example.matching.service.ability.AbilityEvidenceIngestionService;
import com.example.matching.service.ability.DynamicCredibilityService;
import com.example.matching.service.rag.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class AbilityEvidenceIngestionServiceImpl implements AbilityEvidenceIngestionService {

    private static final DateTimeFormatter CODE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    private final TalentQueryPort talentQueryPort;
    private final PostQueryPort postQueryPort;
    private final TagQueryPort tagQueryPort;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final ContestQueryPort contestQueryPort;
    private final AbilityCrossValidationService crossValidationService;
    private final DynamicCredibilityService dynamicCredibilityService;

    @Override
    @Transactional
    public void ingestEmployeeAbility(Long abilityId, String sourceType) {
        if (abilityId == null) {
            return;
        }
        EmployeeAbilityDTO ability = talentQueryPort.getEmpAbilityById(abilityId);
        if (ability == null) {
            return;
        }
        EmployeeDTO employee = talentQueryPort.getEmployeeById(ability.empId());
        TagDTO tag = ability.tagId() != null ? tagQueryPort.getTagById(ability.tagId()) : null;
        String effectiveSourceType = hasText(sourceType) ? sourceType : "EMP_ABILITY";
        String employeeName = employee != null ? employee.realName() : "员工#" + ability.empId();
        String abilityName = hasText(ability.abilityName())
                ? ability.abilityName()
                : (tag != null && hasText(tag.tagName()) ? tag.tagName() : null);
        if (!hasText(abilityName)) {
            log.warn("跳过无名称人员能力知识摄取: abilityId={}, empId={}", ability.id(), ability.empId());
            return;
        }

        AbilityCrossValidationService.ValidationResult validationResult = null;
        try {
            validationResult = crossValidationService.validateAbility(
                    ability.empId(), ability.tagId(), ability.masteryLevel(),
                    ability.evaluationSource(), ability.id()
            );
            log.info("交叉验证结果: empId={}, ability={}, status={}, score={}, recommendation={}",
                    ability.empId(), abilityName, validationResult.status(),
                    validationResult.consistencyScore(), validationResult.recommendation());
        } catch (Exception e) {
            log.warn("交叉验证异常，继续处理: {}", e.getMessage());
        }

        String title = "人员能力：" + employeeName + " - " + abilityName;
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("人员：").append(employeeName).append("\n");
        contentBuilder.append("能力：").append(abilityName).append("\n");
        contentBuilder.append("掌握等级：").append(safe(ability.masteryLevel())).append("\n");
        contentBuilder.append("能力来源：").append(safe(ability.evaluationSource())).append("\n");
        contentBuilder.append("来源权重：").append(safe(ability.sourceWeight())).append("\n");
        contentBuilder.append("评价日期：").append(safe(ability.evaluationDate())).append("\n");

        if (validationResult != null) {
            contentBuilder.append("\n\n## 交叉验证结果\n");
            contentBuilder.append("一致性分数：").append(validationResult.consistencyScore()).append("\n");
            contentBuilder.append("验证状态：").append(validationResult.status()).append("\n");
            contentBuilder.append("历史证据数：").append(validationResult.historyCount()).append("\n");
            contentBuilder.append("建议操作：").append(validationResult.recommendation()).append("\n");
            if (validationResult.detail() != null && !validationResult.detail().isBlank()) {
                contentBuilder.append("验证详情：").append(validationResult.detail()).append("\n");
            }
        }

        String content = contentBuilder.toString();

        double dynamicWeight = dynamicCredibilityService.getWeight(ability.evaluationSource());
        BigDecimal credibilityScore = BigDecimal.valueOf(dynamicWeight * 100);

        if (validationResult != null && "CONSISTENT".equals(validationResult.status())) {
            credibilityScore = credibilityScore.add(new BigDecimal("5")).min(new BigDecimal("100"));
            dynamicCredibilityService.recordFeedback(ability.evaluationSource(), true, null);
        } else if (validationResult != null && "INCONSISTENT".equals(validationResult.status())) {
            credibilityScore = credibilityScore.subtract(new BigDecimal("10")).max(new BigDecimal("0"));
            dynamicCredibilityService.recordFeedback(ability.evaluationSource(), false, ability.masteryLevel());
        }

        Long documentId = saveAndIndexKnowledgeDocument(effectiveSourceType, ability.id(), title, content);
        List<Long> documentIds = documentId != null ? List.of(documentId) : Collections.emptyList();

        createEvidenceIfAbsent(effectiveSourceType, ability.id(), "EMP_ABILITY", ability.id(),
                title, content, abilityName, ability.tagId(), scoreFromLevel(ability.masteryLevel()),
                credibilityScore, null, documentIds);
    }

    @Override
    @Transactional
    public void ingestPostAbilityModel(Long modelId, String sourceType) {
        if (modelId == null) {
            return;
        }
        PostAbilityDTO model = postQueryPort.getPostAbilityModelById(modelId);
        if (model == null) {
            return;
        }
        PostDTO post = postQueryPort.getPostById(model.postId());
        TagDTO tag = model.tagId() != null ? tagQueryPort.getTagById(model.tagId()) : null;
        String effectiveSourceType = hasText(sourceType) ? sourceType : "POST_ABILITY_MODEL";
        String postName = post != null ? post.postName() : "岗位#" + model.postId();
        // 岗位能力模型允许未关联系统标签，优先使用模型自身的能力名称，避免出现“能力#null”。
        String abilityName = model.abilityName();
        if (!hasText(abilityName)) {
            abilityName = tag != null && hasText(tag.tagName()) ? tag.tagName() : null;
        }
        if (!hasText(abilityName)) {
            log.warn("跳过无名称岗位能力知识摄取: modelId={}, postId={}", model.id(), model.postId());
            return;
        }
        String title = "岗位能力：" + postName + " - " + abilityName;
        String content = "岗位：" + postName + "\n"
                + "能力：" + abilityName + "\n"
                + "最低要求等级：" + safe(model.minRequiredLevel()) + "\n"
                + "权重：" + safe(model.weight()) + "\n"
                + "是否必需：" + safe(model.isRequired()) + "\n"
                + "是否核心：" + safe(model.isCore()) + "\n"
                + "备注：" + "";

        Long documentId = saveAndIndexKnowledgeDocument(effectiveSourceType, model.id(), title, content);
        List<Long> documentIds = documentId != null ? List.of(documentId) : Collections.emptyList();

        createEvidenceIfAbsent(effectiveSourceType, model.id(), "POST_ABILITY_MODEL", model.id(),
                title, content, abilityName, model.tagId(), scoreFromLevel(model.minRequiredLevel()),
                new BigDecimal("85"), null, documentIds);
    }

    private void createEvidenceIfAbsent(String sourceType, Long sourceRefId, String targetType, Long targetRefId,
                                        String title, String text, String abilityName, Long tagId,
                                        BigDecimal confidenceScore, BigDecimal credibilityScore,
                                        List<Long> ragChunkIds, List<Long> ragDocumentIds) {
        if (contestQueryPort.evidenceExists(sourceType, sourceRefId, targetType, targetRefId)) {
            return;
        }

        contestQueryPort.saveEvidence(new ContestQueryPort.EvidenceWriteCommand(
                generateEvidenceCode(), sourceType, sourceRefId, title, text,
                targetType, targetRefId, abilityName, tagId,
                clampScore(confidenceScore), clampScore(credibilityScore),
                ragChunkIds, ragDocumentIds));
    }

    private Long saveAndIndexKnowledgeDocument(String sourceType, Long sourceRefId, String title, String content) {
        Long existingId = knowledgeDocumentService.findExistingDocumentId(sourceType, sourceRefId);

        com.example.matching.dto.rag.KnowledgeDocumentSaveDTO dto =
                new com.example.matching.dto.rag.KnowledgeDocumentSaveDTO();
        if (existingId != null) {
            dto.setId(existingId);
        }
        dto.setSourceType(sourceType);
        dto.setSourceRefId(sourceRefId);
        dto.setTitle(title);
        dto.setContent(content);
        com.example.matching.entity.rag.RagKnowledgeDocument saved = knowledgeDocumentService.saveDocument(dto);
        if (saved != null && saved.getId() != null) {
            knowledgeDocumentService.indexDocument(saved.getId());
            return saved.getId();
        }
        return null;
    }

    private String generateEvidenceCode() {
        String timestamp = LocalDateTime.now().format(CODE_FORMATTER);
        long seq = SEQUENCE.incrementAndGet() % 1000;
        return String.format("EVD_%s_%03d", timestamp, seq);
    }

    private BigDecimal scoreFromLevel(Integer level) {
        if (level == null) {
            return BigDecimal.ZERO;
        }
        return clampScore(BigDecimal.valueOf(level).multiply(new BigDecimal("20")));
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(Object value) {
        return value != null ? String.valueOf(value) : "";
    }
}
