package com.example.matching.service.closure.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.dto.closure.ComprehensiveDiagnosisFactDTO;
import com.example.matching.dto.closure.ComprehensiveDiagnosisFactDTO.*;
import com.example.matching.dto.learning.LearningPathItemDTO;
import com.example.matching.dto.learning.LearningPathRequestDTO;
import com.example.matching.dto.matching.MatchingReportDTO;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.post.PostQueryPort.PostAbilityDTO;
import com.example.matching.port.post.PostQueryPort.PostDTO;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.tag.TagQueryPort.TagDTO;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.port.talent.TalentQueryPort.EmployeeAbilityDTO;
import com.example.matching.port.talent.TalentQueryPort.EmployeeDTO;
import com.example.matching.service.learning.LearningPathService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 综合诊断事实包构建：评分快照、硬性条件、能力差距、证据风险、语义/反馈信号、学习资源。
 * <p>
 * 从 ComprehensiveDiagnosisServiceImpl（700+ 行）中拆分的只读事实构建组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComprehensiveDiagnosisFactBuilder {

    private final TalentQueryPort talentQueryPort;
    private final PostQueryPort postQueryPort;
    private final TagQueryPort tagQueryPort;
    private final LearningPathService learningPathService;
    private final ObjectMapper objectMapper;
    public ComprehensiveDiagnosisFactDTO buildFactPackage(MatchingRecord record) {
        ComprehensiveDiagnosisFactDTO fact = new ComprehensiveDiagnosisFactDTO();
        fact.setRecordId(record.getId());
        fact.setEmpId(record.getEmpId());
        fact.setPostId(record.getPostId());

        // 加载关联数据
        EmployeeDTO emp = record.getEmpId() != null ? talentQueryPort.getEmployeeById(record.getEmpId()) : null;
        PostDTO post = record.getPostId() != null ? postQueryPort.getPostById(record.getPostId()) : null;

        fact.setEmpName(emp != null ? emp.realName() : null);
        fact.setPostName(post != null ? post.postName() : null);
        fact.setPostLevel(post != null ? post.postLevel() : null);

        // 维度1: 分数快照
        fact.setScores(buildScoreSnapshot(record));

        // 维度2: 硬条件差距
        fact.setHardConditions(buildHardConditionFacts(record));

        // 维度3: 能力等级差距
        List<AbilityGapFact> abilityGaps = buildAbilityGapFacts(record);
        fact.setAbilityGaps(abilityGaps);

        // 维度4: 证据风险
        fact.setEvidenceRisks(buildEvidenceRiskFacts(record, abilityGaps));

        // 维度5: 语义匹配信号
        fact.setSemanticSignals(buildSemanticSignal(record, emp, post));

        // 维度6: 反馈信号
        fact.setFeedbackSignals(buildFeedbackSignal(record));

        // 维度7: 可用学习资源
        fact.setAvailableLearningResources(buildLearningResourceFacts(abilityGaps));

        return fact;
    }

    /**
     * 维度1: 构建多维度分数快照
     */
    private ScoreSnapshot buildScoreSnapshot(MatchingRecord record) {
        ScoreSnapshot scores = new ScoreSnapshot();
        scores.setFinalMatchScore(record.getFinalMatchScore() != null ? record.getFinalMatchScore() : record.getAiMatchScore());
        scores.setAbilityScore(record.getL2Score());
        scores.setSemanticScore(record.getVectorScore());
        scores.setEvidenceScore(record.getEvidenceScore());
        scores.setLlmScore(record.getLlmScore());
        scores.setModelQualityScore(record.getModelQualityCoefficient());
        scores.setHardConditionScore(null); // 从 quantitativeReport 解析
        scores.setFeedbackAdjustment(record.getFeedbackCalibration());
        scores.setScreeningLevel(record.getScreeningLevel());
        scores.setMatchStatus(record.getMatchStatus());

        // 尝试从 quantitativeReport 解析更多分数维度
        if (hasText(record.getQuantitativeReport())) {
            try {
                MatchingReportDTO report = objectMapper.readValue(record.getQuantitativeReport(), MatchingReportDTO.class);
                if (report.getFeedbackAdjustment() != null) {
                    scores.setFeedbackAdjustment(report.getFeedbackAdjustment());
                }
            } catch (Exception e) {
                log.debug("Failed to parse quantitativeReport for score snapshot: recordId={}", record.getId());
            }
        }

        return scores;
    }

    /**
     * 维度2: 构建硬条件差距事实
     */
    private List<HardConditionFact> buildHardConditionFacts(MatchingRecord record) {
        List<HardConditionFact> facts = new ArrayList<>();
        if (!hasText(record.getHardConditionResult())) {
            return facts;
        }
        try {
            Map<String, Object> hcResult = objectMapper.readValue(
                    record.getHardConditionResult(), new TypeReference<Map<String, Object>>() {});
            Object detailsObj = hcResult.get("details");
            if (detailsObj instanceof List<?> details) {
                for (Object item : details) {
                    if (item instanceof Map<?, ?> map) {
                        HardConditionFact fact = new HardConditionFact();
                        fact.setField(str(map.get("field")));
                        fact.setLabel(str(map.get("label")));
                        fact.setOperator(str(map.get("operator")));
                        fact.setExpectedValue(str(map.get("expectedValue")));
                        fact.setActualValue(str(map.get("actualValue")));
                        fact.setPassed(Boolean.TRUE.equals(map.get("passed")));
                        fact.setSource(str(map.get("source")));
                        facts.add(fact);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse hardConditionResult: recordId={}", record.getId());
        }
        return facts;
    }

    /**
     * 维度3: 构建能力等级差距事实
     * <p>
     * 优先从 quantitativeReport 解析（含匹配系数、相似度等详细数据），
     * 降级到直接比对岗位要求与员工能力。
     */
    private List<AbilityGapFact> buildAbilityGapFacts(MatchingRecord record) {
        // 优先从报告解析
        List<AbilityGapFact> gaps = extractGapsFromReport(record);
        if (!gaps.isEmpty()) {
            return gaps;
        }
        // 降级：直接比对
        return compareAbilityLevels(record.getEmpId(), record.getPostId());
    }

    /**
     * 从量化报告中提取能力差距
     */
    private List<AbilityGapFact> extractGapsFromReport(MatchingRecord record) {
        if (!hasText(record.getQuantitativeReport())) {
            return List.of();
        }
        try {
            MatchingReportDTO report = objectMapper.readValue(record.getQuantitativeReport(), MatchingReportDTO.class);
            if (report.getAbilityDetails() == null || report.getAbilityDetails().isEmpty()) {
                return List.of();
            }
            List<AbilityGapFact> gaps = new ArrayList<>();
            for (MatchingReportDTO.AbilityDetail detail : report.getAbilityDetails()) {
                // 只收集未通过或弱证据的能力
                if (detail.isPassed() && !detail.isWeakEvidence()) {
                    continue;
                }
                AbilityGapFact gap = new AbilityGapFact();
                gap.setTagId(detail.getTagId());
                gap.setAbilityName(detail.getTagName());
                gap.setCurrentLevel(detail.getActualLevel());
                gap.setRequiredLevel(detail.getRequiredLevel());
                gap.setCore(detail.getIsCore() != null && detail.getIsCore() == 1);
                gap.setRequired(detail.getIsRequired() != null && detail.getIsRequired() == 1);
                gap.setMatchCoefficient(detail.getMatchCoefficient());
                gap.setSimilarityScore(detail.getSimilarityScore());
                gap.setWeakEvidence(detail.isWeakEvidence());
                gap.setReason(resolveGapReason(detail));

                // 证据来源
                if (detail.getEvidences() != null) {
                    for (MatchingReportDTO.EvidenceItem ev : detail.getEvidences()) {
                        EvidenceSource source = new EvidenceSource();
                        source.setSource(ev.getSource());
                        source.setLevel(ev.getLevel());
                        source.setCredibility(ev.getCredibility());
                        source.setTimeFactor(ev.getTimeFactor());
                        gap.getEvidenceSources().add(source);
                    }
                }
                gaps.add(gap);
            }
            return gaps;
        } catch (Exception e) {
            log.debug("Failed to parse report for ability gaps: recordId={}", record.getId());
            return List.of();
        }
    }

    /**
     * 直接比对岗位要求与员工能力（降级方案）
     */
    private List<AbilityGapFact> compareAbilityLevels(Long empId, Long postId) {
        if (empId == null || postId == null) {
            return List.of();
        }
        List<PostAbilityDTO> requirements = postQueryPort.listRequirementsByPostId(postId);
        if (requirements.isEmpty()) {
            return List.of();
        }
        List<EmployeeAbilityDTO> abilities = talentQueryPort.listAbilitiesByEmpId(empId);

        Map<Long, EmployeeAbilityDTO> abilityByTag = new HashMap<>();
        Map<String, EmployeeAbilityDTO> abilityByName = new HashMap<>();
        for (EmployeeAbilityDTO ability : abilities) {
            if (ability.tagId() != null) {
                abilityByTag.put(ability.tagId(), ability);
            }
            if (hasText(ability.abilityName())) {
                abilityByName.put(normalizeAbilityName(ability.abilityName()), ability);
            }
        }
        Map<Long, String> tagNames = loadTagNames(requirements);

        List<AbilityGapFact> gaps = new ArrayList<>();
        for (PostAbilityDTO req : requirements) {
            Integer requiredLevel = req.minRequiredLevel() != null ? req.minRequiredLevel() : 0;
            EmployeeAbilityDTO ability = req.tagId() != null ? abilityByTag.get(req.tagId()) : null;
            if (ability == null && hasText(req.abilityName())) {
                ability = abilityByName.get(normalizeAbilityName(req.abilityName()));
            }
            int currentLevel = ability != null && ability.masteryLevel() != null ? ability.masteryLevel() : 0;
            if (currentLevel >= requiredLevel) {
                continue;
            }
            AbilityGapFact gap = new AbilityGapFact();
            gap.setTagId(req.tagId());
            String abilityName = hasText(req.abilityName()) ? req.abilityName().trim() : tagNames.get(req.tagId());
            gap.setAbilityName(hasText(abilityName) ? abilityName : "Ability#" + req.tagId());
            gap.setCurrentLevel(BigDecimal.valueOf(currentLevel));
            gap.setRequiredLevel(requiredLevel);
            gap.setCore(req.isCore() != null && req.isCore() == 1);
            gap.setRequired(req.isRequired() != null && req.isRequired() == 1);
            gap.setWeakEvidence(ability == null);
            gap.setReason("当前等级 " + currentLevel + " 低于要求等级 " + requiredLevel);
            gaps.add(gap);
        }
        return gaps;
    }

    /**
     * 维度4: 构建证据风险事实
     */
    private List<EvidenceRiskFact> buildEvidenceRiskFacts(MatchingRecord record, List<AbilityGapFact> abilityGaps) {
        List<EvidenceRiskFact> risks = new ArrayList<>();

        // 从能力差距中提取弱证据风险
        for (AbilityGapFact gap : abilityGaps) {
            if (gap.isWeakEvidence()) {
                EvidenceRiskFact risk = new EvidenceRiskFact();
                risk.setAbilityName(gap.getAbilityName());
                risk.setRiskType("WEAK_SOURCE");
                risk.setDescription(gap.getAbilityName() + " 缺少可靠证据支撑");
                risk.setSourceCount(gap.getEvidenceSources().size());
                risk.setPrimarySourceType(detectPrimarySourceType(gap.getEvidenceSources()));
                risks.add(risk);
            } else if (gap.getEvidenceSources().size() == 1) {
                EvidenceRiskFact risk = new EvidenceRiskFact();
                risk.setAbilityName(gap.getAbilityName());
                risk.setRiskType("SINGLE_SOURCE");
                risk.setDescription(gap.getAbilityName() + " 仅有单一来源证据");
                risk.setSourceCount(1);
                risk.setPrimarySourceType(gap.getEvidenceSources().get(0).getSource());
                risk.setCredibility(gap.getEvidenceSources().get(0).getCredibility());
                risks.add(risk);
            } else if (!gap.getEvidenceSources().isEmpty()) {
                // 检查是否有过期或低可信度证据
                for (EvidenceSource source : gap.getEvidenceSources()) {
                    if (source.getCredibility() != null && source.getCredibility().compareTo(new BigDecimal("0.5")) < 0) {
                        EvidenceRiskFact risk = new EvidenceRiskFact();
                        risk.setAbilityName(gap.getAbilityName());
                        risk.setRiskType("LOW_CREDIBILITY");
                        risk.setDescription(gap.getAbilityName() + " 存在低可信度来源 (" + source.getSource() + ")");
                        risk.setSourceCount(gap.getEvidenceSources().size());
                        risk.setPrimarySourceType(source.getSource());
                        risk.setCredibility(source.getCredibility());
                        risks.add(risk);
                        break;
                    }
                }
            }
        }

        // 从报告中解析证据置信度
        if (hasText(record.getQuantitativeReport())) {
            try {
                MatchingReportDTO report = objectMapper.readValue(record.getQuantitativeReport(), MatchingReportDTO.class);
                if (report.getAbilityDetails() != null) {
                    for (MatchingReportDTO.AbilityDetail detail : report.getAbilityDetails()) {
                        if (detail.isPassed() && !detail.isWeakEvidence()) {
                            continue;
                        }
                        boolean alreadyCovered = risks.stream()
                                .anyMatch(r -> r.getAbilityName() != null && r.getAbilityName().equals(detail.getTagName()));
                        if (!alreadyCovered && detail.isWeakEvidence()) {
                            EvidenceRiskFact risk = new EvidenceRiskFact();
                            risk.setAbilityName(detail.getTagName());
                            risk.setRiskType("WEAK_SOURCE");
                            risk.setDescription(detail.getTagName() + " 证据可信度不足");
                            risk.setSourceCount(detail.getEvidences() != null ? detail.getEvidences().size() : 0);
                            risks.add(risk);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse report for evidence risks: recordId={}", record.getId());
            }
        }

        return risks;
    }

    /**
     * 维度5: 构建语义匹配信号
     */
    private SemanticSignal buildSemanticSignal(MatchingRecord record, EmployeeDTO emp, PostDTO post) {
        SemanticSignal signal = new SemanticSignal();
        signal.setVectorScore(record.getVectorScore());
        signal.setProfileSemanticScore(record.getProfileSemanticScore());
        signal.setVectorAvailable(record.getVectorScore() != null);

        // 构建摘要（截断以控制 token）
        if (emp != null) {
            signal.setEmployeeProfileSummary(buildEmployeeSummary(emp, record.getEmpId()));
        }
        if (post != null) {
            signal.setPostDescriptionSummary(truncate(post.jobDescription(), 500));
        }

        return signal;
    }

    /**
     * 维度6: 构建反馈信号
     */
    private FeedbackSignal buildFeedbackSignal(MatchingRecord record) {
        FeedbackSignal signal = new FeedbackSignal();
        signal.setFeedbackCalibration(record.getFeedbackCalibration());
        signal.setApprovalStatus(record.getApprovalStatus());
        signal.setManualRemark(record.getManualRemark());

        // 解析结构化反馈原因
        if (hasText(record.getFeedbackReasons())) {
            try {
                List<String> reasons = objectMapper.readValue(record.getFeedbackReasons(), new TypeReference<List<String>>() {});
                signal.setFeedbackReasons(reasons);
            } catch (Exception e) {
                log.debug("Failed to parse feedbackReasons: recordId={}", record.getId());
            }
        }

        return signal;
    }

    /**
     * 维度7: 构建可用学习资源
     */
    private List<LearningResourceFact> buildLearningResourceFacts(List<AbilityGapFact> abilityGaps) {
        List<LearningResourceFact> resources = new ArrayList<>();
        List<String> gapNames = abilityGaps.stream()
                .map(AbilityGapFact::getAbilityName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (gapNames.isEmpty()) {
            return resources;
        }

        try {
            LearningPathRequestDTO request = new LearningPathRequestDTO();
            request.setAbilityNames(gapNames);
            request.setTargetLevel(3);
            List<LearningPathItemDTO> pathItems = learningPathService.generateLearningPath(request);

            for (LearningPathItemDTO item : pathItems) {
                LearningResourceFact resource = new LearningResourceFact();
                resource.setAbilityName(item.getAbilityName());
                resource.setTitle(item.getTitle());
                resource.setResourceType(item.getResourceType());
                resource.setDifficultyLevel(item.getDifficultyLevel());
                resource.setUrl(item.getUrl());
                resources.add(resource);
            }
        } catch (Exception e) {
            log.debug("Failed to load learning resources: {}", e.getMessage());
        }

        return resources;
    }

    // ===== AI 综合分析 =====

    public ComprehensiveDiagnosisFactDTO buildEmptyFactPackage(Long recordId) {
        ComprehensiveDiagnosisFactDTO fact = new ComprehensiveDiagnosisFactDTO();
        fact.setRecordId(recordId);
        fact.setScores(new ScoreSnapshot());
        return fact;
    }

    private String resolveGapReason(MatchingReportDTO.AbilityDetail detail) {
        if (hasText(detail.getPassedDesc())) {
            return detail.getPassedDesc();
        }
        if (detail.isWeakEvidence()) {
            return "证据薄弱，需要补充确认";
        }
        return "能力等级未达到岗位要求";
    }

    private String detectPrimarySourceType(List<EvidenceSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return "NONE";
        }
        return sources.stream()
                .collect(Collectors.groupingBy(EvidenceSource::getSource, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
    }

    private String buildEmployeeSummary(EmployeeDTO emp, Long empId) {
        StringBuilder sb = new StringBuilder();
        sb.append(emp.realName());
        if (emp.level() != null) {
            sb.append(" (").append(emp.level()).append(")");
        }
        // 加载能力摘要
        List<EmployeeAbilityDTO> abilities = talentQueryPort.listAbilitiesByEmpId(empId).stream()
                .limit(10).toList();
        if (!abilities.isEmpty()) {
            Set<Long> tagIds = abilities.stream().map(EmployeeAbilityDTO::tagId).collect(Collectors.toSet());
            Map<Long, String> tagNames = new HashMap<>();
            List<TagDTO> tags = tagQueryPort.batchGetTags(new ArrayList<>(tagIds));
            for (TagDTO tag : tags) {
                tagNames.put(tag.id(), tag.tagName());
            }
            sb.append(" 能力: ");
            for (EmployeeAbilityDTO a : abilities) {
                String name = a.abilityName();
                if (name == null || name.isBlank()) {
                    name = tagNames.get(a.tagId());
                }
                if (name == null || name.isBlank()) {
                    continue;
                }
                sb.append(name).append("(L").append(a.masteryLevel()).append(") ");
            }
        }
        return truncate(sb.toString(), 300);
    }

    private Map<Long, String> loadTagNames(List<PostAbilityDTO> requirements) {
        Set<Long> tagIds = new LinkedHashSet<>();
        for (PostAbilityDTO req : requirements) {
            if (req.tagId() != null) {
                tagIds.add(req.tagId());
            }
        }
        if (tagIds.isEmpty()) {
            return Map.of();
        }
        List<TagDTO> tags = tagQueryPort.batchGetTags(new ArrayList<>(tagIds));
        Map<Long, String> result = new LinkedHashMap<>();
        for (TagDTO tag : tags) {
            result.put(tag.id(), tag.tagName());
        }
        return result;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String str(Object value) {
        return value != null ? value.toString() : null;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private String normalizeAbilityName(String abilityName) {
        return abilityName == null ? "" : abilityName.trim().replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }
}
