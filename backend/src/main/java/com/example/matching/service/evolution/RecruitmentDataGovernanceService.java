package com.example.matching.service.evolution;

import java.util.List;

/**
 * 招聘数据治理服务接口
 * <p>
 * 负责处理招聘JD的时滞、噪声、重复等数据质量问题。
 * 招聘数据作为辅助验证源，不作为唯一主依据。
 *
 * @author system
 */
public interface RecruitmentDataGovernanceService {

    /**
     * 治理结果
     */
    record GovernanceResult(
            int totalProcessed,
            int duplicatesRemoved,
            int noiseFiltered,
            int qualityPassed,
            List<String> warnings
    ) {}

    /**
     * 时效性评分结果
     */
    record FreshnessScore(
            Long jdId,
            double score,
            int daysSincePublished,
            String freshnessLevel
    ) {}

    /**
     * 去重结果
     */
    record DuplicateResult(
            Long jdId,
            boolean isDuplicate,
            Long canonicalJdId,
            String duplicateGroupKey
    ) {}

    /**
     * 治理导入批次的招聘数据
     *
     * @param batchNo 导入批次号
     * @return 治理结果
     */
    GovernanceResult governBatch(String batchNo);

    /**
     * 计算时效性评分
     *
     * @param jdId 招聘JD ID
     * @return 时效性评分
     */
    FreshnessScore calculateFreshnessScore(Long jdId);

    /**
     * 批量计算时效性评分
     *
     * @param jdIds 招聘JD ID列表
     * @return 时效性评分列表
     */
    List<FreshnessScore> batchCalculateFreshnessScore(List<Long> jdIds);

    /**
     * 检测重复
     *
     * @param jdId 招聘JD ID
     * @return 去重结果
     */
    DuplicateResult detectDuplicate(Long jdId);

    /**
     * 批量检测重复
     *
     * @param jdIds 招聘JD ID列表
     * @return 去重结果列表
     */
    List<DuplicateResult> batchDetectDuplicate(List<Long> jdIds);

    /**
     * 计算来源多样性
     *
     * @param postName 岗位名称
     * @return 来源多样性评分 0-100
     */
    int calculateSourceDiversity(String postName);

    /**
     * 计算公司多样性
     *
     * @param postName 岗位名称
     * @return 公司多样性评分 0-100
     */
    int calculateCompanyDiversity(String postName);
}
