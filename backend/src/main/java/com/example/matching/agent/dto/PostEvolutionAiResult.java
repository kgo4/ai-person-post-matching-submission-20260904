package com.example.matching.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 岗位演化 AI Agent 输出
 * <p>
 * LLM 基于「岗位当前能力状态 + 检索到的证据片段」产出变更建议。
 * 防幻觉约束：
 * <ul>
 *   <li>每条建议必须引用输入证据列表中的真实编号（evidenceRef）</li>
 *   <li>新增能力名必须出现在所引用证据片段中（由后端归一化校验）</li>
 *   <li>已有能力变更名必须匹配岗位能力表中的能力</li>
 * </ul>
 */
@Data
public class PostEvolutionAiResult {

    private List<ChangeSuggestion> suggestions;

    @Data
    public static class ChangeSuggestion {

        /** 能力名称（必须来自证据片段或岗位现有能力） */
        private String abilityName;

        /** ADD / UPDATE_LEVEL / UPDATE_WEIGHT / UPDATE_CORE / REMOVE */
        private String action;

        private Integer newLevel;

        private BigDecimal newWeight;

        private Integer newIsCore;

        /** 变更理由（基于证据） */
        private String reason;

        /** 引用的证据编号，对应输入证据列表下标 */
        private Integer evidenceRef;
    }
}
