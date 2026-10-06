package com.example.matching.controller.vector;

import com.example.matching.application.vectorsearch.VectorSearchApiFacade;
import com.example.matching.common.result.R;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VectorSearchControllerTest {

    @Test
    void searchEmployeesReturnsCandidates() {
        VectorSearchApiFacade facade = mock(VectorSearchApiFacade.class);
        VectorSearchController controller = new VectorSearchController(facade);

        Map<String, Object> hit = Map.of(
                "employeeId", 1L, "employeeName", "张三", "score", 0.91D);
        when(facade.searchEmployeesForPost("资深Java工程师", 5)).thenReturn(List.of(hit));

        R<List<Map<String, Object>>> response = controller.searchEmployees("资深Java工程师", 5);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsExactly(hit);
    }

    @Test
    void searchPostsReturnsPosts() {
        VectorSearchApiFacade facade = mock(VectorSearchApiFacade.class);
        VectorSearchController controller = new VectorSearchController(facade);

        Map<String, Object> hit = Map.of(
                "postId", 10L, "postTitle", "Java后端工程师", "score", 0.88D);
        when(facade.searchPostsForEmployee("熟悉Spring Cloud微服务", 10)).thenReturn(List.of(hit));

        R<List<Map<String, Object>>> response = controller.searchPosts("熟悉Spring Cloud微服务", 10);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsExactly(hit);
    }
}
