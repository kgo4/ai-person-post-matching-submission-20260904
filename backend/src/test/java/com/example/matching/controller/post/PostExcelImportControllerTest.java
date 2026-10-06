package com.example.matching.controller.post;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.application.post.PostExcelApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.PostImportBatchVO;
import com.example.matching.dto.post.PostImportConfirmDTO;
import com.example.matching.dto.post.PostImportPreviewDTO;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostExcelImportControllerTest {

    @Test
    void uploadAndAnalyzeReturnsPreview() throws Exception {
        PostExcelApiFacade facade = mock(PostExcelApiFacade.class);
        PostExcelImportController controller = new PostExcelImportController(facade);

        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("posts.xlsx");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        PostImportPreviewDTO preview = new PostImportPreviewDTO();
        when(facade.uploadAndAnalyze(eq("posts.xlsx"), any(InputStream.class))).thenReturn(preview);

        R<PostImportPreviewDTO> response = controller.uploadAndAnalyze(file);

        assertThat(response.getData()).isSameAs(preview);
    }

    @Test
    void uploadAndAnalyzeThrowsIllegalArgumentWhenStreamFails() throws Exception {
        PostExcelApiFacade facade = mock(PostExcelApiFacade.class);
        PostExcelImportController controller = new PostExcelImportController(facade);

        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("posts.xlsx");
        doThrow(new IOException("boom")).when(file).getInputStream();

        assertThatThrownBy(() -> controller.uploadAndAnalyze(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to read uploaded Excel file")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void analyzeBatchInvokesFacadeAndReturnsOk() {
        PostExcelApiFacade facade = mock(PostExcelApiFacade.class);
        PostExcelImportController controller = new PostExcelImportController(facade);

        R<Void> response = controller.analyzeBatch(10L);

        verify(facade).analyzeBatch(10L);
        assertThat(response.getData()).isNull();
    }

    @Test
    void getPreviewReturnsImportPreview() {
        PostExcelApiFacade facade = mock(PostExcelApiFacade.class);
        PostExcelImportController controller = new PostExcelImportController(facade);

        PostImportPreviewDTO preview = new PostImportPreviewDTO();
        when(facade.getPreview(10L)).thenReturn(preview);

        R<PostImportPreviewDTO> response = controller.getPreview(10L);

        assertThat(response.getData()).isSameAs(preview);
    }

    @Test
    void confirmAndImportInvokesFacadeAndReturnsOk() {
        PostExcelApiFacade facade = mock(PostExcelApiFacade.class);
        PostExcelImportController controller = new PostExcelImportController(facade);

        PostImportConfirmDTO confirmDTO = new PostImportConfirmDTO();
        R<Void> response = controller.confirmAndImport(confirmDTO);

        verify(facade).confirmAndImport(confirmDTO);
        assertThat(response.getData()).isNull();
    }

    @Test
    void includeCompletedBatchInvokesFacade() {
        PostExcelApiFacade facade = mock(PostExcelApiFacade.class);
        PostExcelImportController controller = new PostExcelImportController(facade);
        when(facade.includeBatchInMarketDiscovery(10L)).thenReturn(3);

        R<Integer> response = controller.includeBatchInMarketDiscovery(10L);

        assertThat(response.getData()).isEqualTo(3);
        verify(facade).includeBatchInMarketDiscovery(10L);
    }

    @Test
    void cancelBatchInvokesFacadeAndReturnsOk() {
        PostExcelApiFacade facade = mock(PostExcelApiFacade.class);
        PostExcelImportController controller = new PostExcelImportController(facade);

        R<Void> response = controller.cancelBatch(10L);

        verify(facade).cancelBatch(10L);
        assertThat(response.getData()).isNull();
    }

    @Test
    void pageBatchesReturnsBatchPage() {
        PostExcelApiFacade facade = mock(PostExcelApiFacade.class);
        PostExcelImportController controller = new PostExcelImportController(facade);

        Page<PostImportBatchVO> page = new Page<>();
        when(facade.pageBatches(1L, 10L, 1)).thenReturn(page);

        R<Page<PostImportBatchVO>> response = controller.pageBatches(1L, 10L, 1);

        assertThat(response.getData()).isSameAs(page);
    }

    @Test
    void retryBatchInvokesFacadeAndReturnsOk() {
        PostExcelApiFacade facade = mock(PostExcelApiFacade.class);
        PostExcelImportController controller = new PostExcelImportController(facade);

        R<Void> response = controller.retryBatch(10L);

        verify(facade).retryBatch(10L);
        assertThat(response.getData()).isNull();
    }
}
