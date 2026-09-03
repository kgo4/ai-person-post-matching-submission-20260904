package com.example.matching.controller.matching;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingFeedbackControllerContractTest {

    @Test
    void feedbackQueriesUseExportEnabledParameter() throws NoSuchMethodException {
        Method page = MatchingFeedbackController.class.getMethod(
                "page", long.class, long.class, Integer.class);
        Method export = MatchingFeedbackController.class.getMethod(
                "exportFeedback", Integer.class, jakarta.servlet.http.HttpServletResponse.class);

        assertThat(page.getParameters()[2].getName()).isEqualTo("exportEnabled");
        assertThat(export.getParameters()[0].getName()).isEqualTo("exportEnabled");
    }
}
