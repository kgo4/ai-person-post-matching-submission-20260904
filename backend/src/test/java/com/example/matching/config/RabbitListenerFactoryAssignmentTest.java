package com.example.matching.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitListenerFactoryAssignmentTest {

    private static final Set<String> SLOW_LISTENERS = Set.of(
        "MatchingTaskListener", "ResumeParseTaskListener", "ExcelImportAnalyzeListener",
        "PostEvolutionAgentTaskListener", "AiTestTaskListener", "GraphBuildTaskListener"
    );

    private static final Set<String> FAST_LISTENERS = Set.of(
        "GraphChangeSetListener"
    );

    @Test
    void slowListenersUseSlowFactory() throws Exception {
        for (String className : SLOW_LISTENERS) {
            Class<?> clazz = Class.forName("com.example.matching.listener." + className);
            boolean hasSlowAnnot = Arrays.stream(clazz.getDeclaredMethods())
                .flatMap(m -> Arrays.stream(m.getAnnotationsByType(RabbitListener.class)))
                .anyMatch(a -> "slowRabbitListenerContainerFactory".equals(a.containerFactory()));
            assertThat(hasSlowAnnot)
                .as(className + " should use slowRabbitListenerContainerFactory")
                .isTrue();
        }
    }

    @Test
    void fastListenersUseFastFactory() throws Exception {
        for (String className : FAST_LISTENERS) {
            Class<?> clazz = Class.forName("com.example.matching.listener." + className);
            boolean hasFastAnnot = Arrays.stream(clazz.getDeclaredMethods())
                .flatMap(m -> Arrays.stream(m.getAnnotationsByType(RabbitListener.class)))
                .anyMatch(a -> "fastRabbitListenerContainerFactory".equals(a.containerFactory()));
            assertThat(hasFastAnnot)
                .as(className + " should use fastRabbitListenerContainerFactory")
                .isTrue();
        }
    }

    @Test
    void eventListenerLearningProjectApprovedUsesDedicatedRetryFactory() throws Exception {
        Class<?> clazz = Class.forName("com.example.matching.event.listener.LearningProjectApprovedListener");
        boolean hasFastAnnot = Arrays.stream(clazz.getDeclaredMethods())
            .flatMap(m -> Arrays.stream(m.getAnnotationsByType(RabbitListener.class)))
            .anyMatch(a -> "learningOutcomeClosureRabbitListenerContainerFactory".equals(a.containerFactory()));
        assertThat(hasFastAnnot)
            .as("LearningProjectApprovedListener should use the dedicated retry factory")
            .isTrue();
    }
}
