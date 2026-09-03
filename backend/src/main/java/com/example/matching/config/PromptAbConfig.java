package com.example.matching.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Prompt A/B 实验配置（application.yml 中定义）
 * <p>
 * 配置示例：
 * <pre>
 * prompt:
 *   ab:
 *     experiments:
 *       - promptName: matching-prompt
 *         enabled: false
 *         versions: v1.0, v2.0
 *         splitPct: 50
 *         description: "权重描述措辞优化"
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "prompt.ab")
public class PromptAbConfig {

    /** 总开关：false 时所有实验停用 */
    private boolean enabled = false;

    /** 实验列表 */
    private List<Experiment> experiments = new ArrayList<>();

    @Data
    public static class Experiment {
        /** Prompt 模板名（不含 .ftl 后缀） */
        private String promptName;
        /** 是否启用 */
        private boolean enabled = false;
        /** 实验版本号列表，如 ["v1.0", "v2.0"] */
        private List<String> versions = new ArrayList<>();
        /** B 版本流量比例 0-100 */
        private int splitPct = 50;
        /** 实验描述 */
        private String description;

        /**
         * 根据 userId hash 决定走 A（旧版本）还是 B（新版本）
         * @return versions 中的索引，0 = A（旧版/首版），1 = B（新版/次版）
         */
        public int selectVersion(Long userId) {
            if (!enabled || versions.size() < 2) return 0;
            int hash = Math.abs(Long.hashCode(userId != null ? userId : 0));
            return (hash % 100 < splitPct) ? 1 : 0;
        }

        /**
         * B 版本对应的实际文件名（如 matching-prompt-v2.ftl）
         */
        public String getVersionFileName(int versionIndex) {
            if (versionIndex <= 0 || versions.size() <= 1) return promptName;
            return promptName + "-" + versions.get(versionIndex).replace(".", "");
        }
    }

    /**
     * 查找指定 Prompt 是否有活跃实验
     */
    public Experiment findExperiment(String promptName) {
        return experiments.stream()
                .filter(e -> e.promptName.equals(promptName) && e.enabled && e.versions.size() >= 2)
                .findFirst().orElse(null);
    }
}