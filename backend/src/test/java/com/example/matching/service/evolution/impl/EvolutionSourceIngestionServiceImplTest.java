package com.example.matching.service.evolution.impl;

import com.example.matching.dto.evolution.EvolutionSourceUploadDTO;
import com.example.matching.dto.rag.KnowledgeDocumentSaveDTO;
import com.example.matching.entity.rag.KnowledgeSourceDocument;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.mapper.rag.KnowledgeSourceDocumentMapper;
import com.example.matching.service.rag.KnowledgeDocumentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvolutionSourceIngestionServiceImplTest {

    @Mock private KnowledgeSourceDocumentMapper sourceDocumentMapper;
    @Mock private KnowledgeDocumentService knowledgeDocumentService;

    private Path uploadedFile;

    @AfterEach
    void cleanUpUploadedFile() throws Exception {
        if (uploadedFile != null) {
            Files.deleteIfExists(uploadedFile);
        }
    }

    @Test
    void uploadIndustryWhitepaper_createsAndLinksRagDocument() {
        EvolutionSourceIngestionServiceImpl service =
                new EvolutionSourceIngestionServiceImpl(sourceDocumentMapper, knowledgeDocumentService);
        EvolutionSourceUploadDTO request = new EvolutionSourceUploadDTO();
        request.setTitle("2026 AI 行业白皮书");
        request.setEvolutionEnabled(true);

        doAnswer(invocation -> {
            KnowledgeSourceDocument document = invocation.getArgument(0);
            document.setId(91L);
            return 1;
        }).when(sourceDocumentMapper).insert(any(KnowledgeSourceDocument.class));
        RagKnowledgeDocument ragDocument = new RagKnowledgeDocument();
        ragDocument.setId(301L);
        when(knowledgeDocumentService.saveDocument(any(KnowledgeDocumentSaveDTO.class))).thenReturn(ragDocument);

        KnowledgeSourceDocument result = service.uploadIndustryWhitepaper(
                "whitepaper.txt", "AI 人才需求增长".getBytes(StandardCharsets.UTF_8), request, 7L);

        ArgumentCaptor<KnowledgeDocumentSaveDTO> ragCaptor = ArgumentCaptor.forClass(KnowledgeDocumentSaveDTO.class);
        verify(knowledgeDocumentService).saveDocument(ragCaptor.capture());
        assertThat(ragCaptor.getValue().getSourceType()).isEqualTo("INDUSTRY_WHITEPAPER");
        assertThat(ragCaptor.getValue().getSourceRefId()).isEqualTo(91L);
        assertThat(ragCaptor.getValue().getContent()).isEqualTo("AI 人才需求增长");
        assertThat(result.getRagDocumentId()).isEqualTo(301L);
        assertThat(result.getStoragePath()).isNotBlank();
        uploadedFile = Path.of(result.getStoragePath());
        verify(sourceDocumentMapper).updateById(result);
    }
}
