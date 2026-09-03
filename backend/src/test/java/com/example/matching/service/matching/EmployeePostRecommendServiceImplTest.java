package com.example.matching.service.matching;

import com.example.matching.service.matching.impl.EmployeePostRecommendServiceImpl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.matching.dto.matching.PostRecommendDTO;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.post.PostHardConditionRuleService;
import com.example.matching.service.matching.FeedbackCalibrationService;
import com.example.matching.service.matching.MatchingScoreService;
import com.example.matching.service.matching.MatchingTrainingWeightProfileStore.WeightProfile;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.vector.MilvusVectorService;
import com.example.matching.resilience.AiServiceResilience;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class EmployeePostRecommendServiceImplTest {

    @Mock
    private MilvusVectorService milvusVectorService;
    @Mock
    private MatchingTrainingWeightProfileStore weightProfileStore;
    @Mock
    private MatchingScoreService matchingScoreService;
    @Mock
    private MatchingAlgorithmService matchingAlgorithmService;
    @Mock
    private PostHardConditionRuleService postHardConditionRuleService;
    @Mock
    private EmpEmployeeMapper empEmployeeMapper;
    @Mock
    private EmpAbilityMapper empAbilityMapper;
    @Mock
    private PostPostMapper postPostMapper;
    @Mock
    private PostAbilityModelMapper postAbilityModelMapper;
    @Mock
    private AbilityTagMapper abilityTagMapper;
    @Mock
    private EmpResumeParseMapper empResumeParseMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private PostAbilityModelService postAbilityModelService;
    @Mock
    private FeedbackCalibrationService feedbackCalibrationService;
    @Mock
    private AiServiceResilience aiServiceResilience;

    @Test
    void fallsBackToActivePostsWhenMilvusReturnsNoVectorResults() {
        EmployeePostRecommendServiceImpl service = createService();

        EmpEmployee emp = new EmpEmployee();
        emp.setId(1001L);
        emp.setRealName("Alice");
        when(empEmployeeMapper.selectById(1001L)).thenReturn(emp);
        when(empAbilityMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(empResumeParseMapper.selectOne(any())).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(milvusVectorService.searchPostsForEmployee(any(), anyInt())).thenReturn(Collections.emptyList());

        PostPost post = new PostPost();
        post.setId(2001L);
        post.setPostName("Java Developer");
        post.setStatus(1);
        when(postPostMapper.selectList(any(Wrapper.class))).thenReturn(List.of(post));
        when(postAbilityModelMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        when(weightProfileStore.currentProfile()).thenReturn(WeightProfile.defaultProfile());
        when(matchingScoreService.score(any())).thenReturn(defaultScoreResult());

        PostRecommendDTO.Request request = new PostRecommendDTO.Request();
        request.setEmpId(1001L);
        request.setTopK(5);

        PostRecommendDTO.Response response = service.recommendPostsForEmployee(request);

        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getRecommendations().get(0).getPostId()).isEqualTo(2001L);
        assertThat(response.getRecommendations().get(0).getVectorScore()).isNull();
        // M7：向量分缺失时显式标记近似值，不伪造
        assertThat(response.getRecommendations().get(0).isApproximate()).isTrue();
        verify(matchingScoreService).score(any());
    }

    @Test
    void strictHardConditionModeFiltersFailedPostCandidates() {
        EmployeePostRecommendServiceImpl service = createService();

        EmpEmployee emp = new EmpEmployee();
        emp.setId(1001L);
        emp.setRealName("Alice");
        when(empEmployeeMapper.selectById(1001L)).thenReturn(emp);
        when(empAbilityMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(empResumeParseMapper.selectOne(any())).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(milvusVectorService.searchPostsForEmployee(any(), anyInt())).thenReturn(Collections.emptyList());

        PostPost post = new PostPost();
        post.setId(2001L);
        post.setPostName("Java Developer");
        post.setStatus(1);
        when(postPostMapper.selectList(any())).thenReturn(List.of(post));
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(postHardConditionRuleService.toHardConditions(anyLong())).thenReturn(List.of(new com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition()));
        when(matchingAlgorithmService.checkHardConditions(any(), any(), any())).thenReturn(failedHardCondition());
        lenient().when(weightProfileStore.currentProfile()).thenReturn(WeightProfile.defaultProfile());

        PostRecommendDTO.Request request = new PostRecommendDTO.Request();
        request.setEmpId(1001L);
        request.setTopK(5);
        request.setStrictHardConditionMode(true);

        PostRecommendDTO.Response response = service.recommendPostsForEmployee(request);

        assertThat(response.getRecommendations()).isEmpty();
    }

    @Test
    void previewUsesRealModelQualityScoreAndFeedbackCalibration() {
        EmployeePostRecommendServiceImpl service = createService();

        EmpEmployee emp = new EmpEmployee();
        emp.setId(1001L);
        emp.setRealName("Alice");
        when(empEmployeeMapper.selectById(1001L)).thenReturn(emp);
        when(empAbilityMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(empResumeParseMapper.selectOne(any())).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(milvusVectorService.searchPostsForEmployee(any(), anyInt())).thenReturn(Collections.emptyList());

        PostPost post = new PostPost();
        post.setId(2001L);
        post.setPostName("Java Developer");
        post.setStatus(1);
        when(postPostMapper.selectList(any(Wrapper.class))).thenReturn(List.of(post));
        when(postAbilityModelMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        when(weightProfileStore.currentProfile()).thenReturn(WeightProfile.defaultProfile());
        when(matchingScoreService.score(any())).thenReturn(defaultScoreResult());
        // 岗位质量和反馈元数据可保留给报告，但不能改变统一正式评分。
        when(postAbilityModelService.calculateQualityScore(2001L)).thenReturn(new java.math.BigDecimal("90.00"));
        when(feedbackCalibrationService.calculateCalibration(2001L)).thenReturn(new java.math.BigDecimal("3.00"));

        PostRecommendDTO.Request request = new PostRecommendDTO.Request();
        request.setEmpId(1001L);
        request.setTopK(5);

        service.recommendPostsForEmployee(request);

        org.mockito.ArgumentCaptor<MatchScoreInput> captor =
                org.mockito.ArgumentCaptor.forClass(MatchScoreInput.class);
        verify(matchingScoreService).score(captor.capture());
        MatchScoreInput input = captor.getValue();
        assertThat(input.weightProfile().getAbilityWeight()).isEqualTo(0.65d);
        assertThat(input.aiScore()).isNull();
    }

    private EmployeePostRecommendServiceImpl createService() {
        doAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get())
                .when(aiServiceResilience).callWithResilienceOrThrow(any(), any(), anyLong());
        return new EmployeePostRecommendServiceImpl(
                milvusVectorService,
                matchingAlgorithmService,
                postHardConditionRuleService,
                empEmployeeMapper,
                empAbilityMapper,
                postPostMapper,
                postAbilityModelMapper,
                abilityTagMapper,
                empResumeParseMapper,
                new ObjectMapper(),
                redisTemplate,
                new MatchingProfileTextBuilder(new ObjectMapper()),
                weightProfileStore,
                matchingScoreService,
                postAbilityModelService,
                feedbackCalibrationService,
                aiServiceResilience
        );
    }

    private MatchScoreResult defaultScoreResult() {
        return new MatchScoreResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, false, MatchScoreResult.CURRENT_VERSION);
    }

    private MatchingAlgorithmService.HardConditionResult failedHardCondition() {
        MatchingAlgorithmService.HardConditionResult result = new MatchingAlgorithmService.HardConditionResult();
        result.setPassed(false);
        MatchingAlgorithmService.ConditionDetail detail = new MatchingAlgorithmService.ConditionDetail();
        detail.setField("education");
        detail.setPassed(false);
        result.setDetails(List.of(detail));
        return result;
    }
}
