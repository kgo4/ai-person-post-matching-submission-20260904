package com.example.matching.dto.matching;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 匹配专用员工能力快照（M-12）
 * <p>
 * 匹配算法与评分层只消费该 DTO，不接触数据库 Entity。
 *
 * @param abilityId       能力记录ID（融合画像或 emp_ability）
 * @param tagId           能力标签ID
 * @param abilityName     能力标签名称
 * @param level           能力等级（1-5）
 * @param confidence      置信度（0-1）
 * @param sourceType      来源类型（如 PROFILE_FUSED、MANUAL）
 * @param sourceWeight    来源可信度权重（0-1）
 * @param evaluationDate  最近评估日期
 */
public record MatchingAbilitySnapshot(
        Long abilityId,
        Long tagId,
        String abilityName,
        Integer level,
        BigDecimal confidence,
        String sourceType,
        BigDecimal sourceWeight,
        LocalDate evaluationDate
) {
}
