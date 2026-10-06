package com.example.matching.application.rag;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.dto.rag.KnowledgeChunkResultDTO;
import com.example.matching.dto.rag.KnowledgeChunkSearchDTO;
import com.example.matching.dto.rag.KnowledgeDocumentQueryDTO;
import com.example.matching.dto.rag.KnowledgeDocumentSaveDTO;
import com.example.matching.dto.rag.api.KnowledgeDocumentCreateRequest;
import com.example.matching.dto.rag.api.KnowledgeDocumentResponse;
import com.example.matching.dto.rag.api.KnowledgeDocumentUpdateRequest;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.service.rag.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RagKnowledgeApiFacade {

    private final KnowledgeDocumentService knowledgeDocumentService;

    public KnowledgeDocumentResponse createDocument(KnowledgeDocumentCreateRequest req) {
        KnowledgeDocumentSaveDTO dto = toSaveDTO(req);
        RagKnowledgeDocument entity = knowledgeDocumentService.saveDocument(dto);
        return toResponse(entity);
    }

    public KnowledgeDocumentResponse updateDocument(Long id, KnowledgeDocumentUpdateRequest req) {
        KnowledgeDocumentSaveDTO dto = toSaveDTO(req);
        dto.setId(id);
        RagKnowledgeDocument entity = knowledgeDocumentService.saveDocument(dto);
        return toResponse(entity);
    }

    public PageResponse<KnowledgeDocumentResponse> pageDocuments(long current, long size, String sourceType, String docStatus, String title) {
        KnowledgeDocumentQueryDTO query = new KnowledgeDocumentQueryDTO();
        query.setSourceType(sourceType);
        query.setDocStatus(docStatus);
        query.setTitle(title);
        IPage<RagKnowledgeDocument> page = knowledgeDocumentService.pageDocuments(new Page<>(current, size), query);
        return PageResponse.from(page, RagKnowledgeApiFacade::toResponse);
    }

    public KnowledgeDocumentResponse getDocument(Long id) {
        RagKnowledgeDocument entity = knowledgeDocumentService.getDocumentById(id);
        return toResponse(entity);
    }

    public Map<String, Object> indexDocument(Long id) {
        int chunkCount = knowledgeDocumentService.indexDocument(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", id);
        result.put("chunkCount", chunkCount);
        return result;
    }

    public Map<String, Object> indexDocuments(String sourceType, boolean onlyUnindexed, int limit) {
        return knowledgeDocumentService.indexDocuments(sourceType, onlyUnindexed, limit);
    }

    public Map<String, Object> backfill(String sourceType, int limit) {
        int created = knowledgeDocumentService.backfillDocuments(sourceType, limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceType", sourceType);
        result.put("created", created);
        return result;
    }

    public List<KnowledgeChunkResultDTO> searchChunks(String queryText, String scenario, Integer topK, List<String> sourceTypes) {
        KnowledgeChunkSearchDTO dto = new KnowledgeChunkSearchDTO();
        dto.setQueryText(queryText);
        dto.setScenario(scenario);
        dto.setTopK(topK);
        dto.setSourceTypes(sourceTypes);
        return knowledgeDocumentService.searchChunks(dto);
    }

    private KnowledgeDocumentSaveDTO toSaveDTO(KnowledgeDocumentCreateRequest req) {
        KnowledgeDocumentSaveDTO dto = new KnowledgeDocumentSaveDTO();
        dto.setSourceType(req.sourceType());
        dto.setSourceRefId(req.sourceRefId());
        dto.setTitle(req.title());
        dto.setContent(req.content());
        dto.setMetadataJson(req.metadataJson());
        return dto;
    }

    private KnowledgeDocumentSaveDTO toSaveDTO(KnowledgeDocumentUpdateRequest req) {
        KnowledgeDocumentSaveDTO dto = new KnowledgeDocumentSaveDTO();
        dto.setSourceType(req.sourceType());
        dto.setSourceRefId(req.sourceRefId());
        dto.setTitle(req.title());
        dto.setContent(req.content());
        dto.setMetadataJson(req.metadataJson());
        return dto;
    }

    static KnowledgeDocumentResponse toResponse(RagKnowledgeDocument e) {
        if (e == null) return null;
        return new KnowledgeDocumentResponse(
                e.getId(), e.getDocCode(), e.getSourceType(), e.getSourceRefId(),
                e.getTitle(), e.getContent(), e.getMetadataJson(), e.getDocStatus(),
                e.getChunkCount(), e.getLastIndexedTime(), e.getCreatedBy(),
                e.getCreatedTime(), e.getUpdatedBy(), e.getUpdatedTime()
        );
    }
}
