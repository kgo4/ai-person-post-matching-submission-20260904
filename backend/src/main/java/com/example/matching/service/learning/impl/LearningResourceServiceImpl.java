package com.example.matching.service.learning.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.learning.LearningResourceQueryDTO;
import com.example.matching.dto.learning.LearningResourceSaveDTO;
import com.example.matching.dto.learning.api.CoverImageUploadRequest;
import com.example.matching.entity.learning.LearningResource;
import com.example.matching.mapper.learning.LearningResourceMapper;
import com.example.matching.service.learning.LearningResourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 学习资源服务实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningResourceServiceImpl implements LearningResourceService {

    private static final String COVER_UPLOAD_ROOT = "uploads/learning-covers";

    /** 允许的封面图片类型 -> 扩展名 */
    private static final Map<String, String> IMAGE_EXT_BY_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp");

    private final LearningResourceMapper resourceMapper;

    @Override
    public String uploadCover(CoverImageUploadRequest request) {
        if (request == null || request.content() == null || request.content().length == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "上传文件不能为空");
        }
        String contentType = request.contentType();
        String ext = contentType == null ? null : IMAGE_EXT_BY_TYPE.get(contentType.toLowerCase());
        if (ext == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "仅支持 JPG/PNG/GIF/WebP 格式的封面图片");
        }
        try {
            Path uploadDir = Paths.get(COVER_UPLOAD_ROOT);
            Files.createDirectories(uploadDir);
            String filename = UUID.randomUUID().toString().replace("-", "") + ext;
            Path target = uploadDir.resolve(filename);
            Files.write(target, request.content());
            log.info("资源封面上传成功: file={}, size={}", filename, request.content().length);
            return "/uploads/learning-covers/" + filename;
        } catch (IOException e) {
            log.error("资源封面上传失败: error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "封面保存失败: " + e.getMessage());
        }
    }

    @Override
    public LearningResource saveResource(LearningResourceSaveDTO dto) {
        LearningResource resource;
        if (dto.getId() != null) {
            resource = resourceMapper.selectById(dto.getId());
            if (resource == null) {
                throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "学习资源不存在: " + dto.getId());
            }
            resource.setAbilityName(dto.getAbilityName());
            resource.setTagId(dto.getTagId());
            resource.setTitle(dto.getTitle());
            resource.setResourceType(dto.getResourceType());
            resource.setDifficultyLevel(dto.getDifficultyLevel());
            resource.setUrl(dto.getUrl());
            resource.setDescription(dto.getDescription());
            resource.setPlatform(dto.getPlatform());
            resource.setPlatformIcon(dto.getPlatformIcon());
            resource.setCoverImageUrl(dto.getCoverImageUrl());
            resource.setDuration(dto.getDuration());
            resource.setSortOrder(dto.getSortOrder());
            resourceMapper.updateById(resource);
        } else {
            resource = new LearningResource();
            resource.setResourceCode("RES_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
            resource.setAbilityName(dto.getAbilityName());
            resource.setTagId(dto.getTagId());
            resource.setTitle(dto.getTitle());
            resource.setResourceType(dto.getResourceType());
            resource.setDifficultyLevel(dto.getDifficultyLevel() != null ? dto.getDifficultyLevel() : 1);
            resource.setUrl(dto.getUrl());
            resource.setDescription(dto.getDescription());
            resource.setPlatform(dto.getPlatform());
            resource.setPlatformIcon(dto.getPlatformIcon());
            resource.setCoverImageUrl(dto.getCoverImageUrl());
            resource.setDuration(dto.getDuration());
            resource.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
            resource.setStatus(1);
            resourceMapper.insert(resource);
        }
        return resource;
    }

    @Override
    public IPage<LearningResource> pageResources(Page<LearningResource> page, LearningResourceQueryDTO query) {
        LambdaQueryWrapper<LearningResource> wrapper = new LambdaQueryWrapper<>();
        if (query.getAbilityName() != null && !query.getAbilityName().isBlank()) {
            wrapper.like(LearningResource::getAbilityName, query.getAbilityName());
        }
        if (query.getTagId() != null) {
            wrapper.eq(LearningResource::getTagId, query.getTagId());
        }
        if (query.getResourceType() != null && !query.getResourceType().isBlank()) {
            wrapper.eq(LearningResource::getResourceType, query.getResourceType());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w
                    .like(LearningResource::getTitle, kw)
                    .or()
                    .like(LearningResource::getDescription, kw)
                    .or()
                    .like(LearningResource::getAbilityName, kw));
        }
        if (query.getPlatform() != null && !query.getPlatform().isBlank()) {
            wrapper.eq(LearningResource::getPlatform, query.getPlatform());
        }
        if (query.getStatus() != null) {
            wrapper.eq(LearningResource::getStatus, query.getStatus());
        }
        // sortOrder 权重优先，再按难度升序（V114 保证 sort_order 非空）
        wrapper.last("ORDER BY sort_order ASC, difficulty_level ASC");
        return resourceMapper.selectPage(page, wrapper);
    }

    @Override
    public LearningResource getResourceById(Long id) {
        LearningResource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "学习资源不存在: " + id);
        }
        return resource;
    }

    @Override
    public void deleteResource(Long id) {
        LearningResource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "学习资源不存在: " + id);
        }
        resourceMapper.deleteById(id);
        log.info("删除学习资源: id={}, title={}", id, resource.getTitle());
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "非法状态值: " + status);
        }
        LearningResource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "学习资源不存在: " + id);
        }
        resource.setStatus(status);
        resourceMapper.updateById(resource);
        log.info("更新学习资源状态: id={}, status={}", id, status);
    }

    @Override
    public void batchUpdateStatus(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "资源ID列表不能为空");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "非法状态值: " + status);
        }
        LambdaUpdateWrapper<LearningResource> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(LearningResource::getId, ids);
        wrapper.set(LearningResource::getStatus, status);
        resourceMapper.update(null, wrapper);
        log.info("批量更新学习资源状态: ids={}, status={}", ids, status);
    }

    @Override
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "资源ID列表不能为空");
        }
        resourceMapper.deleteBatchIds(ids);
        log.info("批量删除学习资源: ids={}", ids);
    }
}
