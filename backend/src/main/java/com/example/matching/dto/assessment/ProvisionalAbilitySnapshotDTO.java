package com.example.matching.dto.assessment;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 临时能力快照 DTO
 * <p>
 * 强制匹配时构建的一次性快照，仅用于软评分，不污染正式画像。
 *
 * @author system
 */
@Data
public class ProvisionalAbilitySnapshotDTO {

    /** 快照令牌 */
    private String snapshotToken;

    /** 员工ID */
    private Long empId;

    /** 快照创建时间 */
    private String createdAt;

    /** 策略版本（折减系数等） */
    private String policyVersion;

    /** 快照中的能力项 */
    private List<SnapshotAbilityItem> abilities = new ArrayList<>();

    /** 风险标记 */
    private List<String> riskFlags = new ArrayList<>();

    /**
     * 快照能力项
     */
    @Data
    public static class SnapshotAbilityItem {
        private Long claimGroupId;
        private Long tagId;
        private String abilityName;
        private Integer claimedLevel;
        private String evidenceStatus;
        private List<String> sourceTypes = new ArrayList<>();
        /** 软评分折减权重 0-1 */
        private Double softWeightFactor;
    }
}
