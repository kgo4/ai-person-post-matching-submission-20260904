package com.example.matching.service.matching.impl;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.dto.matching.MatchDetailDTO;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.matching.MatchingBlackWhiteList;
import com.example.matching.entity.matching.MatchingRecord;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.service.matching.MatchingAlgorithmService;
import com.example.matching.service.matching.TagCanonicalResolver;
import com.example.matching.service.matching.algorithm.AbilityEvidenceFusionService;
import com.example.matching.service.matching.algorithm.HardConditionChecker;
import com.example.matching.service.matching.algorithm.MatchingReportAssembler;
import com.example.matching.service.matching.algorithm.SemanticMatchEngine;
import com.example.matching.service.system.SourceWeightConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 匹配算法服务实现（M-09）。
 * <p>
 * 汇聚语义匹配、能力证据融合、硬条件检查、报告组装等匹配算法能力，统一委托给算法子组件。
 */
@Slf4j
@Service
public class MatchingAlgorithmServiceImpl implements MatchingAlgorithmService {

    // ===== 委托组件 =====
    private final SemanticMatchEngine semanticMatchEngine;
    private final AbilityEvidenceFusionService fusionService;
    private final HardConditionChecker hardConditionChecker;
    private final MatchingReportAssembler reportAssembler;

    @Autowired
    public MatchingAlgorithmServiceImpl(SemanticMatchEngine semanticMatchEngine,
                                        AbilityEvidenceFusionService fusionService,
                                        HardConditionChecker hardConditionChecker,
                                        MatchingReportAssembler reportAssembler) {
        this.semanticMatchEngine = semanticMatchEngine;
        this.fusionService = fusionService;
        this.hardConditionChecker = hardConditionChecker;
        this.reportAssembler = reportAssembler;
    }

    /**
     * 测试/工具构造函数：使用最小依赖直接装配算法组件（不依赖 Spring 容器）。
     */
    public MatchingAlgorithmServiceImpl(TagCanonicalResolver tagCanonicalResolver,
                                        VectorEmbeddingService vectorEmbeddingService,
                                        TagQueryPort tagQueryPort,
                                        ObjectMapper objectMapper) {
        this(tagCanonicalResolver, vectorEmbeddingService, tagQueryPort, objectMapper, null);
    }

    /**
     * 测试/工具构造函数：使用最小依赖直接装配算法组件（不依赖 Spring 容器）。
     */
    public MatchingAlgorithmServiceImpl(TagCanonicalResolver tagCanonicalResolver,
                                        VectorEmbeddingService vectorEmbeddingService,
                                        TagQueryPort tagQueryPort,
                                        ObjectMapper objectMapper,
                                        RedisTemplate<String, Object> redisTemplate) {
        this.semanticMatchEngine = new SemanticMatchEngine(tagCanonicalResolver, vectorEmbeddingService, tagQueryPort, redisTemplate);
        this.fusionService = new AbilityEvidenceFusionService(
                new com.example.matching.service.system.SourceWeightResolver(
                        new SourceWeightConfigService() {
                            @Override
                            public java.math.BigDecimal getWeight(String sourceType) { return new java.math.BigDecimal("0.10"); }
                            @Override
                            public java.util.List<com.example.matching.entity.system.SourceWeightConfig> listAll() { return java.util.List.of(); }
                            @Override
                            public java.util.List<com.example.matching.entity.system.SourceWeightConfig> batchUpdate(java.util.List<com.example.matching.entity.system.SourceWeightConfig> configs) { return configs; }
                        }));
        this.hardConditionChecker = new HardConditionChecker(objectMapper);
        this.reportAssembler = new MatchingReportAssembler(objectMapper, this.fusionService, this.semanticMatchEngine);
    }

    // ===== 四级语义匹配核心逻辑（委托） =====

    @Override
    public List<MatchDetailDTO> performSemanticMatching(Map<Long, BigDecimal> fusedLevels,
                                                        List<MatchingAbilitySnapshot> empAbilities,
                                                        List<MatchingRequirementSnapshot> postRequirements) {
        return semanticMatchEngine.performSemanticMatching(fusedLevels, empAbilities, postRequirements);
    }

    @Override
    public List<MatchDetailDTO> performSemanticMatchingForPreview(Map<Long, BigDecimal> fusedLevels,
                                                                   List<MatchingAbilitySnapshot> empAbilities,
                                                                   List<MatchingRequirementSnapshot> postRequirements) {
        return semanticMatchEngine.performSemanticMatching(fusedLevels, empAbilities, postRequirements, false);
    }

    // ===== 必填能力检查（委托） =====

    @Override
    public boolean checkRequiredAbilitiesWithDetails(List<MatchDetailDTO> matchDetails) {
        return semanticMatchEngine.checkRequiredAbilitiesWithDetails(matchDetails);
    }

    // ===== 加权打分（委托） =====

    @Override
    public BigDecimal calculateAbilityCompatibilityScore(List<MatchDetailDTO> matchDetails,
                                                         List<MatchingRequirementSnapshot> postRequirements) {
        return semanticMatchEngine.calculateAbilityCompatibilityScore(matchDetails, postRequirements);
    }

    // ===== 能力证据融合（委托） =====

    @Override
    public Map<Long, BigDecimal> fuseAbilityLevel(List<MatchingAbilitySnapshot> empAbilities) {
        return fusionService.fuseAbilityLevel(empAbilities);
    }

    @Override
    public Map<Long, List<MatchingAlgorithmService.EvidenceDetail>> generateEvidenceDetail(List<MatchingAbilitySnapshot> empAbilities) {
        Map<Long, List<com.example.matching.service.matching.algorithm.EvidenceDetail>> raw = fusionService.generateEvidenceDetail(empAbilities);
        Map<Long, List<MatchingAlgorithmService.EvidenceDetail>> result = new HashMap<>();
        for (var entry : raw.entrySet()) {
            result.put(entry.getKey(), entry.getValue().stream()
                    .map(this::toInnerEvidence)
                    .toList());
        }
        return result;
    }

    // ===== 匹配状态判定 =====

    @Override
    public int determineMatchStatus(BigDecimal score) {
        double value = score.doubleValue();
        if (value >= 90) return 1;
        if (value >= 75) return 2;
        if (value >= 60) return 3;
        return 4;
    }

    // ===== L2匹配计算 =====

    @Override
    public MatchingRecord matchWithAbilities(Long empId, Long postId,
                                           List<MatchingAbilitySnapshot> abilities,
                                           List<MatchingRequirementSnapshot> requirements,
                                           List<MatchingBlackWhiteList> bwList,
                                           String batchNo,
                                           BigDecimal milvusVectorScore) {
        MatchingRecord record = new MatchingRecord();
        record.setEmpId(empId);
        record.setPostId(postId);
        record.setBatchNo(batchNo);

        if (bwList != null) {
            for (MatchingBlackWhiteList bw : bwList) {
                boolean empMatch = bw.getEmpId() != null && bw.getEmpId().equals(empId);
                boolean postMatch = bw.getPostId() != null && bw.getPostId().equals(postId);
                if (empMatch && postMatch) {
                    if (bw.getListType() != null && bw.getListType() == 2) {
                        record.setL2Score(BigDecimal.ZERO);
                        record.setPostModelScore(BigDecimal.ZERO);
                        record.setVectorScore(milvusVectorScore != null ? milvusVectorScore : BigDecimal.ZERO);
                        return record;
                    }
                    if (bw.getListType() != null && bw.getListType() == 1) {
                        record.setL2Score(new BigDecimal("100.00"));
                        record.setPostModelScore(new BigDecimal("100.00"));
                        record.setVectorScore(new BigDecimal("100.00"));
                        return record;
                    }
                }
            }
        }

        Map<Long, BigDecimal> fusedLevels = fusionService.fuseAbilityLevel(abilities);

        List<MatchDetailDTO> matchDetails = semanticMatchEngine.performSemanticMatching(fusedLevels, abilities, requirements);
        record.setMatchDetails(matchDetails);
        BigDecimal weightedScore = semanticMatchEngine.calculateAbilityCompatibilityScore(matchDetails, requirements);

        record.setL2Score(weightedScore);
        record.setPostModelScore(weightedScore);
        record.setVectorScore(milvusVectorScore != null ? milvusVectorScore : BigDecimal.ZERO);

        return record;
    }

    // ===== 报告生成（委托） =====

    @Override
    public String generateReport(MatchingRecord record, String empName, String postName,
                                 List<MatchingAbilitySnapshot> empAbilities,
                                 List<MatchingRequirementSnapshot> postRequirements,
                                 Map<Long, String> tagNameMap) {
        return reportAssembler.generateReport(record, empName, postName, empAbilities, postRequirements, tagNameMap);
    }

    @Override
    public String generateReport(MatchingRecord record, String empName, String postName,
                                 List<MatchingAbilitySnapshot> empAbilities,
                                 List<MatchingRequirementSnapshot> postRequirements,
                                 Map<Long, String> tagNameMap,
                                 List<MatchDetailDTO> matchDetails) {
        return reportAssembler.generateReport(record, empName, postName, empAbilities, postRequirements,
                tagNameMap, matchDetails);
    }

    // ===== 辅助方法 =====

    @Override
    public double calculateTimeFactor(LocalDate evaluationDate) {
        return fusionService.calculateTimeFactor(evaluationDate);
    }

    // ===== 硬性条件检查（委托） =====

    @Override
    public MatchingAlgorithmService.HardConditionResult checkHardConditions(MatchingEmployeeProfile emp,
                                                                            List<HardCondition> conditions) {
        return checkHardConditions(emp, conditions, null);
    }

    @Override
    public MatchingAlgorithmService.HardConditionResult checkHardConditions(MatchingEmployeeProfile emp,
                                                                            List<HardCondition> conditions,
                                                                            Map<String, Object> resumeBasicInfo) {
        EmpEmployee employee = toEmployeeValue(emp);
        com.example.matching.service.matching.algorithm.HardConditionResult delegate =
                hardConditionChecker.checkHardConditions(employee, conditions, resumeBasicInfo);
        MatchingAlgorithmService.HardConditionResult result = new MatchingAlgorithmService.HardConditionResult();
        result.setPassed(delegate.isPassed());
        result.setDetails(
                delegate.getDetails() != null ? delegate.getDetails().stream()
                        .map(d -> {
                            MatchingAlgorithmService.ConditionDetail cd = new MatchingAlgorithmService.ConditionDetail();
                            cd.setField(d.getField());
                            cd.setLabel(d.getLabel());
                            cd.setOperator(d.getOperator());
                            cd.setExpectedValue(d.getExpectedValue());
                            cd.setActualValue(d.getActualValue());
                            cd.setPassed(d.isPassed());
                            cd.setSource(d.getSource());
                            return cd;
                        }).toList()
                        : List.of());
        return result;
    }

    /**
     * 将匹配员工画像转换为硬条件检查所需的最小值对象（仅读取档案字段，无持久化行为）。
     */
    private static EmpEmployee toEmployeeValue(MatchingEmployeeProfile emp) {
        EmpEmployee employee = new EmpEmployee();
        if (emp != null) {
            employee.setId(emp.empId());
            employee.setRealName(emp.realName());
            employee.setLevel(emp.level());
            employee.setGender(emp.gender());
            employee.setExtendFields(emp.extendFields());
        }
        return employee;
    }

    private MatchingAlgorithmService.EvidenceDetail toInnerEvidence(com.example.matching.service.matching.algorithm.EvidenceDetail src) {
        MatchingAlgorithmService.EvidenceDetail dst = new MatchingAlgorithmService.EvidenceDetail();
        dst.setTagId(src.getTagId());
        dst.setMasteryLevel(src.getMasteryLevel());
        dst.setSource(src.getSource());
        dst.setCredibility(src.getCredibility());
        dst.setSourceWeight(src.getSourceWeight());
        dst.setTimeFactor(src.getTimeFactor());
        dst.setEvaluationDate(src.getEvaluationDate());
        return dst;
    }
}
