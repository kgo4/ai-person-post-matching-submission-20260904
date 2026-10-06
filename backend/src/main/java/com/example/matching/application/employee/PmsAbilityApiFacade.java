package com.example.matching.application.employee;

import com.example.matching.dto.employee.api.PmsAnalysisTaskResponse;
import com.example.matching.dto.employee.api.PmsRosterResponse;
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

    // ------------------------------------------------------------------
    // PMS 人员花名册（HR 独立功能）
    // ------------------------------------------------------------------

    /** 花名册本身就是响应对象，Facade 只做转发，不再包装一层以免统计口径与列表分叉。 */
    public PmsRosterResponse roster() {
        return pmsAbilityAnalysisService.listRoster();
    }

    public PmsUserMappingResponse bindRosterUser(Long pmsUserId, Long empId) {
        return toResponse(pmsAbilityAnalysisService.bindRosterUser(pmsUserId, empId));
    }

    public void unbindRosterUser(Long pmsUserId) {
        pmsAbilityAnalysisService.unbindRosterUser(pmsUserId);
    }

    public PmsAnalysisTaskResponse analyzeByPmsUser(Long pmsUserId, int months) {
        return toResponse(pmsAbilityAnalysisService.analyzeByPmsUser(pmsUserId, months));
    }

    public List<PmsAnalysisTaskResponse> getHistoryByPmsUser(Long pmsUserId) {
        return pmsAbilityAnalysisService.getAnalysisHistoryByPmsUser(pmsUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<Map<String, Object>> listPmsUsers() {
        return pmsAbilityAnalysisService.listPmsUsers();
    }

    public boolean testConnection() {
        return pmsAbilityAnalysisService.testConnection();
    }

    /**
     * 同步结果口径（2026-09-04 起）：
     * <ul>
     *   <li>{@code newSynced} —— 本次新落映射的 PMS 人员（已同步未绑定）</li>
     *   <li>{@code totalPmsUsers} —— PMS 库里的总人数</li>
     *   <li>{@code alreadySynced} —— 此前已同步过的（含已绑定）</li>
     *   <li>{@code autoBound} —— 本次顺带按工号唯一命中并自动绑定的人数</li>
     * </ul>
     * 旧口径的 {@code newMapped / unmatched} 已不再返回：那个「unmatched」是
     * 「没能建立起绑定的 PMS 人员数」，在只落映射的新模型下恒等于未绑定人数，会说谎。
     */
    public Map<String, Object> syncPmsUsers() {
        int[] result = pmsAbilityAnalysisService.syncPmsUsers();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("newSynced", result[0]);
        data.put("totalPmsUsers", result[1]);
        data.put("alreadySynced", result[2]);
        data.put("autoBound", result[3]);
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
