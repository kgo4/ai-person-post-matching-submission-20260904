package com.example.matching.application.rag;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.rag.api.RagQueryLogResponse;
import com.example.matching.entity.rag.RagQueryLog;
import com.example.matching.service.rag.RagQueryLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RagLogApiFacade {

    private final RagQueryLogService ragQueryLogService;

    public PageResponse<RagQueryLogResponse> pageLogs(long current, long size, String scenario) {
        IPage<RagQueryLog> page = ragQueryLogService.pageLogs(new Page<>(current, size), scenario);
        return PageResponse.from(page, RagLogApiFacade::toResponse);
    }

    public RagQueryLogResponse getLog(Long id) {
        RagQueryLog entity = ragQueryLogService.getLogById(id);
        return toResponse(entity);
    }

    static RagQueryLogResponse toResponse(RagQueryLog e) {
        if (e == null) return null;
        return new RagQueryLogResponse(
                e.getId(), e.getQueryCode(), e.getScenario(), e.getQueryText(),
                e.getTopK(), e.getRetrievedChunkIds(), e.getContextText(),
                e.getPromptSnapshot(), e.getResponseSnapshot(), e.getLatencyMs(),
                e.getHitCount(), e.getCreatedBy(), e.getCreatedTime()
        );
    }
}
