package com.example.matching.service.ability;

import com.example.matching.entity.ability.AgentMemory;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMemoryLifecycleContractTest {

    @Test
    void memoryStoresAnEmbeddingForSemanticRetrieval() {
        Field embedding = Arrays.stream(AgentMemory.class.getDeclaredFields())
                .filter(field -> field.getName().equals("embeddingVector"))
                .findFirst()
                .orElse(null);

        assertThat(embedding).isNotNull();
        assertThat(embedding.getGenericType().getTypeName())
                .isEqualTo("java.util.List<java.lang.Float>");
    }

    @Test
    void dueMemoryExpirationIsScheduled() throws Exception {
        Class<?> scheduler = Class.forName("com.example.matching.schedule.AgentMemoryExpirationScheduler");
        Method method = scheduler.getDeclaredMethod("expireDueMemories");

        assertThat(method.getAnnotation(Scheduled.class)).isNotNull();
    }
}
