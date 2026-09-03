package com.example.matching.application.employee;

import com.example.matching.dto.employee.EmpAbilitySaveDTO;
import com.example.matching.dto.employee.api.EmployeeAbilityCreateRequest;
import com.example.matching.dto.employee.api.EmployeeAbilityResponse;
import com.example.matching.dto.employee.api.EmployeeAbilityUpdateRequest;
import com.example.matching.dto.employee.api.PendingAbilityClaimResponse;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.service.employee.EmpAbilityService;
import com.example.matching.vo.employee.EmpAbilityProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmpAbilityApiFacade {

    private final EmpAbilityService empAbilityService;

    public EmpAbilityProfileVO getProfile(Long empId) {
        return empAbilityService.getProfile(empId);
    }

    public List<EmployeeAbilityResponse> listByEmpId(Long empId) {
        List<EmpAbility> abilities = empAbilityService.listByEmpId(empId);
        return abilities.stream()
                .map(ability -> toResponse(ability, null))
                .toList();
    }

    public List<PendingAbilityClaimResponse> listPendingClaims(Long empId) {
        return empAbilityService.listPendingClaims(empId).stream()
                .map(this::toPendingClaimResponse)
                .toList();
    }

    public void save(EmployeeAbilityCreateRequest req) {
        EmpAbilitySaveDTO dto = new EmpAbilitySaveDTO();
        dto.setEmpId(req.empId());
        dto.setAbilityName(req.abilityName());
        dto.setTagId(req.tagId());
        dto.setMasteryLevel(req.masteryLevel());
        dto.setEvaluationSource(req.evaluationSource());
        dto.setSourceWeight(req.sourceWeight());
        dto.setEvaluationDate(req.evaluationDate());
        dto.setRemark(req.remark());
        empAbilityService.saveAbility(dto);
    }

    public void update(Long id, EmployeeAbilityUpdateRequest req) {
        EmpAbilitySaveDTO dto = new EmpAbilitySaveDTO();
        dto.setId(id);
        dto.setAbilityName(req.abilityName());
        dto.setTagId(req.tagId());
        dto.setMasteryLevel(req.masteryLevel());
        dto.setEvaluationSource(req.evaluationSource());
        dto.setSourceWeight(req.sourceWeight());
        dto.setEvaluationDate(req.evaluationDate());
        dto.setRemark(req.remark());
        empAbilityService.saveAbility(dto);
    }

    public void batchSave(List<EmpAbilitySaveDTO> list) {
        empAbilityService.batchSave(list);
    }

    public void delete(Long id) {
        empAbilityService.removeById(id);
    }

    private EmployeeAbilityResponse toResponse(EmpAbility e, String tagName) {
        if (e == null) return null;
        String effectiveName = e.getAbilityName() != null && !e.getAbilityName().isBlank()
                ? e.getAbilityName() : tagName;
        return new EmployeeAbilityResponse(
                e.getId(),
                e.getEmpId(),
                e.getTagId(),
                effectiveName,
                effectiveName,
                e.getAssessmentAbilityId(),
                e.getWorkflowId(),
                e.getMasteryLevel(),
                e.getAbilityLevel(),
                e.getEvaluationSource(),
                e.getSourceWeight(),
                e.getEvaluationDate(),
                e.getRemark(),
                e.getCreatedTime(),
                e.getUpdatedTime());
    }

    private PendingAbilityClaimResponse toPendingClaimResponse(PersonAbilityClaim claim) {
        return new PendingAbilityClaimResponse(
                claim.getId(), claim.getEmpId(), claim.getTagId(), claim.getAbilityName(),
                claim.getClaimedLevel(), claim.getSourceType(), claim.getSourceRefId(),
                claim.getEvidenceText(), claim.getConfidenceScore(), claim.getHarnessDecision(),
                claim.getHarnessLogId(), claim.getStatus(), claim.getCreatedTime());
    }
}
