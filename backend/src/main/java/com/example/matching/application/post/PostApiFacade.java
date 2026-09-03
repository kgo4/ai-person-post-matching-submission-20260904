package com.example.matching.application.post;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.post.api.PostCreateRequest;
import com.example.matching.dto.post.api.PostResponse;
import com.example.matching.dto.post.api.PostUpdateRequest;
import com.example.matching.entity.post.PostPost;
import com.example.matching.service.common.BusinessCodeGenerator;
import com.example.matching.service.post.PostPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostApiFacade {

    private final PostPostService postPostService;
    private final BusinessCodeGenerator businessCodeGenerator;

    public PageResponse<PostResponse> page(long current, long size, String keyword, Integer status) {
        var page = postPostService.pagePosts(new Page<>(current, size), keyword, status);
        return PageResponse.from(page, PostApiFacade::toResponse);
    }

    public List<PostResponse> listEnabled() {
        return postPostService.listEnabled().stream()
                .map(PostApiFacade::toResponse)
                .toList();
    }

    public PostResponse get(Long id) {
        return toResponse(postPostService.getById(id));
    }

    public void create(PostCreateRequest req) {
        PostPost entity = new PostPost();
        entity.setPostCode(StringUtils.hasText(req.postCode())
                ? req.postCode() : businessCodeGenerator.nextPostCode());
        entity.setPostName(req.postName());
        entity.setJobDescription(req.jobDescription());
        entity.setStatus(req.status());
        entity.setPostLevel(req.postLevel());
        entity.setDepartmentId(null);
        postPostService.save(entity);
    }

    public void update(Long id, PostUpdateRequest req) {
        PostPost entity = new PostPost();
        entity.setId(id);
        entity.setPostCode(req.postCode());
        entity.setPostName(req.postName());
        entity.setJobDescription(req.jobDescription());
        entity.setStatus(req.status());
        entity.setPostLevel(req.postLevel());
        entity.setDepartmentId(null);
        postPostService.updateById(entity);
    }

    public void delete(Long id) {
        postPostService.removeById(id);
    }

    static PostResponse toResponse(PostPost entity) {
        if (entity == null) return null;
        return new PostResponse(
                entity.getId(),
                entity.getPostCode(),
                entity.getPostName(),
                entity.getJobDescription(),
                entity.getStatus(),
                entity.getPostLevel(),
                entity.getDepartmentId(),
                entity.getCreatedTime(),
                entity.getUpdatedTime()
        );
    }
}
