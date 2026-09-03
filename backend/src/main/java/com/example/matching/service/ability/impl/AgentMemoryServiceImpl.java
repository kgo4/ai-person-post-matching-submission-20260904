package com.example.matching.service.ability.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.entity.ability.AgentMemory;
import com.example.matching.entity.ability.AgentMemoryHitLog;
import com.example.matching.entity.ability.PersonAbilityGovernanceEvent;
import com.example.matching.mapper.ability.AgentMemoryHitLogMapper;
import com.example.matching.mapper.ability.AgentMemoryMapper;
import com.example.matching.mapper.ability.PersonAbilityGovernanceEventMapper;
import com.example.matching.service.ability.AgentMemoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMemoryServiceImpl extends ServiceImpl<AgentMemoryMapper, AgentMemory> implements AgentMemoryService {

    private static final ObjectMapper RULE_KEY_MAPPER = new ObjectMapper();
    private static final float SEMANTIC_MATCH_THRESHOLD = 0.72f;

    private final PersonAbilityGovernanceEventMapper eventMapper;
    private final AgentMemoryHitLogMapper hitLogMapper;
    private final ObjectMapper objectMapper;
    private final AgentMemorySearchEngine searchEngine;
    private final MemorySearchCacheEpoch cacheEpoch;

    @Override
    public List<AgentMemory> getActiveMemories(String scope) {
        return searchEngine.getActiveMemories(scope);
    }

    @Override
    public List<AgentMemory> getMemoriesByType(String memoryType) {
        return list(Wrappers.<AgentMemory>lambdaQuery()
                .eq(AgentMemory::getMemoryType, memoryType)
                .eq(AgentMemory::getStatus, "ACTIVE")
                .orderByDesc(AgentMemory::getPriority));
    }

    @Override
    public List<AgentMemory> searchMemories(String text, String scope) {
        return searchEngine.searchMemories(text, scope);
    }

    @Override
    public List<AgentMemory> searchActiveRules(String text, String scope) {
        return searchEngine.searchActiveRules(text, scope);
    }

    @Override
    @Transactional
    public Long createMemory(AgentMemory memory) {
        if (memory.getStatus() == null) memory.setStatus("DRAFT");
        if (memory.getPriority() == null) memory.setPriority(0);
        if (memory.getUseCount() == null) memory.setUseCount(0);
        if (memory.getRevision() == null) memory.setRevision(1);
        LocalDateTime now = LocalDateTime.now();
        searchEngine.populateEmbedding(memory);

        if (StringUtils.hasText(memory.getRuleKey())) {
            AgentMemory existing = getOne(Wrappers.<AgentMemory>lambdaQuery()
                    .eq(AgentMemory::getRuleKey, memory.getRuleKey())
                    .eq(AgentMemory::getStatus, "ACTIVE")
                    .orderByDesc(AgentMemory::getRevision)
                    .last("LIMIT 1"));
            if (existing != null) {
                if ("DRAFT".equals(memory.getStatus())) {
                    return existing.getId();
                }
                // 版本化更新：新行 revision+1，旧行标记 SUPERSEDED，不覆盖历史
                // 注意顺序：必须先释放旧行的 rule_key（uk_agent_memory_rule_key 唯一索引），
                // 再插入同 rule_key 的新行，否则唯一键冲突导致整个事务回滚
                memory.setId(null);
                memory.setRevision(existing.getRevision() == null ? 2 : existing.getRevision() + 1);
                memory.setSupersedesMemoryId(existing.getId());
                memory.setStatus("ACTIVE");
                memory.setUseCount(0);
                memory.setCreatedTime(now);
                memory.setUpdatedTime(now);
                searchEngine.populateEmbedding(memory);
                existing.setStatus("SUPERSEDED");
                existing.setUpdatedTime(now);
                updateById(existing);
                supersedeConflictingRules(memory, existing.getId());
                save(memory);
                if (memory.getApplicableScope() != null) {
                    cacheEpoch.advance(memory.getApplicableScope());
                }
                cacheEpoch.advance("ALL");
                log.info("Versioned agent memory update: id={} supersedes {}, revision={}",
                        memory.getId(), existing.getId(), memory.getRevision());
                return memory.getId();
            }
        }

        memory.setCreatedTime(now);
        memory.setUpdatedTime(now);
        if ("ACTIVE".equals(memory.getStatus())) {
            supersedeConflictingRules(memory, null);
        }

        save(memory);
        if (memory.getApplicableScope() != null) {
            cacheEpoch.advance(memory.getApplicableScope());
        }
        cacheEpoch.advance("ALL");
        log.info("Created agent memory: id={}, type={}, title={}, ruleKey={}",
                memory.getId(), memory.getMemoryType(), memory.getTitle(), memory.getRuleKey());
        return memory.getId();
    }

    @Override
    @Transactional
    public void markUsed(Long memoryId) {
        // 修复：原实现读-改-写存在并发丢失更新（批量评分时多线程命中同一记忆），
        // 改为原子 SQL 递增 use_count
        lambdaUpdate()
                .eq(AgentMemory::getId, memoryId)
                .setSql("use_count = COALESCE(use_count, 0) + 1")
                .set(AgentMemory::getLastUsedTime, LocalDateTime.now())
                .update();
    }

    @Override
    @Transactional
    public void logHit(Long memoryId, String agentName, String sourceType, Long sourceRefId,
                       String hitText, String hitContextJson, String outcome) {
        AgentMemoryHitLog log = new AgentMemoryHitLog();
        log.setMemoryId(memoryId);
        log.setAgentName(agentName);
        log.setSourceType(sourceType);
        log.setSourceRefId(sourceRefId);
        log.setHitText(hitText);
        log.setHitContextJson(hitContextJson);
        log.setOutcome(outcome);
        log.setHitTime(LocalDateTime.now());
        hitLogMapper.insert(log);
    }

    @Override
    @Transactional
    public void markUsedAndLogHit(Long memoryId, String agentName, String sourceType, Long sourceRefId,
                                  String hitText, String hitContextJson, String outcome) {
        markUsed(memoryId);
        logHit(memoryId, agentName, sourceType, sourceRefId, hitText, hitContextJson, outcome);
    }

    @Override
    @Transactional
    public void supersedeByRuleKey(String ruleKey, Long excludeId) {
        if (!StringUtils.hasText(ruleKey)) return;
        var wrapper = Wrappers.<AgentMemory>lambdaQuery()
                .eq(AgentMemory::getRuleKey, ruleKey)
                .eq(AgentMemory::getStatus, "ACTIVE");
        if (excludeId != null) {
            wrapper.ne(AgentMemory::getId, excludeId);
        }
        List<AgentMemory> oldRules = list(wrapper);
        for (AgentMemory old : oldRules) {
            old.setStatus("SUPERSEDED");
            old.setUpdatedTime(LocalDateTime.now());
            updateById(old);
            log.info("Superseded agent memory: id={}, ruleKey={}", old.getId(), ruleKey);
        }
    }

    public static String computeRuleKey(String scope, String memoryType, String rulePayloadJson) {
        String raw = (scope != null ? scope : "") + "|"
                + (memoryType != null ? memoryType : "") + "|"
                + canonicalConditionAndAction(rulePayloadJson);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw BusinessException.of(ErrorCodeEnum.SYSTEM_ERROR, "SHA-256 摘要算法不可用", e).put("operation", "computeRuleKey").build();
        }
    }

    private static String canonicalConditionAndAction(String rulePayloadJson) {
        if (!StringUtils.hasText(rulePayloadJson)) return "";
        try {
            JsonNode root = RULE_KEY_MAPPER.readTree(rulePayloadJson);
            ObjectNode key = RULE_KEY_MAPPER.createObjectNode();
            key.set("condition", canonicalize(root.path("condition")));
            key.set("action", canonicalize(root.path("action")));
            return RULE_KEY_MAPPER.writeValueAsString(key);
        } catch (Exception e) {
            throw new IllegalArgumentException("rulePayloadJson must contain valid JSON", e);
        }
    }

    private static JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return RULE_KEY_MAPPER.nullNode();
        }
        if (node.isObject()) {
            ObjectNode result = RULE_KEY_MAPPER.createObjectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((name, value) -> result.set(name, canonicalize(value)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = RULE_KEY_MAPPER.createArrayNode();
            node.forEach(value -> result.add(canonicalize(value)));
            return result;
        }
        return node;
    }

    private void supersedeConflictingRules(AgentMemory incoming, Long excludeId) {
        if (!StringUtils.hasText(incoming.getRulePayloadJson())) return;
        List<AgentMemory> candidates = list(Wrappers.<AgentMemory>lambdaQuery()
                .eq(AgentMemory::getStatus, "ACTIVE")
                .eq(AgentMemory::getApplicableScope, incoming.getApplicableScope())
                .eq(AgentMemory::getMemoryType, incoming.getMemoryType()));
        String incomingCondition = extractCanonicalCondition(incoming.getRulePayloadJson());
        String incomingAction = extractCanonicalAction(incoming.getRulePayloadJson());
        boolean superseded = false;
        for (AgentMemory old : candidates) {
            if (old.getId().equals(excludeId) || !StringUtils.hasText(old.getRulePayloadJson())) continue;
            if (incomingCondition.equals(extractCanonicalCondition(old.getRulePayloadJson()))
                    && !incomingAction.equals(extractCanonicalAction(old.getRulePayloadJson()))) {
                old.setStatus("SUPERSEDED");
                old.setUpdatedTime(LocalDateTime.now());
                updateById(old);
                logConflictSuperseded(old, incoming);
                superseded = true;
            }
        }
        if (superseded) {
            if (incoming.getApplicableScope() != null) {
                cacheEpoch.advance(incoming.getApplicableScope());
            }
            cacheEpoch.advance("ALL");
        }
    }

    private String extractCanonicalCondition(String payload) {
        try {
            return RULE_KEY_MAPPER.writeValueAsString(canonicalize(RULE_KEY_MAPPER.readTree(payload).path("condition")));
        } catch (Exception e) {
            return "";
        }
    }

    private String extractCanonicalAction(String payload) {
        try {
            return RULE_KEY_MAPPER.writeValueAsString(canonicalize(RULE_KEY_MAPPER.readTree(payload).path("action")));
        } catch (Exception e) {
            return "";
        }
    }

    private void logConflictSuperseded(AgentMemory old, AgentMemory incoming) {
        try {
            logHit(old.getId(), "AGENT_MEMORY_GOVERNANCE", null, null, null,
                    objectMapper.writeValueAsString(Map.of(
                            "outcome", "CONFLICT_SUPERSEDED",
                            "supersededByRuleKey", incoming.getRuleKey())),
                    "CONFLICT_SUPERSEDED");
        } catch (Exception e) {
            log.warn("Failed to log superseded memory conflict: memoryId={}", old.getId(), e);
        }
    }

    @Override
    @Transactional
    public void createMemories(List<AgentMemory> memories) {
        for (AgentMemory memory : memories) createMemory(memory);
    }

    @Override
    public List<AgentMemory> getTagNormalizeMemories() {
        return getMemoriesByType("TAG_NORMALIZE");
    }

    @Override
    public List<AgentMemory> getTagRejectMemories() {
        return getMemoriesByType("TAG_REJECT");
    }

    @Override
    public Page<AgentMemory> pageMemories(Integer pageNum, Integer pageSize, String status,
                                          String memoryType, String scope, String keyword) {
        Page<AgentMemory> page = new Page<>(pageNum, pageSize);
        var wrapper = Wrappers.<AgentMemory>lambdaQuery();
        if (StringUtils.hasText(status)) wrapper.eq(AgentMemory::getStatus, status);
        if (StringUtils.hasText(memoryType)) wrapper.eq(AgentMemory::getMemoryType, memoryType);
        if (StringUtils.hasText(scope)) {
            wrapper.and(w -> w.eq(AgentMemory::getApplicableScope, scope).or()
                    .eq(AgentMemory::getApplicableScope, "ALL"));
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(AgentMemory::getTitle, keyword).or()
                    .like(AgentMemory::getContent, keyword));
        }
        wrapper.orderByDesc(AgentMemory::getPriority).orderByDesc(AgentMemory::getCreatedTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional
    public void enableMemory(Long memoryId) {
        updateMemoryStatus(memoryId, "ACTIVE", null);
    }

    @Override
    @Transactional
    public void disableMemory(Long memoryId) {
        updateMemoryStatus(memoryId, "DISABLED", null);
    }

    @Override
    @Transactional
    public void expireMemory(Long memoryId) {
        updateMemoryStatus(memoryId, "EXPIRED", LocalDateTime.now());
    }

    @Override
    @Transactional
    public int expireDueMemories() {
        LocalDateTime now = LocalDateTime.now();
        List<String> scopes = baseMapper.selectObjs(Wrappers.<AgentMemory>lambdaQuery()
                .select(AgentMemory::getApplicableScope)
                .eq(AgentMemory::getStatus, "ACTIVE")
                .isNotNull(AgentMemory::getExpireTime)
                .le(AgentMemory::getExpireTime, now)
                .groupBy(AgentMemory::getApplicableScope))
                .stream()
                .map(obj -> (String) obj)
                .toList();
        int count = baseMapper.update(null, Wrappers.<AgentMemory>lambdaUpdate()
                .eq(AgentMemory::getStatus, "ACTIVE")
                .isNotNull(AgentMemory::getExpireTime)
                .le(AgentMemory::getExpireTime, now)
                .set(AgentMemory::getStatus, "EXPIRED")
                .set(AgentMemory::getUpdatedTime, now));
        if (count > 0) {
            for (String scope : scopes) {
                if (scope != null) cacheEpoch.advance(scope);
            }
            cacheEpoch.advance("ALL");
        }
        return count;
    }

    private void updateMemoryStatus(Long memoryId, String status, LocalDateTime expireTime) {
        AgentMemory memory = getById(memoryId);
        if (memory == null) return;
        memory.setStatus(status);
        if (expireTime != null) memory.setExpireTime(expireTime);
        memory.setUpdatedTime(LocalDateTime.now());
        updateById(memory);
        if (memory.getApplicableScope() != null) {
            cacheEpoch.advance(memory.getApplicableScope());
        }
        cacheEpoch.advance("ALL");
        log.info("Updated agent memory status: id={}, status={}", memoryId, status);
    }

    @Override
    public Page<PersonAbilityGovernanceEvent> pageEvents(Integer pageNum, Integer pageSize,
                                                           String modifyType, Long empId, Long tagId) {
        Page<PersonAbilityGovernanceEvent> page = new Page<>(pageNum, pageSize);
        var wrapper = Wrappers.<PersonAbilityGovernanceEvent>lambdaQuery();
        if (StringUtils.hasText(modifyType)) wrapper.eq(PersonAbilityGovernanceEvent::getModifyType, modifyType);
        if (empId != null) wrapper.eq(PersonAbilityGovernanceEvent::getEmpId, empId);
        if (tagId != null) {
            wrapper.and(w -> w.eq(PersonAbilityGovernanceEvent::getOldTagId, tagId).or()
                    .eq(PersonAbilityGovernanceEvent::getNewTagId, tagId));
        }
        wrapper.orderByDesc(PersonAbilityGovernanceEvent::getCreatedTime);
        return eventMapper.selectPage(page, wrapper);
    }

    @Override
    public PersonAbilityGovernanceEvent getEventById(Long eventId) {
        return eventMapper.selectById(eventId);
    }
}
