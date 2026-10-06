package com.example.matching.dto.matching;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 匹配执行请求DTO
 */
@Data
@Schema(description = "匹配执行请求，支持三种正式模式：SINGLE_EVAL / EMP_TO_POST / POST_TO_EMP")
public class MatchingExecuteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "正式匹配模式", example = "SINGLE_EVAL")
    private String mode;

    @Schema(description = "正式匹配对列表")
    private List<MatchingPair> pairs;

    @Deprecated
    @Schema(description = "旧版接口字段：单个岗位ID，兼容迁移期保留")
    private Long postId;

    @Deprecated
    @Schema(description = "旧版接口字段：员工ID列表，兼容迁移期保留")
    private List<Long> empIds;

    @Schema(description = "匹配算法策略", example = "threeLevel")
    private String matchStrategy;

    @Schema(description = "L1硬性条件列表")
    private List<HardCondition> hardConditions;

    @Schema(description = "是否启用L3 AI深度匹配", example = "false")
    private Boolean enableAiMatching;

    @Schema(description = "Force L3 AI scoring even when the automatic threshold is not met", example = "false")
    private Boolean forceAiMatching;

    @Schema(description = "AI分析人数上限", example = "5")
    private Integer aiTopN;

    @Schema(description = "触发AI分析的L2最低分阈值", example = "60")
    private Integer aiThreshold;

    @Schema(description = "候选范围：ALL_ACTIVE(默认)/VECTOR_RECALL/EXPLICIT_EMPLOYEES", example = "ALL_ACTIVE")
    private CandidateScope candidateScope = CandidateScope.ALL_ACTIVE;

    /** 内部标记：本次执行来自异步 MatchingTask 消费端，禁止再次转交异步任务（防循环） */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean taskExecution;

    /** 关联的异步任务ID（内部使用，listener 消费时以任务快照设置，用于执行中检查取消/删除） */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String taskId;

    /** 匹配批次号（16位）：异步任务由 submitTask 生成并写入 matching_task.batch_no，消费端以任务为准对齐；同步执行时为空则内部新生成 */
    @Schema(description = "匹配批次号（内部使用，异步任务消费端强制对齐任务快照）", hidden = true)
    private String batchNo;

    public List<MatchingPair> normalizedPairs() {
        if (pairs != null && !pairs.isEmpty()) {
            return pairs;
        }
        if (postId != null && empIds != null && !empIds.isEmpty()) {
            List<MatchingPair> legacyPairs = new ArrayList<>(empIds.size());
            for (Long empId : empIds) {
                MatchingPair pair = new MatchingPair();
                pair.setEmpId(empId);
                pair.setPostId(postId);
                legacyPairs.add(pair);
            }
            return legacyPairs;
        }
        return List.of();
    }

    public String normalizedMode() {
        if (mode != null && !mode.isBlank()) {
            return mode.trim().toUpperCase(Locale.ROOT);
        }
        if (pairs != null && !pairs.isEmpty()) {
            if (pairs.size() == 1) {
                return "SINGLE_EVAL";
            }
            Set<Long> empIdSet = new LinkedHashSet<>();
            Set<Long> postIdSet = new LinkedHashSet<>();
            for (MatchingPair pair : pairs) {
                if (pair != null) {
                    empIdSet.add(pair.getEmpId());
                    postIdSet.add(pair.getPostId());
                }
            }
            if (empIdSet.size() == 1) {
                return "EMP_TO_POST";
            }
            if (postIdSet.size() == 1) {
                return "POST_TO_EMP";
            }
            return "POST_TO_EMP";
        }
        if (postId != null && empIds != null && !empIds.isEmpty()) {
            return empIds.size() == 1 ? "SINGLE_EVAL" : "POST_TO_EMP";
        }
        return null;
    }

    public Set<Long> normalizedEmpIds() {
        Set<Long> empIdSet = new LinkedHashSet<>();
        for (MatchingPair pair : normalizedPairs()) {
            if (pair != null && pair.getEmpId() != null) {
                empIdSet.add(pair.getEmpId());
            }
        }
        return empIdSet;
    }

    public Set<Long> normalizedPostIds() {
        Set<Long> postIdSet = new LinkedHashSet<>();
        for (MatchingPair pair : normalizedPairs()) {
            if (pair != null && pair.getPostId() != null) {
                postIdSet.add(pair.getPostId());
            }
        }
        return postIdSet;
    }

    /**
     * 硬性条件定义
     */
    @Data
    @Schema(description = "硬性条件：字段 + 操作符 + 值，全部满足才通过L1")
    public static class HardCondition implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "条件字段，例如 education / gender", example = "education")
        private String field;

        @Schema(description = "操作符：gte/lte/eq/neq/in", example = "gte")
        private String operator;

        @Schema(description = "条件值", example = "本科")
        private String value;

        @Schema(description = "字段类型：text/number/select/date/rank", example = "rank")
        private String fieldType;

        @Schema(description = "枚举等级映射JSON，仅 rank 类型使用", example = "{\"大专\":2,\"本科\":3}")
        private String valueRankJson;

        @Schema(description = "条件描述", example = "学历不低于本科")
        private String label;
    }

    @Data
    @Schema(description = "正式匹配对：一个员工和一个岗位")
    public static class MatchingPair implements Serializable {
        private static final long serialVersionUID = 1L;

        @NotNull(message = "员工ID不能为空")
        @Schema(description = "员工ID", example = "10001")
        private Long empId;

        @NotNull(message = "岗位ID不能为空")
        @Schema(description = "岗位ID", example = "20001")
        private Long postId;
    }
}
