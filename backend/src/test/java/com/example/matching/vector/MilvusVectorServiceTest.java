package com.example.matching.vector;

import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.system.AbilityTagMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MilvusVectorServiceTest {

    @Test
    void resolvesAllDistinctAbilityTagsWithOneBatchQuery() {
        AbilityTagMapper mapper = mock(AbilityTagMapper.class);
        AbilityTag java = new AbilityTag();
        java.setId(1L);
        java.setTagName("Java");
        AbilityTag spring = new AbilityTag();
        spring.setId(2L);
        spring.setTagName("Spring");
        when(mapper.selectBatchIds(List.of(1L, 2L))).thenReturn(List.of(java, spring));

        MilvusVectorService service = new MilvusVectorService();
        ReflectionTestUtils.setField(service, "abilityTagMapper", mapper);
        @SuppressWarnings("unchecked")
        Map<Long, String> names = (Map<Long, String>) ReflectionTestUtils.invokeMethod(
                service, "resolveTagNames", List.of(1L, 2L, 1L));

        assertThat(names).containsEntry(1L, "Java").containsEntry(2L, "Spring");
        verify(mapper).selectBatchIds(List.of(1L, 2L));
    }
}
