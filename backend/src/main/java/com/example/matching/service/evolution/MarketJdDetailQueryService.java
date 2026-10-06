package com.example.matching.service.evolution;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.evolution.api.MarketJdDetailResponse;
import com.example.matching.entity.evolution.MarketJdData;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.mapper.evolution.MarketJdDataMapper;
import com.example.matching.mapper.post.PostPostMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.evolution.crawler.CrawlerBatchLogService;
import com.example.matching.service.evolution.crawler.MarketJdAiSkillTagCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 单条市场 JD 解析结果查询（只读）。
 * <p>
 * 单独成一个类而不是塞进 {@link MarketJdImportService}：
 * <ul>
 *   <li>写入链路（导入 / 解析）与读取链路（结果查看）职责不同，混在一起会让导入服务的构造参数
 *       继续膨胀（该项目新增构造参数会让全仓手工装配的单测一起编译失败）；</li>
 *   <li>本类只依赖 Mapper，不需要 Agent / Harness / 清洗等重依赖，单测成本低。</li>
 * </ul>
 * 标签 ID → 名称的反解必须在服务端做：{@code skill_tags} 存的是 ID，前端拿不到标签表。
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketJdDetailQueryService {

    private final MarketJdDataMapper marketJdDataMapper;
    private final AbilityTagMapper abilityTagMapper;
    private final PostPostMapper postPostMapper;
    private final ObjectMapper objectMapper;

    /**
     * 查询一条市场 JD 的解析结果。
     *
     * @param id 市场 JD 主键
     * @throws BusinessException id 为空（400）或记录不存在（404）
     */
    public MarketJdDetailResponse getDetail(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "请指定要查看的市场 JD");
        }
        MarketJdData jd = marketJdDataMapper.selectById(id);
        if (jd == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "该市场 JD 不存在或已被删除");
        }

        return new MarketJdDetailResponse(
                jd.getId(),
                jd.getBatchNo(),
                jd.getPostName(),
                jd.getCompanyName(),
                jd.getCity(),
                jd.getSalaryRange(),
                jd.getJobDescription(),
                jd.getRequirements(),
                jd.getSourcePlatform(),
                jd.getPublishedTime(),
                jd.getAnalysisStatus(),
                jd.getIsDuplicate(),
                jd.getQualityScore(),
                jd.getNoiseScore(),
                jd.getFreshnessScore(),
                jd.getMatchedPostId(),
                resolveMatchedPostName(jd.getMatchedPostId()),
                jd.getIngestChannel(),
                CrawlerBatchLogService.ingestChannelText(jd.getIngestChannel()),
                jd.getSkillTags(),
                resolveTags(jd.getSkillTags()),
                jd.getRecommendedSkillTags(),
                resolveTags(jd.getRecommendedSkillTags()),
                jd.getAiSkillTags(),
                MarketJdAiSkillTagCodec.decode(jd.getAiSkillTags(), objectMapper),
                jd.getCreatedTime());
    }

    /**
     * 把 {@code skill_tags} / {@code recommended_skill_tags} 反解成可读标签。
     * <p>
     * 三态兼容（历史数据不干净，任何一种都不该让整页报错）：
     * <ol>
     *   <li>ID 数组（本系统正常写法，如 {@code [12,45]}）→ 查标签表补名称；查不到（已删除/停用）
     *       仍返回 {@code tagId} 且 {@code tagName} 为 null，前端可显示「标签 #12（已删除或停用）」；</li>
     *   <li>字符串数组或纯文本（早期人工上传的批次把标签名直接塞进该列，如 {@code Java,Spring}）
     *       → 按名称展示，{@code tagId} 为 null；</li>
     *   <li>空 / {@code null} / 非法 JSON → 空列表，由调用方用 {@code rawSkillTags} 兜底展示。</li>
     * </ol>
     */
    List<MarketJdDetailResponse.TagRef> resolveTags(String raw) {
        List<Object> tokens = parseTagTokens(raw);
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<MarketJdDetailResponse.TagRef> resolved = new ArrayList<>();
        List<Integer> pendingIndexes = new ArrayList<>();
        List<Long> pendingIds = new ArrayList<>();

        for (Object token : tokens) {
            Long tagId = asLong(token);
            if (tagId == null) {
                String name = asText(token);
                if (name != null && !name.isBlank()) {
                    resolved.add(new MarketJdDetailResponse.TagRef(null, name.trim(), null, null, null));
                }
                continue;
            }
            // 先占位，等批量查出标签后按原顺序回填，保证展示顺序与入库顺序一致
            pendingIndexes.add(resolved.size());
            resolved.add(null);
            pendingIds.add(tagId);
        }

        if (!pendingIds.isEmpty()) {
            Map<Long, AbilityTag> tagsById = loadTags(pendingIds);
            for (int i = 0; i < pendingIds.size(); i++) {
                Long tagId = pendingIds.get(i);
                AbilityTag tag = tagsById.get(tagId);
                resolved.set(pendingIndexes.get(i), new MarketJdDetailResponse.TagRef(
                        tagId,
                        tag != null ? tag.getTagName() : null,
                        tag != null ? tag.getTagCode() : null,
                        tag != null ? tag.getTagCategory() : null,
                        tag != null ? tag.getTagLevel() : null));
            }
        }

        return resolved.stream().filter(Objects::nonNull).toList();
    }

    private Map<Long, AbilityTag> loadTags(List<Long> ids) {
        List<Long> distinct = ids.stream().distinct().toList();
        Map<Long, AbilityTag> tagsById = new HashMap<>();
        try {
            List<AbilityTag> tags = abilityTagMapper.selectBatchIds(distinct);
            if (tags != null) {
                for (AbilityTag tag : tags) {
                    if (tag != null && tag.getId() != null) {
                        tagsById.put(tag.getId(), tag);
                    }
                }
            }
        } catch (Exception e) {
            // 标签查询失败不应让「结果查看」整个不可用：保留 tagId，名称由前端显示成待补
            log.warn("反解市场 JD 能力标签失败，将只返回标签ID: ids={}", distinct, e);
        }
        return tagsById;
    }

    private String resolveMatchedPostName(Long matchedPostId) {
        if (matchedPostId == null) {
            return null;
        }
        try {
            PostPost post = postPostMapper.selectById(matchedPostId);
            return post != null ? post.getPostName() : null;
        } catch (Exception e) {
            log.warn("查询市场 JD 匹配岗位失败: postId={}", matchedPostId, e);
            return null;
        }
    }

    private List<Object> parseTagTokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String text = raw.trim();
        if ("null".equalsIgnoreCase(text)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node == null || node.isNull() || node.isMissingNode()) {
                return List.of();
            }
            if (node.isArray()) {
                List<Object> tokens = new ArrayList<>();
                node.forEach(child -> {
                    if (child == null || child.isNull()) {
                        return;
                    }
                    if (child.isNumber()) {
                        tokens.add(child.asLong());
                    } else if (child.isTextual()) {
                        tokens.add(child.asText());
                    } else {
                        tokens.add(child.toString());
                    }
                });
                return tokens;
            }
            if (node.isNumber()) {
                return List.of(node.asLong());
            }
            if (node.isTextual()) {
                return splitPlainText(node.asText());
            }
            return List.of();
        } catch (Exception ignored) {
            // 不是合法 JSON —— 早期人工上传可能写入的是「Java,Spring」这类纯文本
            return splitPlainText(text);
        }
    }

    private List<Object> splitPlainText(String text) {
        List<Object> tokens = new ArrayList<>();
        for (String part : text.split("[,，;；\\n]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private Long asLong(Object token) {
        if (token instanceof Number number) {
            return number.longValue();
        }
        if (token instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty() || trimmed.length() > 19 || !trimmed.chars().allMatch(Character::isDigit)) {
                return null;
            }
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String asText(Object token) {
        if (token instanceof String text) {
            return text;
        }
        return token == null ? null : String.valueOf(token);
    }
}
