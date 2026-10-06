package com.example.matching.agent.service;

import com.example.matching.agent.dto.EmployeeAbilityAgentRequest;
import com.example.matching.agent.dto.EmployeeAbilityAgentResult;
import com.example.matching.agent.dto.person.PersonAbilityExtractRequest;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;

/**
 * 员工能力Agent服务接口
 *
 * @author system
 */
public interface EmployeeAbilityAgentService {

    /**
     * 分析员工能力画像（解释已有能力）
     *
     * @param request 请求
     * @return 分析结果
     */
    EmployeeAbilityAgentResult analyze(EmployeeAbilityAgentRequest request);

    /**
     * 从来源材料提取员工能力声明
     * <p>
     * 职责：从指定来源材料中提取能力标签，返回统一格式的 claim 列表。
     * 每个 claim 都必须包含 evidenceText 和 sourceRefs。
     *
     * @param request 提取请求（包含 sourceText）
     * @return 提取结果
     */
    PersonAbilityExtractionResult extractAbilities(PersonAbilityExtractRequest request);
}
