package com.example.matching.service.system;

import com.example.matching.service.system.impl.AbilityTagNormalizerImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbilityTagNormalizerImplTest {

    private final AbilityTagNormalizer normalizer = new AbilityTagNormalizerImpl();

    @Test
    void preservesPunctuationThatIsPartOfTechnicalIdentifiers() {
        assertEquals("C++", normalizer.normalize("C++"));
        assertEquals("C#", normalizer.normalize("C#"));
        assertEquals(".NET", normalizer.normalize(".NET"));
        assertEquals("Node.js", normalizer.normalize("Node.js"));
    }
}
