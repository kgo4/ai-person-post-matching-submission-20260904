package com.example.matching.controller.post;

import com.example.matching.application.post.PostModelImportApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.dto.post.PostModelExcelRowDTO;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostModelImportControllerTest {

    @Test
    void parseExcelReturnsParsedRows() throws Exception {
        PostModelImportApiFacade facade = mock(PostModelImportApiFacade.class);
        PostModelImportController controller = new PostModelImportController(facade);

        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        PostModelExcelRowDTO row = new PostModelExcelRowDTO();
        when(facade.parseExcel(any(InputStream.class))).thenReturn(List.of(row));

        R<List<PostModelExcelRowDTO>> response = controller.parseExcel(file);

        assertThat(response.getData()).containsExactly(row);
    }

    @Test
    void parseExcelThrowsIllegalArgumentWhenStreamFails() throws Exception {
        PostModelImportApiFacade facade = mock(PostModelImportApiFacade.class);
        PostModelImportController controller = new PostModelImportController(facade);

        MultipartFile file = mock(MultipartFile.class);
        doThrow(new IOException("boom")).when(file).getInputStream();

        assertThatThrownBy(() -> controller.parseExcel(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to read uploaded Excel file")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void importTemplateBReturnsCountMap() {
        PostModelImportApiFacade facade = mock(PostModelImportApiFacade.class);
        PostModelImportController controller = new PostModelImportController(facade);

        List<PostModelExcelRowDTO> rows = List.of(new PostModelExcelRowDTO());
        Map<Long, Integer> countMap = Map.of(2001L, 3);
        when(facade.batchImportFromTemplateB(rows)).thenReturn(countMap);

        R<Map<Long, Integer>> response = controller.importTemplateB(rows);

        assertThat(response.getData()).isSameAs(countMap);
    }

    @Test
    void importTemplateAReturnsCountMap() {
        PostModelImportApiFacade facade = mock(PostModelImportApiFacade.class);
        PostModelImportController controller = new PostModelImportController(facade);

        List<PostModelExcelRowDTO> rows = List.of(new PostModelExcelRowDTO());
        Map<Long, Integer> countMap = Map.of(2001L, 2);
        when(facade.batchImportFromTemplateA(rows)).thenReturn(countMap);

        R<Map<Long, Integer>> response = controller.importTemplateA(rows);

        assertThat(response.getData()).isSameAs(countMap);
    }

    @Test
    void normalizeWeightsReturnsConfigList() {
        PostModelImportApiFacade facade = mock(PostModelImportApiFacade.class);
        PostModelImportController controller = new PostModelImportController(facade);

        List<PostAbilityModelConfigDTO> configs = List.of(new PostAbilityModelConfigDTO());
        when(facade.normalizeWeights(2001L)).thenReturn(configs);

        R<List<PostAbilityModelConfigDTO>> response = controller.normalizeWeights(2001L);

        assertThat(response.getData()).isSameAs(configs);
    }

    @Test
    void copyPostModelReturnsCopiedCount() {
        PostModelImportApiFacade facade = mock(PostModelImportApiFacade.class);
        PostModelImportController controller = new PostModelImportController(facade);

        when(facade.copyPostModel(1001L, 2001L)).thenReturn(5);

        R<Integer> response = controller.copyPostModel(1001L, 2001L);

        assertThat(response.getData()).isEqualTo(5);
    }
}
