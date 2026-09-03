package com.example.matching.application.matching;

import com.example.matching.dto.matching.EmployeeRecommendDTO;
import com.example.matching.dto.matching.PostRecommendDTO;
import com.example.matching.service.matching.EmployeePostRecommendService;
import com.example.matching.service.matching.EmployeeRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchingRecommendApiFacade {

    private final EmployeePostRecommendService employeePostRecommendService;
    private final EmployeeRecommendService employeeRecommendService;

    public PostRecommendDTO.Response recommendPostsForEmployee(PostRecommendDTO.Request request) {
        return employeePostRecommendService.recommendPostsForEmployee(request);
    }

    public EmployeeRecommendDTO.Response recommendEmployeesForPost(EmployeeRecommendDTO.Request request) {
        return employeeRecommendService.recommendEmployeesForPost(request);
    }
}
