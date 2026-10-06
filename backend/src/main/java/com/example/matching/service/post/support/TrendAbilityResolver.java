package com.example.matching.service.post.support;

import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.config.PostTrendProperties;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagCandidate;
import com.example.matching.service.evolution.support.EvolutionAbilityTagCatalog;
import com.example.matching.service.system.AbilityTagCandidateService;
import com.example.matching.service.system.support.AbilityTagMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 把 LLM 从材料里抽出的能力名**归位**到系统既有能力词表。
 * <p>
 * 归位是「AI 提能力名」与「系统标签体系」之间唯一的接口。不归位的后果是岗位能力画像里
 * 堆满同名异写法（「大模型应用」/「大模型应用开发」/「LLM应用」），标签统计随之失效。
 * <p>
 * 三级策略（前一级命中就不再往后走，避免无谓的向量调用与候选写入）：
 * <ol>
 *   <li><b>确定性匹配</b> —— 精确 / 别名 / 归一化，复用 {@link AbilityTagMatchService}；</li>
 *   <li><b>向量兜底</b> —— 在启用标签的向量里取最相似的一条，相似度达标才算归位；</li>
 *   <li><b>进候选池</b> —— 都未命中则写入 {@code ability_tag_candidate}（人工内联确认）。
 *       <b>不会自动进正式标签库</b>，岗位画像落库时该能力 tagId 为空、abilityName 有值
 *       （与 V131「岗位能力画像与标签解耦」的口径一致）。</li>
 * </ol>
 * 候选写入失败不影响解析：能力仍会以 tagId=null 的形式进入候选，只是少了后续的人工归位入口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrendAbilityResolver {

    /** 趋势解析提出的能力标签候选的来源标识，用于候选详情页按本次任务过滤 */
    public static final String SOURCE_TYPE_POST_TREND = "POST_TREND";

    private static final int EVIDENCE_SNIPPET_LIMIT = 500;

    private final AbilityTagMatchService abilityTagMatchService;
    private final EvolutionAbilityTagCatalog abilityTagCatalog;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final AbilityTagCandidateService abilityTagCandidateService;
    private final PostTrendProperties properties;

    /**
     * 归位结果。
     *
     * @param tagId         归位命中时的正式标签ID（已按 canonicalTagId 归一），未归位为 null
     * @param matchedTagName 归位命中时的标签名；未归位时回填 AI 原始能力名，保证调用方始终有可展示文本
     * @param resolved      是否已归位
     * @param similarTagId  未归位时最相似的既有标签（仅当相似度达到参考阈值）
     * @param similarity    最相似标签的相似度
     * @param tagCandidateId 未归位时写入的候选ID
     */
    public record TagResolution(Long tagId, String matchedTagName, boolean resolved,
                                Long similarTagId, String similarTagName, BigDecimal similarity,
                                Long tagCandidateId) {
    }

    public TagResolution resolve(String abilityName, Long taskId, String evidenceText) {
        if (!StringUtils.hasText(abilityName)) {
            return new TagResolution(null, null, false, null, null, null, null);
        }
        String name = abilityName.trim();

        // 1) 确定性匹配：先把不需要向量的情况吃掉
        AbilityTag exact = abilityTagMatchService.matchByName(name);
        if (exact != null) {
            return new TagResolution(canonicalId(exact), exact.getTagName(), true, null, null, null, null);
        }

        // 2) 向量兜底：取最相似的既有标签
        AbilityTag nearest = null;
        BigDecimal similarity = null;
        List<Float> queryVector = safeEmbed(name);
        if (queryVector != null && !queryVector.isEmpty()) {
            double best = -1D;
            for (AbilityTag tag : abilityTagCatalog.activeTags()) {
                if (tag == null || tag.getEmbeddingVector() == null || tag.getEmbeddingVector().isEmpty()) {
                    continue;
                }
                Float cosine = vectorEmbeddingService.cosineSimilarity(queryVector, tag.getEmbeddingVector());
                if (cosine == null) {
                    continue;
                }
                if (cosine > best) {
                    best = cosine;
                    nearest = tag;
                }
            }
            if (nearest != null) {
                similarity = BigDecimal.valueOf(best).setScale(4, RoundingMode.HALF_UP);
            }
        }

        if (nearest != null && similarity != null
                && similarity.doubleValue() >= properties.getTagResolveSimilarity()) {
            return new TagResolution(canonicalId(nearest), nearest.getTagName(), true,
                    nearest.getId(), nearest.getTagName(), similarity, null);
        }

        // 3) 未归位：进候选池，人工内联确认
        boolean worthReporting = nearest != null && similarity != null
                && similarity.doubleValue() >= properties.getTagReportSimilarity();
        Long candidateId = createCandidate(name, taskId, evidenceText,
                worthReporting ? nearest : null, worthReporting ? similarity : null);

        return new TagResolution(null, name, false,
                worthReporting ? nearest.getId() : null,
                worthReporting ? nearest.getTagName() : null,
                worthReporting ? similarity : null,
                candidateId);
    }

    /** 归一标签ID：有 canonicalTagId 时以它为准，否则用自身，避免同一能力落在不同标签上。 */
    private Long canonicalId(AbilityTag tag) {
        return tag.getCanonicalTagId() != null ? tag.getCanonicalTagId() : tag.getId();
    }

    private List<Float> safeEmbed(String text) {
        try {
            return vectorEmbeddingService.embed(text);
        } catch (Exception e) {
            log.warn("能力名向量化失败，跳过向量归位: name={}, err={}", text, e.getMessage());
            return null;
        }
    }

    private Long createCandidate(String name, Long taskId, String evidenceText,
                                 AbilityTag similar, BigDecimal similarity) {
        try {
            AbilityTagCandidate candidate = new AbilityTagCandidate();
            candidate.setCandidateName(name);
            candidate.setSourceType(SOURCE_TYPE_POST_TREND);
            candidate.setSourceRefId(taskId);
            candidate.setEvidenceText(truncate(evidenceText, EVIDENCE_SNIPPET_LIMIT));
            candidate.setReason("权威材料解析提出的岗位能力，未在既有能力标签库中找到对应项");
            if (similar != null) {
                candidate.setSimilarTagId(similar.getId());
                candidate.setSimilarTagName(similar.getTagName());
                candidate.setSimilarityScore(similarity);
                candidate.setReasoning("与既有标签「" + similar.getTagName() + "」相似度 " + similarity
                        + "，未达自动归位阈值，需人工判断采用现有标签或确认新增");
            }
            return abilityTagCandidateService.addCandidate(candidate);
        } catch (Exception e) {
            // 候选池写入失败不应中断整次解析：能力仍以 tagId=null 落库，仅少一个归位入口
            log.warn("写入能力标签候选失败，能力按未归位处理: name={}, err={}", name, e.getMessage());
            return null;
        }
    }

    private String truncate(String text, int limit) {
        if (text == null) {
            return null;
        }
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
