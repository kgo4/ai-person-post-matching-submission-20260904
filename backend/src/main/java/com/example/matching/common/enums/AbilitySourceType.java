package com.example.matching.common.enums;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Canonical ability-evidence source vocabulary. */
public final class AbilitySourceType {

    public static final String ASSESSMENT_WORKFLOW = "ASSESSMENT_WORKFLOW";

    public static final String RESUME_PARSE = "RESUME_PARSE";
    public static final String AI_TEST = "AI_TEST";
    public static final String AI_PROJECT = "AI_PROJECT";
    public static final String AI_INTERVIEW = "AI_INTERVIEW";
    public static final String LEARNING_PROJECT = "LEARNING_PROJECT";
    public static final String MANUAL = "MANUAL";
    public static final String PERFORMANCE = "PERFORMANCE";
    public static final String PROFILE_FUSED = "PROFILE_FUSED";

    private static final Set<String> CANONICAL_SOURCES = Set.of(
            ASSESSMENT_WORKFLOW, RESUME_PARSE, AI_TEST, AI_PROJECT, AI_INTERVIEW,
            LEARNING_PROJECT, MANUAL, PERFORMANCE, PROFILE_FUSED);

    /** Sources that remain part of the current personnel-assessment evidence chain. */
    private static final Set<String> CONFIGURABLE_ASSESSMENT_SOURCES = Set.of(
            RESUME_PARSE, AI_TEST, AI_INTERVIEW);

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("PMS", AI_PROJECT),
            Map.entry("PROJECT", AI_PROJECT),
            Map.entry("PMS_ANALYSIS", AI_PROJECT),
            Map.entry("PROJECT_SYSTEM", AI_PROJECT),
            Map.entry("AI_ASSESSMENT", AI_TEST),
            Map.entry("AI_VIDEO_INTERVIEW", AI_INTERVIEW),
            Map.entry("VIDEO_INTERVIEW", AI_INTERVIEW),
            Map.entry("LEARNING", LEARNING_PROJECT),
            Map.entry("LEARNING_OUTCOME", LEARNING_PROJECT),
            Map.entry("MANUAL_IMPORT", MANUAL));

    private AbilitySourceType() {
    }

    public static String canonicalize(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return MANUAL;
        }
        String normalized = sourceType.trim().toUpperCase(Locale.ROOT);
        if (CANONICAL_SOURCES.contains(normalized)) {
            return normalized;
        }
        return ALIASES.getOrDefault(normalized, MANUAL);
    }

    public static boolean isConfigurableAssessmentSource(String sourceType) {
        return CONFIGURABLE_ASSESSMENT_SOURCES.contains(canonicalize(sourceType));
    }

}
