package com.example.matching.agent.dto;

import com.example.matching.agent.dto.graph.AgentGraphContext;
import com.example.matching.application.agent.AgentScoreBreakdown;
import com.example.matching.application.agent.EmployeeAbilitySnapshot;
import com.example.matching.application.agent.PostRequirementSnapshot;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Agent上下文包DTO
 * <p>
 * employeeAbilities / postRequirements / scoreBreakdown are strongly typed.
 * graphSummary and feedbackSignals remain as open-structure Map because
 * their shape varies by scenario and enrichment data source.
 *
 * @author system
 */
@Data
public class AgentContextPackage {
    /** 员工ID */
    private Long empId;

    /** 员工姓名 */
    private String empName;

    /** 岗位ID */
    private Long postId;

    /** 岗位名称 */
    private String postName;

    /** 匹配记录ID */
    private Long matchingRecordId;

    /** 匹配分数 */
    private BigDecimal matchScore;

    /** 员工能力列表 */
    private List<EmployeeAbilitySnapshot> employeeAbilities;

    /** 岗位要求列表 */
    private List<PostRequirementSnapshot> postRequirements;

    /** 评分明细 */
    private List<AgentScoreBreakdown> scoreBreakdown;

    /** 来源引用列表 */
    private List<AgentSourceRef> sourceRefs;

    /** 图谱摘要（开放结构，场景差异大） */
    private Map<String, Object> graphSummary;

    /** 反馈信号（开放结构，来源多样） */
    private Map<String, Object> feedbackSignals;

    /** 图谱预构建上下文（任务级受限子图，一次性放入 Agent 上下文） */
    private AgentGraphContext graphContext;
}
