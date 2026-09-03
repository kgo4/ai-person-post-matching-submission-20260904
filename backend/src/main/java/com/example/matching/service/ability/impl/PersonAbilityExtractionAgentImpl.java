package com.example.matching.service.ability.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.common.enums.AbilitySourceType;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.mapper.ability.PersonAbilityClaimMapper;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.service.ability.PersonAbilityExtractionAgent;
import com.example.matching.common.util.PersonAbilityClaimNormalizer;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.example.matching.dto.harness.AiHarnessClaimDTO;
import com.example.matching.dto.harness.AiHarnessDecisionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 人员能力提取Agent实现
 * <p>
 * 负责从各来源提取能力主张（PersonAbilityClaim）。
 * 来源包括：简历解析、AI测评、PMS、项目系统、学习成果等。
 * <p>
 * 注意：AI面试由独立的AIInterviewAgent负责，输出InterviewAbilityObservation。
 * 本Agent不处理AI面试来源。
 * <p>
 * 简历解析格式规范：JSON 读取统一委托 {@link PersonAbilityClaimNormalizer}（claims[] 为唯一
 * 规范格式，旧 abilities/tagName/level 兼容已收敛到该类）。活跃导入主路径为
 * {@link com.example.matching.service.employee.impl.ResumeAbilityImportService}，
 * 本类 extractFromResume 仅为画像构建链路提供的薄适配层。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonAbilityExtractionAgentImpl implements PersonAbilityExtractionAgent {

    private final EmpResumeParseMapper resumeParseMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final PersonAbilityClaimMapper claimMapper;
    private final AiTrustHarnessService aiTrustHarnessService;
    private final PersonAbilityClaimNormalizer claimNormalizer;

    // ==================== 来源类型常量 ====================
    private static final String SOURCE_RESUME_PARSE = AbilitySourceType.RESUME_PARSE;
    private static final String SOURCE_AI_TEST = AbilitySourceType.AI_TEST;
    private static final String SOURCE_AI_PROJECT = AbilitySourceType.AI_PROJECT;
    private static final String SOURCE_LEARNING_PROJECT = AbilitySourceType.LEARNING_PROJECT;
    private static final String SOURCE_MANUAL = AbilitySourceType.MANUAL;

    // ==================== 来源权重配置 ====================
    private static final BigDecimal WEIGHT_RESUME_PARSE = BigDecimal.valueOf(0.15);
    private static final BigDecimal WEIGHT_AI_TEST = BigDecimal.valueOf(0.20);
    private static final BigDecimal WEIGHT_AI_PROJECT = BigDecimal.valueOf(0.30);
    private static final BigDecimal WEIGHT_LEARNING_PROJECT = BigDecimal.valueOf(0.10);
    private static final BigDecimal WEIGHT_MANUAL = BigDecimal.valueOf(0.10);

    @Override
    @Transactional
    public List<PersonAbilityClaim> extractFromResume(Long empId, Long parseId) {
        log.info("从简历解析提取能力主张，empId={}, parseId={}", empId, parseId);

        List<PersonAbilityClaim> claims = new ArrayList<>();

        // 加载简历解析记录
        EmpResumeParse resumeParse = resumeParseMapper.selectById(parseId);
        if (resumeParse == null || resumeParse.getAiAnalysisResult() == null) {
            log.warn("简历解析记录不存在或无AI分析结果，empId={}, parseId={}", empId, parseId);
            return claims;
        }

        try {
            // 统一经 PersonAbilityClaimNormalizer 解析（规范 claims[] + 旧格式兼容）
            com.example.matching.agent.dto.person.PersonAbilityExtractionResult extractionResult =
                    claimNormalizer.normalize(resumeParse.getAiAnalysisResult());
            List<com.example.matching.agent.dto.person.PersonAbilityClaim> dtoClaims =
                    extractionResult == null ? List.of() : extractionResult.getClaims();
            if (dtoClaims.isEmpty()) {
                return claims;
            }

            String resumeContent = resumeParse.getParsedContent();
            String evidenceSnippet = resumeContent != null && resumeContent.length() > 500
                    ? resumeContent.substring(0, 500) + "..." : resumeContent;

            for (com.example.matching.agent.dto.person.PersonAbilityClaim dtoClaim : dtoClaims) {
                // 构建能力主张
                PersonAbilityClaim claim = new PersonAbilityClaim();
                claim.setEmpId(empId);
                claim.setAbilityName(dtoClaim.getAbilityName());
                claim.setNormalizedAbilityName(dtoClaim.getNormalizedAbilityName());
                claim.setClaimedLevel(dtoClaim.getMasteryLevel());
                claim.setSourceType(SOURCE_RESUME_PARSE);
                claim.setSourceRefId(parseId);
                claim.setSourceWeight(WEIGHT_RESUME_PARSE);
                claim.setEvidenceText(hasText(dtoClaim.getEvidenceText()) ? dtoClaim.getEvidenceText() : evidenceSnippet);
                claim.setConfidenceScore(BigDecimal.valueOf(60)); // 简历解析的基础置信度
                claim.setFreshnessScore(calculateFreshness(resumeParse.getCreatedTime()));
                claim.setAuthorityScore(BigDecimal.valueOf(50)); // 简历的权威度中等
                claim.setStatus("ACTIVE");

                claims.add(claim);
            }

            log.info("从简历解析提取能力主张完成，empId={}, claimCount={}", empId, claims.size());
        } catch (Exception e) {
            log.error("从简历解析提取能力主张失败，empId={}, error={}", empId, e.getMessage(), e);
        }

        return claims;
    }

    @Override
    @Transactional
    public List<PersonAbilityClaim> extractFromAiTest(Long empId, Long testId) {
        log.info("从AI测评提取能力主张，empId={}, testId={}", empId, testId);

        List<PersonAbilityClaim> claims = new ArrayList<>();

        // 从已有的EmpAbility表中提取AI测评来源的能力
        List<EmpAbility> existingAbilities = empAbilityMapper.selectList(
                Wrappers.<EmpAbility>lambdaQuery()
                        .eq(EmpAbility::getEmpId, empId)
                        .eq(EmpAbility::getEvaluationSource, SOURCE_AI_TEST)
        );

        for (EmpAbility ability : existingAbilities) {
            PersonAbilityClaim claim = new PersonAbilityClaim();
            claim.setEmpId(empId);
            claim.setTagId(ability.getTagId());
            claim.setClaimedLevel(ability.getMasteryLevel());
            claim.setSourceType(SOURCE_AI_TEST);
            claim.setSourceRefId(testId);
            claim.setSourceWeight(WEIGHT_AI_TEST);
            claim.setEvidenceText(ability.getRemark());
            claim.setConfidenceScore(BigDecimal.valueOf(70)); // AI测评的置信度较高
            claim.setFreshnessScore(calculateFreshnessFromLocalDate(ability.getEvaluationDate()));
            claim.setAuthorityScore(BigDecimal.valueOf(70)); // AI测评的权威度较高
            claim.setStatus("ACTIVE");

            claims.add(claim);
        }

        log.info("从AI测评提取能力主张完成，empId={}, claimCount={}", empId, claims.size());
        return claims;
    }

    @Override
    @Transactional
    public List<PersonAbilityClaim> extractFromPms(Long empId) {
        log.info("从PMS系统提取能力主张，empId={}", empId);

        List<PersonAbilityClaim> claims = new ArrayList<>();

        // 从已有的EmpAbility表中提取PMS来源的能力
        List<EmpAbility> existingAbilities = empAbilityMapper.selectList(
                Wrappers.<EmpAbility>lambdaQuery()
                        .eq(EmpAbility::getEmpId, empId)
                        .eq(EmpAbility::getEvaluationSource, SOURCE_AI_PROJECT)
        );

        for (EmpAbility ability : existingAbilities) {
            PersonAbilityClaim claim = new PersonAbilityClaim();
            claim.setEmpId(empId);
            claim.setTagId(ability.getTagId());
            claim.setClaimedLevel(ability.getMasteryLevel());
            claim.setSourceType(SOURCE_AI_PROJECT);
            claim.setSourceWeight(WEIGHT_AI_PROJECT);
            claim.setEvidenceText(ability.getRemark());
            claim.setConfidenceScore(BigDecimal.valueOf(85)); // PMS的置信度很高
            claim.setFreshnessScore(calculateFreshnessFromLocalDate(ability.getEvaluationDate()));
            claim.setAuthorityScore(BigDecimal.valueOf(90)); // PMS的权威度很高
            claim.setStatus("ACTIVE");

            claims.add(claim);
        }

        log.info("从PMS系统提取能力主张完成，empId={}, claimCount={}", empId, claims.size());
        return claims;
    }

    @Override
    @Transactional
    public List<PersonAbilityClaim> extractFromProject(Long empId) {
        log.info("从项目经历提取能力主张，empId={}", empId);

        List<PersonAbilityClaim> claims = new ArrayList<>();

        // 从已有的EmpAbility表中提取项目来源的能力
        List<EmpAbility> existingAbilities = empAbilityMapper.selectList(
                Wrappers.<EmpAbility>lambdaQuery()
                        .eq(EmpAbility::getEmpId, empId)
                        .eq(EmpAbility::getEvaluationSource, SOURCE_AI_PROJECT)
        );

        for (EmpAbility ability : existingAbilities) {
            PersonAbilityClaim claim = new PersonAbilityClaim();
            claim.setEmpId(empId);
            claim.setTagId(ability.getTagId());
            claim.setClaimedLevel(ability.getMasteryLevel());
            claim.setSourceType(SOURCE_AI_PROJECT);
            claim.setSourceWeight(WEIGHT_AI_PROJECT);
            claim.setEvidenceText(ability.getRemark());
            claim.setConfidenceScore(BigDecimal.valueOf(80)); // 项目经历的置信度较高
            claim.setFreshnessScore(calculateFreshnessFromLocalDate(ability.getEvaluationDate()));
            claim.setAuthorityScore(BigDecimal.valueOf(85)); // 项目经历的权威度较高
            claim.setStatus("ACTIVE");

            claims.add(claim);
        }

        log.info("从项目经历提取能力主张完成，empId={}, claimCount={}", empId, claims.size());
        return claims;
    }

    @Override
    @Transactional
    public List<PersonAbilityClaim> extractFromLearning(Long empId) {
        log.info("从学习成果提取能力主张，empId={}", empId);

        List<PersonAbilityClaim> claims = new ArrayList<>();

        // 从已有的EmpAbility表中提取学习来源的能力
        List<EmpAbility> existingAbilities = empAbilityMapper.selectList(
                Wrappers.<EmpAbility>lambdaQuery()
                        .eq(EmpAbility::getEmpId, empId)
                        .eq(EmpAbility::getEvaluationSource, SOURCE_LEARNING_PROJECT)
        );

        for (EmpAbility ability : existingAbilities) {
            PersonAbilityClaim claim = new PersonAbilityClaim();
            claim.setEmpId(empId);
            claim.setTagId(ability.getTagId());
            claim.setClaimedLevel(ability.getMasteryLevel());
            claim.setSourceType(SOURCE_LEARNING_PROJECT);
            claim.setSourceWeight(WEIGHT_LEARNING_PROJECT);
            claim.setEvidenceText(ability.getRemark());
            claim.setConfidenceScore(BigDecimal.valueOf(65)); // 学习成果的置信度中等
            claim.setFreshnessScore(calculateFreshnessFromLocalDate(ability.getEvaluationDate()));
            claim.setAuthorityScore(BigDecimal.valueOf(60)); // 学习成果的权威度中等
            claim.setStatus("ACTIVE");

            claims.add(claim);
        }

        log.info("从学习成果提取能力主张完成，empId={}, claimCount={}", empId, claims.size());
        return claims;
    }

    @Override
    @Transactional
    public List<PersonAbilityClaim> extractFromManual(Long empId) {
        log.info("从人工录入提取能力主张，empId={}", empId);

        List<PersonAbilityClaim> claims = new ArrayList<>();

        // 从已有的EmpAbility表中提取人工录入来源的能力
        List<EmpAbility> existingAbilities = empAbilityMapper.selectList(
                Wrappers.<EmpAbility>lambdaQuery()
                        .eq(EmpAbility::getEmpId, empId)
                        .eq(EmpAbility::getEvaluationSource, SOURCE_MANUAL)
        );

        for (EmpAbility ability : existingAbilities) {
            PersonAbilityClaim claim = new PersonAbilityClaim();
            claim.setEmpId(empId);
            claim.setTagId(ability.getTagId());
            claim.setClaimedLevel(ability.getMasteryLevel());
            claim.setSourceType(SOURCE_MANUAL);
            claim.setSourceWeight(WEIGHT_MANUAL);
            claim.setEvidenceText(ability.getRemark());
            claim.setConfidenceScore(BigDecimal.valueOf(75)); // 人工录入的置信度较高
            claim.setFreshnessScore(calculateFreshnessFromLocalDate(ability.getEvaluationDate()));
            claim.setAuthorityScore(BigDecimal.valueOf(80)); // 人工录入的权威度较高
            claim.setStatus("ACTIVE");

            claims.add(claim);
        }

        log.info("从人工录入提取能力主张完成，empId={}, claimCount={}", empId, claims.size());
        return claims;
    }

    @Override
    @Transactional
    public List<PersonAbilityClaim> extractAll(Long empId) {
        log.info("提取所有来源的能力主张，empId={}", empId);

        List<PersonAbilityClaim> allClaims = new ArrayList<>();

        // 从各来源提取能力主张
        allClaims.addAll(extractFromPms(empId));
        allClaims.addAll(extractFromAiTest(empId, null));
        allClaims.addAll(extractFromLearning(empId));
        allClaims.addAll(extractFromManual(empId));

        // 注意：简历解析需要parseId，这里从最新的解析记录中提取
        List<EmpResumeParse> resumeParses = resumeParseMapper.selectList(
                Wrappers.<EmpResumeParse>lambdaQuery()
                        .eq(EmpResumeParse::getEmpId, empId)
                        .eq(EmpResumeParse::getStatus, 2)
                        .orderByDesc(EmpResumeParse::getCreatedTime)
        );
        if (!resumeParses.isEmpty()) {
            allClaims.addAll(extractFromResume(empId, resumeParses.get(0).getId()));
        }

        log.info("提取所有来源的能力主张完成，empId={}, totalClaimCount={}", empId, allClaims.size());
        return allClaims;
    }

    // ==================== 内部辅助方法 ====================

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 计算时效性评分（基于创建时间）
     */
    private BigDecimal calculateFreshness(java.time.LocalDateTime createdTime) {
        if (createdTime == null) {
            return BigDecimal.valueOf(50);
        }

        long daysSinceCreated = java.time.Duration.between(createdTime, java.time.LocalDateTime.now()).toDays();
        if (daysSinceCreated <= 30) {
            return BigDecimal.valueOf(100);
        } else if (daysSinceCreated <= 90) {
            return BigDecimal.valueOf(80);
        } else if (daysSinceCreated <= 180) {
            return BigDecimal.valueOf(60);
        } else if (daysSinceCreated <= 365) {
            return BigDecimal.valueOf(40);
        } else {
            return BigDecimal.valueOf(20);
        }
    }

    /**
     * 计算时效性评分（基于LocalDate）
     */
    private BigDecimal calculateFreshnessFromLocalDate(java.time.LocalDate evaluationDate) {
        if (evaluationDate == null) {
            return BigDecimal.valueOf(50);
        }

        long daysSinceEvaluation = java.time.LocalDate.now().toEpochDay() - evaluationDate.toEpochDay();
        if (daysSinceEvaluation <= 30) {
            return BigDecimal.valueOf(100);
        } else if (daysSinceEvaluation <= 90) {
            return BigDecimal.valueOf(80);
        } else if (daysSinceEvaluation <= 180) {
            return BigDecimal.valueOf(60);
        } else if (daysSinceEvaluation <= 365) {
            return BigDecimal.valueOf(40);
        } else {
            return BigDecimal.valueOf(20);
        }
    }
}
