package com.example.matching.agent.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.entity.learning.LearningResource;
import com.example.matching.mapper.learning.LearningResourceMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("workflow3")
@ExtendWith(MockitoExtension.class)
class LearningResourceToolTest {

    @Mock
    private LearningResourceMapper learningResourceMapper;

    @InjectMocks
    private LearningResourceTool tool;

    @Test
    void searchLearningResourcesShouldReturnNoResultsFoundWhenEmpty() {
        when(learningResourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<Map<String, Object>> result = tool.searchLearningResources("nonexistent");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("available", false);
        assertThat(result.get(0)).containsEntry("reason", "no_results_found");
    }

    @Test
    void searchLearningResourcesShouldReturnExpectedStructure() {
        LearningResource resource = new LearningResource();
        resource.setId(1L);
        resource.setResourceCode("LC-JAVA-001");
        resource.setAbilityName("Java");
        resource.setTagId(100L);
        resource.setTitle("Java核心编程");
        resource.setResourceType("COURSE");
        resource.setDifficultyLevel(3);
        resource.setUrl("https://example.com/java");
        resource.setDescription("Learn Java");
        resource.setPlatform("Udemy");
        resource.setPlatformIcon("https://icon.example.com/udemy.png");
        resource.setCoverImageUrl("https://img.example.com/java.jpg");
        resource.setDuration("10h");

        when(learningResourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(resource));

        List<Map<String, Object>> result = tool.searchLearningResources("Java");

        assertThat(result).hasSize(1);
        Map<String, Object> item = result.get(0);
        assertThat(item).containsEntry("id", 1L);
        assertThat(item).containsEntry("resourceCode", "LC-JAVA-001");
        assertThat(item).containsEntry("abilityName", "Java");
        assertThat(item).containsEntry("title", "Java核心编程");
        assertThat(item).containsEntry("resourceType", "COURSE");
    }

    @Test
    void searchByTagIdShouldReturnNoResultsFoundWhenEmpty() {
        when(learningResourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<Map<String, Object>> result = tool.searchByTagId(999L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("available", false);
        assertThat(result.get(0)).containsEntry("reason", "no_results_found");
    }

    @Test
    void searchByTagIdShouldReturnExpectedStructure() {
        LearningResource resource = new LearningResource();
        resource.setId(2L);
        resource.setResourceCode("LC-PY-001");
        resource.setAbilityName("Python");
        resource.setTitle("Python入门");

        when(learningResourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(resource));

        List<Map<String, Object>> result = tool.searchByTagId(200L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("id", 2L);
        assertThat(result.get(0)).containsEntry("abilityName", "Python");
    }

    @Test
    void getLearningResourceDetailShouldReturnUnavailableWhenNotFound() {
        when(learningResourceMapper.selectById(999L)).thenReturn(null);

        Map<String, Object> result = tool.getLearningResourceDetail(999L);

        assertThat(result).isNotNull();
        assertThat(result).containsEntry("available", false);
        assertThat(result).containsEntry("reason", "no_results_found");
    }

    @Test
    void getLearningResourceDetailShouldReturnExpectedStructure() {
        LearningResource resource = new LearningResource();
        resource.setId(3L);
        resource.setStatus(1);
        resource.setResourceCode("LC-GO-001");
        resource.setAbilityName("Go");
        resource.setTitle("Go语言实战");

        when(learningResourceMapper.selectById(3L)).thenReturn(resource);

        Map<String, Object> result = tool.getLearningResourceDetail(3L);

        assertThat(result).isNotNull();
        assertThat(result).containsEntry("id", 3L);
        assertThat(result).containsEntry("resourceCode", "LC-GO-001");
        assertThat(result).containsEntry("abilityName", "Go");
    }

    @Test
    void searchLearningResourcesShouldReturnUnavailableOnMapperException() {
        when(learningResourceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenThrow(new RuntimeException("database unavailable"));

        List<Map<String, Object>> result = tool.searchLearningResources("Java");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("available", false);
        assertThat(result.get(0)).containsEntry("reason", "learning_resource_unavailable");
    }

    @Test
    void searchByTagIdShouldReturnUnavailableOnMapperException() {
        when(learningResourceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenThrow(new RuntimeException("database unavailable"));

        List<Map<String, Object>> result = tool.searchByTagId(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("available", false);
        assertThat(result.get(0)).containsEntry("reason", "learning_resource_unavailable");
    }
}
