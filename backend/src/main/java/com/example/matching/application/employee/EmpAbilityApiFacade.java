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
    /** 员工侧结果可见性统一判据（流程完成 + HR 审核清空）；HR 调用不受限 */
    private final com.example.matching.application.common.EmployeeResultVisibility employeeResultVisibility;
    /** 「仅本人」范围判定：用于区分员工视角与管理端视角 */
    private final com.example.matching.application.common.SelfScopeSupport selfScopeSupport;

    /**
     * 员工能力画像。
     *
     * <p>【2026-09-04 收口】员工本人只有在「评估流程完成 **且** HR 把全部能力项审核完毕」
     * 之后才能看到自己的能力项；审核中间态不进入员工侧视图。
     * 原实现没有任何闸门 —— 前端虽然隐藏了，但接口直连仍能拿到未定稿能力，
     * 属于「前端隐藏当安全边界」的典型问题。HR 查看人员能力用于审核与修改，不受此限制。
     */
    public EmpAbilityProfileVO getProfile(Long empId) {
        EmpAbilityProfileVO profile = empAbilityService.getProfile(empId);
        if (profile == null || !employeeResultVisibility.shouldHideFromEmployee(empId)) {
            return profile;
        }
        // 保留姓名等非能力信息，清空能力明细与综合分：员工侧此时只应看到「审核中」的说明
        profile.setAbilityDetails(List.of());
        profile.setOverallScore(java.math.BigDecimal.ZERO);
        return profile;
    }

    public List<EmployeeAbilityResponse> listByEmpId(Long empId) {
        List<EmpAbility> abilities = empAbilityService.listByEmpId(empId);
        return abilities.stream()
                .map(ability -> toResponse(ability, null))
                .toList();
    }

    /**
     * 待融合能力声明（「待确立能力」）。
     *
     * <p>【2026-09-04 口径】员工侧**永不展示审核期中间态** —— 员工只看到 HR 审核定稿后的能力，
     * 因此员工调用一律返回空列表（前端已不再请求该接口，这里再加一层后端兜底）。
     * HR 需要这份清单来推进 Harness 审核，不受限制。
     */
    public List<PendingAbilityClaimResponse> listPendingClaims(Long empId) {
        if (selfScopeSupport.isSelfOnlyCaller()) {
            return List.of();
        }
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
