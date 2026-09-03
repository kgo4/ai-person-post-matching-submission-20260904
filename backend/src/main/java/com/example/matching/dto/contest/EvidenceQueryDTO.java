package com.example.matching.dto.contest;

import lombok.Data;

/**
 * 证据查询DTO
 *
 * @author system
 */
@Data
public class EvidenceQueryDTO {

    /** 来源类型 */
    private String sourceType;

    /** 目标类型 */
    private String targetType;

    /** 证据状态 */
    private String evidenceStatus;

    /** 能力名称（模糊匹配） */
    private String abilityName;
}
