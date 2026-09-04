package com.example.matching.service.impl;

import com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition;
import com.example.matching.dto.post.PostHardConditionRuleDTO;
import com.example.matching.entity.post.PostHardConditionRule;
import com.example.matching.mapper.post.PostHardConditionRuleMapper;
import com.example.matching.service.post.impl.PostHardConditionRuleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostHardConditionRuleServiceImplTest {

    private final PostHardConditionRuleMapper mapper = mock(PostHardConditionRuleMapper.class);
    private CapturingService service;

    @BeforeEach
    void setUp() {
        service = new CapturingService();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

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

    @Test
    void savesNewRuleWithDefaults() {
        PostHardConditionRuleDTO dto = dto(10L, "city", "equals", "Hefei");

        service.saveRule(dto);

        assertThat(service.savedSingle).isNotNull();
        assertThat(service.savedSingle.getId()).isNull();
        assertThat(service.savedSingle.getEnabled()).isEqualTo(1);
        assertThat(service.savedSingle.getSortOrder()).isZero();
        assertThat(service.savedSingle.getFieldName()).isEqualTo("city");
    }

    @Test
    void updatesExistingRuleAndPreservesExplicitValues() {
        PostHardConditionRule existing = new PostHardConditionRule();
        existing.setId(17L);
        existing.setPostId(1L);
        service.existing = existing;
        PostHardConditionRuleDTO dto = dto(10L, "level", "gte", "P6");
        dto.setId(17L);
        dto.setEnabled(0);
        dto.setSortOrder(9);

        service.saveRule(dto);

        assertThat(service.savedSingle).isSameAs(existing);
        assertThat(existing.getEnabled()).isZero();
        assertThat(existing.getSortOrder()).isEqualTo(9);
        assertThat(existing.getExpectedValue()).isEqualTo("P6");
    }

    @Test
    void rejectsMissingRuleFieldsAndUnknownRuleId() {
        assertThatThrownBy(() -> service.saveRule(dto(null, "city", "equals", "Hefei")))
                .hasMessageContaining("缺少必要字段");

        PostHardConditionRuleDTO missing = dto(10L, "city", "equals", "Hefei");
        missing.setId(404L);
        assertThatThrownBy(() -> service.saveRule(missing)).hasMessageContaining("不存在");
    }

    @Test
    void replacesRulesForPostAndRejectsDuplicates() {
        when(mapper.physicalDeleteByPostId(10L)).thenReturn(2);

        service.batchConfig(10L, List.of(dto(null, "city", "equals", "Hefei")));

        verify(mapper).physicalDeleteByPostId(10L);
        assertThat(service.savedBatch).singleElement().satisfies(rule -> {
            assertThat(rule.getPostId()).isEqualTo(10L);
            assertThat(rule.getId()).isNull();
            assertThat(rule.getEnabled()).isEqualTo(1);
            assertThat(rule.getSortOrder()).isZero();
        });

        assertThatThrownBy(() -> service.batchConfig(10L, List.of(
                dto(null, "city", "equals", "Hefei"),
                dto(null, "city", "equals", "Hefei"))))
                .hasMessageContaining("重复");
    }

    @Test
    void clearsRulesWithoutWritingWhenBatchIsEmpty() {
        service.batchConfig(10L, List.of());

        verify(mapper).physicalDeleteByPostId(10L);
        assertThat(service.savedBatch).isEmpty();
    }

    private PostHardConditionRuleDTO dto(Long postId, String field, String operator, String expected) {
        PostHardConditionRuleDTO dto = new PostHardConditionRuleDTO();
        dto.setPostId(postId);
        dto.setFieldName(field);
        dto.setFieldLabel(field + " label");
        dto.setOperator(operator);
        dto.setExpectedValue(expected);
        return dto;
    }

    private static class CapturingService extends PostHardConditionRuleServiceImpl {
        private PostHardConditionRule existing;
        private PostHardConditionRule savedSingle;
        private List<PostHardConditionRule> savedBatch = new ArrayList<>();

        @Override
        public PostHardConditionRule getById(java.io.Serializable id) {
            return existing;
        }

        @Override
        public boolean saveOrUpdate(PostHardConditionRule entity) {
            savedSingle = entity;
            return true;
        }

        @Override
        public boolean saveBatch(java.util.Collection<PostHardConditionRule> entities) {
            savedBatch = new ArrayList<>(entities);
            return true;
        }
    }
}
