package com.example.matching.application.post;

import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.dto.post.PostModelExcelRowDTO;
import com.example.matching.service.post.PostModelExcelImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostModelImportApiFacade {

    private final PostModelExcelImportService modelExcelImportService;

    public List<PostModelExcelRowDTO> parseExcel(InputStream inputStream) {
        return modelExcelImportService.parseExcel(inputStream);
    }

    public Map<Long, Integer> batchImportFromTemplateB(List<PostModelExcelRowDTO> rows) {
        return modelExcelImportService.batchImportFromTemplateB(rows);
    }

    public Map<Long, Integer> batchImportFromTemplateA(List<PostModelExcelRowDTO> rows) {
        return modelExcelImportService.batchImportFromTemplateA(rows);
    }

    public List<PostAbilityModelConfigDTO> normalizeWeights(Long postId) {
        return modelExcelImportService.normalizeWeights(postId);
    }

    public int copyPostModel(Long sourcePostId, Long targetPostId) {
        return modelExcelImportService.copyPostModel(sourcePostId, targetPostId);
    }
}
