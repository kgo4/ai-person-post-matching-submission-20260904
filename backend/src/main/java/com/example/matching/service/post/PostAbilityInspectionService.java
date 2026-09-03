package com.example.matching.service.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.util.AbilityNameNormalizer;
import com.example.matching.entity.governance.GovernanceAdmissionRecord;
import com.example.matching.entity.post.PostAbilityGroundingRecord;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.mapper.governance.GovernanceAdmissionMapper;
import com.example.matching.mapper.post.PostAbilityGroundingRecordMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.vo.post.PostAbilityInspectionItemVO;
import com.example.matching.vo.post.PostAbilityInspectionPostVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 岗位能力巡检服务（入库后的 AI 幻觉巡检台）
 * <p>
 * 以岗位为单位聚合 post_ability_model（岗位能力表），逐条能力标注风险：
 * <ul>
 *   <li>治理准入记录（governance_admission，经 post_ability_model.governance_admission_id 关联）</li>
 *   <li>JD 提取台账（post_ability_grounding_record，经 post_id + 归一化能力名关联）</li>
 *   <li>能力自身字段信号（空名/#null/未命名、AI 来源、无标签、等级/权重越界）</li>
 * </ul>
 * 该服务只读聚合 + 辅助巡检；修改/删除能力复用现有 /api/post/ability-model 接口，
 * 不触碰人员 harness 判定链路，也不影响岗位能力主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostAbilityInspectionService {

    private final PostAbilityModelMapper abilityModelMapper;
    private final PostPostMapper postPostMapper;
    private final GovernanceAdmissionMapper admissionMapper;
    private final PostAbilityGroundingRecordMapper groundingMapper;

    /** 常见的异常能力名片段 */
    private static final String[] ABNORMAL_NAME_PATTERNS = {"#null", "未命名", "null"};

    /**
     * 分页查询岗位聚合列表（仅包含至少有一条岗位能力的岗位）。
     *
     * @param keyword   岗位名称关键字（可选）
     * @param onlyRisky 只看有风险能力的岗位（可选）
     * @param onlyAi    只看包含 AI 来源能力的岗位（可选）
     */
    public IPage<PostAbilityInspectionPostVO> pagePosts(String keyword, Boolean onlyRisky, Boolean onlyAi,
                                                         long current, long size) {
        List<PostAbilityModel> models = abilityModelMapper.selectList(
                new LambdaQueryWrapper<PostAbilityModel>().eq(PostAbilityModel::getIsDeleted, 0));

        Map<Long, List<PostAbilityModel>> byPost = models.stream()
                .collect(Collectors.groupingBy(PostAbilityModel::getPostId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, PostPost> postMap = loadPostMap(byPost.keySet());

        List<PostAbilityInspectionPostVO> all = new ArrayList<>();
        for (Map.Entry<Long, List<PostAbilityModel>> entry : byPost.entrySet()) {
            Long postId = entry.getKey();
            PostPost post = postMap.get(postId);
            if (post == null || post.getIsDeleted() != null && post.getIsDeleted() == 1) {
                continue;
            }
            List<PostAbilityModel> abilities = entry.getValue();
            Map<Long, GovernanceAdmissionRecord> admissionMap = loadAdmissionMap(abilities);
            Map<String, PostAbilityGroundingRecord> groundingMap = loadGroundingMap(postId, abilities);

            PostAbilityInspectionPostVO vo = new PostAbilityInspectionPostVO();
            vo.setPostId(postId);
            vo.setPostName(post.getPostName());
            vo.setPostCode(post.getPostCode());
            vo.setAbilityCount(abilities.size());

            int risky = 0;
            int high = 0;
            int ai = 0;
            for (PostAbilityModel m : abilities) {
                PostAbilityInspectionItemVO item = inspect(m,
                        findAdmission(admissionMap, m),
                        groundingMap.get(normalizeAbility(m.getAbilityName())));
                if (!"NORMAL".equals(item.getRiskLevel())) {
                    risky++;
                }
                if ("HIGH".equals(item.getRiskLevel())) {
                    high++;
                }
                if (Boolean.TRUE.equals(item.getAiSource())) {
                    ai++;
                }
            }
            vo.setRiskyCount(risky);
            vo.setHighCount(high);
            vo.setAiSourceCount(ai);

            if (onlyRisky != null && onlyRisky && risky == 0) {
                continue;
            }
            if (onlyAi != null && onlyAi && ai == 0) {
                continue;
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = keyword.trim().toLowerCase();
                boolean match = (post.getPostName() != null && post.getPostName().toLowerCase().contains(kw))
                        || (post.getPostCode() != null && post.getPostCode().toLowerCase().contains(kw));
                if (!match) {
                    continue;
                }
            }
            all.add(vo);
        }

        // 内存分页（岗位数量级小，无需 SQL 聚合）
        long total = all.size();
        long from = Math.max(0, (current - 1) * size);
        long to = Math.min(total, from + size);
        IPage<PostAbilityInspectionPostVO> page = new Page<>(current, size, total);
        page.setRecords(from >= total ? List.of() : all.subList((int) from, (int) to));
        return page;
    }

    /**
     * 查询单个岗位的全部能力明细（含风险标注）。
     */
    public List<PostAbilityInspectionItemVO> listAbilities(Long postId) {
        List<PostAbilityModel> abilities = abilityModelMapper.selectList(
                new LambdaQueryWrapper<PostAbilityModel>()
                        .eq(PostAbilityModel::getPostId, postId)
                        .eq(PostAbilityModel::getIsDeleted, 0));
        if (abilities.isEmpty()) {
            return List.of();
        }
        Map<Long, GovernanceAdmissionRecord> admissionMap = loadAdmissionMap(abilities);
        Map<String, PostAbilityGroundingRecord> groundingMap = loadGroundingMap(postId, abilities);

        List<PostAbilityInspectionItemVO> result = new ArrayList<>();
        for (PostAbilityModel m : abilities) {
            result.add(inspect(m,
                    findAdmission(admissionMap, m),
                    groundingMap.get(normalizeAbility(m.getAbilityName()))));
        }
        return result;
    }

    /**
     * 全岗位巡检汇总：岗位数、能力总数、风险能力数、高风险数、AI 来源能力数。
     */
    public Map<String, Long> summary() {
        List<PostAbilityModel> models = abilityModelMapper.selectList(
                new LambdaQueryWrapper<PostAbilityModel>().eq(PostAbilityModel::getIsDeleted, 0));
        Map<Long, List<PostAbilityModel>> byPost = models.stream()
                .collect(Collectors.groupingBy(PostAbilityModel::getPostId, LinkedHashMap::new, Collectors.toList()));
        java.util.Set<Long> validPostIds = loadPostMap(byPost.keySet()).keySet();

        long postCount = 0;
        long riskyCount = 0;
        long highCount = 0;
        long aiCount = 0;
        for (Map.Entry<Long, List<PostAbilityModel>> entry : byPost.entrySet()) {
            Long postId = entry.getKey();
            if (!validPostIds.contains(postId)) {
                continue;
            }
            postCount++;
            List<PostAbilityModel> abilities = entry.getValue();
            Map<Long, GovernanceAdmissionRecord> admissionMap = loadAdmissionMap(abilities);
            Map<String, PostAbilityGroundingRecord> groundingMap = loadGroundingMap(postId, abilities);
            for (PostAbilityModel m : abilities) {
                PostAbilityInspectionItemVO item = inspect(m,
                        findAdmission(admissionMap, m),
                        groundingMap.get(normalizeAbility(m.getAbilityName())));
                if (!"NORMAL".equals(item.getRiskLevel())) {
                    riskyCount++;
                }
                if ("HIGH".equals(item.getRiskLevel())) {
                    highCount++;
                }
                if (Boolean.TRUE.equals(item.getAiSource())) {
                    aiCount++;
                }
            }
        }
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("postCount", postCount);
        result.put("abilityCount", (long) models.size());
        result.put("riskyCount", riskyCount);
        result.put("highCount", highCount);
        result.put("aiSourceCount", aiCount);
        return result;
    }

    /** 批量加载岗位名称 */
    private Map<Long, PostPost> loadPostMap(java.util.Set<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return postPostMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(PostPost::getId, p -> p, (a, b) -> a));
    }

    /** 按 post_ability_model.governance_admission_id 批量加载治理准入记录。 */
    private Map<Long, GovernanceAdmissionRecord> loadAdmissionMap(List<PostAbilityModel> abilities) {
        List<Long> admissionIds = abilities.stream()
                .map(PostAbilityModel::getGovernanceAdmissionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (admissionIds.isEmpty()) {
            return Map.of();
        }
        try {
            return admissionMapper.selectBatchIds(admissionIds).stream()
                    .collect(Collectors.toMap(GovernanceAdmissionRecord::getId, r -> r, (a, b) -> a));
        } catch (Exception e) {
            log.warn("加载治理准入记录失败，跳过该风险来源: postId={}, err={}", abilities.get(0).getPostId(), e.getMessage());
            return Map.of();
        }
    }

    private GovernanceAdmissionRecord findAdmission(Map<Long, GovernanceAdmissionRecord> admissionMap,
                                                     PostAbilityModel ability) {
        Long admissionId = ability.getGovernanceAdmissionId();
        return admissionId == null ? null : admissionMap.get(admissionId);
    }

    /** 按岗位 + 归一化能力名批量加载提取台账，取最新一条 */
    private Map<String, PostAbilityGroundingRecord> loadGroundingMap(Long postId, List<PostAbilityModel> abilities) {
        try {
            List<PostAbilityGroundingRecord> records = groundingMapper.selectList(
                    new LambdaQueryWrapper<PostAbilityGroundingRecord>()
                            .eq(PostAbilityGroundingRecord::getPostId, postId));
            Map<String, PostAbilityGroundingRecord> map = new HashMap<>();
            for (PostAbilityGroundingRecord r : records) {
                String key = normalizeAbility(r.getNormalizedAbilityName() != null
                        ? r.getNormalizedAbilityName() : r.getAbilityName());
                if (key.isBlank()) {
                    continue;
                }
                PostAbilityGroundingRecord existing = map.get(key);
                if (existing == null
                        || (r.getCreatedTime() != null
                        && (existing.getCreatedTime() == null || r.getCreatedTime().isAfter(existing.getCreatedTime())))) {
                    map.put(key, r);
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("加载 JD 提取台账失败，跳过该风险来源: postId={}, err={}", postId, e.getMessage());
            return Map.of();
        }
    }

    /** 对单条岗位能力做风险聚合标注 */
    private PostAbilityInspectionItemVO inspect(PostAbilityModel m,
                                                GovernanceAdmissionRecord admission,
                                                PostAbilityGroundingRecord grounding) {
        PostAbilityInspectionItemVO vo = new PostAbilityInspectionItemVO();
        vo.setId(m.getId());
        vo.setPostId(m.getPostId());
        vo.setAbilityName(m.getAbilityName());
        vo.setTagId(m.getTagId());
        vo.setTechStack(m.getTechStack());
        vo.setMinRequiredLevel(m.getMinRequiredLevel());
        vo.setWeight(m.getWeight());
        vo.setIsRequired(m.getIsRequired());
        vo.setIsCore(m.getIsCore());
        vo.setSourceType(m.getSourceType());
        vo.setModelVersion(m.getModelVersion());
        vo.setRemark(m.getRemark());
        vo.setCreatedTime(m.getCreatedTime());

        List<String> tags = new ArrayList<>();
        boolean hasRisk = false;
        boolean high = false;

        // 1. 名称异常
        String name = m.getAbilityName();
        if (name == null || name.isBlank() || containsAny(name, ABNORMAL_NAME_PATTERNS)) {
            tags.add("名称异常");
            hasRisk = true;
            high = true;
        }

        // 2. AI 生成来源标记
        String st = m.getSourceType();
        boolean ai = st != null && !st.isBlank() && !"MANUAL".equalsIgnoreCase(st) && !"NULL".equalsIgnoreCase(st);
        vo.setAiSource(ai);
        if (ai) {
            tags.add("AI生成");
        }

        // 3. 未关联标签（信息提示，非阻断）
        if (m.getTagId() == null) {
            tags.add("未关联标签");
        }

        // 4. 等级越界
        if (m.getMinRequiredLevel() == null || m.getMinRequiredLevel() < 1 || m.getMinRequiredLevel() > 5) {
            tags.add("等级异常");
            hasRisk = true;
        }

        // 5. 权重越界
        if (m.getWeight() == null || m.getWeight().signum() <= 0
                || m.getWeight().compareTo(new BigDecimal("100")) > 0) {
            tags.add("权重异常");
            hasRisk = true;
        }

        // 6. 治理准入记录（岗位演化/市场JD 等走 harness 的路径）
        if (admission != null) {
            vo.setHarnessDecision(admission.getFinalDecision());
            vo.setHarnessRiskLevel(admission.getRiskLevel());
            vo.setHarnessReason(admission.getReasonJson());
            vo.setHarnessCheckCode(admission.getHarnessCheckCode());
            if ("BLOCK".equals(admission.getFinalDecision())) {
                tags.add("Harness拦截");
                hasRisk = true;
                high = true;
            } else if ("REVIEW".equals(admission.getFinalDecision())) {
                tags.add("Harness复核");
                hasRisk = true;
            } else if ("HIGH".equals(admission.getRiskLevel())) {
                tags.add("高风险");
                hasRisk = true;
                high = true;
            } else if ("MEDIUM".equals(admission.getRiskLevel())) {
                tags.add("中风险");
                hasRisk = true;
            }
        }

        // 7. JD 提取台账（普通提取/确认入库路径）
        if (grounding != null) {
            vo.setGroundingStatus(grounding.getValidationStatus());
            vo.setGroundingReason(grounding.getValidationReason());
            vo.setEvidenceText(grounding.getEvidenceText());
            if ("REJECTED".equals(grounding.getValidationStatus())) {
                tags.add("提取被拒");
                hasRisk = true;
                high = true;
            } else if ("DEFERRED".equals(grounding.getValidationStatus())) {
                tags.add("提取未确认");
                hasRisk = true;
            }
        }

        vo.setRiskTags(tags);
        vo.setRiskLevel(hasRisk ? (high ? "HIGH" : "WARN") : "NORMAL");
        return vo;
    }

    private boolean containsAny(String text, String[] patterns) {
        for (String p : patterns) {
            if (text.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeAbility(String name) {
        return AbilityNameNormalizer.normalize(name);
    }
}
