package com.example.matching.controller.matching;

import com.example.matching.application.matching.MatchingRecommendApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.matching.EmployeeRecommendDTO;
import com.example.matching.dto.matching.PostRecommendDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/matching/recommend")
@RequiredArgsConstructor
public class MatchingRecommendController {

    private final MatchingRecommendApiFacade matchingRecommendApiFacade;

    @PostMapping("/posts-by-employee")
    public R<PostRecommendDTO.Response> recommendPostsByEmployee(@RequestBody PostRecommendDTO.Request request) {
        log.info("员工推荐岗位请求：empId={}, topK={}", request.getEmpId(), request.getTopK());
        if (request.getEmpId() == null) return R.fail("员工ID不能为空");
        PostRecommendDTO.Response response = matchingRecommendApiFacade.recommendPostsForEmployee(request);
        return R.ok(response);
    }

    @PostMapping("/employees-by-post")
    public R<EmployeeRecommendDTO.Response> recommendEmployeesByPost(@RequestBody EmployeeRecommendDTO.Request request) {
        log.info("岗位推荐员工请求：postId={}, topK={}", request.getPostId(), request.getTopK());
        if (request.getPostId() == null) return R.fail("岗位ID不能为空");
        EmployeeRecommendDTO.Response response = matchingRecommendApiFacade.recommendEmployeesForPost(request);
        return R.ok(response);
    }
}
