package com.example.matching.controller.post;

import com.example.matching.application.post.PostApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.api.PostCreateRequest;
import com.example.matching.dto.post.api.PostResponse;
import com.example.matching.dto.post.api.PostUpdateRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostPostControllerTest {

    private static PostResponse createPost() {
        return new PostResponse(1L, "POST_JAVA", "Java开发工程师", "职责描述", 1, "P5", 1001L, null, null);
    }

    @Test
    void pageReturnsPostPage() {
        PostApiFacade facade = mock(PostApiFacade.class);
        PostPostController controller = new PostPostController(facade);

        PostResponse post = createPost();
        PageResponse<PostResponse> page = new PageResponse<>(List.of(post), 1, 1, 10, 1);
        when(facade.page(1L, 10L, "java", 1)).thenReturn(page);

        R<PageResponse<PostResponse>> response = controller.page(1L, 10L, "java", 1);

        assertThat(response.getData().records()).containsExactly(post);
        assertThat(response.getData().total()).isEqualTo(1);
    }

    @Test
    void listEnabledReturnsEnabledPosts() {
        PostApiFacade facade = mock(PostApiFacade.class);
        PostPostController controller = new PostPostController(facade);

        PostResponse post = createPost();
        when(facade.listEnabled()).thenReturn(List.of(post));

        R<List<PostResponse>> response = controller.listEnabled();

        assertThat(response.getData()).containsExactly(post);
    }

    @Test
    void getByIdReturnsPost() {
        PostApiFacade facade = mock(PostApiFacade.class);
        PostPostController controller = new PostPostController(facade);

        PostResponse post = createPost();
        when(facade.get(1L)).thenReturn(post);

        R<PostResponse> response = controller.getById(1L);

        assertThat(response.getData()).isSameAs(post);
    }

    @Test
    void saveInvokesFacadeAndReturnsOk() {
        PostApiFacade facade = mock(PostApiFacade.class);
        PostPostController controller = new PostPostController(facade);

        PostCreateRequest request = new PostCreateRequest("POST_JAVA", "Java开发工程师", "职责描述", 1, "P5");

        R<Void> response = controller.save(request);

        verify(facade).create(request);
        assertThat(response.getData()).isNull();
    }

    @Test
    void updateInvokesFacadeAndReturnsOk() {
        PostApiFacade facade = mock(PostApiFacade.class);
        PostPostController controller = new PostPostController(facade);

        PostUpdateRequest request = new PostUpdateRequest("POST_JAVA", "Java开发工程师", "职责描述", 1, "P5");

        R<Void> response = controller.update(1L, request);

        verify(facade).update(1L, request);
        assertThat(response.getData()).isNull();
    }

    @Test
    void deleteInvokesFacadeAndReturnsOk() {
        PostApiFacade facade = mock(PostApiFacade.class);
        PostPostController controller = new PostPostController(facade);

        R<Void> response = controller.delete(1L);

        verify(facade).delete(1L);
        assertThat(response.getData()).isNull();
    }
}
