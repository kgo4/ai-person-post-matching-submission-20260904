package com.example.matching.application.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.system.AbilityTagSaveDTO;
import com.example.matching.dto.system.api.AbilityTagCreateRequest;
import com.example.matching.dto.system.api.AbilityTagResponse;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.service.common.BusinessCodeGenerator;
import com.example.matching.service.system.AbilityTagService;
import com.example.matching.vo.system.AbilityTagTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AbilityTagApiFacade {

    private final AbilityTagService abilityTagService;
    private final BusinessCodeGenerator businessCodeGenerator;

    public List<AbilityTagTreeVO> getTree() {
        return abilityTagService.getTree();
    }

    public List<AbilityTagTreeVO> getByCategory(String category) {
        return abilityTagService.getByCategory(category);
    }

    public PageResponse<AbilityTagResponse> page(long current, long size, String keyword, String category) {
        IPage<AbilityTag> page = abilityTagService.pageTags(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size), keyword, category);
        return PageResponse.from(page, this::toResponse);
    }

    public AbilityTagResponse get(Long id) {
        AbilityTag entity = abilityTagService.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCodeEnum.ABILITY_TAG_NOT_FOUND);
        }
        return toResponse(entity);
    }

    public AbilityTagResponse create(AbilityTagCreateRequest request) {
        AbilityTagSaveDTO command = new AbilityTagSaveDTO();
        command.setTagCode(StringUtils.hasText(request.tagCode())
                ? request.tagCode() : businessCodeGenerator.nextAbilityTagCode());
        command.setTagName(request.tagName());
        command.setParentId(request.parentId());
        command.setTagCategory(request.tagCategory());
        command.setDescription(request.description());
        command.setSortOrder(request.sortOrder());
        command.setTagLevel(request.tagLevel());
        Long savedId = abilityTagService.saveTag(command);
        return get(savedId);
    }

    public void update(Long id, AbilityTagCreateRequest request) {
        AbilityTagSaveDTO command = new AbilityTagSaveDTO();
        command.setId(id);
        command.setTagCode(request.tagCode());
        command.setTagName(request.tagName());
        command.setParentId(request.parentId());
        command.setTagCategory(request.tagCategory());
        command.setDescription(request.description());
        command.setSortOrder(request.sortOrder());
        command.setTagLevel(request.tagLevel());
        abilityTagService.saveTag(command);
    }

    public void updateStatus(Long id, Integer status) {
        abilityTagService.updateStatus(id, status);
    }

    public int batchGenerateVectors() {
        return abilityTagService.batchGenerateVectors();
    }

    public void delete(Long id) {
        abilityTagService.deleteTag(id);
    }

    private AbilityTagResponse toResponse(AbilityTag entity) {
        return new AbilityTagResponse(
            entity.getId(), entity.getTagCode(), entity.getTagName(),
            entity.getParentId(), entity.getTagCategory(), entity.getDomain(),
            entity.getTagLevel(), entity.getDescription(), entity.getSortOrder(),
            entity.getIsSystem(), entity.getSourceType(), entity.getStatus(),
            entity.getCreatedTime(), entity.getUpdatedTime()
        );
    }
}
