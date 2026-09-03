package com.example.matching.controller.post;

import com.example.matching.application.post.PostModelVersionApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.api.PostModelVersionItemRequest;
import com.example.matching.dto.post.api.PostModelVersionItemResponse;
import com.example.matching.dto.post.api.PostModelVersionResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostModelVersionControllerTest {

    private static PostModelVersionResponse createVersion() {
        return new PostModelVersionResponse(
                1L, 2001L, "v20250101000000", "MANUAL", "DRAFT",
                new BigDecimal("0.95"), 5, new BigDecimal("100.00"), "版本说明", null, null, null, null);
    }

    @Test
    void createDraftReturnsVersion() {
        PostModelVersionApiFacade facade = mock(PostModelVersionApiFacade.class);
        PostModelVersionController controller = new PostModelVersionController(facade);

        PostModelVersionResponse version = createVersion();
        when(facade.createDraft(2001L, "MANUAL", "创建草稿")).thenReturn(version);

        R<PostModelVersionResponse> response = controller.createDraft(2001L, "MANUAL", "创建草稿");

        assertThat(response.getData()).isSameAs(version);
    }

    @Test
    void saveVersionItemsInvokesFacadeAndReturnsOk() {
        PostModelVersionApiFacade facade = mock(PostModelVersionApiFacade.class);
        PostModelVersionController controller = new PostModelVersionController(facade);

        List<PostModelVersionItemRequest> items = List.of(
                new PostModelVersionItemRequest(101L, 3, new BigDecimal("20.00"), 1, 1, "理由"));

        R<Void> response = controller.saveVersionItems(10L, items);

        verify(facade).saveVersionItems(10L, items);
        assertThat(response.getData()).isNull();
    }

    @Test
    void publishVersionInvokesFacadeAndReturnsOk() {
        PostModelVersionApiFacade facade = mock(PostModelVersionApiFacade.class);
        PostModelVersionController controller = new PostModelVersionController(facade);

        R<Void> response = controller.publishVersion(10L);

        verify(facade).publishVersion(10L);
        assertThat(response.getData()).isNull();
    }

    @Test
    void rollbackToVersionInvokesFacadeAndReturnsOk() {
        PostModelVersionApiFacade facade = mock(PostModelVersionApiFacade.class);
        PostModelVersionController controller = new PostModelVersionController(facade);

        R<Void> response = controller.rollbackToVersion(10L);

        verify(facade).rollbackToVersion(10L);
        assertThat(response.getData()).isNull();
    }

    @Test
    void listVersionsReturnsVersionList() {
        PostModelVersionApiFacade facade = mock(PostModelVersionApiFacade.class);
        PostModelVersionController controller = new PostModelVersionController(facade);

        PostModelVersionResponse version = createVersion();
        when(facade.listVersions(2001L)).thenReturn(List.of(version));

        R<List<PostModelVersionResponse>> response = controller.listVersions(2001L);

        assertThat(response.getData()).containsExactly(version);
    }

    @Test
    void getVersionDetailReturnsVersion() {
        PostModelVersionApiFacade facade = mock(PostModelVersionApiFacade.class);
        PostModelVersionController controller = new PostModelVersionController(facade);

        PostModelVersionResponse version = createVersion();
        when(facade.getVersionDetail(10L)).thenReturn(version);

        R<PostModelVersionResponse> response = controller.getVersionDetail(10L);

        assertThat(response.getData()).isSameAs(version);
    }

    @Test
    void getVersionItemsReturnsItemList() {
        PostModelVersionApiFacade facade = mock(PostModelVersionApiFacade.class);
        PostModelVersionController controller = new PostModelVersionController(facade);

        PostModelVersionItemResponse item = new PostModelVersionItemResponse(
                1L, 10L, 101L, 3, new BigDecimal("20.00"), 1, 1, "理由", null);
        when(facade.getVersionItems(10L)).thenReturn(List.of(item));

        R<List<PostModelVersionItemResponse>> response = controller.getVersionItems(10L);

        assertThat(response.getData()).containsExactly(item);
    }

    @Test
    void deleteDraftInvokesFacadeAndReturnsOk() {
        PostModelVersionApiFacade facade = mock(PostModelVersionApiFacade.class);
        PostModelVersionController controller = new PostModelVersionController(facade);

        R<Void> response = controller.deleteDraft(10L);

        verify(facade).deleteDraft(10L);
        assertThat(response.getData()).isNull();
    }
}
