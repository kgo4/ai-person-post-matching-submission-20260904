package com.example.matching.service.matching;

import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.mapper.matching.MatchingBlackWhiteListMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.matching.impl.MatchingDataQueryServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingDataQueryServiceTest {

    @Mock private EmpEmployeeMapper empEmployeeMapper;
    @Mock private EmpAbilityMapper empAbilityMapper;
    @Mock private EmpResumeParseMapper empResumeParseMapper;
    @Mock private PostPostMapper postPostMapper;
    @Mock private PostAbilityModelMapper postAbilityModelMapper;
    @Mock private AbilityTagMapper abilityTagMapper;
    @Mock private com.example.matching.port.employee.EmployeeAbilityReadPort employeeAbilityReadPort;
    @Mock private MatchingBlackWhiteListMapper blackWhiteListMapper;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private MatchingDataQueryServiceImpl service;

    @Test
    void batchLoadAbilities_excludesPendingProfilesFromMatching() {
        // PENDING 画像被端口排除 → 员工无权威能力
        when(employeeAbilityReadPort.loadAuthoritativeAbilities(List.of(1L)))
                .thenReturn(java.util.Map.of());

        var abilities = service.batchLoadAbilities(List.of(1L));

        assertThat(abilities).doesNotContainKey(1L);
    }

    @Test
    void listPostsByIdsLoadsAllRequestedPostsInOneQuery() {
        PostPost first = new PostPost();
        first.setId(1L);
        PostPost second = new PostPost();
        second.setId(2L);
        when(postPostMapper.selectList(any())).thenReturn(List.of(first, second));

        assertThat(service.listPostsByIds(List.of(1L, 2L))).containsExactly(first, second);
    }

}
