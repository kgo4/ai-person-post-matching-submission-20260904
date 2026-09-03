package com.example.matching.controller.matching;

import com.example.matching.application.matching.MatchingRecommendApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.matching.EmployeeRecommendDTO;
import com.example.matching.dto.matching.PostRecommendDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchingRecommendControllerTest {

    @Test
    void recommendPostsByEmployeeReturnsFacadeResponse() {
        MatchingRecommendApiFacade facade = mock(MatchingRecommendApiFacade.class);
        MatchingRecommendController controller = new MatchingRecommendController(facade);

        PostRecommendDTO.Request request = new PostRecommendDTO.Request();
        request.setEmpId(10001L);

        PostRecommendDTO.Response responseData = new PostRecommendDTO.Response();
        responseData.setEmpId(10001L);
        responseData.setEmpName("张三");
        responseData.setRecommendations(List.of());
        when(facade.recommendPostsForEmployee(request)).thenReturn(responseData);

        R<PostRecommendDTO.Response> response = controller.recommendPostsByEmployee(request);

        assertThat(response.getData()).isEqualTo(responseData);
        assertThat(response.getData().getEmpId()).isEqualTo(10001L);
    }

    @Test
    void recommendPostsByEmployeeRejectsMissingEmpId() {
        MatchingRecommendApiFacade facade = mock(MatchingRecommendApiFacade.class);
        MatchingRecommendController controller = new MatchingRecommendController(facade);

        PostRecommendDTO.Request request = new PostRecommendDTO.Request();

        R<PostRecommendDTO.Response> response = controller.recommendPostsByEmployee(request);

        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("员工ID不能为空");
    }

    @Test
    void recommendEmployeesByPostReturnsFacadeResponse() {
        MatchingRecommendApiFacade facade = mock(MatchingRecommendApiFacade.class);
        MatchingRecommendController controller = new MatchingRecommendController(facade);

        EmployeeRecommendDTO.Request request = new EmployeeRecommendDTO.Request();
        request.setPostId(20001L);

        EmployeeRecommendDTO.Response responseData = new EmployeeRecommendDTO.Response();
        responseData.setPostId(20001L);
        responseData.setPostName("Java工程师");
        responseData.setRecommendations(List.of());
        when(facade.recommendEmployeesForPost(request)).thenReturn(responseData);

        R<EmployeeRecommendDTO.Response> response = controller.recommendEmployeesByPost(request);

        assertThat(response.getData()).isEqualTo(responseData);
        assertThat(response.getData().getPostId()).isEqualTo(20001L);
    }

    @Test
    void recommendEmployeesByPostRejectsMissingPostId() {
        MatchingRecommendApiFacade facade = mock(MatchingRecommendApiFacade.class);
        MatchingRecommendController controller = new MatchingRecommendController(facade);

        EmployeeRecommendDTO.Request request = new EmployeeRecommendDTO.Request();

        R<EmployeeRecommendDTO.Response> response = controller.recommendEmployeesByPost(request);

        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo("岗位ID不能为空");
    }
}
