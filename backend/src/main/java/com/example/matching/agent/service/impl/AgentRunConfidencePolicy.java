package com.example.matching.agent.service.impl;

import com.example.matching.agent.dto.AgentSourceRef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Component
public class AgentRunConfidencePolicy {

    private static final BigDecimal MAX_CONFIDENCE = new BigDecimal("100");
    private static final BigDecimal FALLBACK_CAP = new BigDecimal("60.00");

    /**
     * Calculate overall confidence as weighted mean of non-null sourceRef confidence scores.
     * Returns null when no references exist.
     * Caps fallback runs at 60.
     */
    public BigDecimal calculate(List<AgentSourceRef> sourceRefs, boolean fallbackUsed) {
        if (sourceRefs == null || sourceRefs.isEmpty()) {
            return null;
        }

        List<AgentSourceRef> scored = sourceRefs.stream()
                .filter(ref -> ref.getConfidenceScore() != null)
                .toList();

        if (scored.isEmpty()) {
            return null;
        }

        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (AgentSourceRef ref : scored) {
            BigDecimal score = ref.getConfidenceScore();
            if (score != null) {
                sum = sum.add(score);
                count++;
            }
        }

        BigDecimal mean = count > 0
                ? sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : null;

        if (fallbackUsed && mean != null && mean.compareTo(FALLBACK_CAP) > 0) {
            return FALLBACK_CAP;
        }

        return mean;
    }
}
