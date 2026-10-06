package com.example.matching.dto.assessment;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合 Harness 逐能力审核结果 DTO
 *
 * @author system
 */
@Data
public class HarnessBatchItemResultDTO {

    /** 能力聚合组ID */
    private Long claimGroupId;

    /** 决策：PASS/REVIEW/BLOCK */
    private String decision;

    /** 能力是否得到支持 */
    private Boolean abilitySupported;

    /** 支持的等级上限：1-5 */
    private Integer supportedLevelCeiling;

    /** 风险等级 */
    private String riskLevel;

    /** 原因码列表 */
    private List<String> reasonCodes = new ArrayList<>();

    /** 证据引用列表 */
    private List<String> evidenceRefs = new ArrayList<>();
}
