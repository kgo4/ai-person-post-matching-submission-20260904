package com.example.matching.application.employee;

import com.example.matching.dto.employee.api.AiTestRequest;
import com.example.matching.dto.employee.api.AiTestResponse;
import com.example.matching.entity.employee.EmpAiTest;
import com.example.matching.service.employee.AiTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiTestApiFacade {

    private final AiTestService aiTestService;

    public AiTestResponse generatePostTest(Long empId, Long postId, Long userId) {
        EmpAiTest test = aiTestService.generatePostTest(empId, postId, userId);
        return toResponse(test);
    }

    public AiTestResponse generateTest(Long empId, Long abilityTagId, Long userId) {
        EmpAiTest test = aiTestService.generateTest(empId, abilityTagId, userId);
        return toResponse(test);
    }

    public AiTestResponse submitAnswers(Long testId, Map<String, Object> answers) {
        EmpAiTest result = aiTestService.submitAnswers(testId, new java.util.LinkedHashMap<>(answers));
        return toResponse(result);
    }

    public AiTestResponse getTestResult(Long id) {
        return toResponse(aiTestService.getTestResult(id));
    }

    public List<AiTestResponse> listByEmpId(Long empId) {
        return aiTestService.listByEmpId(empId).stream()
                .map(this::toResponse)
                .toList();
    }

    public boolean importToAbilityProfile(Long id) {
        return aiTestService.importToAbilityProfile(id);
    }

    public boolean redeliverTask(Long id) {
        return aiTestService.redeliverTask(id);
    }

    private AiTestResponse toResponse(EmpAiTest e) {
        if (e == null) return null;
        return new AiTestResponse(
                e.getId(),
                e.getEmpId(),
                e.getTestTitle(),
                e.getAbilityTagId(),
                e.getAbilityTagName(),
                e.getQuestions(),
                e.getAnswers(),
                e.getAiEvaluation(),
                e.getAnalysisReport(),
                e.getErrorMessage(),
                e.getScore(),
                e.getMasteryLevel(),
                e.getStatus(),
                e.getCreatedTime(),
                e.getCompletedTime(),
                e.getImportedTime());
    }
}
