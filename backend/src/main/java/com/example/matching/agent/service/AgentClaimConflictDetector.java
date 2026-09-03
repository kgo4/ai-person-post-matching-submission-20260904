package com.example.matching.agent.service;

import com.example.matching.agent.dto.post.PostAbilityClaim;
import com.example.matching.agent.dto.person.PersonAbilityClaim;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 矛盾声明检测器 —— 按规范化能力标签与来源引用分组，
 * 同一键出现不兼容的等级断言时标记 CONFLICTING_CLAIM 并强制 REVIEW。
 * <p>
 * 这是确定性的冲突检测（同键不同等级/状态），不是对任意散文的语义推断。
 */
@Slf4j
@Component
public class AgentClaimConflictDetector {

    public static final String CONFLICTING_CLAIM = "CONFLICTING_CLAIM";

    /**
     * 检测人员能力声明中的矛盾（同标签同来源不同等级）。
     *
     * @return 冲突键列表（空表示无冲突）
     */
    public List<String> detectPersonClaimConflicts(List<PersonAbilityClaim> claims) {
        Map<String, Integer> levelByKey = new HashMap<>();
        Map<String, String> keyByTag = new HashMap<>();
        Set<String> conflicts = new LinkedHashSet<>();

        for (PersonAbilityClaim claim : claims) {
            if (claim.getAbilityTagId() == null) {
                continue;
            }
            String key = conflictKey(claim.getAbilityTagId(), claim.getSourceType());
            Integer level = claim.getMasteryLevel();
            if (level == null) {
                continue;
            }
            Integer previous = levelByKey.putIfAbsent(key, level);
            if (previous != null && !previous.equals(level)) {
                conflicts.add(key);
                keyByTag.put(key, claim.getAbilityName() != null ? claim.getAbilityName() : String.valueOf(claim.getAbilityTagId()));
            }
        }
        if (!conflicts.isEmpty()) {
            log.warn("[AGENT_CONFLICT] 人员能力声明存在矛盾等级: keys={}", conflicts);
        }
        return List.copyOf(conflicts);
    }

    /**
     * 检测岗位能力声明中的矛盾（同标签同来源不同等级）。
     *
     * @return 冲突键列表（空表示无冲突）
     */
    public List<String> detectPostClaimConflicts(List<PostAbilityClaim> claims) {
        Map<String, Integer> levelByKey = new HashMap<>();
        Set<String> conflicts = new LinkedHashSet<>();

        for (PostAbilityClaim claim : claims) {
            if (claim.getAbilityTagId() == null) {
                continue;
            }
            String key = conflictKey(claim.getAbilityTagId(), claim.getSourceType());
            Integer level = claim.getRequiredLevel();
            if (level == null) {
                continue;
            }
            Integer previous = levelByKey.putIfAbsent(key, level);
            if (previous != null && !previous.equals(level)) {
                conflicts.add(key);
            }
        }
        if (!conflicts.isEmpty()) {
            log.warn("[AGENT_CONFLICT] 岗位能力声明存在矛盾等级: keys={}", conflicts);
        }
        return List.copyOf(conflicts);
    }

    private String conflictKey(Long tagId, String sourceType) {
        return tagId + "|" + (sourceType != null ? sourceType : "UNKNOWN");
    }
}
