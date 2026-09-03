package com.example.matching.dto.assessment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 提交测试答案请求 DTO
 * <p>
 * 评估流程内的测试提交必须调用评估专用接口，禁止绕过状态机调用通用接口。
 *
 * @author system
 */
@Data
public class SubmitTestRequest {

    /** 题目 ID -> 答案内容 */
    @NotNull(message = "answers 不能为空")
    private Map<String, Object> answers;
}
