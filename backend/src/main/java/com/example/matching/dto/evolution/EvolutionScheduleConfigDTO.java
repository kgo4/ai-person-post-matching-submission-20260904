package com.example.matching.dto.evolution;

import lombok.Data;

/**
 * 演化定时配置DTO
 *
 * @author system
 */
@Data
public class EvolutionScheduleConfigDTO {

    /** 岗位ID */
    private Long postId;

    /** 是否启用 */
    private Integer enabled;

    /** Cron表达式 */
    private String cronExpression;

    /** 行业 */
    private String industry;

    /** 业务领域 */
    private String businessDomain;

    /** 是否包含行业白皮书 */
    private Integer includeWhitepaper;

    /** 是否包含云知识库 */
    private Integer includeCloudKnowledge;

    /** 是否包含市场JD */
    private Integer includeMarketJd;

}
