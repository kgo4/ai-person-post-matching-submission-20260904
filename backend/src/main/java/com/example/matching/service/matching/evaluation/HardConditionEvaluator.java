package com.example.matching.service.matching.evaluation;

import com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.service.matching.MatchingAlgorithmService;
import com.example.matching.service.post.PostHardConditionRuleService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 统一硬条件评估器
 * <p>
 * 为所有入口点（推荐预览、正式匹配）提供一致的硬条件检查逻辑。
 * 数据查找顺序：
 * 1. 员工静态字段和 extendFields
 * 2. 最近一次成功简历解析 basicInfo
 * 3. 缺失值
 * <p>
 * 结果状态：
 * - PASS：所有配置的硬条件通过
 * - RISK：条件无法确认（数据缺失或模糊）
 * - FAIL：条件已知不通过
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HardConditionEvaluator {

    private final MatchingAlgorithmService matchingAlgorithmService;
    private final PostHardConditionRuleService postHardConditionRuleService;
    private final TalentQueryPort talentQueryPort;
    private final ObjectMapper objectMapper;

    /**
     * 评估员工对指定岗位的硬条件
     *
     * @param employee  员工实体
     * @param postId    岗位ID
     * @return 硬条件评估结果
     */
    public HardConditionEvalResult evaluate(EmpEmployee employee, Long postId) {
        List<HardCondition> conditions = postHardConditionRuleService.toHardConditions(postId);
        return evaluate(employee, conditions);
    }

    /**
     * 评估员工对指定硬条件列表
     *
     * @param employee   员工实体
     * @param conditions 硬条件列表
     * @return 硬条件评估结果
     */
    public HardConditionEvalResult evaluate(EmpEmployee employee, List<HardCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return HardConditionEvalResult.pass();
        }

        // 加载简历解析数据作为回退
        Map<String, Object> resumeBasicInfo = loadResumeBasicInfo(employee.getId());

        MatchingAlgorithmService.HardConditionResult hcResult =
                matchingAlgorithmService.checkHardConditions(
                        com.example.matching.service.matching.MatchingSnapshotAssembler.toEmployeeProfile(employee),
                        conditions, resumeBasicInfo);

        if (hcResult.isPassed()) {
            return HardConditionEvalResult.pass();
        }

        // 检查是否有数据缺失导致的不确定（RISK vs FAIL）
        boolean hasMissingData = hcResult.getDetails().stream()
                .anyMatch(d -> "未填写".equals(d.getSource()) && !d.isPassed());

        if (hasMissingData) {
            return HardConditionEvalResult.risk(hcResult);
        }

        return HardConditionEvalResult.fail(hcResult);
    }

    /**
     * 将评估结果转换为0-100分
     */
    public BigDecimal toScore(HardConditionEvalResult result) {
        return switch (result.getStatus()) {
            case PASS -> new BigDecimal("100");
            case RISK -> new BigDecimal("60");
            case FAIL -> BigDecimal.ZERO;
        };
    }

    private Map<String, Object> loadResumeBasicInfo(Long empId) {
        try {
            TalentQueryPort.ResumeParseDetailDTO resume = talentQueryPort.findLatestCompletedResumeParse(empId);
            if (resume != null && resume.aiAnalysisResult() != null) {
                Map<String, Object> analysis = objectMapper.readValue(
                        resume.aiAnalysisResult(),
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
     * 硬条件评估结果
     */
    @lombok.Data
    public static class HardConditionEvalResult {
        private HardConditionStatus status;
        private BigDecimal score;
        private MatchingAlgorithmService.HardConditionResult detail;

        public static HardConditionEvalResult pass() {
            HardConditionEvalResult r = new HardConditionEvalResult();
            r.setStatus(HardConditionStatus.PASS);
            r.setScore(new BigDecimal("100"));
            return r;
        }

        public static HardConditionEvalResult risk(MatchingAlgorithmService.HardConditionResult detail) {
            HardConditionEvalResult r = new HardConditionEvalResult();
            r.setStatus(HardConditionStatus.RISK);
            r.setScore(new BigDecimal("60"));
            r.setDetail(detail);
            return r;
        }

        public static HardConditionEvalResult fail(MatchingAlgorithmService.HardConditionResult detail) {
            HardConditionEvalResult r = new HardConditionEvalResult();
            r.setStatus(HardConditionStatus.FAIL);
            r.setScore(BigDecimal.ZERO);
            r.setDetail(detail);
            return r;
        }
    }

    public enum HardConditionStatus {
        PASS, RISK, FAIL
    }
}
