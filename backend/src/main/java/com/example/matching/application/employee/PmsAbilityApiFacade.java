package com.example.matching.application.employee;

import com.example.matching.dto.employee.api.PmsAnalysisTaskResponse;
import com.example.matching.dto.employee.api.PmsUserMappingResponse;
import com.example.matching.entity.system.PmsAnalysisTask;
import com.example.matching.entity.system.PmsUserMapping;
import com.example.matching.service.employee.PmsAbilityAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PmsAbilityApiFacade {

    private final PmsAbilityAnalysisService pmsAbilityAnalysisService;

    public PmsUserMappingResponse autoMapUser(Long empId) {
        return toResponse(pmsAbilityAnalysisService.autoMapUser(empId));
    }

    public PmsUserMappingResponse manualMapUser(Long empId, Long pmsUserId) {
        return toResponse(pmsAbilityAnalysisService.manualMapUser(empId, pmsUserId));
    }

    public PmsUserMappingResponse getMapping(Long empId) {
        return toResponse(pmsAbilityAnalysisService.getMapping(empId));
    }

    public PmsAnalysisTaskResponse analyze(Long empId, int months) {
        return toResponse(pmsAbilityAnalysisService.analyzeEmployee(empId, months));
    }

    public List<PmsAnalysisTaskResponse> getHistory(Long empId) {
        return pmsAbilityAnalysisService.getAnalysisHistory(empId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<Map<String, Object>> listPmsUsers() {
        return pmsAbilityAnalysisService.listPmsUsers();
    }

    public boolean testConnection() {
        return pmsAbilityAnalysisService.testConnection();
    }

    public Map<String, Object> syncPmsUsers() {
        int[] result = pmsAbilityAnalysisService.syncPmsUsers();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("newMapped", result[0]);
        data.put("totalPmsUsers", result[1]);
        data.put("alreadyMapped", result[2]);
        data.put("unmatched", result[3]);
        return data;
    }

    public Map<String, Object> getDetail(Long taskId) {
        return pmsAbilityAnalysisService.getAnalysisDetail(taskId);
    }

    public int importAbilities(Long empId, Long taskId, List<Integer> indexes) {
        return pmsAbilityAnalysisService.importAbilities(empId, taskId, indexes);
    }

    private PmsUserMappingResponse toResponse(PmsUserMapping m) {
        if (m == null) return null;
        return new PmsUserMappingResponse(
                m.getId(),
                m.getEmpId(),
                m.getPmsUserId(),
                m.getPmsUsername(),
                m.getPmsNickname(),
                m.getPmsEmployeeId(),
                m.getCreatedTime());
    }

    private PmsAnalysisTaskResponse toResponse(PmsAnalysisTask t) {
        if (t == null) return null;
        return new PmsAnalysisTaskResponse(
                t.getId(),
                t.getEmpId(),
                t.getPmsUserId(),
                t.getAnalysisStatus(),
                t.getDateRangeMonths(),
                t.getWorkOrderCount(),
                t.getBugCount(),
                t.getTestCaseCount(),
                t.getProjectCount(),
                t.getExtractedAbilityCount(),
                t.getErrorMessage(),
                t.getCreatedTime(),
                t.getUpdatedTime());
    }
}
