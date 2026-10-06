package com.example.matching.application.closure;

import com.example.matching.application.common.SelfScopeSupport;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.closure.CapabilityClosureResult;
import com.example.matching.dto.closure.ComprehensiveDiagnosisResultDTO;
import com.example.matching.dto.closure.LearningOutcomeConfirmDTO;
import com.example.matching.dto.closure.MatchDiagnosisResult;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.service.closure.CapabilityClosureService;
import com.example.matching.service.closure.ComprehensiveDiagnosisService;
import com.example.matching.service.matching.MatchingRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CapabilityClosureApiFacade {

    private final CapabilityClosureService capabilityClosureService;
    private final ComprehensiveDiagnosisService comprehensiveDiagnosisService;
    private final SelfScopeSupport selfScopeSupport;
    private final MatchingRecordService matchingRecordService;

    /**
     * 仅本人调用方读取匹配诊断前，必须确认该记录属于本人且已发布。
     * <p>权限码只保证“具备访问诊断能力”，记录归属必须在这里按登录身份收口，
     * 否则员工可以通过遍历 recordId 读到他人的诊断结果。</p>
     */
    private void assertMatchingRecordOwnedByCaller(Long recordId) {
        if (!selfScopeSupport.isSelfOnlyCaller()) {
            return;
        }
        Long empId = selfScopeSupport.currentEmpId();
        boolean owned = empId != null
                && matchingRecordService.getPublishedDetailById(recordId, empId) != null;
        if (!owned) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(), "无权访问该匹配记录");
        }
    }

    public MatchDiagnosisResult diagnoseMatchingRecord(Long recordId) {
        assertMatchingRecordOwnedByCaller(recordId);
        return capabilityClosureService.diagnoseMatchingRecord(recordId);
    }

    public ComprehensiveDiagnosisResultDTO comprehensiveDiagnosis(Long recordId) {
        assertMatchingRecordOwnedByCaller(recordId);
        return comprehensiveDiagnosisService.diagnose(recordId);
    }

    public CapabilityClosureResult confirmLearningOutcome(LearningOutcomeConfirmDTO dto) {
        if (selfScopeSupport.isSelfOnlyCaller()) {
            EmpEmployee employee = selfScopeSupport.currentEmployee();
            if (employee == null || !employee.getId().equals(dto.getEmpId())) {
                throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(), "只能确认本人学习成果");
            }
        }
        return capabilityClosureService.onLearningOutcomeConfirmed(dto);
    }

    public CapabilityClosureResult getLatestByBusinessKey(String businessKey) {
        return capabilityClosureService.getLatestByBusinessKey(businessKey);
    }
}
