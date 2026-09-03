package com.example.matching.controller.closure;

import com.example.matching.application.closure.CapabilityClosureApiFacade;
import com.example.matching.common.result.R;
import com.example.matching.dto.closure.CapabilityClosureResult;
import com.example.matching.dto.closure.ComprehensiveDiagnosisResultDTO;
import com.example.matching.dto.closure.LearningOutcomeConfirmDTO;
import com.example.matching.dto.closure.MatchDiagnosisResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "业务闭环", description = "岗位能力闭环状态、匹配诊断和学习成果确认")
@RestController
@RequestMapping("/api/capability-closure")
@RequiredArgsConstructor
public class CapabilityClosureController {

    private final CapabilityClosureApiFacade capabilityClosureApiFacade;

    @Operation(summary = "获取匹配差距诊断（基础版）")
    @GetMapping("/matching/{recordId}/diagnosis")
    public R<MatchDiagnosisResult> diagnoseMatchingRecord(@PathVariable Long recordId) {
        return R.ok(capabilityClosureApiFacade.diagnoseMatchingRecord(recordId));
    }

    @Operation(summary = "获取综合差距诊断（多维度事实诊断包 + AI分析）")
    @GetMapping("/matching/{recordId}/comprehensive-diagnosis")
    public R<ComprehensiveDiagnosisResultDTO> comprehensiveDiagnosis(@PathVariable Long recordId) {
        return R.ok(capabilityClosureApiFacade.comprehensiveDiagnosis(recordId));
    }

    @Operation(summary = "确认学习成果并回写能力证据")
    @PostMapping("/learning/outcome")
    public R<CapabilityClosureResult> confirmLearningOutcome(@Valid @RequestBody LearningOutcomeConfirmDTO dto) {
        return R.ok(capabilityClosureApiFacade.confirmLearningOutcome(dto));
    }

    @Operation(summary = "按业务键查询闭环状态")
    @GetMapping("/logs/{businessKey}")
    public R<CapabilityClosureResult> getLog(@PathVariable String businessKey) {
        return R.ok(capabilityClosureApiFacade.getLatestByBusinessKey(businessKey));
    }
}
