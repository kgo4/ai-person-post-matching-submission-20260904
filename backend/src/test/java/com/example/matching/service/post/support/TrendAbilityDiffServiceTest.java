package com.example.matching.service.post.support;

import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.dto.post.TrendCandidatePayload;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.service.post.PostAbilityModelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 能力差集单测。
 * <p>
 * 核心回归点：**不得产出删除建议**。材料是抽样的，没提到某项能力只说明这份材料没写，
 * 不代表岗位不再需要；一旦生成删除建议，一次材料缺章就会砍掉真实岗位要求。
 */
@DisplayName("趋势能力差集（S3）")
class TrendAbilityDiffServiceTest {

    private final PostAbilityModelService postAbilityModelService = mock(PostAbilityModelService.class);
    private final TrendAbilityDiffService service = new TrendAbilityDiffService(postAbilityModelService);

    @Test
    @DisplayName("既有但材料未提及的能力只入参考清单，绝不生成删除项")
    void existingButUnmentionedAbilitiesNeverBecomeDeletions() {
        when(postAbilityModelService.listByPostId(7L)).thenReturn(List.of(
                ability("内容安全", 3, 20),
                ability("数据标注", 2, 10)));

        TrendCandidatePayload payload = new TrendCandidatePayload();
        payload.getAbilities().add(item("内容安全", 3, BigDecimal.valueOf(20)));
        payload.getAbilities().add(item("模型评测", 4, BigDecimal.valueOf(30)));

        int diffCount = service.applyDiff(7L, payload);

        // 只有「模型评测」是新增；「数据标注」未被改动
        assertThat(diffCount).isEqualTo(1);
        assertThat(payload.getUnmatchedExistingAbilities()).containsExactly("数据标注");
        assertThat(payload.getAbilities()).extracting(TrendCandidatePayload.TrendAbilityItem::getChangeType)
                .containsExactly("UNCHANGED", "ADD");
    }

    @Test
    @DisplayName("能力名写法不同（括号/空格/大小写）也能与既有画像对上，不被误判为新增")
    void nameNormalizationAvoidsDuplicateAdds() {
        when(postAbilityModelService.listByPostId(7L)).thenReturn(List.of(ability("Spring Boot", 3, 20)));

        TrendCandidatePayload payload = new TrendCandidatePayload();
        payload.getAbilities().add(item("spring-boot", 4, BigDecimal.valueOf(20)));

        int diffCount = service.applyDiff(7L, payload);

        assertThat(diffCount).isEqualTo(1);
        assertThat(payload.getAbilities().get(0).getChangeType()).isEqualTo("UPGRADE_LEVEL");
        assertThat(payload.getAbilities().get(0).getExistingLevel()).isEqualTo(3);
    }

    @Test
    @DisplayName("等级变化优先于权重，同一能力不产生两个变更")
    void levelChangeTakesPrecedenceOverWeight() {
        when(postAbilityModelService.listByPostId(7L)).thenReturn(List.of(ability("向量检索", 2, 10)));

        TrendCandidatePayload payload = new TrendCandidatePayload();
        payload.getAbilities().add(item("向量检索", 5, BigDecimal.valueOf(60)));

        service.applyDiff(7L, payload);

        assertThat(payload.getAbilities()).hasSize(1);
        assertThat(payload.getAbilities().get(0).getChangeType()).isEqualTo("UPGRADE_LEVEL");
    }

    @Test
    @DisplayName("微小权重差异视为噪声，不产出变更")
    void smallWeightDifferenceIsNotAChange() {
        when(postAbilityModelService.listByPostId(7L)).thenReturn(List.of(ability("提示工程", 3, 20)));

        TrendCandidatePayload payload = new TrendCandidatePayload();
        payload.getAbilities().add(item("提示工程", 3, BigDecimal.valueOf(22)));

        assertThat(service.applyDiff(7L, payload)).isZero();
        assertThat(payload.getAbilities().get(0).getChangeType()).isEqualTo("UNCHANGED");
    }

    @Test
    @DisplayName("材料未给等级与权重时不下判定，避免把「没信息」当成「要改」")
    void missingSuggestionIsNotTreatedAsChange() {
        when(postAbilityModelService.listByPostId(7L)).thenReturn(List.of(ability("合规审查", 3, 20)));

        TrendCandidatePayload payload = new TrendCandidatePayload();
        payload.getAbilities().add(item("合规审查", null, null));

        assertThat(service.applyDiff(7L, payload)).isZero();
        assertThat(payload.getAbilities().get(0).getChangeType()).isEqualTo("UNCHANGED");
        assertThat(payload.getAbilities().get(0).getExistingLevel()).isEqualTo(3);
    }

    @Test
    @DisplayName("降级同样是变更，必须让人看到")
    void downgradeIsCountedAsChange() {
        when(postAbilityModelService.listByPostId(7L)).thenReturn(List.of(ability("传统运维", 4, 20)));

        TrendCandidatePayload payload = new TrendCandidatePayload();
        payload.getAbilities().add(item("传统运维", 2, BigDecimal.valueOf(20)));

        assertThat(service.applyDiff(7L, payload)).isEqualTo(1);
        assertThat(payload.getAbilities().get(0).getChangeType()).isEqualTo("DOWNGRADE_LEVEL");
    }

    @Test
    @DisplayName("新岗位候选全部标为新增")
    void markAllAsAddedForNewPost() {
        TrendCandidatePayload payload = new TrendCandidatePayload();
        payload.getAbilities().add(item("内容安全", 3, BigDecimal.valueOf(20)));
        payload.getAbilities().add(item("模型评测", 4, BigDecimal.valueOf(30)));

        service.markAllAsAdded(payload);

        assertThat(payload.getAbilities()).extracting(TrendCandidatePayload.TrendAbilityItem::getChangeType)
                .containsOnly("ADD");
    }

    private PostAbilityModel ability(String name, int level, int weight) {
        PostAbilityModel model = new PostAbilityModel();
        model.setAbilityName(name);
        model.setMinRequiredLevel(level);
        model.setWeight(BigDecimal.valueOf(weight));
        return model;
    }

    private TrendCandidatePayload.TrendAbilityItem item(String name, Integer level, BigDecimal weight) {
        TrendCandidatePayload.TrendAbilityItem item = new TrendCandidatePayload.TrendAbilityItem();
        item.setAbilityName(name);
        item.setSuggestedLevel(level);
        item.setSuggestedWeight(weight);
        return item;
    }

    // ================================================================ 落地合并（S4）

    @Test
    @DisplayName("落地写的是全量清单：材料未提及的既有能力必须保留，不能因为 batchConfig 先删后写而丢失")
    void mergeForPersistKeepsExistingAbilitiesNotMentionedInMaterial() {
        when(postAbilityModelService.listByPostId(7L)).thenReturn(List.of(
                ability("内容安全", 3, 20),
                ability("数据标注", 2, 30)));

        TrendCandidatePayload payload = new TrendCandidatePayload();
        payload.getAbilities().add(item("内容安全", 4, BigDecimal.valueOf(40)));
        payload.getAbilities().add(item("模型评测", 5, BigDecimal.valueOf(40)));

        List<PostAbilityModelConfigDTO> merged = service.mergeForPersist(7L, payload);

        assertThat(merged).extracting(PostAbilityModelConfigDTO::getAbilityName)
                .containsExactlyInAnyOrder("内容安全", "数据标注", "模型评测");
        // 候选给出的新等级覆盖既有等级
        PostAbilityModelConfigDTO contentSafety = find(merged, "内容安全");
        assertThat(contentSafety.getMinRequiredLevel()).isEqualTo(4);
        assertThat(contentSafety.getPostId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("权重合计必须归一为 100，否则 batchConfig 会以「权重总和不在 95-105」直接拒绝落地")
    void mergeForPersistNormalizesWeightsToHundred() {
        when(postAbilityModelService.listByPostId(7L)).thenReturn(List.of());

        TrendCandidatePayload payload = new TrendCandidatePayload();
        payload.getAbilities().add(item("甲", 3, BigDecimal.valueOf(3)));
        payload.getAbilities().add(item("乙", 3, BigDecimal.valueOf(3)));
        payload.getAbilities().add(item("丙", 3, BigDecimal.valueOf(4)));

        List<PostAbilityModelConfigDTO> merged = service.mergeForPersist(null, payload);

        BigDecimal total = merged.stream()
                .map(PostAbilityModelConfigDTO::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("完全没有权重时平均分配，仍然落在合法区间")
    void mergeForPersistFallsBackToEvenWeights() {
        when(postAbilityModelService.listByPostId(7L)).thenReturn(List.of());

        TrendCandidatePayload payload = new TrendCandidatePayload();
        payload.getAbilities().add(item("甲", null, null));
        payload.getAbilities().add(item("乙", null, null));

        List<PostAbilityModelConfigDTO> merged = service.mergeForPersist(null, payload);

        assertThat(merged).hasSize(2);
        merged.forEach(config -> {
            assertThat(config.getWeight()).isEqualByComparingTo("50");
            // 等级缺失时兜底为 1，不能让 batchConfig 拿到 null
            assertThat(config.getMinRequiredLevel()).isEqualTo(1);
            // AI 无法判断「必须具备」，一律非必填落库
            assertThat(config.getIsRequired()).isZero();
        });
    }

    @Test
    @DisplayName("同名能力只写一条，避免 batchConfig 抛「岗位能力重复配置」")
    void mergeForPersistDeduplicatesByNormalizedName() {
        when(postAbilityModelService.listByPostId(7L)).thenReturn(List.of(ability("Spring Boot", 3, 100)));

        TrendCandidatePayload payload = new TrendCandidatePayload();
        payload.getAbilities().add(item("spring-boot", 4, BigDecimal.valueOf(100)));
        payload.getAbilities().add(item("SPRING  BOOT", 4, BigDecimal.valueOf(100)));

        List<PostAbilityModelConfigDTO> merged = service.mergeForPersist(7L, payload);

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getMinRequiredLevel()).isEqualTo(4);
    }

    private PostAbilityModelConfigDTO find(List<PostAbilityModelConfigDTO> list, String abilityName) {
        return list.stream()
                .filter(config -> abilityName.equals(config.getAbilityName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到能力: " + abilityName));
    }
}
