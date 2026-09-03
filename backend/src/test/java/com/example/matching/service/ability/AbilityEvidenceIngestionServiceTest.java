package com.example.matching.service.ability;

import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.port.contest.ContestQueryPort;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.tag.TagQueryPort.*;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.port.talent.TalentQueryPort.*;
import com.example.matching.service.ability.impl.AbilityEvidenceIngestionServiceImpl;
import com.example.matching.service.rag.KnowledgeDocumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbilityEvidenceIngestionServiceTest {

    @Mock private TalentQueryPort talentQueryPort;
    @Mock private PostQueryPort postQueryPort;
    @Mock private TagQueryPort tagQueryPort;
    @Mock private KnowledgeDocumentService knowledgeDocumentService;
    @Mock private ContestQueryPort contestQueryPort;
    @Mock private AbilityCrossValidationService crossValidationService;
    @Mock private DynamicCredibilityService dynamicCredibilityService;

    @InjectMocks
    private AbilityEvidenceIngestionServiceImpl ingestionService;

    @Test
    @DisplayName("employee ability ingestion creates evidence and indexed RAG document")
    void ingestEmployeeAbility_createsEvidenceAndIndexedDocument() {
        lenient().when(dynamicCredibilityService.getWeight(any())).thenReturn(0.5);
        when(crossValidationService.validateAbility(1L, 2L, 4, "AI_TEST", 11L)).thenReturn(
                new AbilityCrossValidationService.ValidationResult(100, "CONSISTENT", 0, "", "ACCEPT"));

        when(talentQueryPort.getEmpAbilityById(11L)).thenReturn(
                new EmployeeAbilityDTO(11L, 1L, 2L, 4, "AI_TEST", new BigDecimal("0.9"), null, null));
        when(talentQueryPort.getEmployeeById(1L)).thenReturn(
                new EmployeeDTO(1L, "张三", "EMP001", null, null, null, null, null));
        when(tagQueryPort.getTagById(2L)).thenReturn(
                new TagDTO(2L, "Java", "JAVA", "TECHNICAL", null, null, null, null, null, null, null, null));
        when(contestQueryPort.evidenceExists("EMP_ABILITY", 11L, "EMP_ABILITY", 11L)).thenReturn(false);
        RagKnowledgeDocument savedDoc = new RagKnowledgeDocument();
        savedDoc.setId(101L);
        doReturn(savedDoc).when(knowledgeDocumentService).saveDocument(any());

        ingestionService.ingestEmployeeAbility(11L, "EMP_ABILITY");

        var commandCaptor = org.mockito.ArgumentCaptor.forClass(ContestQueryPort.EvidenceWriteCommand.class);
        verify(contestQueryPort).saveEvidence(commandCaptor.capture());
        assertEquals("EMP_ABILITY", commandCaptor.getValue().sourceType());
        assertEquals(11L, commandCaptor.getValue().sourceRefId());
    }
}
