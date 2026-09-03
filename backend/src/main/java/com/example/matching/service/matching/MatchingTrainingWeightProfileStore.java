package com.example.matching.service.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 综合评分权重配置存储服务
 * <p>
 * 基于文件的运行时权重方案存储，支持每家公司独立部署时使用不同的权重配置。
 * <p>
 * 该类是 Spring 实例服务，所有方法都是实例方法，
 * 评分服务通过注入该服务来读取当前部署的权重配置。
 *
 * @author system
 */
@Slf4j
@Service
public class MatchingTrainingWeightProfileStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path DEFAULT_RUNTIME_DIR = Paths.get("runtime", "training-center");
    private static final Path DEFAULT_ACTIVE_PROFILE_PATH = DEFAULT_RUNTIME_DIR.resolve("active-weight-profile.json");

    private final Object lock = new Object();
    private volatile WeightProfile cachedProfile;

    @Value("${matching.weight-profile-path:}")
    private String configuredProfilePath;

    @Value("${matching.deployment-code:}")
    private String deploymentCode;

    /** 解析后的运行时目录（实例级别，非静态） */
    private volatile Path resolvedRuntimeDir;

    /** 解析后的权重文件路径（实例级别，非静态） */
    private volatile Path resolvedActiveProfilePath;

    @PostConstruct
    public void init() {
        if (configuredProfilePath != null && !configuredProfilePath.isBlank()) {
            Path customPath = Paths.get(configuredProfilePath);
            resolvedRuntimeDir = customPath.getParent();
            resolvedActiveProfilePath = customPath;
        } else if (deploymentCode != null && !deploymentCode.isBlank()) {
            resolvedRuntimeDir = DEFAULT_RUNTIME_DIR.resolve(deploymentCode);
            resolvedActiveProfilePath = resolvedRuntimeDir.resolve("active-weight-profile.json");
        } else {
            resolvedRuntimeDir = DEFAULT_RUNTIME_DIR;
            resolvedActiveProfilePath = DEFAULT_ACTIVE_PROFILE_PATH;
        }
        // 清除缓存，重新加载
        cachedProfile = null;
        log.info("权重配置存储初始化完成: path={}, deploymentCode={}", resolvedActiveProfilePath, deploymentCode);
    }

    /**
     * 保存当前激活的权重方案（原子写入：先写临时文件再 rename）
     */
    public void saveActiveProfile(WeightProfile profile) {
        validateUnifiedProfile(profile);
        synchronized (lock) {
            try {
                Path runtimeDir = getRuntimeDir();
                Path profilePath = getActiveProfilePath();
                Files.createDirectories(runtimeDir);
                Path tmpPath = profilePath.resolveSibling(profilePath.getFileName() + ".tmp");
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmpPath.toFile(), profile);
                Files.move(tmpPath, profilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                cachedProfile = profile;
                log.info("权重方案已保存: path={}", profilePath);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to save active training weight profile", e);
            }
        }
    }

    /**
     * 获取当前激活的权重方案，优先从缓存读取
     */
    public WeightProfile currentProfile() {
        WeightProfile current = cachedProfile;
        if (current != null) {
            return current;
        }
        synchronized (lock) {
            if (cachedProfile != null) {
                return cachedProfile;
            }
            Path profilePath = getActiveProfilePath();
            if (Files.exists(profilePath)) {
                try {
                    cachedProfile = normalizeUnifiedProfile(
                            OBJECT_MAPPER.readValue(profilePath.toFile(), WeightProfile.class));
                    return cachedProfile;
                } catch (IOException e) {
                    log.warn("读取权重配置文件失败: {}, 使用默认配置", profilePath);
                    cachedProfile = WeightProfile.defaultProfile();
                    return cachedProfile;
                }
            }
            cachedProfile = WeightProfile.defaultProfile();
            return cachedProfile;
        }
    }

    /**
     * 获取当前生效的权重文件路径
     */
    public Path getActiveProfilePath() {
        Path path = resolvedActiveProfilePath;
        return path != null ? path : DEFAULT_ACTIVE_PROFILE_PATH;
    }

    /**
     * 获取当前生效的运行时目录
     */
    public Path getRuntimeDir() {
        Path dir = resolvedRuntimeDir;
        return dir != null ? dir : DEFAULT_RUNTIME_DIR;
    }

    /**
     * 获取当前部署编码
     */
    public String getDeploymentCode() {
        return deploymentCode;
    }

    private WeightProfile normalizeUnifiedProfile(WeightProfile profile) {
        if (profile == null) {
            log.warn("匹配权重配置为空，使用统一默认权重 65/15/10/10: path={}", getActiveProfilePath());
            return WeightProfile.defaultProfile();
        }
        double total = profile.getAbilityWeight() + profile.getSemanticWeight()
                + profile.getEvidenceWeight() + profile.getAiWeight();
        if (Double.isFinite(total) && Math.abs(total - 1.0d) <= 0.000001d
                && profile.getAiWeight() <= 0.20d) {
            return profile;
        }
        log.warn("历史或无效匹配权重配置已忽略，使用统一默认权重 65/15/10/10: path={}",
                getActiveProfilePath());
        WeightProfile fallback = WeightProfile.defaultProfile();
        fallback.setVersion(profile.getVersion());
        return fallback;
    }

    private void validateUnifiedProfile(WeightProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("匹配权重配置不能为空");
        }
        double ability = profile.getAbilityWeight();
        double semantic = profile.getSemanticWeight();
        double evidence = profile.getEvidenceWeight();
        double ai = profile.getAiWeight();
        if (!validWeight(ability) || !validWeight(semantic) || !validWeight(evidence) || !validWeight(ai)
                || ai > 0.20d + 0.000001d) {
            throw new IllegalArgumentException("匹配权重必须在 0% 到 100% 之间，且 AI 权重不能超过 20%");
        }
        double total = ability + semantic + evidence + ai;
        if (Math.abs(total - 1.0d) > 0.000001d) {
            throw new IllegalArgumentException("能力、语义、证据和 AI 权重之和必须等于 100%，当前: " + (total * 100d) + "%");
        }
    }

    private boolean validWeight(double value) {
        return Double.isFinite(value) && value >= 0d && value <= 1d;
    }

    /**
     * 权重方案数据结构
     */
    public static final class WeightProfile {
        /** 权重方案版本标识 */
        private String version;
        /** 单层正式评分权重：四项必须合计 1.0。 */
        private double abilityWeight;
        private double semanticWeight;
        private double evidenceWeight;
        private double aiWeight;

        /*
         * 旧字段只用于读取既有运行时 JSON 配置，新的评分链路、配置 API 和页面均不再使用。
         * 下一次保存统一配置时，它们不会再被写入。
         */
        @Deprecated
        private double noLlmAbilityWeight;
        @Deprecated
        private double noLlmSemanticWeight;
        @Deprecated
        private double noLlmEvidenceWeight;
        @Deprecated
        private double withLlmAbilityWeight;
        @Deprecated
        private double withLlmSemanticWeight;
        @Deprecated
        private double withLlmEvidenceWeight;
        @Deprecated
        private double withLlmLlmWeight;
        private double qualityWeightNoLlm;
        private double qualityWeightWithLlm;
        private double feedbackScale;
        private double ragWeight;
        private Double l1Weight;
        private Double l2Weight;
        private Double l3Weight;
        private Boolean whitelistBypassHardRules;
        /** L2 策略：LENIENT / BALANCED / STRICT。 */
        private String l2MatchingMode;
        private Double requiredSemanticThreshold;
        private Double coreSemanticThreshold;
        private Double optionalSemanticThreshold;
        private Double similarTagMinimumConfidence;
        private Integer allowedLevelGap;
        private Double coreCoverageThreshold;
        private Double requiredCoverageThreshold;
        private Integer l2PassThreshold;
        private Integer aiTriggerThreshold;

        public static WeightProfile defaultProfile() {
            WeightProfile profile = new WeightProfile();
            profile.setAbilityWeight(0.65d);
            profile.setSemanticWeight(0.15d);
            profile.setEvidenceWeight(0.10d);
            profile.setAiWeight(0.10d);
            profile.setQualityWeightNoLlm(0d);
            profile.setQualityWeightWithLlm(0d);
            profile.setFeedbackScale(0d);
            profile.setRagWeight(0.0d); // RAG不参与排名，默认0
            profile.setL1Weight(0d);
            profile.setL2Weight(0d);
            profile.setL3Weight(0d);
            profile.setWhitelistBypassHardRules(true);
            profile.setL2MatchingMode("BALANCED");
            profile.applyL2ModeDefaults("BALANCED");
            return profile;
        }

        // ===== Getters & Setters =====

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public double getAbilityWeight() { return abilityWeight; }
        public void setAbilityWeight(double abilityWeight) { this.abilityWeight = abilityWeight; }

        public double getSemanticWeight() { return semanticWeight; }
        public void setSemanticWeight(double semanticWeight) { this.semanticWeight = semanticWeight; }

        public double getEvidenceWeight() { return evidenceWeight; }
        public void setEvidenceWeight(double evidenceWeight) { this.evidenceWeight = evidenceWeight; }

        public double getAiWeight() { return aiWeight; }
        public void setAiWeight(double aiWeight) { this.aiWeight = aiWeight; }

        public double getNoLlmAbilityWeight() { return noLlmAbilityWeight; }
        public void setNoLlmAbilityWeight(double noLlmAbilityWeight) { this.noLlmAbilityWeight = noLlmAbilityWeight; }

        public double getNoLlmSemanticWeight() { return noLlmSemanticWeight; }
        public void setNoLlmSemanticWeight(double noLlmSemanticWeight) { this.noLlmSemanticWeight = noLlmSemanticWeight; }

        public double getNoLlmEvidenceWeight() { return noLlmEvidenceWeight; }
        public void setNoLlmEvidenceWeight(double noLlmEvidenceWeight) { this.noLlmEvidenceWeight = noLlmEvidenceWeight; }

        public double getWithLlmAbilityWeight() { return withLlmAbilityWeight; }
        public void setWithLlmAbilityWeight(double withLlmAbilityWeight) { this.withLlmAbilityWeight = withLlmAbilityWeight; }

        public double getWithLlmSemanticWeight() { return withLlmSemanticWeight; }
        public void setWithLlmSemanticWeight(double withLlmSemanticWeight) { this.withLlmSemanticWeight = withLlmSemanticWeight; }

        public double getWithLlmEvidenceWeight() { return withLlmEvidenceWeight; }
        public void setWithLlmEvidenceWeight(double withLlmEvidenceWeight) { this.withLlmEvidenceWeight = withLlmEvidenceWeight; }

        public double getWithLlmLlmWeight() { return withLlmLlmWeight; }
        public void setWithLlmLlmWeight(double withLlmLlmWeight) { this.withLlmLlmWeight = withLlmLlmWeight; }

        public double getQualityWeightNoLlm() { return qualityWeightNoLlm; }
        public void setQualityWeightNoLlm(double qualityWeightNoLlm) { this.qualityWeightNoLlm = qualityWeightNoLlm; }

        public double getQualityWeightWithLlm() { return qualityWeightWithLlm; }
        public void setQualityWeightWithLlm(double qualityWeightWithLlm) { this.qualityWeightWithLlm = qualityWeightWithLlm; }

        public double getFeedbackScale() { return feedbackScale; }
        public void setFeedbackScale(double feedbackScale) { this.feedbackScale = feedbackScale; }

        public double getRagWeight() { return ragWeight; }
        public void setRagWeight(double ragWeight) { this.ragWeight = ragWeight; }

        public double getL1Weight() { return l1Weight != null ? l1Weight : 0.20d; }
        public void setL1Weight(double l1Weight) { this.l1Weight = l1Weight; }

        public double getL2Weight() { return l2Weight != null ? l2Weight : 0.60d; }
        public void setL2Weight(double l2Weight) { this.l2Weight = l2Weight; }

        public double getL3Weight() { return l3Weight != null ? l3Weight : 0.20d; }
        public void setL3Weight(double l3Weight) { this.l3Weight = l3Weight; }

        public boolean isWhitelistBypassHardRules() {
            return whitelistBypassHardRules == null || whitelistBypassHardRules;
        }
        public void setWhitelistBypassHardRules(boolean whitelistBypassHardRules) {
            this.whitelistBypassHardRules = whitelistBypassHardRules;
        }

        public String getL2MatchingMode() { return l2MatchingMode == null ? "BALANCED" : l2MatchingMode; }
        public void setL2MatchingMode(String l2MatchingMode) { this.l2MatchingMode = l2MatchingMode; }
        public double getRequiredSemanticThreshold() { return requiredSemanticThreshold != null ? requiredSemanticThreshold : 0.85d; }
        public void setRequiredSemanticThreshold(Double value) { this.requiredSemanticThreshold = value; }
        public double getCoreSemanticThreshold() { return coreSemanticThreshold != null ? coreSemanticThreshold : 0.82d; }
        public void setCoreSemanticThreshold(Double value) { this.coreSemanticThreshold = value; }
        public double getOptionalSemanticThreshold() { return optionalSemanticThreshold != null ? optionalSemanticThreshold : 0.78d; }
        public void setOptionalSemanticThreshold(Double value) { this.optionalSemanticThreshold = value; }
        public double getSimilarTagMinimumConfidence() { return similarTagMinimumConfidence != null ? similarTagMinimumConfidence : 0.80d; }
        public void setSimilarTagMinimumConfidence(Double value) { this.similarTagMinimumConfidence = value; }
        public int getAllowedLevelGap() { return allowedLevelGap != null ? allowedLevelGap : 0; }
        public void setAllowedLevelGap(Integer value) { this.allowedLevelGap = value; }
        public double getCoreCoverageThreshold() { return coreCoverageThreshold != null ? coreCoverageThreshold : 0.80d; }
        public void setCoreCoverageThreshold(Double value) { this.coreCoverageThreshold = value; }
        public double getRequiredCoverageThreshold() { return requiredCoverageThreshold != null ? requiredCoverageThreshold : 0.75d; }
        public void setRequiredCoverageThreshold(Double value) { this.requiredCoverageThreshold = value; }
        public int getL2PassThreshold() { return l2PassThreshold != null ? l2PassThreshold : 60; }
        public void setL2PassThreshold(Integer value) { this.l2PassThreshold = value; }
        public int getAiTriggerThreshold() { return aiTriggerThreshold != null ? aiTriggerThreshold : 60; }
        public void setAiTriggerThreshold(Integer value) { this.aiTriggerThreshold = value; }

        public void applyL2ModeDefaults(String mode) {
            String normalized = mode == null ? "BALANCED" : mode.trim().toUpperCase(java.util.Locale.ROOT);
            setL2MatchingMode(normalized);
            switch (normalized) {
                case "LENIENT" -> {
                    setRequiredSemanticThreshold(0.75d); setCoreSemanticThreshold(0.72d);
                    setOptionalSemanticThreshold(0.68d); setSimilarTagMinimumConfidence(0.70d);
                    setAllowedLevelGap(1); setCoreCoverageThreshold(0.60d); setRequiredCoverageThreshold(0.60d);
                    setL2PassThreshold(55); setAiTriggerThreshold(50);
                }
                case "STRICT" -> {
                    setRequiredSemanticThreshold(0.92d); setCoreSemanticThreshold(0.88d);
                    setOptionalSemanticThreshold(0.85d); setSimilarTagMinimumConfidence(0.90d);
                    setAllowedLevelGap(0); setCoreCoverageThreshold(1.00d); setRequiredCoverageThreshold(0.95d);
                    setL2PassThreshold(75); setAiTriggerThreshold(75);
                }
                default -> {
                    setL2MatchingMode("BALANCED"); setRequiredSemanticThreshold(0.85d); setCoreSemanticThreshold(0.82d);
                    setOptionalSemanticThreshold(0.78d); setSimilarTagMinimumConfidence(0.80d);
                    setAllowedLevelGap(0); setCoreCoverageThreshold(0.80d); setRequiredCoverageThreshold(0.75d);
                    setL2PassThreshold(60); setAiTriggerThreshold(60);
                }
            }
        }

    }
}
