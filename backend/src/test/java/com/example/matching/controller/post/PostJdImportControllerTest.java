package com.example.matching.controller.post;

import com.example.matching.application.post.PostJdImportApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.post.JdAnalyzeRequestDTO;
import com.example.matching.dto.post.JdAnalyzeResponseDTO;
import com.example.matching.dto.post.JdConfirmRequestDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostJdImportControllerTest {

    @Test
    void analyzeReturnsJdAnalysisResult() {
        PostJdImportApiFacade facade = mock(PostJdImportApiFacade.class);
        PostJdImportController controller = new PostJdImportController(facade);

        JdAnalyzeRequestDTO request = new JdAnalyzeRequestDTO();
        request.setPostId(2001L);
        request.setJdText("负责Java后端开发，要求熟练掌握Spring Boot");

        JdAnalyzeResponseDTO result = new JdAnalyzeResponseDTO();
        when(facade.analyzeJd(2001L, request.getJdText())).thenReturn(result);

        R<JdAnalyzeResponseDTO> response = controller.analyze(request);

        assertThat(response.getData()).isSameAs(result);
    }

    @Test
    void confirmAppliesAnalysisResultAndReturnsOk() {
        PostJdImportApiFacade facade = mock(PostJdImportApiFacade.class);
        PostJdImportController controller = new PostJdImportController(facade);

        JdConfirmRequestDTO request = new JdConfirmRequestDTO();
        request.setPostId(2001L);
        List<JdAbilityItemDTO> items = List.of(new JdAbilityItemDTO());
        request.setItems(items);

        R<Void> response = controller.confirm(request);

        verify(facade).applyAnalysisResult(2001L, items);
        assertThat(response.getData()).isNull();
    }
}
