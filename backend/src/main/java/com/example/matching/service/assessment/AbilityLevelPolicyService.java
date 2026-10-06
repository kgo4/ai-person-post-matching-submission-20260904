package com.example.matching.service.assessment;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 能力等级确认策略服务接口
 * <p>
 * 等级确认中心的全部系数与规则配置化，支持版本快照。
 *
 * @author system
 */
public interface AbilityLevelPolicyService {

    /**
     * 当前生效的策略配置（无配置时返回默认策略）。
     */
    LevelPolicy getActivePolicy();

    /**
     * 按版本获取策略配置（无则返回默认）。
     */
    LevelPolicy getPolicy(String version);

    /**
     * 等级确认策略配置
     */
    final class LevelPolicy {
        private final String policyVersion;
        private final String policyName;
        private final int conflictThreshold;
        private final int level4MinIndependentSources;
        private final double highCredibilityThreshold;
        private final BigDecimal autoConfirmWeightThreshold;
        private final BigDecimal reviewWeightThreshold;
        private final Map<String, Integer> singleSourceLevelCeiling;

        public LevelPolicy(String policyVersion, String policyName, int conflictThreshold,
                           int level4MinIndependentSources, double highCredibilityThreshold,
                           BigDecimal autoConfirmWeightThreshold, BigDecimal reviewWeightThreshold,
                           Map<String, Integer> singleSourceLevelCeiling) {
            this.policyVersion = policyVersion;
            this.policyName = policyName;
            this.conflictThreshold = conflictThreshold;
            this.level4MinIndependentSources = level4MinIndependentSources;
            this.highCredibilityThreshold = highCredibilityThreshold;
            this.autoConfirmWeightThreshold = autoConfirmWeightThreshold;
            this.reviewWeightThreshold = reviewWeightThreshold;
            this.singleSourceLevelCeiling = singleSourceLevelCeiling;
        }

        public String getPolicyVersion() {
            return policyVersion;
        }

        public String getPolicyName() {
            return policyName;
        }

        public int getConflictThreshold() {
            return conflictThreshold;
        }

        public int getLevel4MinIndependentSources() {
            return level4MinIndependentSources;
        }

        public double getHighCredibilityThreshold() {
            return highCredibilityThreshold;
        }

        public BigDecimal getAutoConfirmWeightThreshold() {
            return autoConfirmWeightThreshold;
        }

        public BigDecimal getReviewWeightThreshold() {
            return reviewWeightThreshold;
        }

        public Map<String, Integer> getSingleSourceLevelCeiling() {
            return singleSourceLevelCeiling;
        }

        /** 策略快照 JSON（写入决策记录的 policySnapshotJson） */
        public String toSnapshotJson() {
            StringBuilder ceiling = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Integer> entry : singleSourceLevelCeiling.entrySet()) {
                if (!first) {
                    ceiling.append(',');
                }
                first = false;
                ceiling.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
            }
            ceiling.append('}');
            return "{\"policyVersion\":\"" + policyVersion + "\","
                    + "\"policyName\":\"" + policyName + "\","
                    + "\"conflictThreshold\":" + conflictThreshold + ","
                    + "\"level4MinIndependentSources\":" + level4MinIndependentSources + ","
                    + "\"highCredibilityThreshold\":" + highCredibilityThreshold + ","
                    + "\"autoConfirmWeightThreshold\":" + autoConfirmWeightThreshold + ","
                    + "\"reviewWeightThreshold\":" + reviewWeightThreshold + ","
                    + "\"singleSourceLevelCeiling\":" + ceiling + "}";
        }
    }
}
