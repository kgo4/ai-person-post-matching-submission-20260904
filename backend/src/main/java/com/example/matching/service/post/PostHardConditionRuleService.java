package com.example.matching.service.post;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition;
import com.example.matching.dto.post.PostHardConditionRuleDTO;
import com.example.matching.entity.post.PostHardConditionRule;

import java.util.List;

/**
 * 岗位硬性条件规则服务。
 */
public interface PostHardConditionRuleService extends IService<PostHardConditionRule> {

    List<PostHardConditionRule> listByPostId(Long postId);

    List<PostHardConditionRule> listEnabledByPostId(Long postId);

    void saveRule(PostHardConditionRuleDTO dto);

    void batchConfig(Long postId, List<PostHardConditionRuleDTO> list);

    List<HardCondition> toHardConditions(Long postId);
}
