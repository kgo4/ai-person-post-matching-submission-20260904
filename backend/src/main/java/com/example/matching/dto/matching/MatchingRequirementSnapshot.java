package com.example.matching.dto.matching;

import java.math.BigDecimal;

/**
 * 匹配专用岗位要求快照（M-12）
 * <p>
 * 匹配算法与评分层只消费该 DTO，不接触数据库 Entity。
 *
 * @param tagId            能力标签ID
 * @param abilityName      能力标签名称
 * @param minRequiredLevel 最低要求等级（1-5）
 * @param weight           权重（0-100）
 * @param isRequired       是否必需（0-否 1-是）
 * @param isCore           是否核心（0-否 1-是）
 * @param modelVersion     能力模型版本号（可空）
 */
public record MatchingRequirementSnapshot(
        Long tagId,
        String abilityName,
        Integer minRequiredLevel,
        BigDecimal weight,
        Integer isRequired,
        Integer isCore,
        String modelVersion
) {
}
