package com.example.matching.service.evolution;

import java.util.List;

/**
 * 岗位演化信号服务接口
 * <p>
 * 负责将知识检索结果转换为岗位演化任务和变更项。
 * 每个信号必须携带统一 sourceRef，并经过 Harness 校验。
 *
 * @author system
 */
public interface PostEvolutionSignalService {

    /**
     * 演化信号
     */
    record EvolutionSignal(
            String signalType,
            String abilityName,
            Long abilityTagId,
            String changeType,
            String evidenceText,
            List<String> sourceRefs,
            Double confidenceScore,
            Double supportScore
    ) {}

    /**
     * 从行业白皮书生成演化信号
     *
     * @param industry   行业
     * @param documentId 文档ID
     * @return 演化信号列表
     */
    List<EvolutionSignal> generateSignalsFromWhitepaper(String industry, Long documentId);

    /**
     * 从云知识库生成演化信号
     *
     * @param businessDomain 业务领域
     * @param documentId     文档ID
     * @return 演化信号列表
     */
    List<EvolutionSignal> generateSignalsFromCloudKnowledge(String businessDomain, Long documentId);

    /**
     * 从招聘JD生成演化信号（辅助验证）
     *
     * @param postName   岗位名称
     * @param batchNo    导入批次号
     * @return 演化信号列表
     */
    List<EvolutionSignal> generateSignalsFromRecruitmentJd(String postName, String batchNo);

    /**
     * 将信号转换为演化任务
     *
     * @param postId    岗位ID
     * @param signals   信号列表
     * @param triggerType 触发类型
     * @param userId    操作人ID
     * @return 创建的任务ID
     */
    Long convertSignalsToEvolutionTask(Long postId, List<EvolutionSignal> signals,
                                        String triggerType, Long userId);
}
