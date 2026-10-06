package com.example.matching.controller.post;

import com.example.matching.application.post.HardConditionRuleApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.PostHardConditionRuleDTO;
import com.example.matching.dto.post.api.HardConditionRuleResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostHardConditionRuleControllerTest {

    @Test
    void listByPostIdReturnsRuleList() {
        HardConditionRuleApiFacade facade = mock(HardConditionRuleApiFacade.class);
        PostHardConditionRuleController controller = new PostHardConditionRuleController(facade);

        HardConditionRuleResponse rule = new HardConditionRuleResponse(
                1L, 2001L, "education", "学历", "EDUCATION", "GREATER_EQUAL", "本科",
                null, 1, 1, "备注", null, null);
        when(facade.listByPostId(2001L)).thenReturn(List.of(rule));

        R<List<HardConditionRuleResponse>> response = controller.listByPostId(2001L);

        assertThat(response.getData()).containsExactly(rule);
    }

    @Test
    void saveInvokesFacadeAndReturnsOk() {
        HardConditionRuleApiFacade facade = mock(HardConditionRuleApiFacade.class);
        PostHardConditionRuleController controller = new PostHardConditionRuleController(facade);

        PostHardConditionRuleDTO dto = new PostHardConditionRuleDTO();
        dto.setPostId(2001L);

        R<Void> response = controller.save(dto);

        verify(facade).save(dto);
        assertThat(response.getData()).isNull();
    }

    @Test
    void updateInvokesFacadeAndReturnsOk() {
        HardConditionRuleApiFacade facade = mock(HardConditionRuleApiFacade.class);
        PostHardConditionRuleController controller = new PostHardConditionRuleController(facade);

        PostHardConditionRuleDTO dto = new PostHardConditionRuleDTO();
        R<Void> response = controller.update(10L, dto);

        verify(facade).update(10L, dto);
        assertThat(response.getData()).isNull();
    }

    @Test
    void batchConfigInvokesFacadeAndReturnsOk() {
        HardConditionRuleApiFacade facade = mock(HardConditionRuleApiFacade.class);
        PostHardConditionRuleController controller = new PostHardConditionRuleController(facade);

        List<PostHardConditionRuleDTO> list = List.of(new PostHardConditionRuleDTO());
        R<Void> response = controller.batchConfig(2001L, list);

        verify(facade).batchConfig(2001L, list);
        assertThat(response.getData()).isNull();
    }

    @Test
    void deleteInvokesFacadeAndReturnsOk() {
        HardConditionRuleApiFacade facade = mock(HardConditionRuleApiFacade.class);
        PostHardConditionRuleController controller = new PostHardConditionRuleController(facade);

        R<Void> response = controller.delete(10L);

        verify(facade).delete(10L);
        assertThat(response.getData()).isNull();
    }
}
