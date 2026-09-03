package com.example.matching.dto.assessment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 匹配资格预检请求 DTO
 *
 * @author system
 */
@Data
public class EligibilityPrecheckRequest {

    /** 员工 ID 列表 */
    @NotNull(message = "empIds 不能为空")
    private List<Long> empIds;

    /** 目标岗位 ID 列表 */
    @NotNull(message = "postIds 不能为空")
    private List<Long> postIds;
}
