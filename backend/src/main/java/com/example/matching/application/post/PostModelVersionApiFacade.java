package com.example.matching.application.post;

import com.example.matching.dto.post.api.PostModelVersionItemRequest;
import com.example.matching.dto.post.api.PostModelVersionItemResponse;
import com.example.matching.dto.post.api.PostModelVersionResponse;
import com.example.matching.dto.post.api.UnmatchedAbilityDTO;
import com.example.matching.entity.post.PostModelVersion;
import com.example.matching.entity.post.PostModelVersionItem;
import com.example.matching.service.post.PostModelUnmatchedAbilityService;
import com.example.matching.service.post.PostModelVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostModelVersionApiFacade {

    private final PostModelVersionService modelVersionService;
    private final PostModelUnmatchedAbilityService unmatchedAbilityService;

    public PostModelVersionResponse createDraft(Long postId, String sourceType, String description) {
        return toResponse(modelVersionService.createDraft(postId, sourceType, description));
    }

    public void saveVersionItems(Long versionId, List<PostModelVersionItemRequest> items) {
        List<PostModelVersionItem> entities = items.stream()
                .map(item -> {
                    PostModelVersionItem e = new PostModelVersionItem();
                    e.setTagId(item.tagId());
                    e.setMinRequiredLevel(item.minRequiredLevel());
                    e.setWeight(item.weight());
                    e.setIsRequired(item.isRequired());
                    e.setIsCore(item.isCore());
                    e.setReason(item.reason());
                    return e;
                })
                .toList();
        modelVersionService.saveVersionItems(versionId, entities);
    }

    public void publishVersion(Long versionId) {
        modelVersionService.publishVersion(versionId);
    }

    public void rollbackToVersion(Long versionId) {
        modelVersionService.rollbackToVersion(versionId);
    }

    public List<PostModelVersionResponse> listVersions(Long postId) {
        return modelVersionService.listVersions(postId).stream()
                .map(PostModelVersionApiFacade::toResponse)
                .toList();
    }

    public PostModelVersionResponse getVersionDetail(Long versionId) {
        PostModelVersion version = modelVersionService.getVersionDetail(versionId);
        if (version == null) {
            return null;
        }
        List<UnmatchedAbilityDTO> unmatchedDtos = unmatchedAbilityService.listByVersionId(versionId).stream()
                .map(unmatchedAbilityService::toDto)
                .toList();
        return toResponse(version, unmatchedDtos);
    }

    public List<PostModelVersionItemResponse> getVersionItems(Long versionId) {
        return modelVersionService.getVersionItems(versionId).stream()
                .map(PostModelVersionApiFacade::toItemResponse)
                .toList();
    }

    public void deleteDraft(Long versionId) {
        modelVersionService.deleteDraft(versionId);
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

    static PostModelVersionItemResponse toItemResponse(PostModelVersionItem entity) {
        if (entity == null) return null;
        return new PostModelVersionItemResponse(
                entity.getId(),
                entity.getVersionId(),
                entity.getTagId(),
                entity.getMinRequiredLevel(),
                entity.getWeight(),
                entity.getIsRequired(),
                entity.getIsCore(),
                entity.getReason(),
                entity.getCreatedTime()
        );
    }
}
