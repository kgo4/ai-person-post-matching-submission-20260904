package com.example.matching.service.ability;

import java.util.List;

/**
 * 能力多源交叉验证服务
 * <p>
 * 当新来源的能力数据入库时，通过RAG检索同一员工+同一能力的历史证据，
 * 对比新旧数据的一致性，实现多源交叉验证。
 *
 * @author system
 */
public interface AbilityCrossValidationService {

    /**
     * 交叉验证结果
     */
    record ValidationResult(
            /** 一致性分数 0-100 */
            int consistencyScore,
            /** 验证状态：CONSISTENT/INCONSISTENT/NO_HISTORY/ERROR */
            String status,
            /** 历史证据数量 */
            int historyCount,
            /** 验证详情描述 */
            String detail,
            /** 建议操作：ACCEPT/REVIEW/REJECT */
            String recommendation
    ) {}

    /**
     * 验证员工能力数据的一致性
     *
     * @param empId           员工ID
     * @param tagId           能力标签ID
     * @param newLevel        新评估等级
     * @param newSource       新评估来源
     * @param excludeSourceId 需要排除的来源记录ID（避免自己和自己比较）
     * @return 验证结果
     */
    ValidationResult validateAbility(Long empId, Long tagId, Integer newLevel,
                                      String newSource, Long excludeSourceId);

    /**
     * 批量验证员工能力数据
     *
     * @param empId 员工ID
     * @return 各能力标签的验证结果
     */
    List<ValidationResult> validateAllAbilities(Long empId);

    /**
     * 获取能力来源可信度调整建议
     * <p>
     * 基于交叉验证结果，返回建议的来源权重调整值
     *
     * @param empId     员工ID
     * @param tagId     能力标签ID
     * @param source    评估来源
     * @return 建议的权重调整值（-0.2 ~ +0.2）
     */
    double getSuggestedWeightAdjustment(Long empId, Long tagId, String source);
}
