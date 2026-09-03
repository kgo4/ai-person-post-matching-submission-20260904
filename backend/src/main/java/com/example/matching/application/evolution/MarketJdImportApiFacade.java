package com.example.matching.application.evolution;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.evolution.api.MarketJdImportRequest;
import com.example.matching.dto.evolution.api.MarketJdResponse;
import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.service.evolution.MarketJdImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarketJdImportApiFacade {

    private final MarketJdImportService marketJdImportService;

    public Map<String, Object> importTexts(List<String> jdTexts, String sourcePlatform) {
        MarketJdImportService.ImportBatchResult result =
                marketJdImportService.importFromTextListWithBatch(jdTexts, sourcePlatform);
        return Map.of("imported", result.imported(), "batchNo", result.batchNo() == null ? "" : result.batchNo());
    }

    public Map<String, Object> importExcel(List<MarketJdImportRequest> requests) {
        List<MarketJdData> dataList = requests.stream().map(req -> {
            MarketJdData data = new MarketJdData();
            data.setBatchNo(req.batchNo());
            data.setPostName(req.postName());
            data.setCompanyName(req.companyName());
            data.setCity(req.city());
            data.setSalaryRange(req.salaryRange());
            data.setJobDescription(req.jobDescription());
            data.setRequirements(req.requirements());
            data.setSkillTags(req.skillTags());
            data.setSourcePlatform(req.sourcePlatform());
            return data;
        }).toList();
        int imported = marketJdImportService.importFromExcelData(dataList);
        return Map.of("imported", imported);
    }

    public PageResponse<MarketJdResponse> pageMarketJds(long current, long size, String postName, String batchNo) {
        IPage<MarketJdData> page = marketJdImportService.pageMarketJds(new Page<>(current, size), postName, batchNo);
        return PageResponse.from(page, MarketJdImportApiFacade::toResponse);
    }

    public List<MarketJdResponse> getMarketJdsByPostId(Long postId, int limit) {
        List<MarketJdData> list = marketJdImportService.getMarketJdsByPostId(postId, limit);
        return list.stream().map(MarketJdImportApiFacade::toResponse).toList();
    }

    public Map<String, Object> deduplicate(String batchNo) {
        int duplicates = marketJdImportService.deduplicateByBatch(batchNo);
        return Map.of("duplicates", duplicates);
    }

    public MarketJdImportService.BatchStatistics getBatchStatistics(String batchNo) {
        return marketJdImportService.getBatchStatistics(batchNo);
    }

    public MarketJdImportService.BatchAnalysisResult analyzeBatch(String batchNo) {
        return marketJdImportService.analyzeBatch(batchNo);
    }

    static MarketJdResponse toResponse(MarketJdData e) {
        if (e == null) return null;
        return new MarketJdResponse(
                e.getId(), e.getBatchNo(), e.getPostName(), e.getCompanyName(),
                e.getCity(), e.getSalaryRange(), e.getJobDescription(), e.getRequirements(),
                e.getSkillTags(), e.getSourcePlatform(), e.getPublishedTime(), e.getTextHash(),
                e.getSimilarityGroupId(), e.getQualityScore(), e.getIsDuplicate(),
                e.getCanonicalDocumentId(), e.getLastSeenTime(), e.getFreshnessScore(),
                e.getNoiseScore(), e.getCompanyDiversityKey(), e.getMatchedPostId(),
                e.getAnalysisStatus(), e.getCreatedTime()
        );
    }
}
