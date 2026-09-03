package com.example.matching.agent.tools;

import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PostRequirementToolTest {

    @Test
    void getPostInfoReturnsStructuredNotFoundResult() {
        PostPostMapper postMapper = mock(PostPostMapper.class);
        PostRequirementTool tool = new PostRequirementTool(postMapper,
                mock(PostAbilityModelMapper.class), mock(AbilityTagMapper.class));

        Map<String, Object> result = tool.getPostInfo(99L);

        assertThat(result.get("found")).isEqualTo(false);
        assertThat(result.get("available")).isEqualTo(true);
        assertThat(result.get("reason")).isNotNull();
    }

    @Test
    void getPostRequirementsLoadsTagNamesInOneBatch() {
        PostAbilityModelMapper requirementMapper = mock(PostAbilityModelMapper.class);
        AbilityTagMapper tagMapper = mock(AbilityTagMapper.class);
        PostRequirementTool tool = new PostRequirementTool(mock(PostPostMapper.class), requirementMapper, tagMapper);

        PostAbilityModel javaRequirement = new PostAbilityModel();
        javaRequirement.setId(1L);
        javaRequirement.setTagId(101L);
        PostAbilityModel sqlRequirement = new PostAbilityModel();
        sqlRequirement.setId(2L);
        sqlRequirement.setTagId(102L);
        when(requirementMapper.selectList(any())).thenReturn(List.of(javaRequirement, sqlRequirement));

        AbilityTag javaTag = new AbilityTag();
        javaTag.setId(101L);
        javaTag.setTagName("Java");
        AbilityTag sqlTag = new AbilityTag();
        sqlTag.setId(102L);
        sqlTag.setTagName("SQL");
        when(tagMapper.selectBatchIds(List.of(101L, 102L))).thenReturn(List.of(javaTag, sqlTag));

        Map<String, Object> result = tool.getPostRequirements(9L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> requirements = (List<Map<String, Object>>) result.get("items");
        assertThat(requirements).extracting(item -> item.get("abilityName"))
                .containsExactly("Java", "SQL");
        verify(tagMapper).selectBatchIds(List.of(101L, 102L));
        verify(tagMapper, never()).selectById(any());
    }

    @Test
    void getPostInfoReturnsStructuredErrorWhenDbFails() {
        PostPostMapper postMapper = mock(PostPostMapper.class);
        when(postMapper.selectById(7L)).thenThrow(new RuntimeException("connection refused"));
        PostRequirementTool tool = new PostRequirementTool(postMapper,
                mock(PostAbilityModelMapper.class), mock(AbilityTagMapper.class));

        Map<String, Object> result = tool.getPostInfo(7L);

        assertThat(result.get("found")).isEqualTo(false);
        assertThat(result.get("reason")).isNotNull();
    }

    @Test
    void getPostRequirementsReturnsStructuredErrorWhenDbFails() {
        PostAbilityModelMapper requirementMapper = mock(PostAbilityModelMapper.class);
        when(requirementMapper.selectList(any())).thenThrow(new RuntimeException("connection refused"));
        PostRequirementTool tool = new PostRequirementTool(mock(PostPostMapper.class), requirementMapper,
                mock(AbilityTagMapper.class));

        Map<String, Object> result = tool.getPostRequirements(9L);

        assertThat(result.get("reason")).isNotNull();
        assertThat(result.get("items")).isNotNull();
    }
}
