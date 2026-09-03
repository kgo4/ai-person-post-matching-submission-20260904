package com.example.matching.service.matching;

import com.example.matching.service.matching.impl.EmployeeRecommendServiceImpl;

import com.example.matching.dto.matching.EmployeeRecommendDTO;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.port.employee.EmployeeAbilityReadPort;
import com.example.matching.service.post.PostHardConditionRuleService;
import com.example.matching.vector.MilvusVectorService;
import com.example.matching.resilience.AiServiceResilience;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class EmployeeRecommendServiceImplTest {

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
    private PostPostMapper postPostMapper;
    @Mock
    private PostAbilityModelMapper postAbilityModelMapper;
    @Mock
    private AbilityTagMapper abilityTagMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private EmployeeAbilityReadPort employeeAbilityReadPort;
    @Mock
    private AiServiceResilience aiServiceResilience;

    @Test
    void fallsBackToActiveEmployeesWhenMilvusReturnsNoVectorResults() {
        EmployeeRecommendServiceImpl service = createService();

        PostPost post = new PostPost();
        post.setId(2001L);
        post.setPostName("Java Developer");
        post.setStatus(1);
        when(postPostMapper.selectById(2001L)).thenReturn(post);
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(milvusVectorService.searchEmployeesForPost(any(), anyInt())).thenReturn(Collections.emptyList());

        EmpEmployee emp = new EmpEmployee();
        emp.setId(1001L);
        emp.setRealName("Alice");
        when(empEmployeeMapper.selectList(any())).thenReturn(List.of(emp));
        // 无正式能力（权威端口为空）且无待确立能力
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(any())).thenReturn(Map.of());

        EmployeeRecommendDTO.Request request = new EmployeeRecommendDTO.Request();
        request.setPostId(2001L);
        request.setTopK(5);

        EmployeeRecommendDTO.Response response = service.recommendEmployeesForPost(request);

        // 无能力员工禁止匹配，不应出现在推荐列表
        assertThat(response.getRecommendations()).isEmpty();
    }

    @Test
    void employeeWithConfirmedAbilityIsRecommended() {
        EmployeeRecommendServiceImpl service = createService();

        PostPost post = new PostPost();
        post.setId(2001L);
        post.setPostName("Java Developer");
        post.setStatus(1);
        when(postPostMapper.selectById(2001L)).thenReturn(post);
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(milvusVectorService.searchEmployeesForPost(any(), anyInt())).thenReturn(Collections.emptyList());

        EmpEmployee emp = new EmpEmployee();
        emp.setId(1001L);
        emp.setRealName("Alice");
        when(empEmployeeMapper.selectList(any())).thenReturn(List.of(emp));
        // 仅 emp_ability 数据（无 person_ability_profile）也视为有正式能力
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(any()))
                .thenReturn(Map.of(1001L, List.of(snapshot(10L, 3))));
        when(weightProfileStore.currentProfile()).thenReturn(MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile());
        when(matchingScoreService.score(any())).thenReturn(defaultScoreResult());

        EmployeeRecommendDTO.Request request = new EmployeeRecommendDTO.Request();
        request.setPostId(2001L);
        request.setTopK(5);

        EmployeeRecommendDTO.Response response = service.recommendEmployeesForPost(request);

        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getRecommendations().get(0).getEmpId()).isEqualTo(1001L);
        assertThat(response.getRecommendations().get(0).getVectorScore()).isNull();
        verify(matchingScoreService).score(any());
    }

    @Test
    void employeeWithOnlyProvisionalAbilityIsExcluded() {
        EmployeeRecommendServiceImpl service = createService();

        PostPost post = new PostPost();
        post.setId(2001L);
        post.setPostName("Java Developer");
        post.setStatus(1);
        when(postPostMapper.selectById(2001L)).thenReturn(post);
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(milvusVectorService.searchEmployeesForPost(any(), anyInt())).thenReturn(Collections.emptyList());

        EmpEmployee emp = new EmpEmployee();
        emp.setId(1001L);
        emp.setRealName("Alice");
        when(empEmployeeMapper.selectList(any())).thenReturn(List.of(emp));
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(any())).thenReturn(Map.of());

        EmployeeRecommendDTO.Request request = new EmployeeRecommendDTO.Request();
        request.setPostId(2001L);
        request.setTopK(5);

        EmployeeRecommendDTO.Response response = service.recommendEmployeesForPost(request);

        assertThat(response.getRecommendations()).isEmpty();
    }

    @Test
    void strictHardConditionModeFiltersFailedEmployeeCandidates() {
        EmployeeRecommendServiceImpl service = createService();

        PostPost post = new PostPost();
        post.setId(2001L);
        post.setPostName("Java Developer");
        post.setStatus(1);
        when(postPostMapper.selectById(2001L)).thenReturn(post);
        when(postAbilityModelMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(milvusVectorService.searchEmployeesForPost(any(), anyInt())).thenReturn(Collections.emptyList());

        EmpEmployee emp = new EmpEmployee();
        emp.setId(1001L);
        emp.setRealName("Alice");
        when(empEmployeeMapper.selectList(any())).thenReturn(List.of(emp));
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(any()))
                .thenReturn(Map.of(1001L, List.of(snapshot(10L, 3))));
        when(postHardConditionRuleService.toHardConditions(anyLong())).thenReturn(List.of(new com.example.matching.dto.matching.MatchingExecuteDTO.HardCondition()));
        when(matchingAlgorithmService.checkHardConditions(any(), any(), any())).thenReturn(failedHardCondition());
        lenient().when(weightProfileStore.currentProfile()).thenReturn(MatchingTrainingWeightProfileStore.WeightProfile.defaultProfile());

        EmployeeRecommendDTO.Request request = new EmployeeRecommendDTO.Request();
        request.setPostId(2001L);
        request.setTopK(5);
        request.setStrictHardConditionMode(true);

        EmployeeRecommendDTO.Response response = service.recommendEmployeesForPost(request);

        assertThat(response.getRecommendations()).isEmpty();
    }

    private MatchingAbilitySnapshot snapshot(Long tagId, int level) {
        return new MatchingAbilitySnapshot(
                null, tagId, "Java", level, new BigDecimal("0.8"),
                "EMP_ABILITY", new BigDecimal("0.8"), LocalDate.now());
    }

    private EmployeeRecommendServiceImpl createService() {
        doAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get())
                .when(aiServiceResilience).callWithResilienceOrThrow(any(), any(), anyLong());
        return new EmployeeRecommendServiceImpl(
                milvusVectorService,
                matchingAlgorithmService,
                postHardConditionRuleService,
                empEmployeeMapper,
                postPostMapper,
                postAbilityModelMapper,
                abilityTagMapper,
                redisTemplate,
                new MatchingProfileTextBuilder(new com.fasterxml.jackson.databind.ObjectMapper()),
                weightProfileStore,
                matchingScoreService,
                employeeAbilityReadPort,
                aiServiceResilience
        );
    }

    private MatchScoreResult defaultScoreResult() {
        return new MatchScoreResult(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, false, MatchScoreResult.CURRENT_VERSION);
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
