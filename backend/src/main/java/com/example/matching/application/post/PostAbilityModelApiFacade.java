package com.example.matching.application.post;

import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.dto.post.api.PostAbilityModelResponse;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.ability.PostAbilityMemoryGovernanceService;
import com.example.matching.service.post.PostAbilityGroundingRecordService;
import com.example.matching.vo.post.PostAbilityModelVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostAbilityModelApiFacade {

    private final PostAbilityModelService postAbilityModelService;
    private final PostAbilityMemoryGovernanceService postAbilityMemoryGovernanceService;
    private final PostAbilityGroundingRecordService postAbilityGroundingRecordService;

    public PostAbilityModelVO getModel(Long postId) {
        return postAbilityModelService.getPostAbilityModel(postId);
    }

    public List<PostAbilityModelResponse> listByPostId(Long postId) {
        return postAbilityModelService.listByPostId(postId).stream()
                .map(PostAbilityModelApiFacade::toResponse)
                .toList();
    }

    public Set<Long> listConfiguredPostIds(List<Long> postIds) {
        return postAbilityModelService.listConfiguredPostIds(postIds);
    }

    public void save(PostAbilityModelConfigDTO dto) {
        postAbilityModelService.saveConfig(dto);
        postAbilityMemoryGovernanceService.createFutureJdExtractionRule(dto);
    }

    public void update(Long id, PostAbilityModelConfigDTO dto) {
        dto.setId(id);
        postAbilityModelService.saveConfig(dto);
        postAbilityMemoryGovernanceService.createFutureJdExtractionRule(dto);
        // 人工修改岗位能力后，将该能力待处理提取台账标记为已处理，避免台账长期停留在"待处理"观感
        postAbilityGroundingRecordService.resolveForModel(dto.getPostId(), dto.getAbilityName());
    }

    public void batchConfig(List<PostAbilityModelConfigDTO> list) {
        postAbilityModelService.batchConfig(list);
        list.forEach(postAbilityMemoryGovernanceService::createFutureJdExtractionRule);
    }

    public void delete(Long id) {
        PostAbilityModel model = postAbilityModelService.getById(id);
        postAbilityModelService.deleteModel(id);
        if (model != null) {
            postAbilityGroundingRecordService.resolveForModel(model.getPostId(), model.getAbilityName());
        }
    }

    static PostAbilityModelResponse toResponse(PostAbilityModel entity) {
        if (entity == null) return null;
        return new PostAbilityModelResponse(
                entity.getId(),
                entity.getPostId(),
                entity.getTagId(),
                entity.getAbilityName(),
                entity.getMinRequiredLevel(),
                entity.getWeight(),
                entity.getIsRequired(),
                entity.getIsCore(),
                entity.getModelVersion(),
                entity.getRemark(),
                entity.getCreatedTime(),
                entity.getUpdatedTime()
        );
    }
}
