package com.example.matching.agent.service;

import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityClaim;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AgentClaimConflictDetector")
class AgentClaimConflictDetectorTest {

    private final AgentClaimConflictDetector detector = new AgentClaimConflictDetector();

    private PersonAbilityClaim personClaim(Long tagId, String sourceType, Integer level) {
        PersonAbilityClaim claim = new PersonAbilityClaim();
        claim.setAbilityTagId(tagId);
        claim.setSourceType(sourceType);
        claim.setMasteryLevel(level);
        claim.setAbilityName("Java");
        return claim;
    }

    private PostAbilityClaim postClaim(Long tagId, String sourceType, Integer level) {
        PostAbilityClaim claim = new PostAbilityClaim();
        claim.setAbilityTagId(tagId);
        claim.setSourceType(sourceType);
        claim.setRequiredLevel(level);
        claim.setAbilityName("Java");
        return claim;
    }

    @Test
    @DisplayName("同标签同来源不同等级 -> 检测为冲突")
    void sameTagSameSourceDifferentLevelsIsConflict() {
        List<String> conflicts = detector.detectPersonClaimConflicts(List.of(
                personClaim(7L, "RESUME_PARSE", 3),
                personClaim(7L, "RESUME_PARSE", 4)));

        assertThat(conflicts).containsExactly("7|RESUME_PARSE");
    }

    @Test
    @DisplayName("同标签不同来源相同等级 -> 不冲突")
    void sameTagDifferentSourcesSameLevelNotConflict() {
        List<String> conflicts = detector.detectPersonClaimConflicts(List.of(
                personClaim(7L, "RESUME_PARSE", 3),
                personClaim(7L, "AI_INTERVIEW", 3)));

        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("不同标签 -> 不冲突")
    void differentTagsNotConflict() {
        List<String> conflicts = detector.detectPersonClaimConflicts(List.of(
                personClaim(7L, "RESUME_PARSE", 3),
                personClaim(8L, "RESUME_PARSE", 4)));

        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("null 标签或等级跳过")
    void nullTagOrLevelSkipped() {
        PersonAbilityClaim noTag = personClaim(null, "RESUME_PARSE", 3);
        PersonAbilityClaim noLevel = personClaim(7L, "RESUME_PARSE", null);
        PersonAbilityClaim valid = personClaim(7L, "RESUME_PARSE", 3);

        List<String> conflicts = detector.detectPersonClaimConflicts(List.of(noTag, noLevel, valid));

        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("岗位声明冲突检测同样生效")
    void postClaimConflictsDetected() {
        List<String> conflicts = detector.detectPostClaimConflicts(List.of(
                postClaim(7L, "JD_IMPORT", 3),
                postClaim(7L, "JD_IMPORT", 5)));

        assertThat(conflicts).containsExactly("7|JD_IMPORT");
    }
}
