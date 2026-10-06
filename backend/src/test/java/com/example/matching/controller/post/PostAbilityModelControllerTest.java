package com.example.matching.controller.post;

import com.example.matching.application.post.PostAbilityModelApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.dto.post.api.PostAbilityModelResponse;
import com.example.matching.vo.post.PostAbilityModelVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostAbilityModelControllerTest {

    @Test
    void getModelReturnsAbilityModelView() {
        PostAbilityModelApiFacade facade = mock(PostAbilityModelApiFacade.class);
        PostAbilityModelController controller = new PostAbilityModelController(facade);

        PostAbilityModelVO model = new PostAbilityModelVO();
        model.setPostId(2001L);
        model.setPostName("高级Java开发工程师");
        when(facade.getModel(2001L)).thenReturn(model);

        R<PostAbilityModelVO> response = controller.getModel(2001L);

        assertThat(response.getData()).isSameAs(model);
    }

    @Test
    void listByPostIdReturnsAbilityRequirementList() {
        PostAbilityModelApiFacade facade = mock(PostAbilityModelApiFacade.class);
        PostAbilityModelController controller = new PostAbilityModelController(facade);

        PostAbilityModelResponse item = new PostAbilityModelResponse(
                1L, 2001L, 101L, "Java", 3, new BigDecimal("25.00"), 1, 1, "v20250101000000", "备注", null, null);
        when(facade.listByPostId(2001L)).thenReturn(List.of(item));

        R<List<PostAbilityModelResponse>> response = controller.listByPostId(2001L);

        assertThat(response.getData()).containsExactly(item);
    }

    @Test
    void saveInvokesFacadeAndReturnsOk() {
        PostAbilityModelApiFacade facade = mock(PostAbilityModelApiFacade.class);
        PostAbilityModelController controller = new PostAbilityModelController(facade);

        PostAbilityModelConfigDTO dto = new PostAbilityModelConfigDTO();
        dto.setPostId(2001L);
        dto.setTagId(101L);

        R<Void> response = controller.save(dto);

        verify(facade).save(dto);
        assertThat(response.getData()).isNull();
    }

    @Test
    void updateInvokesFacadeAndReturnsOk() {
        PostAbilityModelApiFacade facade = mock(PostAbilityModelApiFacade.class);
        PostAbilityModelController controller = new PostAbilityModelController(facade);

        PostAbilityModelConfigDTO dto = new PostAbilityModelConfigDTO();
        dto.setId(10L);
        dto.setPostId(2001L);

        R<Void> response = controller.update(10L, dto);

        verify(facade).update(10L, dto);
        assertThat(response.getData()).isNull();
    }

    @Test
    void batchConfigInvokesFacadeAndReturnsOk() {
        PostAbilityModelApiFacade facade = mock(PostAbilityModelApiFacade.class);
        PostAbilityModelController controller = new PostAbilityModelController(facade);

        PostAbilityModelConfigDTO dto = new PostAbilityModelConfigDTO();
        dto.setPostId(2001L);
        List<PostAbilityModelConfigDTO> list = List.of(dto);

        R<Void> response = controller.batchConfig(list);

        verify(facade).batchConfig(list);
        assertThat(response.getData()).isNull();
    }

    @Test
    void deleteInvokesFacadeAndReturnsOk() {
        PostAbilityModelApiFacade facade = mock(PostAbilityModelApiFacade.class);
        PostAbilityModelController controller = new PostAbilityModelController(facade);

        R<Void> response = controller.delete(10L);

        verify(facade).delete(10L);
        assertThat(response.getData()).isNull();
    }
}
