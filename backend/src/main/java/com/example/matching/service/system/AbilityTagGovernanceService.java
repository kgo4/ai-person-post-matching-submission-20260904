package com.example.matching.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.entity.system.AbilityTagUsageStat;

import java.util.List;

/**
 * 能力标签治理服务接口
 * <p>
 * 管理候选标签审核和标签使用统计。
 */
public interface AbilityTagGovernanceService extends IService<AbilityTagCandidate> {

    /**
     * 分页查询候选标签
     */
    IPage<AbilityTagCandidate> pageCandidates(IPage<AbilityTagCandidate> page, String status, String sourceType);

    /**
     * 批准候选标签（创建为正式标签）
     *
     * @param candidateId 候选标签ID
     * @param tagCategory 标签分类
     * @param reviewedBy  审核人ID
     * @return 创建的正式标签ID
     */
    Long approveCandidate(Long candidateId, String tagCategory, Long parentDomainId, Long reviewedBy);

    Long approveCandidate(Long candidateId, String tagCategory, Long parentDomainId, Long reviewedBy,
                          String editedCandidateName, String reviewComment);

    default Long approveCandidate(Long candidateId, String tagCategory, Long reviewedBy) {
        return approveCandidate(candidateId, tagCategory, null, reviewedBy);
    }

    /**
     * 拒绝候选标签
     */
    void rejectCandidate(Long candidateId, Long reviewedBy, String reason);

    /**
     * 将候选标签合并到已有标签
     */
    void mergeCandidateToExisting(Long candidateId, Long targetTagId, Long reviewedBy);

    /**
     * 计算标签使用统计
     */
    void computeUsageStats();

    /**
     * 获取标签使用统计列表
     */
    List<AbilityTagUsageStat> getUsageStats(int topN);

    /**
     * 添加候选标签（供其他服务调用）
     */
    void addCandidate(String candidateName, String tagCategory, String sourceType, Long sourceRefId, Long matchedTagId, Double similarityScore, String reasoning);
}
