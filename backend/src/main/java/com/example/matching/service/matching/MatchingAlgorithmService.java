package com.example.matching.service.matching;

import com.example.matching.dto.matching.MatchDetailDTO;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.matching.MatchingBlackWhiteList;
import com.example.matching.entity.matching.MatchingRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 匹配算法服务（M-09 接口）。
 * <p>
 * 汇聚语义匹配、能力证据融合、硬条件检查、报告组装等匹配算法能力。
 * 事务边界由实现类控制，接口不暴露 Mapper / 持久化细节。
 * <p>
 * M-12：算法层只消费匹配专用 DTO（{@link MatchingAbilitySnapshot} / {@link MatchingRequirementSnapshot} /
 * {@link MatchingEmployeeProfile}），不接触数据库 Entity。
 */
public interface MatchingAlgorithmService {

    /**
     * 四级语义匹配核心逻辑（EXACT -> CANONICAL -> CONFIRMED_SIMILAR -> SEMANTIC_FALLBACK）
     */
    List<MatchDetailDTO> performSemanticMatching(Map<Long, BigDecimal> fusedLevels,
                                                 List<MatchingAbilitySnapshot> empAbilities,
                                                 List<MatchingRequirementSnapshot> postRequirements);

    /**
     * 候选预览专用的语义匹配。
     * <p>
     * 预览只能使用已持久化的标签向量，避免为无标签名称在 HTTP 请求内触发外部 embedding。
     * 正式匹配仍使用 {@link #performSemanticMatching(Map, List, List)} 的完整降级能力。
     */
    default List<MatchDetailDTO> performSemanticMatchingForPreview(Map<Long, BigDecimal> fusedLevels,
                                                                    List<MatchingAbilitySnapshot> empAbilities,
                                                                    List<MatchingRequirementSnapshot> postRequirements) {
        return performSemanticMatching(fusedLevels, empAbilities, postRequirements);
    }

    /**
     * 必填能力检查
     */
    boolean checkRequiredAbilitiesWithDetails(List<MatchDetailDTO> matchDetails);

    /**
     * 加权打分
     */
    BigDecimal calculateAbilityCompatibilityScore(List<MatchDetailDTO> matchDetails,
                                                  List<MatchingRequirementSnapshot> postRequirements);

    /**
     * 能力证据融合
     */
    Map<Long, BigDecimal> fuseAbilityLevel(List<MatchingAbilitySnapshot> empAbilities);

    /**
     * 生成能力证据详情
     */
    Map<Long, List<EvidenceDetail>> generateEvidenceDetail(List<MatchingAbilitySnapshot> empAbilities);

    /**
     * 匹配状态判定
     */
    int determineMatchStatus(BigDecimal score);

    /**
     * L2 匹配计算
     */
    MatchingRecord matchWithAbilities(Long empId, Long postId,
                                    List<MatchingAbilitySnapshot> abilities,
                                    List<MatchingRequirementSnapshot> requirements,
                                    List<MatchingBlackWhiteList> bwList,
                                    String batchNo,
                                    BigDecimal milvusVectorScore);

    /**
     * 报告生成
     */
    String generateReport(MatchingRecord record, String empName, String postName,
                          List<MatchingAbilitySnapshot> empAbilities,
                          List<MatchingRequirementSnapshot> postRequirements,
                          Map<Long, String> tagNameMap);

    /**
     * 生成报告时复用 L2 已计算的能力匹配明细，避免同一人岗再次执行语义匹配。
     * 旧实现默认回退到原报告路径，兼容非执行场景和历史调用方。
     */
    default String generateReport(MatchingRecord record, String empName, String postName,
                                  List<MatchingAbilitySnapshot> empAbilities,
                                  List<MatchingRequirementSnapshot> postRequirements,
                                  Map<Long, String> tagNameMap,
                                  List<MatchDetailDTO> matchDetails) {
        return generateReport(record, empName, postName, empAbilities, postRequirements, tagNameMap);
    }

    /**
     * 时间衰减因子
     */
    double calculateTimeFactor(LocalDate evaluationDate);

    /**
     * 硬性条件检查
     */
    HardConditionResult checkHardConditions(MatchingEmployeeProfile emp, List<HardCondition> conditions);

    /**
     * 硬性条件检查（带简历信息）
     */
    HardConditionResult checkHardConditions(MatchingEmployeeProfile emp, List<HardCondition> conditions,
                                            Map<String, Object> resumeBasicInfo);

    // ===== 内部数据类（保持向后兼容） =====

    @lombok.Data
    class HardConditionResult {
        private boolean passed;
        private List<ConditionDetail> details;
    }

    @lombok.Data
    class ConditionDetail {
        private String field;
        private String label;
        private String operator;
        private String expectedValue;
        private String actualValue;
        private boolean passed;
        private String source;
    }

    @lombok.Data
    class EvidenceDetail {
        private Long tagId;
        private Integer masteryLevel;
        private String source;
        private double credibility;
        private double sourceWeight;
        private double timeFactor;
        private LocalDate evaluationDate;
    }
}
