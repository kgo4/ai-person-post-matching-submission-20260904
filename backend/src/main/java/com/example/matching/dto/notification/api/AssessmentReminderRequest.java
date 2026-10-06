package com.example.matching.dto.notification.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * HR 提醒员工进行能力评估的请求。
 *
 * @author system
 */
@Schema(description = "提醒员工能力评估请求")
public class AssessmentReminderRequest {

    @Schema(description = "员工档案ID", example = "1")
    @NotNull(message = "员工ID不能为空")
    private Long empId;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }
}
