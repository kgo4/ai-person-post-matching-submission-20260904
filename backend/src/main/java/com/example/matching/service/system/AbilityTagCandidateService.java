package com.example.matching.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.entity.system.AbilityTagCandidate;

import java.util.List;

/**
 * 候选标签服务接口
 * <p>
 * 管理AI发现的新能力标签，支持审核、合并、升级为正式标签。
 */
public interface AbilityTagCandidateService extends IService<AbilityTagCandidate> {

    /**
     * 分页查询候选标签
     *
     * @param page       分页参数
     * @param status     状态筛选（可选）
     * @param sourceType 来源筛选（可选）
     * @param keyword    关键词搜索（可选）
     * @return 分页结果
     */
    IPage<AbilityTagCandidate> pageCandidates(IPage<AbilityTagCandidate> page,
                                                String status,
                                                String sourceType,
                                                String keyword);

    /**
     * 添加候选标签（AI发现新能力时调用）
     * <p>
     * 如果候选名称已存在且状态为PENDING，增加出现次数。
     * 如果不存在，创建新的候选标签。
     *
     * @param candidate 候选标签
     * @return 候选标签ID
     */
    Long addCandidate(AbilityTagCandidate candidate);

    /**
     * 批量添加候选标签
     *
     * @param candidates 候选标签列表
     */
    void addCandidates(List<AbilityTagCandidate> candidates);

    /**
     * 审核通过（升级为正式标签）
     *
     * @param candidateId 候选标签ID
     * @param reviewerId  审核人ID
     * @param comment     审核意见
     * @return 新创建的正式标签ID
     */
    Long approve(Long candidateId, Long reviewerId, String comment);

    Long approve(Long candidateId, Long parentDomainId, Long reviewerId, String comment);

    /**
     * 审核拒绝
     *
     * @param candidateId 候选标签ID
     * @param reviewerId  审核人ID
     * @param comment     审核意见
     */
    void reject(Long candidateId, Long reviewerId, String comment);

    /**
     * 合并到已有正式标签
     *
     * @param candidateId 候选标签ID
     * @param targetTagId 目标正式标签ID
     * @param reviewerId  审核人ID
     * @param comment     审核意见
     */
    void merge(Long candidateId, Long targetTagId, Long reviewerId, String comment);

    /**
     * 获取高频候选标签（出现次数 >= threshold）
     *
     * @param threshold 最低出现次数
     * @return 候选标签列表
     */
    List<AbilityTagCandidate> getHighFrequencyCandidates(int threshold);

    /**
     * 统计各状态的候选标签数量
     *
     * @return status -> count
     */
    java.util.Map<String, Long> countByStatus();
}
