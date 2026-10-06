package com.example.matching.application.post;

import com.example.matching.dto.post.PostHardConditionRuleDTO;
import com.example.matching.dto.post.api.HardConditionRuleResponse;
import com.example.matching.entity.post.PostHardConditionRule;
import com.example.matching.service.post.PostHardConditionRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HardConditionRuleApiFacade {

    private final PostHardConditionRuleService postHardConditionRuleService;

    public List<HardConditionRuleResponse> listByPostId(Long postId) {
        return postHardConditionRuleService.listByPostId(postId).stream()
                .map(HardConditionRuleApiFacade::toResponse)
                .toList();
    }

    public void save(PostHardConditionRuleDTO dto) {
        postHardConditionRuleService.saveRule(dto);
    }

    public void update(Long id, PostHardConditionRuleDTO dto) {
        dto.setId(id);
        postHardConditionRuleService.saveRule(dto);
    }

    public void batchConfig(Long postId, List<PostHardConditionRuleDTO> list) {
        postHardConditionRuleService.batchConfig(postId, list);
    }

    public void delete(Long id) {
        postHardConditionRuleService.removeById(id);
    }

    static HardConditionRuleResponse toResponse(PostHardConditionRule entity) {
        if (entity == null) return null;
        return new HardConditionRuleResponse(
                entity.getId(),
                entity.getPostId(),
                entity.getFieldName(),
                entity.getFieldLabel(),
                entity.getFieldType(),
                entity.getOperator(),
                entity.getExpectedValue(),
                entity.getValueRankJson(),
                entity.getEnabled(),
                entity.getSortOrder(),
                entity.getRemark(),
                entity.getCreatedTime(),
                entity.getUpdatedTime()
        );
    }
}
