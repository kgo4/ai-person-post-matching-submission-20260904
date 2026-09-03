package com.example.matching.service.learning;

import com.example.matching.dto.learning.LearningPathItemDTO;
import com.example.matching.dto.learning.LearningPathRequestDTO;
import com.example.matching.entity.learning.LearningResource;
import com.example.matching.mapper.learning.LearningResourceMapper;
import com.example.matching.service.learning.impl.LearningPathServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 学习路径服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class LearningPathServiceTest {

    @Mock
    private LearningResourceMapper resourceMapper;

    @InjectMocks
    private LearningPathServiceImpl learningPathService;

    private LearningResource practiceResource;
    private LearningResource courseResource;
    private LearningResource bookResource;

    @BeforeEach
    void setUp() {
        practiceResource = new LearningResource();
        practiceResource.setId(1L);
        practiceResource.setAbilityName("Java");
        practiceResource.setTitle("Java实战练习");
        practiceResource.setResourceType("PRACTICE");
        practiceResource.setDifficultyLevel(3);
        practiceResource.setStatus(1);

        courseResource = new LearningResource();
        courseResource.setId(2L);
        courseResource.setAbilityName("Java");
        courseResource.setTitle("Java高级课程");
        courseResource.setResourceType("COURSE");
        courseResource.setDifficultyLevel(3);
        courseResource.setStatus(1);

        bookResource = new LearningResource();
        bookResource.setId(3L);
        bookResource.setAbilityName("Java");
        bookResource.setTitle("Java编程思想");
        bookResource.setResourceType("BOOK");
        bookResource.setDifficultyLevel(2);
        bookResource.setStatus(1);
    }

    @Test
    @DisplayName("精确标签匹配的资源排第一")
    void generateLearningPath_exactMatchRanksFirst() {
        LearningResource otherResource = new LearningResource();
        otherResource.setId(4L);
        otherResource.setAbilityName("JavaScript");
        otherResource.setTitle("JavaScript入门");
        otherResource.setResourceType("COURSE");
        otherResource.setDifficultyLevel(1);
        otherResource.setStatus(1);

        when(resourceMapper.selectList(any())).thenReturn(Arrays.asList(otherResource, practiceResource, courseResource));

        LearningPathRequestDTO request = new LearningPathRequestDTO();
        request.setAbilityNames(Collections.singletonList("Java"));
        request.setTargetLevel(3);

        List<LearningPathItemDTO> result = learningPathService.generateLearningPath(request);

        assertFalse(result.isEmpty());
        assertEquals("Java实战练习", result.get(0).getTitle());
    }

    @Test
    @DisplayName("实践/项目类型排在课程前面")
    void generateLearningPath_practiceRanksBeforeCourse() {
        when(resourceMapper.selectList(any())).thenReturn(Arrays.asList(courseResource, practiceResource));

        LearningPathRequestDTO request = new LearningPathRequestDTO();
        request.setAbilityNames(Collections.singletonList("Java"));
        request.setTargetLevel(3);

        List<LearningPathItemDTO> result = learningPathService.generateLearningPath(request);

        assertFalse(result.isEmpty());
        // PRACTICE 应排在 COURSE 前面
        assertEquals("PRACTICE", result.get(0).getResourceType());
    }

    @Test
    @DisplayName("空请求返回空结果")
    void generateLearningPath_emptyRequestReturnsEmpty() {
        LearningPathRequestDTO request = new LearningPathRequestDTO();
        request.setAbilityNames(Collections.emptyList());

        List<LearningPathItemDTO> result = learningPathService.generateLearningPath(request);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("无匹配资源返回占位项")
    void generateLearningPath_noMatchReturnsPlaceholder() {
        when(resourceMapper.selectList(any())).thenReturn(Collections.emptyList());

        LearningPathRequestDTO request = new LearningPathRequestDTO();
        request.setAbilityNames(Collections.singletonList("不存在的能力"));
        request.setTargetLevel(3);

        List<LearningPathItemDTO> result = learningPathService.generateLearningPath(request);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getTitle().contains("暂无"));
    }
}
