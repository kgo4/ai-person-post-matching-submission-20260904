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
        createAndReturnId(req);
    }

    /**
     * 建岗位并返回主键。
     * <p>
     * 趋势发现的落地需要在同一事务里「建岗位 → 写能力画像 → 回写能力标签候选」，
     * 拿不到 postId 就只能先建岗再反查，中途失败会留下「有岗位、无能力」的脏数据。
     */
    public Long createAndReturnId(PostCreateRequest req) {
        PostPost entity = new PostPost();
        entity.setPostCode(StringUtils.hasText(req.postCode())
                ? req.postCode() : businessCodeGenerator.nextPostCode());
        entity.setPostName(req.postName());
        entity.setJobDescription(req.jobDescription());
        entity.setStatus(req.status());
        entity.setPostLevel(req.postLevel());
        entity.setDepartmentId(null);
        postPostService.save(entity);
        return entity.getId();
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
