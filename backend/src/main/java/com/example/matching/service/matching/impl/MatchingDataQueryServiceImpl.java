package com.example.matching.service.matching.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.entity.matching.MatchingBlackWhiteList;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.employee.EmpEmployeeMapper;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.example.matching.mapper.matching.MatchingBlackWhiteListMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.matching.MatchingDataQueryService;
import com.example.matching.service.matching.MatchingSnapshotAssembler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 匹配模块共用数据查询服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingDataQueryServiceImpl implements MatchingDataQueryService {

    private final EmpEmployeeMapper empEmployeeMapper;
    private final com.example.matching.port.employee.EmployeeAbilityReadPort employeeAbilityReadPort;
    private final EmpResumeParseMapper empResumeParseMapper;
    private final PostPostMapper postPostMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final MatchingBlackWhiteListMapper blackWhiteListMapper;
    private final ObjectMapper objectMapper;

    // ==================== M-12 匹配专用 DTO 查询 ====================

    @Override
    @Transactional(readOnly = true)
    public MatchingEmployeeProfile findEmployeeForMatching(Long empId) {
        EmpEmployee employee = empEmployeeMapper.selectById(empId);
        if (employee == null) {
            return null;
        }
        Map<Long, List<MatchingAbilitySnapshot>> snapshotMap = toSnapshotMap(batchLoadAbilities(List.of(empId)));
        return MatchingSnapshotAssembler.toEmployeeProfile(employee, snapshotMap);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchingEmployeeProfile> findEmployeesForMatching(Collection<Long> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = normalizeIds(empIds);
        List<EmpEmployee> employees = listEmployeesByIds(ids);
        if (employees.isEmpty()) {
            return List.of();
        }
        Map<Long, List<MatchingAbilitySnapshot>> snapshotMap = toSnapshotMap(batchLoadAbilities(ids));
        List<MatchingEmployeeProfile> profiles = new ArrayList<>();
        for (EmpEmployee employee : employees) {
            profiles.add(MatchingSnapshotAssembler.toEmployeeProfile(employee, snapshotMap));
        }
        return profiles;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchingEmployeeProfile> findActiveEmployeesForMatching(Collection<Long> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = normalizeIds(empIds);
        List<EmpEmployee> employees = listActiveEmployeesByIds(ids);
        if (employees.isEmpty()) {
            return List.of();
        }
        Map<Long, List<MatchingAbilitySnapshot>> snapshotMap = toSnapshotMap(batchLoadAbilities(ids));
        List<MatchingEmployeeProfile> profiles = new ArrayList<>();
        for (EmpEmployee employee : employees) {
            profiles.add(MatchingSnapshotAssembler.toEmployeeProfile(employee, snapshotMap));
        }
        return profiles;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchingEmployeeProfile> findAllActiveEmployeesForMatching() {
        List<EmpEmployee> employees = listAllActiveEmployees();
        if (employees.isEmpty()) {
            return List.of();
        }
        List<Long> ids = employees.stream().map(EmpEmployee::getId).filter(Objects::nonNull).toList();
        Map<Long, List<MatchingAbilitySnapshot>> snapshotMap = toSnapshotMap(batchLoadAbilities(ids));
        List<MatchingEmployeeProfile> profiles = new ArrayList<>();
        for (EmpEmployee employee : employees) {
            profiles.add(MatchingSnapshotAssembler.toEmployeeProfile(employee, snapshotMap));
        }
        return profiles;
    }

    @Override
    @Transactional(readOnly = true)
    public MatchingPostProfile findPostForMatching(Long postId) {
        PostPost post = postPostMapper.selectById(postId);
        if (post == null) {
            return null;
        }
        return MatchingSnapshotAssembler.toPostProfile(post, toRequirementSnapshots(listRequirementsByPostId(postId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchingPostProfile> findPostsForMatching(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = normalizeIds(postIds);
        List<PostPost> posts = listPostsByIds(ids);
        if (posts.isEmpty()) {
            return List.of();
        }
        Map<Long, List<MatchingRequirementSnapshot>> requirementsByPost = new HashMap<>();
        for (PostPost post : posts) {
            requirementsByPost.put(post.getId(), toRequirementSnapshots(listRequirementsByPostId(post.getId())));
        }
        List<MatchingPostProfile> profiles = new ArrayList<>();
        for (PostPost post : posts) {
            profiles.add(MatchingSnapshotAssembler.toPostProfile(post, requirementsByPost.get(post.getId())));
        }
        return profiles;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchingRequirementSnapshot> findPostRequirements(Long postId) {
        return toRequirementSnapshots(listRequirementsByPostId(postId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<MatchingAbilitySnapshot>> batchLoadAbilitySnapshots(Collection<Long> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return Map.of();
        }
        return toSnapshotMap(batchLoadAbilities(normalizeIds(empIds)));
    }

    /**
     * 将 EmpAbility 列表转换为能力快照列表（批量加载标签名称）
     */
    private List<MatchingAbilitySnapshot> toSnapshotList(List<EmpAbility> abilities) {
        if (abilities == null || abilities.isEmpty()) {
            return List.of();
        }
        List<Long> tagIds = abilities.stream()
                .map(EmpAbility::getTagId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> tagNameMap = new HashMap<>();
        if (!tagIds.isEmpty()) {
            for (AbilityTag tag : listTagsByIds(tagIds)) {
                if (tag != null) {
                    tagNameMap.put(tag.getId(), tag.getTagName());
                }
            }
        }
        List<MatchingAbilitySnapshot> snapshots = new ArrayList<>();
        for (EmpAbility ability : abilities) {
            snapshots.add(MatchingSnapshotAssembler.toAbilitySnapshot(
                    ability, ability.getAbilityName() != null ? ability.getAbilityName()
                            : tagNameMap.getOrDefault(ability.getTagId(), null)));
        }
        return snapshots;
    }

    private Map<Long, List<MatchingAbilitySnapshot>> toSnapshotMap(Map<Long, List<EmpAbility>> abilitiesMap) {
        Map<Long, List<MatchingAbilitySnapshot>> result = new HashMap<>();
        for (Map.Entry<Long, List<EmpAbility>> entry : abilitiesMap.entrySet()) {
            result.put(entry.getKey(), toSnapshotList(entry.getValue()));
        }
        return result;
    }

    private List<MatchingRequirementSnapshot> toRequirementSnapshots(List<PostAbilityModel> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }
        Map<Long, String> tagNameMap = buildTagNameMap(requirements);
        List<MatchingRequirementSnapshot> snapshots = new ArrayList<>();
        for (PostAbilityModel requirement : requirements) {
            snapshots.add(MatchingSnapshotAssembler.toRequirementSnapshot(
                    requirement, requirement.getAbilityName() != null ? requirement.getAbilityName()
                            : tagNameMap.getOrDefault(requirement.getTagId(), null)));
        }
        return snapshots;
    }

    private static List<Long> normalizeIds(Collection<Long> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    @Override
    public EmpEmployee getEmployeeById(Long empId) {
        return empEmployeeMapper.selectById(empId);
    }

    @Override
    public PostPost getPostById(Long postId) {
        return postPostMapper.selectById(postId);
    }

    @Override
    public List<PostPost> listPostsByIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        return postPostMapper.selectList(
                Wrappers.<PostPost>lambdaQuery().in(PostPost::getId, postIds));
    }

    @Override
    public AbilityTag getTagById(Long tagId) {
        return abilityTagMapper.selectById(tagId);
    }

    @Override
    public List<PostAbilityModel> listRequirementsByPostId(Long postId) {
        return postAbilityModelMapper.selectList(
                Wrappers.<PostAbilityModel>lambdaQuery().eq(PostAbilityModel::getPostId, postId));
    }

    @Override
    public List<MatchingBlackWhiteList> listBlackWhiteListByPostId(Long postId) {
        return blackWhiteListMapper.selectList(
                Wrappers.<MatchingBlackWhiteList>lambdaQuery()
                        .eq(MatchingBlackWhiteList::getPostId, postId)
                        .eq(MatchingBlackWhiteList::getStatus, 1));
    }

    @Override
    public Map<Long, List<EmpAbility>> batchLoadAbilities(List<Long> empIds) {
        Map<Long, List<EmpAbility>> result = new HashMap<>();
        if (empIds == null || empIds.isEmpty()) {
            return result;
        }

        // 匹配统一经 EmployeeAbilityReadPort 读取 emp_ability 正式能力表。
        Map<Long, List<com.example.matching.dto.matching.MatchingAbilitySnapshot>> authoritative =
                employeeAbilityReadPort.loadAuthoritativeAbilities(empIds);
        for (Map.Entry<Long, List<com.example.matching.dto.matching.MatchingAbilitySnapshot>> entry
                : authoritative.entrySet()) {
            result.put(entry.getKey(), entry.getValue().stream()
                    .map(this::snapshotToAbility)
                    .collect(Collectors.toList()));
        }
        return result;
    }

    private EmpAbility snapshotToAbility(
            com.example.matching.dto.matching.MatchingAbilitySnapshot snapshot) {
        EmpAbility ability = new EmpAbility();
        ability.setId(snapshot.abilityId());
        ability.setEmpId(null); // empId 由外层 Map key 提供
        ability.setTagId(snapshot.tagId());
        ability.setAbilityName(snapshot.abilityName());
        ability.setMasteryLevel(snapshot.level());
        ability.setAbilityLevel(snapshot.level());
        ability.setEvaluationSource(snapshot.sourceType() != null
                ? snapshot.sourceType() : "EMP_ABILITY");
        if (snapshot.confidence() != null) {
            ability.setSourceWeight(snapshot.confidence());
        }
        ability.setEvaluationDate(snapshot.evaluationDate());
        return ability;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<Long, Map<String, Object>> batchLoadResumeBasicInfo(List<Long> empIds) {
        Map<Long, Map<String, Object>> result = new HashMap<>();
        if (empIds == null || empIds.isEmpty()) {
            return result;
        }

        List<EmpResumeParse> resumes = empResumeParseMapper.selectList(
                Wrappers.<EmpResumeParse>lambdaQuery()
                        .in(EmpResumeParse::getEmpId, empIds)
                        .eq(EmpResumeParse::getStatus, 2)
                        .orderByDesc(EmpResumeParse::getCreatedTime));

        Map<Long, EmpResumeParse> latestMap = new HashMap<>();
        for (EmpResumeParse resume : resumes) {
            latestMap.putIfAbsent(resume.getEmpId(), resume);
        }

        for (Map.Entry<Long, EmpResumeParse> entry : latestMap.entrySet()) {
            try {
                if (entry.getValue().getAiAnalysisResult() == null) {
                    continue;
                }
                Map<String, Object> analysis = objectMapper.readValue(
                        entry.getValue().getAiAnalysisResult(),
                        new TypeReference<Map<String, Object>>() {});
                Map<String, Object> basicInfo = (Map<String, Object>) analysis.get("basicInfo");
                if (basicInfo != null) {
                    result.put(entry.getKey(), basicInfo);
                }
            } catch (Exception e) {
                log.debug("Failed to parse resume basicInfo: empId={}", entry.getKey());
            }
        }
        return result;
    }

    @Override
    public List<EmpEmployee> listEmployeesByIds(List<Long> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return List.of();
        }
        return empEmployeeMapper.selectList(
                Wrappers.<EmpEmployee>lambdaQuery().in(EmpEmployee::getId, empIds));
    }

    @Override
    public List<EmpEmployee> listActiveEmployeesByIds(List<Long> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return List.of();
        }
        return empEmployeeMapper.selectList(
                Wrappers.<EmpEmployee>lambdaQuery()
                        .in(EmpEmployee::getId, empIds)
                        .eq(EmpEmployee::getStatus, 1)
                        .eq(EmpEmployee::getIsLocked, 0));
    }

    private static final int MAX_ACTIVE_EMPLOYEES = 500;

    @Override
    public List<EmpEmployee> listAllActiveEmployees() {
        // 分页循环加载全部在职员工：不允许 LIMIT 硬截断全量业务匹配的候选池
        List<EmpEmployee> all = new ArrayList<>();
        int page = 1;
        List<EmpEmployee> batch;
        do {
            batch = listActiveEmployeesPaged(page++, MAX_ACTIVE_EMPLOYEES);
            all.addAll(batch);
        } while (batch.size() == MAX_ACTIVE_EMPLOYEES);
        return all;
    }

    @Override
    public long countAllActiveEmployees() {
        return empEmployeeMapper.selectCount(
                Wrappers.<EmpEmployee>lambdaQuery()
                        .eq(EmpEmployee::getStatus, 1)
                        .eq(EmpEmployee::getIsLocked, 0));
    }

    @Override
    public List<EmpEmployee> listActiveEmployeesPaged(int page, int pageSize) {
        int safePageSize = Math.min(pageSize, MAX_ACTIVE_EMPLOYEES);
        return empEmployeeMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, safePageSize),
                Wrappers.<EmpEmployee>lambdaQuery()
                        .eq(EmpEmployee::getStatus, 1)
                        .eq(EmpEmployee::getIsLocked, 0)
        ).getRecords();
    }

    @Override
    public List<AbilityTag> listTagsByIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return abilityTagMapper.selectList(
                Wrappers.<AbilityTag>lambdaQuery().in(AbilityTag::getId, tagIds));
    }

    @Override
    public Map<Long, String> buildTagNameMap(List<PostAbilityModel> requirements) {
        Map<Long, String> tagNameMap = new HashMap<>();
        if (requirements == null || requirements.isEmpty()) {
            return tagNameMap;
        }
        List<Long> tagIds = requirements.stream()
                .map(PostAbilityModel::getTagId).filter(Objects::nonNull).distinct().toList();
        List<AbilityTag> tags = listTagsByIds(tagIds);
        for (AbilityTag tag : tags) {
            tagNameMap.put(tag.getId(), tag.getTagName());
        }
        return tagNameMap;
    }

}
