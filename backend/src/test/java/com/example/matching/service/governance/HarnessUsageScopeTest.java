package com.example.matching.service.governance;

import com.example.matching.service.closure.impl.ComprehensiveDiagnosisServiceImpl;
import com.example.matching.service.contest.report.impl.ContestReportServiceImpl;
import com.example.matching.service.governance.impl.GovernedAdmissionServiceImpl;
import com.example.matching.service.harness.AiTrustHarnessService;
import com.example.matching.service.learning.impl.AiLearningSuggestionValidatorImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessUsageScopeTest {

    @Test
    void nonAbilityGenerationFlowsDoNotDependOnHarness() {
        assertNoHarnessDependency(AiLearningSuggestionValidatorImpl.class);
        assertNoHarnessDependency(ComprehensiveDiagnosisServiceImpl.class);
        assertNoHarnessDependency(ContestReportServiceImpl.class);
        // 治理写入边界收敛到 GovernedAdmissionServiceImpl，其余服务不得直接依赖 Harness
        assertHasHarnessDependency(GovernedAdmissionServiceImpl.class);
    }

    private void assertNoHarnessDependency(Class<?> clazz) {
        Set<String> harnessTypes = Set.of(AiTrustHarnessService.class.getName());
        Set<String> injectedTypes = getInjectedTypes(clazz);
        assertThat(injectedTypes)
                .as("" + clazz.getSimpleName() + " should not depend on AiTrustHarnessService")
                .doesNotContainAnyElementsOf(harnessTypes);
    }

    private void assertHasHarnessDependency(Class<?> clazz) {
        Set<String> harnessTypes = Set.of(AiTrustHarnessService.class.getName());
        Set<String> injectedTypes = getInjectedTypes(clazz);
        assertThat(injectedTypes)
                .as("" + clazz.getSimpleName() + " should depend on AiTrustHarnessService")
                .containsAnyElementsOf(harnessTypes);
    }

    private Set<String> getInjectedTypes(Class<?> clazz) {
        Set<Class<?>> allTypes = Arrays.stream(clazz.getDeclaredFields())
                .map(Field::getType)
                .collect(Collectors.toSet());
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            allTypes.addAll(Arrays.asList(ctor.getParameterTypes()));
        }
        return allTypes.stream()
                .map(Class::getName)
                .collect(Collectors.toSet());
    }
}
