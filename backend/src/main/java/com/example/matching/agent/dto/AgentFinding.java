package com.example.matching.agent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 匹配分析结构化结论（方案第十三章输出回链校验）。
 * <p>
 * 服务端根据校验通过的结构化 finding 生成 strengths/gaps/riskSignals/
 * humanAttentionPoints，不再直接采信模型返回的无引用字符串。
 * <p>
 * type 取值：STRENGTH / GAP / RISK / HUMAN_ATTENTION
 */
@Data
public class AgentFinding {

    /** 结论类型 */
    private String type;

    /** 能力标签 ID（必须属于当前子图 allowedAbilityTagIds） */
    private Long abilityTagId;

    /** 结论文本 */
    private String text;

    /** 来源引用（必须属于 allowedSourceRefs） */
    private List<String> sourceRefs = new ArrayList<>();

    /** 图谱节点键（必须属于当前子图 nodes） */
    private List<String> graphNodeKeys = new ArrayList<>();
}
