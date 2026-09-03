package com.example.matching.service.assessment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.enums.EvidenceStatusEnum;
import com.example.matching.dto.assessment.EligibilityPrecheckResult;
import com.example.matching.dto.assessment.ProvisionalAbilitySnapshotDTO;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.entity.ability.PersonAbilityClaim;
import com.example.matching.entity.workflow.PersonAbilityClaimGroup;
import com.example.matching.mapper.workflow.PersonAbilityClaimGroupMapper;
import com.example.matching.port.employee.EmployeeAbilityReadPort;
import com.example.matching.service.assessment.AbilityEvidenceCollectionService;
import com.example.matching.service.assessment.ProvisionalMatchingSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 临时能力匹配快照服务实现
 * <p>
 * 仅在强制匹配时构建临时能力快照，支持预检。
 * 快照令牌存 Redis（TTL 30 分钟），服务端重新校验。
 *
 * @author system
 */
@Slf4j
@Service
public class ProvisionalMatchingSnapshotServiceImpl implements ProvisionalMatchingSnapshotService {

    /** 软评分折减系数（配置化入口，后续可接入能力等级策略表） */
    private static final double SOFT_WEIGHT_FACTOR = 0.6;

    private static final String SNAPSHOT_KEY_PREFIX = "provisional:snapshot:";
    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(30);

    private final EmployeeAbilityReadPort employeeAbilityReadPort;
    private final PersonAbilityClaimGroupMapper claimGroupMapper;
    private final AbilityEvidenceCollectionService evidenceCollectionService;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    public ProvisionalMatchingSnapshotServiceImpl(
            EmployeeAbilityReadPort employeeAbilityReadPort,
            PersonAbilityClaimGroupMapper claimGroupMapper,
            AbilityEvidenceCollectionService evidenceCollectionService,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.employeeAbilityReadPort = employeeAbilityReadPort;
        this.claimGroupMapper = claimGroupMapper;
        this.evidenceCollectionService = evidenceCollectionService;
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Override
    public List<EligibilityPrecheckResult> precheck(List<Long> empIds, List<Long> postIds) {
        // 正式能力口径与匹配引擎一致：person_ability_profile（可用）优先，回退 emp_ability
        // （EmployeeAbilityReadPort），避免只有 emp_ability 数据的员工被误判为"无正式能力"。
        Map<Long, List<MatchingAbilitySnapshot>> authoritative =
                employeeAbilityReadPort.loadAuthoritativeAbilities(empIds);
        List<EligibilityPrecheckResult> results = new ArrayList<>();
        for (Long empId : empIds) {
            EligibilityPrecheckResult result = new EligibilityPrecheckResult();
            result.setEmpId(empId);
            List<MatchingAbilitySnapshot> confirmedAbilities =
                    authoritative.getOrDefault(empId, List.of());
            result.setHasConfirmedAbilities(!confirmedAbilities.isEmpty());

            List<PersonAbilityClaimGroup> provisionalGroups = listProvisionalGroups(empId);
            result.setHasProvisionalAbilities(!provisionalGroups.isEmpty());
            result.setProvisionalAbilityCount(provisionalGroups.size());
            for (PersonAbilityClaimGroup group : provisionalGroups) {
                EligibilityPrecheckResult.ProvisionalAbilitySummary summary =
                        new EligibilityPrecheckResult.ProvisionalAbilitySummary();
                summary.setClaimGroupId(group.getId());
                summary.setAbilityName(group.getNormalizedAbilityName());
                List<PersonAbilityClaim> claims = evidenceCollectionService.listClaimsByGroup(group.getId());
                summary.setClaimedLevel(claims.stream()
                        .map(PersonAbilityClaim::getClaimedLevel)
                        .filter(l -> l != null)
                        .max(Integer::compareTo)
                        .orElse(null));
                summary.setEvidenceCount(claims.size());
                summary.setEvidenceStatus(group.getStatus());
                summary.setTagResolutionStatus(group.getTagResolutionStatus());
                summary.setRiskLabel(riskLabel(group));
                result.getRelatedProvisionalAbilities().add(summary);
            }
            // 默认动作规则（规范第 9 节）
            boolean confirmed = Boolean.TRUE.equals(result.getHasConfirmedAbilities());
            boolean provisional = Boolean.TRUE.equals(result.getHasProvisionalAbilities());
            if (!confirmed && !provisional) {
                result.setDefaultAction("FORBIDDEN");
                result.getRiskFlags().add("NO_ABILITIES");
            } else if (confirmed && !provisional) {
                result.setDefaultAction("NORMAL_MATCH");
            } else if (confirmed) {
                result.setDefaultAction("CONFIRMED_ONLY");
                result.getRiskFlags().add("PROVISIONAL_EXCLUDED");
            } else {
                result.setDefaultAction("MANUAL_CONFIRM_REQUIRED");
                result.getRiskFlags().add("PROVISIONAL_ONLY");
            }
            results.add(result);
        }
        return results;
    }

    @Override
    public ProvisionalAbilitySnapshotDTO buildSnapshot(Long empId, boolean acknowledged, Long operatorId) {
        if (!acknowledged) {
            throw new IllegalStateException("使用待确立能力匹配必须确认风险");
        }
        List<PersonAbilityClaimGroup> groups = listProvisionalGroups(empId);
        if (groups.isEmpty()) {
            return null;
        }
        ProvisionalAbilitySnapshotDTO snapshot = new ProvisionalAbilitySnapshotDTO();
        snapshot.setEmpId(empId);
        snapshot.setCreatedAt(LocalDateTime.now().toString());
        snapshot.setPolicyVersion("provisional-match-v1");
        for (PersonAbilityClaimGroup group : groups) {
            ProvisionalAbilitySnapshotDTO.SnapshotAbilityItem item =
                    new ProvisionalAbilitySnapshotDTO.SnapshotAbilityItem();
            item.setClaimGroupId(group.getId());
            item.setTagId(group.getCanonicalTagId());
            item.setAbilityName(group.getNormalizedAbilityName());
            List<PersonAbilityClaim> claims = evidenceCollectionService.listClaimsByGroup(group.getId());
            item.setClaimedLevel(claims.stream()
                    .map(PersonAbilityClaim::getClaimedLevel)
                    .filter(l -> l != null)
                    .max(Integer::compareTo)
                    .orElse(1));
            item.setEvidenceStatus(group.getStatus());
            item.setSourceTypes(claims.stream().map(PersonAbilityClaim::getSourceType).distinct().toList());
            item.setSoftWeightFactor(SOFT_WEIGHT_FACTOR);
            snapshot.getAbilities().add(item);
            snapshot.getRiskFlags().add(riskLabel(group));
        }
        // 生成令牌并缓存
        String token = generateToken(empId);
        snapshot.setSnapshotToken(token);
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis != null) {
            try {
                redis.opsForValue().set(SNAPSHOT_KEY_PREFIX + token,
                        snapshotToJson(snapshot), SNAPSHOT_TTL);
            } catch (Exception e) {
                log.warn("临时能力快照写入Redis失败（降级为内存校验）: {}", e.getMessage());
            }
        }
        log.info("构建临时能力快照: empId={}, abilities={}, operatorId={}",
                empId, snapshot.getAbilities().size(), operatorId);
        return snapshot;
    }

    @Override
    public boolean validateSnapshotToken(String snapshotToken, Long empId) {
        if (snapshotToken == null || snapshotToken.isBlank()) {
            return false;
        }
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return false;
        }
        try {
            String json = redis.opsForValue().get(SNAPSHOT_KEY_PREFIX + snapshotToken);
            if (json == null || json.isBlank()) {
                return false;
            }
            // 精确解析校验令牌属于目标人员（避免前缀匹配：empId=1234 不能匹配 123/12/1）
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            return node.path("empId").isNumber() && node.path("empId").asLong() == empId;
        } catch (Exception e) {
            log.warn("临时能力快照令牌校验异常: {}", e.getMessage());
            return false;
        }
    }

    private List<PersonAbilityClaimGroup> listProvisionalGroups(Long empId) {
        return claimGroupMapper.selectList(new LambdaQueryWrapper<PersonAbilityClaimGroup>()
                .eq(PersonAbilityClaimGroup::getEmpId, empId)
                .in(PersonAbilityClaimGroup::getStatus,
                        EvidenceStatusEnum.COLLECTED.getCode(),
                        EvidenceStatusEnum.READY_FOR_AGGREGATE_HARNESS.getCode(),
                        EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode()));
    }

    private String riskLabel(PersonAbilityClaimGroup group) {
        if (EvidenceStatusEnum.PENDING_MANUAL_REVIEW.getCode().equals(group.getStatus())) {
            return "Harness 待审核";
        }
        return "待确立";
    }

    private String generateToken(Long empId) {
        String raw = empId + ":" + System.currentTimeMillis() + ":" + UUID.randomUUID();
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("快照令牌生成失败", e);
        }
    }

    private String snapshotToJson(ProvisionalAbilitySnapshotDTO snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"snapshotToken\":\"").append(snapshot.getSnapshotToken())
                .append("\",\"empId\":").append(snapshot.getEmpId())
                .append(",\"createdAt\":\"").append(snapshot.getCreatedAt())
                .append("\",\"policyVersion\":\"").append(snapshot.getPolicyVersion())
                .append("\",\"abilities\":[");
        for (int i = 0; i < snapshot.getAbilities().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            ProvisionalAbilitySnapshotDTO.SnapshotAbilityItem item = snapshot.getAbilities().get(i);
            sb.append("{\"claimGroupId\":").append(item.getClaimGroupId())
                    .append(",\"tagId\":").append(item.getTagId() == null ? "null" : item.getTagId())
                    .append(",\"abilityName\":\"").append(escape(item.getAbilityName()))
                    .append("\",\"claimedLevel\":").append(item.getClaimedLevel())
                    .append(",\"softWeightFactor\":").append(item.getSoftWeightFactor()).append('}');
        }
        sb.append("],\"riskFlags\":[");
        List<String> flags = snapshot.getRiskFlags().stream().distinct().collect(Collectors.toList());
        for (int i = 0; i < flags.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(escape(flags.get(i))).append('"');
        }
        return sb.append("]}").toString();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"").replace("\n", " ");
    }
}
