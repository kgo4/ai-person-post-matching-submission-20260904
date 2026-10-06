package com.example.matching.service.learning;

import com.example.matching.dto.kg.context.GraphLearningPrerequisiteContext;
import com.example.matching.dto.learning.AiLearningSuggestionDTO;
import com.example.matching.entity.learning.LearningResource;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 学习建议校验器接口
 * <p>
 * 第四层防幻觉控制：服务端校验。
 * AI 输出后，后端逐条校验 resourceId、abilityName、title/url/resourceType、sourceRefs。
 * 校验失败的内容直接丢弃或标记为"AI 建议未采纳"。
 *
 * @author system
 */
public interface AiLearningSuggestionValidator {

    AiLearningSuggestionDTO.ValidationSummary validate(
            List<AiLearningSuggestionDTO.AbilitySuggestion> suggestions,
            Set<String> allowedAbilityNames,
            Map<Long, LearningResource> resourceMap
    );

    AiLearningSuggestionDTO.ValidationSummary validate(
            List<AiLearningSuggestionDTO.AbilitySuggestion> suggestions,
            Set<String> allowedAbilityNames,
            Map<Long, LearningResource> resourceMap,
            Set<Long> allowedTagIds,
            GraphLearningPrerequisiteContext graphPrerequisites
    );
}
