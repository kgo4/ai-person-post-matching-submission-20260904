package com.example.matching.application.matching;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.matching.api.BlackWhiteListEntryRequest;
import com.example.matching.dto.matching.api.BlackWhiteListEntryResponse;
import com.example.matching.entity.matching.MatchingBlackWhiteList;
import com.example.matching.service.matching.MatchingBlackWhiteListService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BlackWhiteListApiFacade {

    private final MatchingBlackWhiteListService matchingBlackWhiteListService;

    public PageResponse<BlackWhiteListEntryResponse> page(long current, long size, Long empId, Long postId) {
        IPage<MatchingBlackWhiteList> page = matchingBlackWhiteListService.pageList(
            new Page<>(current, size), empId, postId);
        return PageResponse.from(page, this::toResponse);
    }

    public void create(BlackWhiteListEntryRequest req) {
        MatchingBlackWhiteList entity = new MatchingBlackWhiteList();
        entity.setEmpId(req.empId());
        entity.setPostId(req.postId());
        entity.setListType(req.listType());
        entity.setRemark(req.remark());
        matchingBlackWhiteListService.save(entity);
    }

    public void update(Long id, BlackWhiteListEntryRequest req) {
        MatchingBlackWhiteList entity = new MatchingBlackWhiteList();
        entity.setId(id);
        entity.setEmpId(req.empId());
        entity.setPostId(req.postId());
        entity.setListType(req.listType());
        entity.setRemark(req.remark());
        matchingBlackWhiteListService.updateById(entity);
    }

    public void delete(Long id) {
        matchingBlackWhiteListService.removeById(id);
    }

    private BlackWhiteListEntryResponse toResponse(MatchingBlackWhiteList e) {
        return new BlackWhiteListEntryResponse(
            e.getId(), e.getEmpId(), e.getPostId(), e.getListType(),
            e.getRemark(), e.getStatus(), e.getCreatedBy(), e.getCreatedTime()
        );
    }
}
