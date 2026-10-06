package com.example.matching.application.employee;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.application.common.FileContent;
import com.example.matching.dto.employee.api.ResumeParseResponse;
import com.example.matching.dto.system.AbilityImportResultDTO;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.entity.workflow.PersonCapabilityWorkflow;
import com.example.matching.service.assessment.CapabilityAssessmentWorkflowService;
import com.example.matching.service.employee.ResumeParseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResumeParseApiFacade {

    private final ResumeParseService resumeParseService;
    private final CapabilityAssessmentWorkflowService workflowService;

    public ResumeParseResponse uploadAndParse(Long empId, String fileName, byte[] content, Long userId) {
        EmpResumeParse result = resumeParseService.uploadAndParse(empId, fileName, content, userId);
        return toSummaryResponse(result);
    }

    public List<ResumeParseResponse> listByEmpId(Long empId) {
        return resumeParseService.listByEmpId(empId).stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    public ResumeParseResponse getById(Long id) {
        return toDetailResponse(resumeParseService.getById(id));
    }

    /**
     * @deprecated 已废弃。简历能力正式入库统一走能力评估工作流证据路径，
     * 此「直接导入正式能力」旧路径保留仅供兼容，勿新增调用。
     */
    @Deprecated
    public AbilityImportResultDTO importToAbilityProfile(Long id) {
        EmpResumeParse record = resumeParseService.getById(id);
        if (record == null) {
            throw new BusinessException(404, "解析记录不存在");
        }
        // 兼容适配：员工存在活跃评估工作流时，禁止旧"直接导入正式能力"路径，
        // 必须通过能力评估流程保存证据（防止新流程双写/污染）
        PersonCapabilityWorkflow workflow = workflowService.getActiveWorkflow(record.getEmpId());
        if (workflow != null) {
            throw new BusinessException(400,
                    "该员工存在活跃能力评估流程，简历能力请通过能力评估流程保存为证据（不再直接正式入库）");
        }
        return resumeParseService.importToAbilityProfile(id);
    }

    public FileContent viewFile(Long id) {
        EmpResumeParse record = resumeParseService.getById(id);
        if (record == null || record.getFilePath() == null) {
            throw new BusinessException(404, "解析记录不存在");
        }
        Path uploadsRoot = Path.of("uploads", "resume").toAbsolutePath().normalize();
        Path filePath = Path.of(record.getFilePath()).toAbsolutePath().normalize();
        if (!filePath.startsWith(uploadsRoot)) {
            throw new BusinessException(403, "非法文件路径");
        }
        File file = filePath.toFile();
        if (!file.exists()) {
            throw new BusinessException(404, "简历文件不存在");
        }
        try {
            String fileName = record.getFileName() != null ? record.getFileName() : "resume";
            return new FileContent(fileName, Files.readAllBytes(file.toPath()));
        } catch (IOException exception) {
            throw new BusinessException(500, "Unable to read resume file");
        }
    }

    public ResumeParseResponse reparse(Long id) {
        return toSummaryResponse(resumeParseService.reparse(id));
    }

    public ResumeParseResponse retryFailedTask(Long id) {
        return toSummaryResponse(resumeParseService.retryFailedTask(id));
    }

    private ResumeParseResponse toSummaryResponse(EmpResumeParse e) {
        return toResponse(e, false);
    }

    private ResumeParseResponse toDetailResponse(EmpResumeParse e) {
        return toResponse(e, true);
    }

    private ResumeParseResponse toResponse(EmpResumeParse e, boolean includePayload) {
        if (e == null) return null;
        return new ResumeParseResponse(
                e.getId(),
                e.getEmpId(),
                e.getFileName(),
                e.getFileType(),
                includePayload ? e.getParsedContent() : null,
                includePayload ? e.getAiAnalysisResult() : null,
                e.getStatus(),
                e.getErrorMessage(),
                e.getRetryCount(),
                e.getCreatedTime(),
                e.getUpdatedTime());
    }
}
