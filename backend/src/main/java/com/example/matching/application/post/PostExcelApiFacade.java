package com.example.matching.application.post;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.post.PostImportBatchVO;
import com.example.matching.dto.post.PostImportConfirmDTO;
import com.example.matching.dto.post.PostImportPreviewDTO;
import com.example.matching.service.post.PostExcelAiImportService;
import com.example.matching.service.post.PostExcelImportTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class PostExcelApiFacade {

    private final PostExcelAiImportService excelAiImportService;
    private final PostExcelImportTemplateService postExcelImportTemplateService;

    public byte[] downloadTemplate() {
        return postExcelImportTemplateService.generate();
    }

    public PostImportPreviewDTO uploadAndAnalyze(String fileName, InputStream inputStream) {
        return excelAiImportService.uploadAndAnalyze(fileName, inputStream);
    }

    public void analyzeBatch(Long batchId) {
        excelAiImportService.analyzeBatch(batchId);
    }

    public PostImportPreviewDTO getPreview(Long batchId) {
        return excelAiImportService.getPreview(batchId);
    }

    public void confirmAndImport(PostImportConfirmDTO confirmDTO) {
        excelAiImportService.confirmAndImport(confirmDTO);
    }

    public int includeBatchInMarketDiscovery(Long batchId) {
        return excelAiImportService.includeBatchInMarketDiscovery(batchId);
    }

    public void cancelBatch(Long batchId) {
        excelAiImportService.cancelBatch(batchId);
    }

    public Page<PostImportBatchVO> pageBatches(long current, long size, Integer importStatus) {
        return excelAiImportService.pageBatches(current, size, importStatus);
    }

    public void retryBatch(Long batchId) {
        excelAiImportService.retryBatch(batchId);
    }

    public void deleteBatch(Long batchId) {
        excelAiImportService.deleteBatch(batchId);
    }
}
