package com.example.matching.service.post.support;

import com.example.matching.common.util.AbilityNameNormalizer;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.dto.post.TrendCandidatePayload;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.service.post.PostAbilityModelService;
import com.example.matching.service.post.PostAbilityWeightNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把候选里的能力清单与既有岗位能力画像做差集，标注每条能力的变更类型。
 * <p>
 * 解析阶段（判定候选是不是「空变更」）与落地阶段（写回能力模型）都要用这份规律，
 * 抽成组件是为了避免两处各写一遍后逐渐漂移——一处改了另一处没改，
 * 结果就是「候选说升级、落地写的是别的」。
 * <p>
 * <b>只产出 ADD / UPGRADE_LEVEL / DOWNGRADE_LEVEL / UPDATE_WEIGHT / UNCHANGED。</b>
 * 绝不产出删除：材料没提到某项能力，只能说明这份材料没写它，不代表岗位不再需要它。
 */
@Component
@RequiredArgsConstructor
public class TrendAbilityDiffService {

    /** 认作「权重发生实质变化」的最小差值，避免 0.3 这类噪声判成变更 */
    private static final BigDecimal WEIGHT_CHANGE_EPSILON = BigDecimal.valueOf(5);

    private final PostAbilityModelService postAbilityModelService;

    /**
     * 就地标注 {@code payload.abilities} 的变更类型，并回填既有等级 / 权重，
     * 同时把「既有但材料未提及」的能力记入 {@code payload.unmatchedExistingAbilities}。
     *
     * @return 实质变更条数（UNCHANGED 不计）
     */
    public int applyDiff(Long postId, TrendCandidatePayload payload) {
        if (payload == null || payload.getAbilities() == null) {
            return 0;
        }
        payload.setUnmatchedExistingAbilities(new java.util.ArrayList<>());

        List<PostAbilityModel> existing = postId == null ? List.of() : postAbilityModelService.listByPostId(postId);
        Map<String, PostAbilityModel> byNormalizedName = new LinkedHashMap<>();
        if (existing != null) {
            for (PostAbilityModel model : existing) {
                if (model == null || !StringUtils.hasText(model.getAbilityName())) {
                    continue;
                }
                byNormalizedName.putIfAbsent(AbilityNameNormalizer.normalize(model.getAbilityName()), model);
            }
        }

        Set<String> touched = new HashSet<>();
        int diffCount = 0;
        for (TrendCandidatePayload.TrendAbilityItem item : payload.getAbilities()) {
            if (item == null || !StringUtils.hasText(item.getAbilityName())) {
                continue;
            }
            String key = AbilityNameNormalizer.normalize(item.getAbilityName());
            PostAbilityModel current = byNormalizedName.get(key);
            if (current == null) {
                item.setChangeType("ADD");
                diffCount++;
                continue;
            }
            touched.add(key);
            item.setExistingLevel(current.getMinRequiredLevel());
            item.setExistingWeight(current.getWeight());

            if (item.getSuggestedLevel() != null && current.getMinRequiredLevel() != null
                    && !item.getSuggestedLevel().equals(current.getMinRequiredLevel())) {
                item.setChangeType(item.getSuggestedLevel() > current.getMinRequiredLevel()
                        ? "UPGRADE_LEVEL" : "DOWNGRADE_LEVEL");
                diffCount++;
                continue;
            }
            if (item.getSuggestedWeight() != null && current.getWeight() != null
                    && item.getSuggestedWeight().subtract(current.getWeight()).abs()
                    .compareTo(WEIGHT_CHANGE_EPSILON) >= 0) {
                item.setChangeType("UPDATE_WEIGHT");
                diffCount++;
                continue;
            }
            item.setChangeType("UNCHANGED");
        }

        for (Map.Entry<String, PostAbilityModel> entry : byNormalizedName.entrySet()) {
            if (!touched.contains(entry.getKey())) {
                payload.getUnmatchedExistingAbilities().add(entry.getValue().getAbilityName());
            }
        }
        return diffCount;
    }

    /** 全部能力标记为新增（新岗位候选：既有岗位不存在，一切能力都是新增）。 */
    public void markAllAsAdded(TrendCandidatePayload payload) {
        if (payload == null || payload.getAbilities() == null) {
            return;
        }
        payload.getAbilities().forEach(item -> {
            if (item != null) {
                item.setChangeType("ADD");
            }
        });
    }

    /** 能力项权重缺失时的兜底最低等级：1=入门；宁可保守也不要凭空虚高。 */
    private static final int DEFAULT_MIN_LEVEL = 1;

    /** 无可用权重时按项数平均分配的目标总量，与 {@code batchConfig} 的百分比口径一致。 */
    private static final BigDecimal TARGET_TOTAL = new BigDecimal("100");

    /**
     * 生成落库用的完整能力清单。
     * <p>
     * <b>必须是「全量」而不是「变更项」</b>：{@code batchConfig} 会先物理删除该岗位全部旧配置再写入，
     * 只传变更项等于把材料没提到的能力全部删掉——一次材料缺章就会砍掉真实岗位要求。
     * 因此这里是「既有能力全部保留 + 候选能力按同名覆盖等级/权重/核心性 + 新名追加」。
     * <p>
     * 返回值已做权重归一（合计恰为 100）与去重（按能力名归一化），
     * 否则 {@code batchConfig} 会因「权重总和不在 95-105」或「能力重复配置」直接抛错。
     */
    public List<PostAbilityModelConfigDTO> mergeForPersist(Long postId, TrendCandidatePayload payload) {
        Map<String, PostAbilityModelConfigDTO> merged = new LinkedHashMap<>();

        // 1) 既有能力：一项都不能少
        if (postId != null) {
            List<PostAbilityModel> existing = postAbilityModelService.listByPostId(postId);
            if (existing != null) {
                for (PostAbilityModel model : existing) {
                    if (model == null || !StringUtils.hasText(model.getAbilityName())) {
                        continue;
                    }
                    PostAbilityModelConfigDTO dto = new PostAbilityModelConfigDTO();
                    dto.setPostId(postId);
                    dto.setTagId(model.getTagId());
                    dto.setAbilityName(model.getAbilityName().trim());
                    dto.setTechStack(model.getTechStack());
                    dto.setMinRequiredLevel(model.getMinRequiredLevel());
                    dto.setWeight(PostAbilityWeightNormalizer.toPercentage(model.getWeight(), null));
                    dto.setIsRequired(model.getIsRequired() == null ? 0 : model.getIsRequired());
                    dto.setIsCore(model.getIsCore() == null ? 0 : model.getIsCore());
                    merged.putIfAbsent(AbilityNameNormalizer.normalize(model.getAbilityName()), dto);
                }
            }
        }

        // 2) 候选能力：同名覆盖建议值，新名追加
        if (payload != null && payload.getAbilities() != null) {
            for (TrendCandidatePayload.TrendAbilityItem item : payload.getAbilities()) {
                if (item == null || !StringUtils.hasText(item.getAbilityName())) {
                    continue;
                }
                String key = AbilityNameNormalizer.normalize(item.getAbilityName());
                PostAbilityModelConfigDTO dto = merged.get(key);
                if (dto == null) {
                    dto = new PostAbilityModelConfigDTO();
                    dto.setPostId(postId);
                    dto.setAbilityName(item.getAbilityName().trim());
                    // AI 无法判断某项能力是否「必须具备」，一律按非必填落库，由管理员后续收紧
                    dto.setIsRequired(0);
                    merged.put(key, dto);
                }
                if (item.getTagId() != null) {
                    dto.setTagId(item.getTagId());
                }
                if (item.getSuggestedLevel() != null) {
                    dto.setMinRequiredLevel(item.getSuggestedLevel());
                }
                if (item.getSuggestedWeight() != null && item.getSuggestedWeight().signum() > 0) {
                    dto.setWeight(item.getSuggestedWeight());
                }
                if (item.getIsCore() != null) {
                    dto.setIsCore(item.getIsCore());
                }
            }
        }

        List<PostAbilityModelConfigDTO> list = new ArrayList<>(merged.values());
        for (PostAbilityModelConfigDTO dto : list) {
            if (dto.getMinRequiredLevel() == null) {
                dto.setMinRequiredLevel(DEFAULT_MIN_LEVEL);
            }
            if (dto.getIsRequired() == null) {
                dto.setIsRequired(0);
            }
            if (dto.getIsCore() == null) {
                dto.setIsCore(0);
            }
        }
        normalizeWeights(list);
        return list;
    }

    /**
     * 把权重缩放到合计恰为 100。
     * <p>
     * 不能依赖 {@code PostAbilityWeightNormalizer}：它在合计已经落在 95-105 时原样放行，
     * 而 LLM 给出的权重常是「有值但量级不对」（如合计 40 或某项为 0-1 相对值），
     * 那样写进去要么越界报错，要么单点权重失真。这里统一按比例缩放并把舍入误差补给最大项。
     */
    private void normalizeWeights(List<PostAbilityModelConfigDTO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (PostAbilityModelConfigDTO dto : list) {
            if (dto.getWeight() != null && dto.getWeight().signum() > 0) {
                total = total.add(dto.getWeight());
            }
        }
        if (total.signum() <= 0) {
            // 完全没有可用权重：平均分配，至少保证总和落在合法区间
            BigDecimal even = TARGET_TOTAL.divide(new BigDecimal(list.size()), 2, RoundingMode.HALF_UP);
            for (PostAbilityModelConfigDTO dto : list) {
                dto.setWeight(even);
            }
            return;
        }

        BigDecimal assigned = BigDecimal.ZERO;
        PostAbilityModelConfigDTO heaviest = null;
        BigDecimal heaviestWeight = null;
        for (PostAbilityModelConfigDTO dto : list) {
            BigDecimal raw = dto.getWeight() != null && dto.getWeight().signum() > 0
                    ? dto.getWeight() : BigDecimal.ZERO;
            BigDecimal scaled = raw.multiply(TARGET_TOTAL).divide(total, 2, RoundingMode.HALF_UP);
            dto.setWeight(scaled);
            assigned = assigned.add(scaled);
            if (heaviestWeight == null || scaled.compareTo(heaviestWeight) > 0) {
                heaviestWeight = scaled;
                heaviest = dto;
            }
        }
        if (heaviest != null) {
            BigDecimal delta = TARGET_TOTAL.subtract(assigned);
            BigDecimal fixed = heaviest.getWeight().add(delta).setScale(2, RoundingMode.HALF_UP);
            // 极端情况下（项数极多、权重极悬殊）修正后可能落到 0 以下，回退为 0 由 batchConfig 兜底校验
            heaviest.setWeight(fixed.signum() < 0 ? BigDecimal.ZERO : fixed);
        }
    }
}
