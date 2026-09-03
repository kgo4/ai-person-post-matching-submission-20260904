package com.example.matching.controller.rag;

import com.example.matching.application.rag.RagKnowledgeApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.rag.KnowledgeChunkResultDTO;
import com.example.matching.dto.rag.api.KnowledgeDocumentCreateRequest;
import com.example.matching.dto.rag.api.KnowledgeDocumentResponse;
import com.example.matching.dto.rag.api.KnowledgeDocumentUpdateRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagKnowledgeControllerTest {

    private static KnowledgeDocumentResponse createDoc(Long id, String title) {
        return new KnowledgeDocumentResponse(
                id, "DOC_001", "POST", 100L, title, "内容", null,
                "INDEXED", 5, null, 1L, null, 1L, null);
    }

    @Test
    void createDocumentReturnsDocument() {
        RagKnowledgeApiFacade facade = mock(RagKnowledgeApiFacade.class);
        RagKnowledgeController controller = new RagKnowledgeController(facade);

        KnowledgeDocumentCreateRequest request =
                new KnowledgeDocumentCreateRequest("POST", 100L, "岗位JD", "内容", null);
        KnowledgeDocumentResponse doc = createDoc(1L, "岗位JD");
        when(facade.createDocument(request)).thenReturn(doc);

        R<KnowledgeDocumentResponse> response = controller.createDocument(request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(doc);
        verify(facade).createDocument(request);
    }

    @Test
    void updateDocumentReturnsDocument() {
        RagKnowledgeApiFacade facade = mock(RagKnowledgeApiFacade.class);
        RagKnowledgeController controller = new RagKnowledgeController(facade);

        KnowledgeDocumentUpdateRequest request =
                new KnowledgeDocumentUpdateRequest("POST", 100L, "岗位JD-更新", "新内容", null, "INDEXED");
        KnowledgeDocumentResponse doc = createDoc(1L, "岗位JD-更新");
        when(facade.updateDocument(1L, request)).thenReturn(doc);

        R<KnowledgeDocumentResponse> response = controller.updateDocument(1L, request);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(doc);
        verify(facade).updateDocument(1L, request);
    }

    @Test
    void pageDocumentsReturnsPage() {
        RagKnowledgeApiFacade facade = mock(RagKnowledgeApiFacade.class);
        RagKnowledgeController controller = new RagKnowledgeController(facade);

        KnowledgeDocumentResponse doc = createDoc(2L, "员工画像");
        PageResponse<KnowledgeDocumentResponse> page =
                new PageResponse<>(List.of(doc), 1, 1, 10, 1);
        when(facade.pageDocuments(1, 10, "EMPLOYEE", "INDEXED", "画像")).thenReturn(page);

        R<PageResponse<KnowledgeDocumentResponse>> response =
                controller.pageDocuments(1, 10, "EMPLOYEE", "INDEXED", "画像");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData().records()).containsExactly(doc);
        assertThat(response.getData().total()).isEqualTo(1);
    }

    @Test
    void getDocumentReturnsDocument() {
        RagKnowledgeApiFacade facade = mock(RagKnowledgeApiFacade.class);
        RagKnowledgeController controller = new RagKnowledgeController(facade);

        KnowledgeDocumentResponse doc = createDoc(3L, "能力标签说明");
        when(facade.getDocument(3L)).thenReturn(doc);

        R<KnowledgeDocumentResponse> response = controller.getDocument(3L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).isEqualTo(doc);
    }

    @Test
    void indexDocumentReturnsChunkCount() {
        RagKnowledgeApiFacade facade = mock(RagKnowledgeApiFacade.class);
        RagKnowledgeController controller = new RagKnowledgeController(facade);

        Map<String, Object> indexResult = Map.of("documentId", 1L, "chunkCount", 5);
        when(facade.indexDocument(1L)).thenReturn(indexResult);

        R<Map<String, Object>> response = controller.indexDocument(1L);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData())
                .containsEntry("documentId", 1L)
                .containsEntry("chunkCount", 5);
    }

    @Test
    void indexDocumentsReturnsBatchResult() {
        RagKnowledgeApiFacade facade = mock(RagKnowledgeApiFacade.class);
        RagKnowledgeController controller = new RagKnowledgeController(facade);

        Map<String, Object> batchResult = Map.of("indexed", 3, "skipped", 2);
        when(facade.indexDocuments("POST", true, 100)).thenReturn(batchResult);

        R<Map<String, Object>> response = controller.indexDocuments("POST", true, 100);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("indexed", 3).containsEntry("skipped", 2);
    }

    @Test
    void backfillReturnsCreatedCount() {
        RagKnowledgeApiFacade facade = mock(RagKnowledgeApiFacade.class);
        RagKnowledgeController controller = new RagKnowledgeController(facade);

        Map<String, Object> backfillResult = Map.of("sourceType", "POST", "created", 8);
        when(facade.backfill("POST", 100)).thenReturn(backfillResult);

        R<Map<String, Object>> response = controller.backfill("POST", 100);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsEntry("sourceType", "POST").containsEntry("created", 8);
    }

    @Test
    void searchChunksReturnsHits() {
        RagKnowledgeApiFacade facade = mock(RagKnowledgeApiFacade.class);
        RagKnowledgeController controller = new RagKnowledgeController(facade);

        KnowledgeChunkResultDTO chunk = new KnowledgeChunkResultDTO();
        chunk.setChunkId(10L);
        chunk.setDocumentId(1L);
        chunk.setDocumentTitle("岗位JD");
        chunk.setSourceType("POST");
        chunk.setChunkText("要求熟练掌握Java");
        chunk.setScore(0.92f);
        chunk.setChunkIndex(1);

        when(facade.searchChunks("Java", "JD_ABILITY_EXTRACT", 5, List.of("POST", "EMPLOYEE")))
                .thenReturn(List.of(chunk));

        R<List<KnowledgeChunkResultDTO>> response =
                controller.searchChunks("Java", "JD_ABILITY_EXTRACT", 5, List.of("POST", "EMPLOYEE"));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsExactly(chunk);
    }
}
