package com.example.matching.application.post;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.post.PostPrototypeSaveDTO;
import com.example.matching.dto.post.PostPrototypeVO;
import com.example.matching.dto.post.api.PostPrototypeResponse;
import com.example.matching.entity.post.PostPrototype;
import com.example.matching.service.post.PostPrototypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class PostPrototypeApiFacade {

    private final PostPrototypeService postPrototypeService;

    public PageResponse<PostPrototypeResponse> page(Integer pageNum, Integer pageSize, String keyword, String industry, String category) {
        IPage<PostPrototype> page = postPrototypeService.pagePrototypes(new Page<>(pageNum, pageSize), keyword, industry, category);
        Function<PostPrototype, PostPrototypeResponse> converter = p -> new PostPrototypeResponse(
                p.getId(), p.getPrototypeName(), p.getDescription(),
                p.getIndustry(), p.getCategory(), p.getStatus(), p.getCreatedTime()
        );
        return new PageResponse<>(
                page.getRecords().stream().map(converter).toList(),
                page.getTotal(), page.getCurrent(), page.getSize(), page.getPages()
        );
    }

    public List<PostPrototypeResponse> listEnabled() {
        return postPrototypeService.listEnabled().stream()
                .map(p -> new PostPrototypeResponse(
                        p.getId(), p.getPrototypeName(), p.getDescription(),
                        p.getIndustry(), p.getCategory(), p.getStatus(), p.getCreatedTime()
                ))
                .toList();
    }

    public PostPrototypeVO getDetail(Long id) {
        return postPrototypeService.getDetail(id);
    }

    public void save(PostPrototypeSaveDTO dto) {
        postPrototypeService.savePrototype(dto);
    }

    public void delete(Long id) {
        postPrototypeService.deletePrototype(id);
    }

    public List<PostPrototypeVO> recall(String description, int topN) {
        return postPrototypeService.recallByDescription(description, topN);
    }

    public void applyToPost(Long prototypeId, Long postId) {
        postPrototypeService.applyPrototypeToPost(prototypeId, postId);
    }
}
