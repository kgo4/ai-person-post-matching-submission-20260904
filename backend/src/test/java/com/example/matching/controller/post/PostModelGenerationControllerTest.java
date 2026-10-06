package com.example.matching.controller.post;

import com.example.matching.application.post.PostModelGenerationApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.PostModelGenerationFromJdDTO;
import com.example.matching.dto.post.api.PostModelVersionResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostModelGenerationControllerTest {

    private static PostModelVersionResponse createVersion() {
        return new PostModelVersionResponse(
                1L, 2001L, "v20250101000000", "TEMPLATE", "DRAFT",
                new BigDecimal("0.95"), 5, new BigDecimal("100.00"), "版本说明", null, null, null, null);
    }

    @Test
    void generateFromPrototypeReturnsVersion() {
        PostModelGenerationApiFacade facade = mock(PostModelGenerationApiFacade.class);
        PostModelGenerationController controller = new PostModelGenerationController(facade);

        PostModelVersionResponse version = createVersion();
        when(facade.generateFromPrototype(2001L, 5L, "从原型生成")).thenReturn(version);

        R<PostModelVersionResponse> response = controller.generateFromPrototype(2001L, 5L, "从原型生成");

        assertThat(response.getData()).isSameAs(version);
    }

    @Test
    void generateFromJdReturnsVersion() {
        PostModelGenerationApiFacade facade = mock(PostModelGenerationApiFacade.class);
        PostModelGenerationController controller = new PostModelGenerationController(facade);

        PostModelGenerationFromJdDTO dto = new PostModelGenerationFromJdDTO("负责Java后端开发");
        PostModelVersionResponse version = createVersion();
        when(facade.generateFromJD(2001L, dto.jdText(), "从JD生成")).thenReturn(version);

        R<PostModelVersionResponse> response = controller.generateFromJD(2001L, dto, "从JD生成");

        assertThat(response.getData()).isSameAs(version);
    }

    @Test
    void generateFromCopyReturnsVersion() {
        PostModelGenerationApiFacade facade = mock(PostModelGenerationApiFacade.class);
        PostModelGenerationController controller = new PostModelGenerationController(facade);

        PostModelVersionResponse version = createVersion();
        when(facade.generateFromCopy(1001L, 2001L, "复制生成")).thenReturn(version);

        R<PostModelVersionResponse> response = controller.generateFromCopy(1001L, 2001L, "复制生成");

        assertThat(response.getData()).isSameAs(version);
    }
}
