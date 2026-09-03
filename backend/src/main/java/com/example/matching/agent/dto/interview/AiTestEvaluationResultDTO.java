package com.example.matching.agent.dto.interview;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTestEvaluationResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer score;
    private Integer masteryLevel;
    private String status;
    private Integer suggestedLevel;
    private String analysisReport;
    private List<QuestionResult> questionResults;
    private List<String> sourceRefs;
    private List<String> supportingEvidenceRefs;
    private List<String> missingEvidence;
    private List<String> reasonCodes;
    private Boolean fallbackUsed;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer questionIndex;
        private Boolean isCorrect;
        private Integer score;
        private String comment;
        /** 同一回答对每个已绑定标签的独立核验结论。 */
        private List<TagEvaluation> tagEvaluations;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagEvaluation implements Serializable {
        private Long tagId;
        private Integer score;
        private Integer masteryLevel;
        private String status;
        private String evidenceText;
        private List<String> evidenceRefs;
    }

}
