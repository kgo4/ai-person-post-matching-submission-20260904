package com.example.matching.controller.post;

import com.example.matching.application.post.PostPanoramaApiFacade;
import com.example.matching.common.result.R;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostPanoramaControllerTest {

    @Test
    void overviewReturnsPanoramaMap() {
        PostPanoramaApiFacade facade = mock(PostPanoramaApiFacade.class);
        PostPanoramaController controller = new PostPanoramaController(facade);

        Map<String, Object> overview = Map.of("postCount", 12L);
        when(facade.getOverview("P5", "技术", "java")).thenReturn(overview);

        R<Map<String, Object>> response = controller.overview("P5", "技术", "java");

        assertThat(response.getData()).isSameAs(overview);
    }

    @Test
    void filtersReturnsFilterOptions() {
        PostPanoramaApiFacade facade = mock(PostPanoramaApiFacade.class);
        PostPanoramaController controller = new PostPanoramaController(facade);

        Map<String, Object> filters = Map.of("levels", List.of("P1", "P5"));
        when(facade.getFilters()).thenReturn(filters);

        R<Map<String, Object>> response = controller.filters();

        assertThat(response.getData()).isSameAs(filters);
    }

    @Test
    void postDetailReturnsPostInfo() {
        PostPanoramaApiFacade facade = mock(PostPanoramaApiFacade.class);
        PostPanoramaController controller = new PostPanoramaController(facade);

        Map<String, Object> detail = Map.of("postId", 2001L);
        when(facade.getPostDetail(2001L)).thenReturn(detail);

        R<Map<String, Object>> response = controller.postDetail(2001L);

        assertThat(response.getData()).isSameAs(detail);
    }

    @Test
    void postDetailFailsWhenPostMissing() {
        PostPanoramaApiFacade facade = mock(PostPanoramaApiFacade.class);
        PostPanoramaController controller = new PostPanoramaController(facade);

        when(facade.getPostDetail(999L)).thenReturn(null);

        R<Map<String, Object>> response = controller.postDetail(999L);

        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("岗位不存在");
    }

    @Test
    void abilityDetailReturnsAbilityInfo() {
        PostPanoramaApiFacade facade = mock(PostPanoramaApiFacade.class);
        PostPanoramaController controller = new PostPanoramaController(facade);

        Map<String, Object> detail = Map.of("abilityId", 101L);
        when(facade.getAbilityDetail(101L)).thenReturn(detail);

        R<Map<String, Object>> response = controller.abilityDetail(101L);

        assertThat(response.getData()).isSameAs(detail);
    }

    @Test
    void abilityDetailFailsWhenAbilityMissing() {
        PostPanoramaApiFacade facade = mock(PostPanoramaApiFacade.class);
        PostPanoramaController controller = new PostPanoramaController(facade);

        when(facade.getAbilityDetail(999L)).thenReturn(null);

        R<Map<String, Object>> response = controller.abilityDetail(999L);

        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("能力标签不存在");
    }

    @Test
    void graphReturnsGraphMap() {
        PostPanoramaApiFacade facade = mock(PostPanoramaApiFacade.class);
        PostPanoramaController controller = new PostPanoramaController(facade);

        Map<String, Object> graph = Map.of("nodes", List.of());
        when(facade.getGraph(2001L, "P5", "技术", "java", 20)).thenReturn(graph);

        R<Map<String, Object>> response = controller.graph(2001L, "P5", "技术", "java", 20);

        assertThat(response.getData()).isSameAs(graph);
    }
}
