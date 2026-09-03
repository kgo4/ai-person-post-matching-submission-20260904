package com.example.matching.service.post;

import com.example.matching.agent.dto.post.PostAbilityClaim;

import java.util.List;

public interface PostAbilityGroundingRecordService {
    void append(List<PostAbilityClaim> claims, String status);

    /**
     * 岗位能力被人工修改/删除后，将对应能力的待处理提取台账（SUBMITTED/DEFERRED）标记为 RESOLVED（人工已处理），
     * 避免台账长期停留在"待处理"观感。
     */
    void resolveForModel(Long postId, String abilityName);
}
