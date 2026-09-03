package com.example.matching.service.impl;

import com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition;
import com.example.matching.entity.post.PostHardConditionRule;
import com.example.matching.service.post.impl.PostHardConditionRuleServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostHardConditionRuleServiceImplTest {

    @Test
    void convertsPostRulesToMatchingHardConditions() {
        PostHardConditionRuleServiceImpl service = new PostHardConditionRuleServiceImpl() {
            @Override
            public List<PostHardConditionRule> listEnabledByPostId(Long postId) {
                PostHardConditionRule rule = new PostHardConditionRule();
                rule.setPostId(postId);
                rule.setFieldName("skillDirection");
                rule.setFieldLabel("Skill Direction");
                rule.setFieldType("rank");
                rule.setOperator("contains");
                rule.setExpectedValue("Java");
                rule.setValueRankJson("{\"初级\":1,\"中级\":2,\"高级\":3}");
                return List.of(rule);
            }
        };

        List<HardCondition> conditions = service.toHardConditions(2001L);

        assertThat(conditions).hasSize(1);
        assertThat(conditions.get(0).getField()).isEqualTo("skillDirection");
        assertThat(conditions.get(0).getOperator()).isEqualTo("contains");
        assertThat(conditions.get(0).getValue()).isEqualTo("Java");
        assertThat(conditions.get(0).getFieldType()).isEqualTo("rank");
        assertThat(conditions.get(0).getValueRankJson()).isEqualTo("{\"初级\":1,\"中级\":2,\"高级\":3}");
        assertThat(conditions.get(0).getLabel()).contains("Skill Direction");
    }
}
