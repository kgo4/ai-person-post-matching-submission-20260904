package com.example.matching.service.rag.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.rag.KnowledgeChunkResultDTO;
import com.example.matching.dto.rag.KnowledgeChunkSearchDTO;
import com.example.matching.dto.rag.KnowledgeDocumentQueryDTO;
import com.example.matching.dto.rag.KnowledgeDocumentSaveDTO;
import com.example.matching.entity.rag.RagKnowledgeChunk;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.event.RagKnowledgeDocumentSavedEvent;
import com.example.matching.mapper.rag.RagKnowledgeChunkMapper;
import com.example.matching.mapper.rag.RagKnowledgeDocumentMapper;
import com.example.matching.port.contest.ContestQueryPort;
import com.example.matching.port.learning.LearningQueryPort;
import com.example.matching.port.post.PostQueryPort;
import com.example.matching.port.tag.TagQueryPort;
import com.example.matching.port.talent.TalentQueryPort;
import com.example.matching.ai.service.VectorEmbeddingService;
import com.example.matching.port.knowledge.KnowledgeProjectionPort;
import com.example.matching.service.rag.KnowledgeChunker;
import com.example.matching.service.rag.KnowledgeDocumentService;
import com.example.matching.service.rag.RagVectorStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private final RagKnowledgeDocumentMapper documentMapper;
    private final RagKnowledgeChunkMapper chunkMapper;
    private final KnowledgeChunker knowledgeChunker;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final RagVectorStore ragVectorStore;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final KnowledgeProjectionPort knowledgeProjectionPort;
    private final com.example.matching.service.rag.KnowledgeDocumentDeduplicator deduplicator;
    private final com.example.matching.config.EmbeddingDescriptor embeddingDescriptor;

    // 跨域查询通过 Port 接口
    private final PostQueryPort postQueryPort;
    private final TagQueryPort tagQueryPort;
    private final LearningQueryPort learningQueryPort;
    private final TalentQueryPort talentQueryPort;
    private final ContestQueryPort contestQueryPort;

    @Override
    @Transactional
    public RagKnowledgeDocument saveDocument(KnowledgeDocumentSaveDTO dto) {
        RagKnowledgeDocument doc;
        if (dto.getId() != null) {
            doc = documentMapper.selectById(dto.getId());
            if (doc == null) {
                doc = new RagKnowledgeDocument();
                doc.setId(dto.getId());
            }
        } else {
            doc = new RagKnowledgeDocument();
            doc.setDocCode("DOC_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        }
        doc.setSourceType(dto.getSourceType());
        doc.setSourceRefId(dto.getSourceRefId());
        doc.setTitle(dto.getTitle());
        doc.setContent(dto.getContent());

        // 溯源元数据：规范化去重键 + 来源分组 + 嵌入模型描述
        doc.setCanonicalContentHash(deduplicator.canonicalHash(dto.getContent()));
        doc.setCanonicalSourceGroup(deduplicator.sourceGroup(dto.getSourceType()));
        doc.setEmbeddingModel(embeddingDescriptor.modelName());
        doc.setEmbeddingDimension(embeddingDescriptor.dimension());
        writeCanonicalTagMetadata(doc, dto);

        String contentHash = computeContentHash(dto.getContent());
        boolean contentChanged = !contentHash.equals(doc.getContentHash());
        if (contentChanged) {
            long newRevision = (doc.getContentRevision() != null ? doc.getContentRevision() : 0L) + 1;
            doc.setContentRevision(newRevision);
            doc.setContentHash(contentHash);
            doc.setIndexingStatus("PENDING");
            doc.setIndexingError(null);
        }
        doc.setLastIndexedTime(null);

        if (dto.getId() == null) {
            doc.setDocStatus("ACTIVE");
        }
        if (dto.getId() != null) {
            documentMapper.updateById(doc);
        } else {
            documentMapper.insert(doc);
        }

        if (contentChanged && knowledgeProjectionPort != null && doc.getId() != null
                && doc.getContentRevision() != null) {
            knowledgeProjectionPort.enqueueMilvusRagDocument(
                    doc.getId(), doc.getContentRevision(), contentHash);
        }

        eventPublisher.publishEvent(new RagKnowledgeDocumentSavedEvent(doc.getId()));
        return doc;
    }

    private String computeContentHash(String content) {
        if (content == null) return DigestUtils.md5DigestAsHex("".getBytes(StandardCharsets.UTF_8));
        return DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 受控别名归一化：仅复用治理后的能力标签目录。
     * 精确标签名或已批准别名匹配到正式标签时，在元数据中记录 canonicalTagId 与规范化名称；
     * 未知名称保持不变，交由治理审核，绝不通过编辑距离或 LLM 推断同义词。
     */
    private void writeCanonicalTagMetadata(RagKnowledgeDocument doc, KnowledgeDocumentSaveDTO dto) {
        try {
            String tagName = null;
            if ("ABILITY_TAG".equals(dto.getSourceType())) {
                tagName = dto.getTitle() != null
                        ? dto.getTitle().replaceFirst("^能力标签：", "").trim()
                        : null;
            } else if (dto.getContent() != null
                    && dto.getContent().startsWith("能力标签名称：")) {
                int end = dto.getContent().indexOf('\n');
                if (end > 0) {
                    tagName = dto.getContent().substring("能力标签名称：".length(), end).trim();
                }
            }
            if (tagName == null || tagName.isBlank()) {
                return;
            }
            TagQueryPort.TagDTO tag = tagQueryPort.getTagByName(tagName);
            if (tag == null) {
                return;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("canonicalTagId", tag.canonicalTagId() != null ? tag.canonicalTagId() : tag.id());
            metadata.put("canonicalAbilityName", tag.tagName());
            doc.setMetadataJson(objectMapper.writeValueAsString(metadata));
        } catch (Exception e) {
            log.debug("写入规范化标签元数据失败: {}", e.getMessage());
        }
    }

    @Override
    public IPage<RagKnowledgeDocument> pageDocuments(Page<RagKnowledgeDocument> page, KnowledgeDocumentQueryDTO query) {
        LambdaQueryWrapper<RagKnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RagKnowledgeDocument::getIsDeleted, 0);
        if (query != null) {
            if (query.getSourceType() != null && !query.getSourceType().isBlank())
                wrapper.eq(RagKnowledgeDocument::getSourceType, query.getSourceType());
            if (query.getTitle() != null && !query.getTitle().isBlank())
                wrapper.and(w -> w.like(RagKnowledgeDocument::getTitle, query.getTitle())
                        .or().like(RagKnowledgeDocument::getContent, query.getTitle()));
        }
        wrapper.orderByDesc(RagKnowledgeDocument::getCreatedTime);
        return documentMapper.selectPage(page, wrapper);
    }

    @Override
    public RagKnowledgeDocument getDocumentById(Long id) {
        RagKnowledgeDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "知识文档不存在: " + id);
        }
        return doc;
    }

    @Override
    @Transactional
    public int indexDocument(Long documentId) {
        RagKnowledgeDocument doc = getDocumentById(documentId);
        long revision = doc.getContentRevision() == null ? 1L : doc.getContentRevision();
        List<RagKnowledgeChunk> previousChunks = chunkMapper.selectList(
                new LambdaQueryWrapper<RagKnowledgeChunk>()
                        .eq(RagKnowledgeChunk::getDocumentId, documentId));

        // Saving an unchanged document still invokes this method from several
        // business projections. Its current revision has already been indexed,
        // so inserting the same chunk indexes again would violate the unique key.
        if ("INDEXED".equals(doc.getIndexingStatus())
                && Long.valueOf(revision).equals(doc.getIndexedRevision())
                && !previousChunks.isEmpty()) {
            return previousChunks.size();
        }

        com.example.matching.service.rag.ChunkingProfile profile =
                com.example.matching.service.rag.ChunkingProfile.forSourceType(doc.getSourceType());
        List<String> chunkTexts = knowledgeChunker.chunk(doc.getContent(), profile);
        if (chunkTexts.isEmpty()) {
            doc.setChunkCount(0);
            doc.setLastIndexedTime(LocalDateTime.now());
            doc.setIndexedRevision(revision);
            doc.setIndexingStatus("INDEXED");
            doc.setIndexingError(null);
            documentMapper.updateById(doc);
            return 0;
        }

        List<List<Float>> embeddings = vectorEmbeddingService.embedBatch(chunkTexts);
        boolean embeddingsAvailable = embeddings != null
                && embeddings.size() == chunkTexts.size()
                && embeddings.stream().allMatch(vector -> vector != null && !vector.isEmpty());

        for (int i = 0; i < chunkTexts.size(); i++) {
            RagKnowledgeChunk chunk = new RagKnowledgeChunk();
            chunk.setDocumentId(documentId);
            chunk.setChunkIndex(i);
            chunk.setChunkText(chunkTexts.get(i));
            chunk.setChunkStatus("ACTIVE");
            chunk.setTokenCount(chunkTexts.get(i).length());
            chunk.setDocumentRevision(revision);
            chunk.setIsCurrent(1);
            chunk.setChunkProfile(profile.name());

            if (embeddingsAvailable) {
                try {
                    chunk.setEmbeddingVector(objectMapper.writeValueAsString(embeddings.get(i)));
                } catch (Exception e) {
                    log.warn("序列化向量失败: {}", e.getMessage());
                }
            }
            chunkMapper.insert(chunk);

            if (embeddingsAvailable) {
                try {
                    ragVectorStore.insert(chunk, doc.getSourceType(), embeddings.get(i));
                } catch (RagVectorStoreFallbackException fallbackEx) {
                    // H3：Milvus 不可用，数据已落 MySQL 权威表；标记 DEGRADED 且不更新
                    // indexedRevision，由补偿调度器（RagKnowledgeIndexRecoveryScheduler）
                    // 在 Milvus 恢复后重放投影。不重试到永久失败。
                    doc.setChunkCount(chunkTexts.size());
                    doc.setLastIndexedTime(LocalDateTime.now());
                    doc.setIndexingStatus("DEGRADED");
                    doc.setIndexingError("Milvus 投影降级（数据已落 MySQL），等待补偿同步: "
                            + fallbackEx.getMessage());
                    documentMapper.updateById(doc);
                    log.warn("RAG 文档索引降级完成（数据已落 MySQL）: documentId={}, documentIndex={}",
                            documentId, i);
                    return chunkTexts.size();
                }
            }
        }

        if (!embeddingsAvailable) {
            // The document and its chunks remain searchable through MySQL keyword
            // retrieval. Keep indexedRevision behind so the recovery scheduler can
            // replay this exact revision once the embedding provider recovers.
            doc.setChunkCount(chunkTexts.size());
            doc.setLastIndexedTime(LocalDateTime.now());
            doc.setIndexingStatus("DEGRADED");
            doc.setIndexingError("嵌入服务未返回完整向量，已保留 MySQL 分块并等待补偿同步");
            documentMapper.updateById(doc);
            log.warn("RAG 文档嵌入降级完成（MySQL 分块可用）: documentId={}", documentId);
            return chunkTexts.size();
        }

        // Only remove the previous projection after every new chunk/vector has been written.
        for (RagKnowledgeChunk previous : previousChunks) {
            ragVectorStore.deleteByChunkId(previous.getId());
            chunkMapper.deleteById(previous.getId());
        }

        doc.setChunkCount(chunkTexts.size());
        doc.setLastIndexedTime(LocalDateTime.now());
        doc.setIndexedRevision(revision);
        doc.setEmbeddingModel(embeddingDescriptor.modelName());
        doc.setEmbeddingDimension(embeddingDescriptor.dimension());
        doc.setIndexingStatus("INDEXED");
        doc.setIndexingError(null);
        documentMapper.updateById(doc);

        return chunkTexts.size();
    }

    @Override
    @Transactional
    public Map<String, Object> indexDocuments(String sourceType, boolean onlyUnindexed, int limit) {
        int effectiveLimit = limit > 0 ? limit : 100;
        LambdaQueryWrapper<RagKnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RagKnowledgeDocument::getDocStatus, "ACTIVE");
        wrapper.eq(RagKnowledgeDocument::getIsDeleted, 0);
        if (sourceType != null && !sourceType.isBlank()) {
            wrapper.eq(RagKnowledgeDocument::getSourceType, sourceType);
        }
        if (onlyUnindexed) {
            wrapper.isNull(RagKnowledgeDocument::getLastIndexedTime);
        }
        wrapper.orderByDesc(RagKnowledgeDocument::getUpdatedTime);
        wrapper.last("LIMIT " + effectiveLimit);

        List<RagKnowledgeDocument> docs = documentMapper.selectList(wrapper);
        int chunkCount = 0;
        for (RagKnowledgeDocument doc : docs) {
            chunkCount += indexDocument(doc.getId());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentCount", docs.size());
        result.put("chunkCount", chunkCount);
        return result;
    }

    @Override
    @Transactional
    public int backfillDocuments(String sourceType, int limit) {
        return switch (sourceType) {
            case "POST_PROTOTYPE" -> backfillFromPostPrototype(limit);
            case "ABILITY_TAG" -> backfillFromAbilityTag(limit);
            case "LEARNING_RESOURCE" -> backfillFromLearningResource(limit);
            case "JD_IMPORT" -> backfillFromJdImport(limit);
            case "CONTEST_EVIDENCE" -> backfillFromContestEvidence(limit);
            case "EMP_ABILITY" -> backfillFromEmpAbility(limit);
            case "POST_ABILITY_MODEL" -> backfillFromPostAbilityModel(limit);
            default -> throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "不支持的来源类型: " + sourceType);
        };
    }

    @Override
    public List<KnowledgeChunkResultDTO> searchChunks(KnowledgeChunkSearchDTO dto) {
        List<Float> queryVector = vectorEmbeddingService.embed(dto.getQueryText());
        if (queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }

        int topK = dto.getTopK() != null ? dto.getTopK() : 5;
        List<RagVectorStore.ScoredChunk> scoredChunks = ragVectorStore.search(queryVector, topK, dto.getSourceTypes());

        // 批量加载文档，避免每命中一次 selectById（N+1）
        List<Long> documentIds = scoredChunks.stream()
                .map(sc -> sc.chunk().getDocumentId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, RagKnowledgeDocument> documentsById = documentIds.isEmpty()
                ? Map.of()
                : documentMapper.selectBatchIds(documentIds).stream()
                        .collect(Collectors.toMap(RagKnowledgeDocument::getId, doc -> doc, (a, b) -> a));

        return scoredChunks.stream().map(sc -> {
            KnowledgeChunkResultDTO result = new KnowledgeChunkResultDTO();
            result.setChunkId(sc.chunk().getId());
            result.setDocumentId(sc.chunk().getDocumentId());
            result.setChunkText(sc.chunk().getChunkText());
            result.setChunkIndex(sc.chunk().getChunkIndex());
            result.setScore(sc.score());
            RagKnowledgeDocument doc = documentsById.get(sc.chunk().getDocumentId());
            if (doc != null) {
                result.setDocumentTitle(doc.getTitle());
                result.setSourceType(doc.getSourceType());
            }
            return result;
        }).collect(Collectors.toList());
    }

    @Override
    public Long findExistingDocumentId(String sourceType, Long sourceRefId) {
        if (sourceType == null || sourceRefId == null) return null;
        LambdaQueryWrapper<RagKnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RagKnowledgeDocument::getSourceType, sourceType);
        wrapper.eq(RagKnowledgeDocument::getSourceRefId, sourceRefId);
        wrapper.eq(RagKnowledgeDocument::getIsDeleted, 0);
        wrapper.last("LIMIT 1");
        RagKnowledgeDocument existing = documentMapper.selectOne(wrapper);
        return existing != null ? existing.getId() : null;
    }

    // ===================== 回填方法 =====================

    private int backfillFromPostPrototype(int limit) {
        List<PostQueryPort.PostPrototypeDTO> prototypes = postQueryPort.listActivePrototypes(limit);
        int created = 0;
        for (var p : prototypes) {
            if (existsBySource("POST_PROTOTYPE", p.id())) continue;
            String content = "岗位原型名称：" + p.prototypeName() + "\n"
                    + "行业：" + (p.industry() != null ? p.industry() : "") + "\n"
                    + "分类：" + (p.category() != null ? p.category() : "") + "\n"
                    + "描述：" + (p.description() != null ? p.description() : "");
            KnowledgeDocumentSaveDTO dto = new KnowledgeDocumentSaveDTO();
            dto.setSourceType("POST_PROTOTYPE");
            dto.setSourceRefId(p.id());
            dto.setTitle("岗位原型：" + p.prototypeName());
            dto.setContent(content);
            saveDocument(dto);
            created++;
        }
        return created;
    }

    private int backfillFromAbilityTag(int limit) {
        List<TagQueryPort.TagDTO> tags = tagQueryPort.listActiveTags(limit);
        int created = 0;
        for (var tag : tags) {
            if (existsBySource("ABILITY_TAG", tag.id())) continue;
            String content = "能力标签名称：" + tag.tagName() + "\n"
                    + "分类：" + (tag.tagCategory() != null ? tag.tagCategory() : "") + "\n"
                    + "描述：" + "";
            KnowledgeDocumentSaveDTO dto = new KnowledgeDocumentSaveDTO();
            dto.setSourceType("ABILITY_TAG");
            dto.setSourceRefId(tag.id());
            dto.setTitle("能力标签：" + tag.tagName());
            dto.setContent(content);
            saveDocument(dto);
            created++;
        }
        return created;
    }

    private int backfillFromLearningResource(int limit) {
        List<LearningQueryPort.LearningResourceDTO> resources = learningQueryPort.listActiveResources(limit);
        int created = 0;
        for (var r : resources) {
            if (existsBySource("LEARNING_RESOURCE", r.id())) continue;
            String content = "学习资源：" + r.title() + "\n"
                    + "能力：" + (r.abilityName() != null ? r.abilityName() : "") + "\n"
                    + "类型：" + (r.resourceType() != null ? r.resourceType() : "") + "\n"
                    + "描述：" + (r.description() != null ? r.description() : "");
            KnowledgeDocumentSaveDTO dto = new KnowledgeDocumentSaveDTO();
            dto.setSourceType("LEARNING_RESOURCE");
            dto.setSourceRefId(r.id());
            dto.setTitle("学习资源：" + r.title());
            dto.setContent(content);
            saveDocument(dto);
            created++;
        }
        return created;
    }

    private int backfillFromJdImport(int limit) {
        List<PostQueryPort.JdImportTaskDTO> tasks = postQueryPort.listAnalyzedJdImportTasks(limit);
        int created = 0;
        for (var t : tasks) {
            if (existsBySource("JD_IMPORT", t.id())) continue;
            KnowledgeDocumentSaveDTO dto = new KnowledgeDocumentSaveDTO();
            dto.setSourceType("JD_IMPORT");
            dto.setSourceRefId(t.id());
            dto.setTitle("JD导入 #" + t.id());
            dto.setContent(t.jdRawText());
            saveDocument(dto);
            created++;
        }
        return created;
    }

    private int backfillFromContestEvidence(int limit) {
        List<ContestQueryPort.ContestEvidenceDTO> items = contestQueryPort.listAllEvidence(limit);
        int created = 0;
        for (var item : items) {
            if (existsBySource("CONTEST_EVIDENCE", item.id())) continue;
            String content = "证据标题：" + (item.sourceTitle() != null ? item.sourceTitle() : "") + "\n"
                    + "证据文本：" + (item.sourceText() != null ? item.sourceText() : "");
            KnowledgeDocumentSaveDTO dto = new KnowledgeDocumentSaveDTO();
            dto.setSourceType("CONTEST_EVIDENCE");
            dto.setSourceRefId(item.id());
            dto.setTitle("竞赛证据：" + (item.sourceTitle() != null ? item.sourceTitle() : ""));
            dto.setContent(content);
            saveDocument(dto);
            created++;
        }
        return created;
    }

    private int backfillFromEmpAbility(int limit) {
        List<TalentQueryPort.EmployeeAbilityDTO> abilities = talentQueryPort.listActiveAbilities(limit);
        int created = 0;
        for (var a : abilities) {
            if (existsBySource("EMP_ABILITY", a.id())) continue;
            String content = "员工能力\n掌握等级：" + a.masteryLevel() + "\n"
                    + "来源：" + (a.evaluationSource() != null ? a.evaluationSource() : "");
            KnowledgeDocumentSaveDTO dto = new KnowledgeDocumentSaveDTO();
            dto.setSourceType("EMP_ABILITY");
            dto.setSourceRefId(a.id());
            dto.setTitle("员工能力#" + a.id());
            dto.setContent(content);
            saveDocument(dto);
            created++;
        }
        return created;
    }

    private int backfillFromPostAbilityModel(int limit) {
        List<PostQueryPort.PostAbilityDTO> models = postQueryPort.listActivePostAbilityModels(limit);
        int created = 0;
        for (var m : models) {
            if (existsBySource("POST_ABILITY_MODEL", m.id())) continue;
            String abilityName = m.abilityName();
            if (abilityName == null || abilityName.isBlank()) {
                abilityName = "未命名岗位能力";
            }
            String content = "岗位能力模型\n标签ID：" + m.tagId() + "\n"
                    + "能力名称：" + abilityName + "\n"
                    + "最低要求：" + m.minRequiredLevel() + "\n权重：" + m.weight();
            KnowledgeDocumentSaveDTO dto = new KnowledgeDocumentSaveDTO();
            dto.setSourceType("POST_ABILITY_MODEL");
            dto.setSourceRefId(m.id());
            dto.setTitle("岗位能力：" + abilityName);
            dto.setContent(content);
            saveDocument(dto);
            created++;
        }
        return created;
    }

    private boolean existsBySource(String sourceType, Long sourceRefId) {
        LambdaQueryWrapper<RagKnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RagKnowledgeDocument::getSourceType, sourceType);
        wrapper.eq(RagKnowledgeDocument::getSourceRefId, sourceRefId);
        wrapper.eq(RagKnowledgeDocument::getIsDeleted, 0);
        return documentMapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<RagKnowledgeDocument> listAllActiveDocuments(int limit) {
        LambdaQueryWrapper<RagKnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RagKnowledgeDocument::getDocStatus, "ACTIVE");
        wrapper.eq(RagKnowledgeDocument::getIsDeleted, 0);
        if (limit > 0) wrapper.last("LIMIT " + limit);
        return documentMapper.selectList(wrapper);
    }
}
