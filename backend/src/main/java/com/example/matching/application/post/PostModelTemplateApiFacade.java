package com.example.matching.application.post;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.post.PostTemplateSaveDTO;
import com.example.matching.dto.post.api.PostModelTemplateResponse;
import com.example.matching.dto.post.api.TemplateAbilityItemRequest;
import com.example.matching.dto.post.api.TemplateAbilityResponse;
import com.example.matching.entity.post.PostModelTemplate;
import com.example.matching.entity.post.TemplateAbilityModel;
import com.example.matching.service.post.PostModelTemplateService;
import com.example.matching.service.post.TemplateAbilityModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostModelTemplateApiFacade {

    private final PostModelTemplateService postModelTemplateService;
    private final TemplateAbilityModelService templateAbilityModelService;

    public PageResponse<PostModelTemplateResponse> page(long current, long size, String keyword) {
        var page = postModelTemplateService.pageTemplates(new Page<>(current, size), keyword);
        return PageResponse.from(page, PostModelTemplateApiFacade::toResponse);
    }

    public PostModelTemplateResponse get(Long id) {
        return toResponse(postModelTemplateService.getById(id));
    }

    public void save(PostTemplateSaveDTO dto) {
        postModelTemplateService.saveTemplate(dto);
    }

    public void update(Long id, PostTemplateSaveDTO dto) {
        dto.setId(id);
        postModelTemplateService.saveTemplate(dto);
    }

    public void delete(Long id) {
        postModelTemplateService.removeById(id);
    }

    public List<TemplateAbilityResponse> getAbilityModels(Long templateId) {
        return templateAbilityModelService.listByTemplateId(templateId).stream()
                .map(PostModelTemplateApiFacade::toAbilityResponse)
                .toList();
    }

    public void saveAbilityModels(Long templateId, List<TemplateAbilityItemRequest> items) {
        List<TemplateAbilityModel> entities = items.stream()
                .map(item -> {
                    TemplateAbilityModel e = new TemplateAbilityModel();
                    e.setTagId(item.tagId());
                    e.setMinRequiredLevel(item.minRequiredLevel());
                    e.setWeight(item.weight());
                    e.setIsRequired(item.isRequired());
                    e.setIsCore(item.isCore());
                    e.setRemark(item.remark());
                    return e;
                })
                .toList();
        templateAbilityModelService.batchSave(templateId, entities);
    }

    public void applyTemplateToPost(Long templateId, Long postId) {
        templateAbilityModelService.applyTemplateToPost(templateId, postId);
    }

    static PostModelTemplateResponse toResponse(PostModelTemplate entity) {
        if (entity == null) return null;
        return new PostModelTemplateResponse(
                entity.getId(),
                entity.getTemplateCode(),
                entity.getTemplateName(),
                entity.getPostSequence(),
                entity.getPostLevelRange(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getCreatedTime(),
                entity.getUpdatedTime()
        );
    }

    static TemplateAbilityResponse toAbilityResponse(TemplateAbilityModel entity) {
        if (entity == null) return null;
        return new TemplateAbilityResponse(
                entity.getId(),
                entity.getTemplateId(),
                entity.getTagId(),
                entity.getMinRequiredLevel(),
                entity.getWeight(),
                entity.getIsRequired(),
                entity.getIsCore(),
                entity.getRemark(),
                entity.getCreatedTime()
        );
    }
}
