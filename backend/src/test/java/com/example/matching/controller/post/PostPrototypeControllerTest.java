package com.example.matching.controller.post;

import com.example.matching.application.post.PostPrototypeApiFacade;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.result.R;
import com.example.matching.dto.post.PostPrototypeSaveDTO;
import com.example.matching.dto.post.PostPrototypeVO;
import com.example.matching.dto.post.api.PostPrototypeResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostPrototypeControllerTest {

    private static PostPrototypeResponse createPrototype() {
        return new PostPrototypeResponse(1L, "后端开发工程师", "描述", "互联网", "技术", 1, null);
    }

    @Test
    void pageReturnsPrototypePage() {
        PostPrototypeApiFacade facade = mock(PostPrototypeApiFacade.class);
        PostPrototypeController controller = new PostPrototypeController(facade);

        PostPrototypeResponse prototype = createPrototype();
        PageResponse<PostPrototypeResponse> page = new PageResponse<>(List.of(prototype), 1, 1, 10, 1);
        when(facade.page(1, 10, "后端", "互联网", "技术")).thenReturn(page);

        R<PageResponse<PostPrototypeResponse>> response = controller.page(1, 10, "后端", "互联网", "技术");

        assertThat(response.getData().records()).containsExactly(prototype);
        assertThat(response.getData().total()).isEqualTo(1);
    }

    @Test
    void listEnabledReturnsEnabledPrototypes() {
        PostPrototypeApiFacade facade = mock(PostPrototypeApiFacade.class);
        PostPrototypeController controller = new PostPrototypeController(facade);

        PostPrototypeResponse prototype = createPrototype();
        when(facade.listEnabled()).thenReturn(List.of(prototype));

        R<List<PostPrototypeResponse>> response = controller.listEnabled();

        assertThat(response.getData()).containsExactly(prototype);
    }

    @Test
    void getDetailReturnsPrototypeWithTags() {
        PostPrototypeApiFacade facade = mock(PostPrototypeApiFacade.class);
        PostPrototypeController controller = new PostPrototypeController(facade);

        PostPrototypeVO vo = new PostPrototypeVO();
        vo.setId(1L);
        vo.setPrototypeName("后端开发工程师");
        when(facade.getDetail(1L)).thenReturn(vo);

        R<PostPrototypeVO> response = controller.getDetail(1L);

        assertThat(response.getData()).isSameAs(vo);
    }

    @Test
    void saveInvokesFacadeAndReturnsOk() {
        PostPrototypeApiFacade facade = mock(PostPrototypeApiFacade.class);
        PostPrototypeController controller = new PostPrototypeController(facade);

        PostPrototypeSaveDTO dto = new PostPrototypeSaveDTO();
        dto.setPrototypeName("后端开发工程师");

        R<Void> response = controller.save(dto);

        verify(facade).save(dto);
        assertThat(response.getData()).isNull();
    }

    @Test
    void deleteInvokesFacadeAndReturnsOk() {
        PostPrototypeApiFacade facade = mock(PostPrototypeApiFacade.class);
        PostPrototypeController controller = new PostPrototypeController(facade);

        R<Void> response = controller.delete(1L);

        verify(facade).delete(1L);
        assertThat(response.getData()).isNull();
    }

    @Test
    void recallReturnsSimilarPrototypes() {
        PostPrototypeApiFacade facade = mock(PostPrototypeApiFacade.class);
        PostPrototypeController controller = new PostPrototypeController(facade);

        PostPrototypeVO vo = new PostPrototypeVO();
        vo.setId(1L);
        when(facade.recall("岗位描述", 5)).thenReturn(List.of(vo));

        R<List<PostPrototypeVO>> response = controller.recall("岗位描述", 5);

        assertThat(response.getData()).containsExactly(vo);
    }

    @Test
    void applyToPostInvokesFacadeAndReturnsOk() {
        PostPrototypeApiFacade facade = mock(PostPrototypeApiFacade.class);
        PostPrototypeController controller = new PostPrototypeController(facade);

        R<Void> response = controller.applyToPost(1L, 2001L);

        verify(facade).applyToPost(1L, 2001L);
        assertThat(response.getData()).isNull();
    }
}
