package com.example.matching.service.ability.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.contest.ContestEvidenceItem;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.contest.ContestEvidenceItemMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.ability.AbilityCrossValidationService;
import com.example.matching.service.ability.AbilityQualityMonitorService;
import com.example.matching.service.rag.RagRetrievalService;
import com.example.matching.service.rag.RagScenarioEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 能力质量监控服务实现
 * <p>
 * 基于RAG检索和数据分析，监控能力数据质量，识别问题数据，
 * 生成质量报告，实现能力数据的持续改进闭环。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbilityQualityMonitorServiceImpl implements AbilityQualityMonitorService {

    private final EmpAbilityMapper empAbilityMapper;
    private final EmpEmployeeMapper empEmployeeMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final ContestEvidenceItemMapper evidenceItemMapper;
    private final RagRetrievalService ragRetrievalService;
    private final AbilityCrossValidationService crossValidationService;

    private static final int DEFAULT_EXPIRY_MONTHS = 12;
    private static final int CONSISTENCY_THRESHOLD = 70;
    private static final AtomicLong REPORT_SEQUENCE = new AtomicLong(0);

    @Override
    public List<QualityIssue> scanEmployeeAbilities(Long empId) {
        List<QualityIssue> issues = new ArrayList<>();
        ScanContext context = loadScanContext(empId, true);

        // 检测各类问题
        issues.addAll(detectIsolatedAbilities(context));
        issues.addAll(detectExpiredAbilities(context, DEFAULT_EXPIRY_MONTHS));
        issues.addAll(detectInconsistentAbilities(context));
        issues.addAll(detectMissingEvidence(context));

        return issues;
    }

    @Override
    public QualityReport scanAllAbilities(int limit) {
        List<QualityIssue> allIssues = new ArrayList<>();

        // 获取所有员工
        List<EmpEmployee> employees = empEmployeeMapper.selectList(
                Wrappers.<EmpEmployee>lambdaQuery()
                        .last("LIMIT " + limit)
        );

        for (EmpEmployee employee : employees) {
            try {
                List<QualityIssue> issues = scanEmployeeAbilities(employee.getId());
                allIssues.addAll(issues);
            } catch (Exception e) {
                log.warn("扫描员工能力失败: empId={}, error={}", employee.getId(), e.getMessage());
            }
        }

        // 统计各级别问题数量
        long highCount = allIssues.stream().filter(i -> "HIGH".equals(i.severity())).count();
        long mediumCount = allIssues.stream().filter(i -> "MEDIUM".equals(i.severity())).count();
        long lowCount = allIssues.stream().filter(i -> "LOW".equals(i.severity())).count();

        // 统计总能力数
        Long totalAbilities = empAbilityMapper.selectCount(
                Wrappers.<EmpAbility>lambdaQuery().eq(EmpAbility::getIsDeleted, 0)
        );

        String reportId = "QR_" + System.currentTimeMillis() + "_" + REPORT_SEQUENCE.incrementAndGet();

        return new QualityReport(
                reportId,
                totalAbilities != null ? totalAbilities.intValue() : 0,
                allIssues.size(),
                (int) highCount,
                (int) mediumCount,
                (int) lowCount,
                allIssues,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    @Override
    public List<QualityIssue> detectIsolatedAbilities(Long empId) {
        return detectIsolatedAbilities(loadScanContext(empId, true));
    }

    private List<QualityIssue> detectIsolatedAbilities(ScanContext context) {
        List<QualityIssue> issues = new ArrayList<>();

        for (EmpAbility ability : context.abilities()) {
            if (!context.evidenceTargetRefs().contains(ability.getId())) {
                AbilityTag tag = context.tagsById().get(ability.getTagId());
                String abilityName = resolveAbilityName(ability, tag);

                issues.add(new QualityIssue(
                        ISSUE_ISOLATED,
                        context.empId(),
                        context.employeeName(),
                        ability.getTagId(),
                        abilityName,
                        "能力记录缺少来源证据支撑",
                        "MEDIUM",
                        "建议补充能力来源证据或重新评估"
                ));
            }
        }

        return issues;
    }

    @Override
    public List<QualityIssue> detectExpiredAbilities(Long empId, int expiryMonths) {
        return detectExpiredAbilities(loadScanContext(empId, false), expiryMonths);
    }

    private List<QualityIssue> detectExpiredAbilities(ScanContext context, int expiryMonths) {
        List<QualityIssue> issues = new ArrayList<>();

        LocalDate expiryDate = LocalDate.now().minusMonths(expiryMonths);
        for (EmpAbility ability : context.abilities()) {
            if (ability.getEvaluationDate() == null || !ability.getEvaluationDate().isBefore(expiryDate)) {
                continue;
            }
            AbilityTag tag = context.tagsById().get(ability.getTagId());
            String abilityName = resolveAbilityName(ability, tag);

            issues.add(new QualityIssue(
                    ISSUE_EXPIRED,
                    context.empId(),
                    context.employeeName(),
                    ability.getTagId(),
                    abilityName,
                    "能力评估已过期（评估日期：" + ability.getEvaluationDate() + "）",
                    "LOW",
                    "建议重新评估该能力"
            ));
        }

        return issues;
    }

    @Override
    public List<QualityIssue> detectInconsistentAbilities(Long empId) {
        return detectInconsistentAbilities(loadScanContext(empId, false));
    }

    private List<QualityIssue> detectInconsistentAbilities(ScanContext context) {
        List<QualityIssue> issues = new ArrayList<>();

        for (EmpAbility ability : context.abilities()) {
            try {
                AbilityCrossValidationService.ValidationResult result =
                        crossValidationService.validateAbility(
                                context.empId(), ability.getTagId(), ability.getMasteryLevel(),
                                ability.getEvaluationSource(), ability.getId()
                        );

                if ("INCONSISTENT".equals(result.status()) && result.consistencyScore() < CONSISTENCY_THRESHOLD) {
                    AbilityTag tag = context.tagsById().get(ability.getTagId());
                    String abilityName = resolveAbilityName(ability, tag);

                    issues.add(new QualityIssue(
                            ISSUE_INCONSISTENT,
                            context.empId(),
                            context.employeeName(),
                            ability.getTagId(),
                            abilityName,
                            "多来源评估结果不一致（一致性分数：" + result.consistencyScore() + "）",
                            result.consistencyScore() < 40 ? "HIGH" : "MEDIUM",
                            "建议人工审核并确认正确等级"
                    ));
                }
            } catch (Exception e) {
                log.warn("检测不一致能力失败: empId={}, tagId={}, error={}", context.empId(), ability.getTagId(), e.getMessage());
            }
        }

        return issues;
    }

    @Override
    public List<QualityIssue> detectMissingEvidence(Long empId) {
        return detectMissingEvidence(loadScanContext(empId, false));
    }

    private List<QualityIssue> detectMissingEvidence(ScanContext context) {
        List<QualityIssue> issues = new ArrayList<>();

        for (EmpAbility ability : context.abilities()) {
            AbilityTag tag = context.tagsById().get(ability.getTagId());
            String abilityName = resolveAbilityName(ability, tag);

            try {
                // RAG检索相关证据
                String query = "能力 " + abilityName + " 证据 评估";
                String ragContext = ragRetrievalService.retrieveContext(query, RagScenarioEnum.EVIDENCE_TRACE, 2);

                if (ragContext == null || ragContext.isBlank()) {
                    issues.add(new QualityIssue(
                            ISSUE_NO_EVIDENCE,
                            context.empId(),
                            context.employeeName(),
                            ability.getTagId(),
                            abilityName,
                            "RAG知识库中未找到相关证据",
                            "MEDIUM",
                            "建议补充能力证明材料到知识库"
                    ));
                }
            } catch (Exception e) {
                log.warn("RAG检索证据失败: empId={}, tagId={}, error={}", context.empId(), ability.getTagId(), e.getMessage());
            }
        }

        return issues;
    }

    private ScanContext loadScanContext(Long empId, boolean loadEvidence) {
        List<EmpAbility> abilities = empAbilityMapper.selectList(
                Wrappers.<EmpAbility>lambdaQuery()
                        .eq(EmpAbility::getEmpId, empId)
                        .eq(EmpAbility::getIsDeleted, 0)
        );
        List<EmpAbility> safeAbilities = abilities != null ? abilities : List.of();

        EmpEmployee employee = empEmployeeMapper.selectById(empId);
        String employeeName = employee != null ? employee.getRealName() : "员工#" + empId;

        Map<Long, AbilityTag> tagsById = loadTags(safeAbilities);
        Set<Long> evidenceTargetRefs = loadEvidence
                ? loadEvidenceTargetRefs(safeAbilities)
                : Set.of();

        return new ScanContext(empId, employeeName, safeAbilities, tagsById, evidenceTargetRefs);
    }

    private Map<Long, AbilityTag> loadTags(Collection<EmpAbility> abilities) {
        Set<Long> tagIds = new LinkedHashSet<>();
        for (EmpAbility ability : abilities) {
            if (ability.getTagId() != null) {
                tagIds.add(ability.getTagId());
            }
        }
        if (tagIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<AbilityTag> tags = abilityTagMapper.selectBatchIds(tagIds);
        Map<Long, AbilityTag> tagsById = new HashMap<>();
        if (tags != null) {
            for (AbilityTag tag : tags) {
                if (tag != null && tag.getId() != null) {
                    tagsById.put(tag.getId(), tag);
                }
            }
        }
        return tagsById;
    }

    private Set<Long> loadEvidenceTargetRefs(Collection<EmpAbility> abilities) {
        Set<Long> abilityIds = new LinkedHashSet<>();
        for (EmpAbility ability : abilities) {
            if (ability.getId() != null) {
                abilityIds.add(ability.getId());
            }
        }
        if (abilityIds.isEmpty()) {
            return Set.of();
        }

        List<ContestEvidenceItem> evidenceItems = evidenceItemMapper.selectList(
                Wrappers.<ContestEvidenceItem>query()
                        .select("target_ref_id")
                        .eq("target_type", "EMP_ABILITY")
                        .in("target_ref_id", abilityIds)
                        .eq("is_deleted", 0)
        );
        Set<Long> targetRefs = new LinkedHashSet<>();
        if (evidenceItems != null) {
            for (ContestEvidenceItem evidenceItem : evidenceItems) {
                if (evidenceItem != null && evidenceItem.getTargetRefId() != null) {
                    targetRefs.add(evidenceItem.getTargetRefId());
                }
            }
        }
        return targetRefs;
    }

    private record ScanContext(
            Long empId,
            String employeeName,
            List<EmpAbility> abilities,
            Map<Long, AbilityTag> tagsById,
            Set<Long> evidenceTargetRefs
    ) {
    }

    private String resolveAbilityName(EmpAbility ability, AbilityTag tag) {
        if (ability != null && ability.getAbilityName() != null && !ability.getAbilityName().isBlank()) {
            return ability.getAbilityName();
        }
        if (tag != null && tag.getTagName() != null && !tag.getTagName().isBlank()) {
            return tag.getTagName();
        }
        return "未命名能力";
    }

    @Override
    public String getImprovementSuggestion(QualityIssue issue) {
        return switch (issue.issueType()) {
            case ISSUE_ISOLATED -> "建议：\n1. 补充能力来源证明材料\n2. 通过AI测试或面试重新评估\n3. 从绩效数据中提取能力证据";
            case ISSUE_EXPIRED -> "建议：\n1. 安排能力重新评估\n2. 通过最新项目数据更新能力等级\n3. 使用AI视频面试进行评估";
            case ISSUE_INCONSISTENT -> "建议：\n1. 人工审核各来源数据\n2. 确认最可信的来源\n3. 统一能力等级";
            case ISSUE_LOW_CREDIBILITY -> "建议：\n1. 使用更高可信度的来源重新评估\n2. 补充绩效或项目数据\n3. 安排AI测试验证";
            case ISSUE_NO_EVIDENCE -> "建议：\n1. 上传能力证明材料到知识库\n2. 关联项目经历和成果\n3. 补充培训或认证记录";
            default -> "建议进行人工审核";
        };
    }
}
