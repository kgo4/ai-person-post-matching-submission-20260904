package com.example.matching.service.system;

import com.example.matching.service.matching.TagCanonicalResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TagCanonicalCacheInvalidatorWiringTest {

    @Test
    void matchingResolverProvidesSystemCanonicalCacheInvalidationContract() {
        assertThat(TagCanonicalCacheInvalidator.class)
                .isAssignableFrom(TagCanonicalResolver.class);
    }
}
