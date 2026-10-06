package com.example.matching.controller.post;

import com.example.matching.application.post.PostModelUnmatchedAbilityApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.api.PostModelUnmatchedBindRequest;
import com.example.matching.dto.post.api.UnmatchedAbilityDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostModelUnmatchedAbilityControllerTest {

    @Test
    void listReturnsUnmatchedAbilities() {
        PostModelUnmatchedAbilityApiFacade facade = mock(PostModelUnmatchedAbilityApiFacade.class);
        PostModelUnmatchedAbilityController controller = new PostModelUnmatchedAbilityController(facade);

        UnmatchedAbilityDTO ability = UnmatchedAbilityDTO.builder()
                .id(1L)
                .versionId(10L)
                .abilityName("AI大模型")
                .status("PENDING")
                .build();
        when(facade.listByVersionId(10L)).thenReturn(List.of(ability));

        R<List<UnmatchedAbilityDTO>> response = controller.list(10L);

        assertThat(response.getData()).containsExactly(ability);
    }

    @Test
    void bindInvokesFacadeAndReturnsOk() {
        PostModelUnmatchedAbilityApiFacade facade = mock(PostModelUnmatchedAbilityApiFacade.class);
        PostModelUnmatchedAbilityController controller = new PostModelUnmatchedAbilityController(facade);

        PostModelUnmatchedBindRequest request = new PostModelUnmatchedBindRequest();
        request.setTagId(101L);

        R<Void> response = controller.bind(10L, 1L, request);

        verify(facade).bind(10L, 1L, request);
        assertThat(response.getData()).isNull();
    }

    @Test
    void ignoreInvokesFacadeAndReturnsOk() {
        PostModelUnmatchedAbilityApiFacade facade = mock(PostModelUnmatchedAbilityApiFacade.class);
        PostModelUnmatchedAbilityController controller = new PostModelUnmatchedAbilityController(facade);

        R<Void> response = controller.ignore(10L, 1L);

        verify(facade).ignore(10L, 1L);
        assertThat(response.getData()).isNull();
    }
}
