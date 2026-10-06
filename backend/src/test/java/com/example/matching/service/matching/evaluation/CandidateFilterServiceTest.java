package com.example.matching.service.matching.evaluation;

import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.post.PostPostMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CandidateFilterServiceTest {

    private final EmpEmployeeMapper employeeMapper = mock(EmpEmployeeMapper.class);
    private final PostPostMapper postMapper = mock(PostPostMapper.class);
    private final CandidateFilterService service = new CandidateFilterService(employeeMapper, postMapper);

    @Test
    void skipsDatabaseWhenEmployeeCandidateIdsAreEmpty() {
        assertThat(service.filterActiveEmployees(null)).isEmpty();
        assertThat(service.filterActiveEmployees(List.of())).isEmpty();
        verifyNoInteractions(employeeMapper);
    }

    @Test
    void skipsDatabaseWhenPostCandidateIdsAreEmpty() {
        assertThat(service.filterActivePosts(null)).isEmpty();
        assertThat(service.filterActivePosts(List.of())).isEmpty();
        verifyNoInteractions(postMapper);
    }

    @Test
    void extractsOnlyParseableVectorReferenceIds() {
        List<Long> ids = service.extractCandidateIds(List.of(
                Map.of("refId", "12"),
                Map.of("refId", 34L),
                Map.of("refId", "not-a-number"),
                Map.of()));

        assertThat(ids).containsExactly(12L, 34L);
    }
}
