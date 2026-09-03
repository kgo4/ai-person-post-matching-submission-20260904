package com.example.matching.application.post;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.post.PostCleaningRecordPageQuery;
import com.example.matching.dto.post.PostCleaningRecordVO;
import com.example.matching.dto.post.PostCleaningResult;
import com.example.matching.service.post.PostDataCleaningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostCleaningApiFacade {

    private final PostDataCleaningService postDataCleaningService;

    public Page<PostCleaningRecordVO> pageRecords(PostCleaningRecordPageQuery query) {
        return postDataCleaningService.pageRecords(query);
    }

    public PostCleaningRecordVO getRecordDetail(Long id) {
        return postDataCleaningService.getRecordDetail(id);
    }

    public PostCleaningResult reparse(Long id) {
        return postDataCleaningService.reparse(id);
    }
}
