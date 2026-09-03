package com.example.matching.service.learning;

import com.example.matching.dto.kg.context.GraphLearningPrerequisiteContext;
import com.example.matching.dto.learning.AiLearningSuggestionDTO;
import com.example.matching.entity.learning.LearningResource;
import com.example.matching.service.learning.impl.AiLearningSuggestionValidatorImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiLearningSuggestionValidatorImplTest {

    private final AiLearningSuggestionValidatorImpl validator = new AiLearningSuggestionValidatorImpl();

    @Test
    void filtersSuggestionWithoutVerifiedTagId() {
        AiLearningSuggestionDTO.AbilitySuggestion suggestion = suggestion(null, "Java", 1L);

        validator.validate(new ArrayList<>(List.of(suggestion)), Set.of("Java"), resources(), Set.of(7L), emptyGraph());

        assertThat(suggestion.isInsufficientEvidence()).isTrue();
        assertThat(suggestion.getSteps()).isEmpty();
    }

    @Test
    void ordersPrerequisiteSuggestionBeforeDependentSuggestion() {
        AiLearningSuggestionDTO.AbilitySuggestion dependent = suggestion(7L, "Advanced Java", 1L);
        AiLearningSuggestionDTO.AbilitySuggestion prerequisite = suggestion(5L, "Java Basics", 1L);
        List<AiLearningSuggestionDTO.AbilitySuggestion> suggestions = new ArrayList<>(List.of(dependent, prerequisite));
        GraphLearningPrerequisiteContext graph = new GraphLearningPrerequisiteContext(
                List.of(5L, 7L),
                List.of(new GraphLearningPrerequisiteContext.PrerequisiteNode(
                        7L, "Advanced Java", 5L, "Java Basics", "PREREQUISITE_OF", List.of(), "v1")));

        validator.validate(suggestions, Set.of("Java Basics", "Advanced Java"), resources(), Set.of(5L, 7L), graph);

        assertThat(suggestions).extracting(AiLearningSuggestionDTO.AbilitySuggestion::getTagId)
                .containsExactly(5L, 7L);
    }

    private AiLearningSuggestionDTO.AbilitySuggestion suggestion(Long tagId, String abilityName, Long resourceId) {
        AiLearningSuggestionDTO.LearningStep step = new AiLearningSuggestionDTO.LearningStep();
        step.setResourceId(resourceId);
        AiLearningSuggestionDTO.AbilitySuggestion suggestion = new AiLearningSuggestionDTO.AbilitySuggestion();
        suggestion.setTagId(tagId);
        suggestion.setAbilityName(abilityName);
        suggestion.setSteps(new ArrayList<>(List.of(step)));
        return suggestion;
    }

    private Map<Long, LearningResource> resources() {
        LearningResource resource = new LearningResource();
        resource.setId(1L);
        resource.setTitle("Java course");
        resource.setResourceType("COURSE");
        return Map.of(1L, resource);
    }

    private GraphLearningPrerequisiteContext emptyGraph() {
        return new GraphLearningPrerequisiteContext(List.of(), List.of());
    }
}
