package com.example.matching.controller.post;

import com.example.matching.application.post.PostModelTemplateApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.PostTemplateSaveDTO;
import com.example.matching.dto.post.api.PostModelTemplateResponse;
import com.example.matching.dto.post.api.TemplateAbilityItemRequest;
import com.example.matching.dto.post.api.TemplateAbilityResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostModelTemplateControllerTest {

    private static PostModelTemplateResponse createTemplate() {
        return new PostModelTemplateResponse(
                1L, "TMP_JAVA", "Java开发模板", "TECH", "P1-P5", "模板描述", 1, null, null);
    }

    @Test
    void pageReturnsTemplatePage() {
        PostModelTemplateApiFacade facade = mock(PostModelTemplateApiFacade.class);
        PostModelTemplateController controller = new PostModelTemplateController(facade);

        PostModelTemplateResponse template = createTemplate();
        PageResponse<PostModelTemplateResponse> page = new PageResponse<>(List.of(template), 1, 1, 10, 1);
        when(facade.page(1L, 10L, "java")).thenReturn(page);

        R<PageResponse<PostModelTemplateResponse>> response = controller.page(1L, 10L, "java");

        assertThat(response.getData().records()).containsExactly(template);
        assertThat(response.getData().total()).isEqualTo(1);
    }

    @Test
    void getByIdReturnsTemplate() {
        PostModelTemplateApiFacade facade = mock(PostModelTemplateApiFacade.class);
        PostModelTemplateController controller = new PostModelTemplateController(facade);

        PostModelTemplateResponse template = createTemplate();
        when(facade.get(5L)).thenReturn(template);

        R<PostModelTemplateResponse> response = controller.getById(5L);

        assertThat(response.getData()).isSameAs(template);
    }

    @Test
    void saveInvokesFacadeAndReturnsOk() {
        PostModelTemplateApiFacade facade = mock(PostModelTemplateApiFacade.class);
        PostModelTemplateController controller = new PostModelTemplateController(facade);

        PostTemplateSaveDTO dto = new PostTemplateSaveDTO();
        dto.setTemplateName("Java开发模板");

        R<Void> response = controller.save(dto);

        verify(facade).save(dto);
        assertThat(response.getData()).isNull();
    }

    @Test
    void updateInvokesFacadeAndReturnsOk() {
        PostModelTemplateApiFacade facade = mock(PostModelTemplateApiFacade.class);
        PostModelTemplateController controller = new PostModelTemplateController(facade);

        PostTemplateSaveDTO dto = new PostTemplateSaveDTO();
        R<Void> response = controller.update(5L, dto);

        verify(facade).update(5L, dto);
        assertThat(response.getData()).isNull();
    }

    @Test
    void deleteInvokesFacadeAndReturnsOk() {
        PostModelTemplateApiFacade facade = mock(PostModelTemplateApiFacade.class);
        PostModelTemplateController controller = new PostModelTemplateController(facade);

        R<Void> response = controller.delete(5L);

        verify(facade).delete(5L);
        assertThat(response.getData()).isNull();
    }

    @Test
    void getAbilityModelsReturnsTemplateAbilities() {
        PostModelTemplateApiFacade facade = mock(PostModelTemplateApiFacade.class);
        PostModelTemplateController controller = new PostModelTemplateController(facade);

        TemplateAbilityResponse ability = new TemplateAbilityResponse(
                1L, 5L, 101L, 3, new BigDecimal("20.00"), 1, 1, "备注", null);
        when(facade.getAbilityModels(5L)).thenReturn(List.of(ability));

        R<List<TemplateAbilityResponse>> response = controller.getAbilityModels(5L);

        assertThat(response.getData()).containsExactly(ability);
    }

    @Test
    void saveAbilityModelsInvokesFacadeAndReturnsOk() {
        PostModelTemplateApiFacade facade = mock(PostModelTemplateApiFacade.class);
        PostModelTemplateController controller = new PostModelTemplateController(facade);

        List<TemplateAbilityItemRequest> items = List.of(
                new TemplateAbilityItemRequest(101L, 3, new BigDecimal("20.00"), 1, 1, "备注"));

        R<Void> response = controller.saveAbilityModels(5L, items);

        verify(facade).saveAbilityModels(5L, items);
        assertThat(response.getData()).isNull();
    }

    @Test
    void applyTemplateToPostInvokesFacadeAndReturnsOk() {
        PostModelTemplateApiFacade facade = mock(PostModelTemplateApiFacade.class);
        PostModelTemplateController controller = new PostModelTemplateController(facade);

        R<Void> response = controller.applyTemplateToPost(5L, 2001L);

        verify(facade).applyTemplateToPost(5L, 2001L);
        assertThat(response.getData()).isNull();
    }
}
