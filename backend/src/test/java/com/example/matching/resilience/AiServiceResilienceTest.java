package com.example.matching.resilience;

import com.example.matching.ai.validation.DeterministicAiFallbacks;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class AiServiceResilienceTest {

    @Test
    void perCallTimeoutCanExceedGlobalTimeout() throws Exception {
        AiServiceResilience resilience = new AiServiceResilience(CircuitBreakerRegistry.ofDefaults(), java.util.concurrent.Executors.newSingleThreadExecutor());
        ReflectionTestUtils.setField(resilience, "timeoutSeconds", 1L);

        Method method = AiServiceResilience.class.getMethod("callWithResilience",
                String.class, Supplier.class, String.class, long.class);
        String result = (String) method.invoke(resilience, "ai-test", (Supplier<String>) () -> {
            try {
                Thread.sleep(1_200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return "ai-response";
        }, DeterministicAiFallbacks.AI_TEST_QUESTIONS, 2L);

        assertThat(result).isEqualTo("ai-response");
    }
}
