package com.example.matching.common.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceRefConstantsTest {

    @Test
    void buildsStandardBusinessAndKnowledgeReferences() {
        assertThat(SourceRefConstants.factRef("EMP_ABILITY", 8L)).isEqualTo("fact:EMP_ABILITY:8");
        assertThat(SourceRefConstants.evidenceRef("CONTEST_EVIDENCE", 9L)).isEqualTo("evidence:CONTEST_EVIDENCE:9");
        assertThat(SourceRefConstants.matchingRef("MATCHING_RECORD", 10L)).isEqualTo("matching:MATCHING_RECORD:10");
        assertThat(SourceRefConstants.sourceRef("RESUME_PARSE", 11L)).isEqualTo("source:RESUME_PARSE:11");
        assertThat(SourceRefConstants.empAbilityFactRef(12L)).isEqualTo("fact:EMP_ABILITY:12");
        assertThat(SourceRefConstants.postAbilityModelFactRef(13L)).isEqualTo("fact:POST_ABILITY_MODEL:13");
        assertThat(SourceRefConstants.contestEvidenceRef(14L)).isEqualTo("evidence:CONTEST_EVIDENCE:14");
        assertThat(SourceRefConstants.matchingRecordRef(15L)).isEqualTo("matching:MATCHING_RECORD:15");
        assertThat(SourceRefConstants.industryWhitepaperRef(16L, "chunk-a"))
                .isEqualTo("source:INDUSTRY_WHITEPAPER:16:chunk-a");
        assertThat(SourceRefConstants.cloudKnowledgeRef(17L, "chunk-b"))
                .isEqualTo("source:CLOUD_KNOWLEDGE_INTERNAL:17:chunk-b");
        assertThat(SourceRefConstants.recruitmentJdRef(18L, "chunk-c"))
                .isEqualTo("source:RECRUITMENT_JD:18:chunk-c");
    }

    @Test
    void recognizesStandardAndDeprecatedFormats() {
        assertThat(SourceRefConstants.isValidFormat(null)).isFalse();
        assertThat(SourceRefConstants.isValidFormat("")).isFalse();
        assertThat(SourceRefConstants.isValidFormat("fact:EMP_ABILITY:1")).isTrue();
        assertThat(SourceRefConstants.isStandardFormat("fact:EMP_ABILITY:1")).isTrue();
        assertThat(SourceRefConstants.isStandardFormat("ai:answer:1")).isFalse();
        assertThat(SourceRefConstants.isDeprecatedFormat("ai:answer:1")).isTrue();
        assertThat(SourceRefConstants.isDeprecatedFormat("generated:value:1")).isTrue();
        assertThat(SourceRefConstants.isDeprecatedFormat("rag:ABILITY_TAG:1")).isTrue();
        assertThat(SourceRefConstants.isDeprecatedFormat("EMP_ABILITY:1")).isTrue();
        assertThat(SourceRefConstants.isDeprecatedFormat("fact:EMP_ABILITY:1")).isFalse();
    }

    @Test
    void parsesReferencePartsAndRejectsMalformedIds() {
        assertThat(SourceRefConstants.parseRefType("fact:EMP_ABILITY:19")).isEqualTo("fact:");
        assertThat(SourceRefConstants.parseEntityType("fact:EMP_ABILITY:19")).isEqualTo("EMP_ABILITY");
        assertThat(SourceRefConstants.parseEntityId("fact:EMP_ABILITY:19")).isEqualTo(19L);
        assertThat(SourceRefConstants.parseEntityId("fact:EMP_ABILITY:not-number")).isNull();
        assertThat(SourceRefConstants.parseRefType("invalid")).isNull();
        assertThat(SourceRefConstants.parseEntityType(null)).isNull();
        assertThat(SourceRefConstants.parseEntityId("fact:EMP_ABILITY")).isNull();
    }
}
