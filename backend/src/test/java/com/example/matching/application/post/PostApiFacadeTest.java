package com.example.matching.application.post;

import com.example.matching.dto.post.api.PostCreateRequest;
import com.example.matching.entity.post.PostPost;
import com.example.matching.service.common.BusinessCodeGenerator;
import com.example.matching.service.post.PostPostService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostApiFacadeTest {

    @Test
    void creates_post_with_generated_code_when_request_code_is_blank() {
        PostPostService postService = mock(PostPostService.class);
        PostApiFacade facade = new PostApiFacade(postService, new BusinessCodeGenerator());

        facade.create(new PostCreateRequest("", "Platform Engineer", "Builds services", 1, "P6"));

        ArgumentCaptor<PostPost> captor = ArgumentCaptor.forClass(PostPost.class);
        verify(postService).save(captor.capture());
        assertThat(captor.getValue().getPostCode()).startsWith("POST_");
    }

    @Test
    void preserves_supplied_post_code_for_external_creation() {
        PostPostService postService = mock(PostPostService.class);
        PostApiFacade facade = new PostApiFacade(postService, new BusinessCodeGenerator());

        facade.create(new PostCreateRequest("EXT-POST-001", "Platform Engineer", "Builds services", 1, "P6"));

        ArgumentCaptor<PostPost> captor = ArgumentCaptor.forClass(PostPost.class);
        verify(postService).save(captor.capture());
        assertThat(captor.getValue().getPostCode()).isEqualTo("EXT-POST-001");
    }
}
