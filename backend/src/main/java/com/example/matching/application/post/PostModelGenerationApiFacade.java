package com.example.matching.application.post;

import com.example.matching.dto.post.api.PostModelVersionResponse;
import com.example.matching.dto.post.api.UnmatchedAbilityDTO;
import com.example.matching.entity.post.PostModelVersion;
import com.example.matching.service.post.PostModelGenerationService;
import com.example.matching.service.post.PostModelUnmatchedAbilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostModelGenerationApiFacade {

    private final PostModelGenerationService modelGenerationService;
    private final PostModelUnmatchedAbilityService unmatchedAbilityService;

    public PostModelVersionResponse generateFromPrototype(Long postId, Long prototypeId, String description) {
        return toResponse(modelGenerationService.generateFromPrototype(postId, prototypeId, description));
    }

    public PostModelVersionResponse generateFromJD(Long postId, String jdText, String description) {
        PostModelVersion version = modelGenerationService.generateFromJD(postId, jdText, description);
        List<UnmatchedAbilityDTO> unmatchedDtos = unmatchedAbilityService.listByVersionId(version.getId()).stream()
                .map(unmatchedAbilityService::toDto)
                .toList();
        return toResponse(version, unmatchedDtos);
    }

    public PostModelVersionResponse generateFromCopy(Long sourcePostId, Long targetPostId, String description) {
        return toResponse(modelGenerationService.generateFromCopy(sourcePostId, targetPostId, description));
    }

    static PostModelVersionResponse toResponse(PostModelVersion entity) {
        return toResponse(entity, null);
    }

    static PostModelVersionResponse toResponse(PostModelVersion entity, List<UnmatchedAbilityDTO> unmatchedDtos) {
        if (entity == null) return null;
        return new PostModelVersionResponse(
                entity.getId(),
                entity.getPostId(),
                entity.getVersionNo(),
                entity.getSourceType(),
                entity.getStatus(),
                entity.getQualityScore(),
                entity.getItemCount(),
                entity.getTotalWeight(),
                entity.getDescription(),
                entity.getPublishTime(),
                entity.getCreatedTime(),
                entity.getUpdatedTime(),
                unmatchedDtos
        );
    }
}
