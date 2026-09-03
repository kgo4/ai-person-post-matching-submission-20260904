package com.example.matching.vo.post;

import lombok.Data;

/**
 * 岗位能力巡检 - 岗位聚合项
 * <p>
 * 以岗位为单位展示能力规模与风险概况，用于"入库后 AI 幻觉巡检"。
 */
@Data
public class PostAbilityInspectionPostVO {

    /** 岗位ID */
    private Long postId;

    /** 岗位名称 */
    private String postName;

    /** 岗位编码 */
    private String postCode;

    /** 能力总数 */
    private Integer abilityCount;

    /** 有风险能力数（WARN/HIGH） */
    private Integer riskyCount;

    /** 高风险能力数 */
    private Integer highCount;

    /** AI 来源能力数 */
    private Integer aiSourceCount;
}
