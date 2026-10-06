package com.example.matching.dto.closure;

import com.example.matching.dto.learning.LearningPathItemDTO;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class MatchDiagnosisResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long matchingRecordId;

    private Long empId;

    private Long postId;

    private List<GapItem> gaps = new ArrayList<>();

    private List<LearningPathItemDTO> learningPath = new ArrayList<>();

    @Data
    public static class GapItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long tagId;

        private String abilityName;

        private BigDecimal currentLevel;

        private Integer requiredLevel;

        private boolean weakEvidence;

        private String reason;
    }
}
