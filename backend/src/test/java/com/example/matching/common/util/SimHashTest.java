package com.example.matching.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimHashTest {

    private static final String TEMPLATE =
            "负责Java后端开发，熟悉Spring Boot、MySQL，本科及以上学历，3年以上工作经验";
    private static final String TEMPLATE_REWRITE =
            "负责Java后端研发，熟悉Spring Boot与MySQL，本科及以上学历，3年以上工作经验";
    private static final String UNRELATED =
            "负责市场推广，制定品牌策略，策划线上线下活动，维护客户关系与渠道资源";

    @Test
    void computeIsDeterministic() {
        assertEquals(SimHash.compute(TEMPLATE), SimHash.compute(TEMPLATE));
    }

    @Test
    void emptyOrNullTextYieldsZero() {
        assertEquals(0L, SimHash.compute(null));
        assertEquals(0L, SimHash.compute(""));
        assertEquals(0L, SimHash.compute("   "));
    }

    @Test
    void normalizationIgnoresWhitespaceCaseAndPunctuation() {
        assertEquals(SimHash.compute("Java Spring Boot"),
                SimHash.compute("  java  spring boot "));
        assertEquals(SimHash.compute("Java，Spring"),
                SimHash.compute("Java Spring"));
    }

    @Test
    void hammingDistanceIsSymmetricAndZeroForSelf() {
        long a = SimHash.compute(TEMPLATE);
        long b = SimHash.compute(UNRELATED);
        assertEquals(0, SimHash.hammingDistance(a, a));
        assertEquals(SimHash.hammingDistance(a, b), SimHash.hammingDistance(b, a));
    }

    @Test
    void templateRewriteIsNearDuplicate() {
        long original = SimHash.compute(TEMPLATE);
        long rewrite = SimHash.compute(TEMPLATE_REWRITE);
        assertNotEquals(original, rewrite, "模板改写指纹不应完全相同（否则无需近似去重）");
        assertTrue(SimHash.hammingDistance(original, rewrite) <= SimHash.DEFAULT_HAMMING_THRESHOLD,
                "模板改写应被判定为近似重复，实际距离=" + SimHash.hammingDistance(original, rewrite));
        assertTrue(SimHash.isNearDuplicate(original, rewrite));
    }

    @Test
    void unrelatedTextIsNotNearDuplicate() {
        long original = SimHash.compute(TEMPLATE);
        long unrelated = SimHash.compute(UNRELATED);
        assertTrue(SimHash.hammingDistance(original, unrelated) > SimHash.DEFAULT_HAMMING_THRESHOLD,
                "完全不同的文本不应被判定为近似重复，实际距离=" + SimHash.hammingDistance(original, unrelated));
        assertFalse(SimHash.isNearDuplicate(original, unrelated));
    }

    @Test
    void zeroFingerprintIsNeverNearDuplicate() {
        assertFalse(SimHash.isNearDuplicate(0L, SimHash.compute(TEMPLATE)));
        assertFalse(SimHash.isNearDuplicate(SimHash.compute(TEMPLATE), 0L));
    }
}
