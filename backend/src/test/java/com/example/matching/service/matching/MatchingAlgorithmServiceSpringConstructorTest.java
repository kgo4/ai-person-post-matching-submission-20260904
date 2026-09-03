package com.example.matching.service.matching;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.service.matching.TagCanonicalResolver;
import com.example.matching.service.matching.impl.MatchingAlgorithmServiceImpl;
import com.example.matching.service.system.SourceWeightConfigService;
import com.example.matching.service.system.SourceWeightResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MatchingAlgorithmServiceSpringConstructorTest {

    @Test
    void springComponentScanCreatesAllMatchingAlgorithmComponents() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TagCanonicalResolver.class, () -> mock(TagCanonicalResolver.class));
            context.registerBean(VectorEmbeddingService.class, () -> mock(VectorEmbeddingService.class));
            context.registerBean(TagQueryPort.class, () -> mock(TagQueryPort.class));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(RedisTemplate.class, () -> mock(RedisTemplate.class));
            context.registerBean(SourceWeightResolver.class,
                    () -> new SourceWeightResolver(mock(SourceWeightConfigService.class)));
            context.scan("com.example.matching.service.matching.algorithm");
            context.register(MatchingAlgorithmServiceImpl.class);

            context.refresh();

            assertThat(context.getBean(MatchingAlgorithmService.class)).isNotNull();
        }
    }
}
