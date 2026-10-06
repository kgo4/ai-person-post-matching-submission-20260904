package com.example.matching.port.employee;

import com.example.matching.dto.matching.MatchingAbilitySnapshot;

import java.util.List;
import java.util.Map;

/**
 * 员工权威能力读取端口（方案第八章）。
 * <p>
 * 统一规则：仅读取 emp_ability 正式人员能力表。
 * <p>
 * 以下模块统一使用本端口，确保三者口径一致：
 * 匹配分使用的能力（MatchingDataQueryServiceImpl）、
 * Agent 解释使用的能力（AiContextPackageServiceImpl）、
 * 图谱子图使用的能力（AgentGraphContextAssemblerImpl）。
 */
public interface EmployeeAbilityReadPort {

    /**
     * 批量加载员工权威能力。
     *
     * @param empIds 员工 ID 列表
     * @return empId -> 正式能力快照列表
     */
    Map<Long, List<MatchingAbilitySnapshot>> loadAuthoritativeAbilities(List<Long> empIds);
}
