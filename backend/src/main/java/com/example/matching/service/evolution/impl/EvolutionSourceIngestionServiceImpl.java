package com.example.matching.service.evolution.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.common.dto.PageResponse;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.common.util.UserFacingMessage;
import com.example.matching.dto.evolution.CloudSyncRequest;
import com.example.matching.dto.evolution.EvolutionSourceUploadDTO;
import com.example.matching.dto.evolution.KnowledgeSourceMaterialQuery;
import com.example.matching.dto.rag.KnowledgeDocumentSaveDTO;
import com.example.matching.entity.rag.KnowledgeSourceDocument;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.mapper.rag.KnowledgeSourceDocumentMapper;
import com.example.matching.service.evolution.EvolutionSourceIngestionService;
import com.example.matching.service.evolution.KnowledgeMaterialCleaner;
import com.example.matching.service.evolution.KnowledgeSourceIndexStatusUpdater;
import com.example.matching.service.evolution.SourceUploadOutcome;
import com.example.matching.service.common.DocumentUploadValidator;
import com.example.matching.service.rag.KnowledgeDocumentDeduplicator;
import com.example.matching.service.rag.KnowledgeDocumentService;
import com.example.matching.vo.evolution.KnowledgeSourceMaterialVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 演化资料入口服务实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvolutionSourceIngestionServiceImpl implements EvolutionSourceIngestionService {

    private final KnowledgeSourceDocumentMapper knowledgeSourceDocumentMapper;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeSourceIndexStatusUpdater indexStatusUpdater;
    private final KnowledgeDocumentDeduplicator deduplicator;
    private final KnowledgeMaterialCleaner materialCleaner;

    /** 文件上传根目录 */
    private static final String UPLOAD_ROOT = "uploads/evolution-sources";

    @Override
    @Transactional
    public KnowledgeSourceDocument uploadIndustryWhitepaper(
            String fileName, byte[] content, EvolutionSourceUploadDTO dto, Long operatorId) {
        DocumentUploadValidator.validateKnowledgeSource(fileName, content);
        log.info("上传行业白皮书: title={}, industry={}", dto.getTitle(), dto.getIndustry());

        // 1. 保存文件
        String filePath = saveFile(fileName, content);

        // 2. 创建知识源文档记录
        KnowledgeSourceDocument document = new KnowledgeSourceDocument();
        document.setSourceType("INDUSTRY_WHITEPAPER");
        document.setSourceCategory("INDUSTRY_WHITEPAPER");
        document.setTitle(dto.getTitle());
        document.setIndustry(dto.getIndustry());
        document.setBusinessDomain(dto.getBusinessDomain());
        document.setUploaderId(operatorId);
        document.setSourceOwner(dto.getTitle());
        document.setPublishedTime(dto.getPublishedTime());
        document.setEffectiveTime(dto.getEffectiveTime());
        document.setCollectedTime(LocalDateTime.now());
        document.setAuthorityLevel(dto.getTrustLevel() != null ? dto.getTrustLevel() : "HIGH");
        document.setAuthorityScore(calculateAuthorityScore(dto.getTrustLevel()));
        document.setTrustLevel(dto.getTrustLevel() != null ? dto.getTrustLevel() : "HIGH");
        document.setFreshnessScore(calculateFreshnessScore(dto.getPublishedTime()));
        document.setQualityScore(BigDecimal.valueOf(85));
        document.setEvolutionEnabled(dto.getEvolutionEnabled() != null && dto.getEvolutionEnabled() ? 1 : 0);
        document.setVisibility("INTERNAL");
        document.setStatus("PENDING");
        document.setChunkCount(0);
        document.setStoragePath(filePath);

        knowledgeSourceDocumentMapper.insert(document);
        linkRagDocument(document, extractText(fileName, content));

        log.info("行业白皮书上传成功: documentId={}, title={}", document.getId(), dto.getTitle());
        return document;
    }

    /**
     * 上传公司内部资料（权威材料走这条）。
     *
     * <p>三步：判重 → 落库 → 交给调用方建索引。
     * 刻意**不在这里建索引**：切片与向量化是慢操作，放进本事务会把写锁一直握到 embedding 返回；
     * 由调用方在事务外调用 {@link #indexKnowledgeSource} 更稳妥。
     */
    @Override
    @Transactional
    public SourceUploadOutcome uploadInternalDocument(
            String fileName, byte[] content, EvolutionSourceUploadDTO dto, Long operatorId) {
        DocumentUploadValidator.validateKnowledgeSource(fileName, content);
        String sourceCategory = dto.getSourceCategory() != null
                ? dto.getSourceCategory()
                : "INTERNAL_BUSINESS_UPDATE";
        boolean ephemeral = Boolean.TRUE.equals(dto.getEphemeral());
        String text = extractText(fileName, content);

        // 判重放在写任何东西之前：命中重复时连文件都不落盘，不留无人引用的副本
        KnowledgeSourceDocument existing = findReusableMaterial(sourceCategory, text, ephemeral);
        if (existing != null) {
            log.info("内部资料命中重复，直接复用: documentId={}, title={}", existing.getId(), existing.getTitle());
            return new SourceUploadOutcome(existing, true, isReusableButUnindexed(existing));
        }

        String filePath = saveFile(fileName, content);
        try {
            KnowledgeSourceDocument document = new KnowledgeSourceDocument();
            document.setSourceType("CLOUD_KNOWLEDGE_INTERNAL");
            document.setSourceCategory(sourceCategory);
            document.setTitle(dto.getTitle());
            document.setIndustry(dto.getIndustry());
            document.setBusinessDomain(dto.getBusinessDomain());
            document.setUploaderId(operatorId);
            document.setSourceOwner(dto.getTitle());
            document.setPublishedTime(dto.getPublishedTime());
            document.setEffectiveTime(dto.getEffectiveTime());
            document.setCollectedTime(LocalDateTime.now());
            document.setAuthorityLevel(dto.getTrustLevel() != null ? dto.getTrustLevel() : "MEDIUM");
            document.setAuthorityScore(calculateAuthorityScore(dto.getTrustLevel()));
            document.setTrustLevel(dto.getTrustLevel() != null ? dto.getTrustLevel() : "MEDIUM");
            document.setFreshnessScore(calculateFreshnessScore(dto.getPublishedTime()));
            document.setQualityScore(BigDecimal.valueOf(80));
            document.setEvolutionEnabled(dto.getEvolutionEnabled() != null && dto.getEvolutionEnabled() ? 1 : 0);
            document.setVisibility("INTERNAL");
            document.setStatus("PENDING");
            document.setIndexStatus("PENDING");
            document.setChunkCount(0);
            document.setStoragePath(filePath);
            document.setEphemeral(ephemeral ? 1 : 0);
            document.setDedupKey(buildDedupKey("CLOUD_KNOWLEDGE_INTERNAL", sourceCategory, text));
            document.setContentHash(deduplicator.canonicalHash(text));

            knowledgeSourceDocumentMapper.insert(document);
            linkRagDocument(document, text);

            log.info("内部资料已入库: documentId={}, title={}, ephemeral={}",
                    document.getId(), dto.getTitle(), ephemeral);
            return new SourceUploadOutcome(document, false, true);
        } catch (RuntimeException failure) {
            // 事务会回滚记录，但已落盘的文件不会自己消失，必须手动清掉，否则磁盘里堆积孤儿文件
            deleteStoredFile(filePath);
            throw failure;
        }
    }

    /**
     * 按「来源类型 + 资料类别 + 规范化内容指纹」找可复用的既有材料。
     *
     * <p>为什么资料类别要进键：类别决定「跨来源印证」信号 ——
     * 同一份内容以「政策文件」和「市场报告」两种类别上传，是**故意**要各算一份的，
     * 合并掉会让同一个岗位永远无法被判为多来源印证。同理也只在
     * {@code CLOUD_KNOWLEDGE_INTERNAL} 这一来源类型内复用，不跨来源蹭。
     *
     * <p>不把「仅试算材料」复用给正式上传：试算材料会被自动清理，
     * 正式材料若挂在它上面，清理后正式材料就凭空消失了。
     */
    private KnowledgeSourceDocument findReusableMaterial(String sourceCategory, String text, boolean ephemeral) {
        if (text == null || text.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<KnowledgeSourceDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeSourceDocument::getDedupKey,
                buildDedupKey("CLOUD_KNOWLEDGE_INTERNAL", sourceCategory, text));
        // 已删除/归档的材料不再复用，否则就是把用户删掉的东西又挂回来
        wrapper.notIn(KnowledgeSourceDocument::getStatus, List.of("DELETED", "ARCHIVED"));
        if (!ephemeral) {
            wrapper.eq(KnowledgeSourceDocument::getEphemeral, 0);
        }
        wrapper.orderByDesc(KnowledgeSourceDocument::getId);
        wrapper.last("LIMIT 1");
        return knowledgeSourceDocumentMapper.selectOne(wrapper);
    }

    /** 复用了材料，但它此前没索引成功（或片段为空）→ 需要补一次索引。 */
    private boolean isReusableButUnindexed(KnowledgeSourceDocument document) {
        if (document.getChunkCount() == null || document.getChunkCount() == 0) {
            return true;
        }
        return "FAILED".equals(document.getIndexStatus());
    }

    /** 去重键：来源类型 + 资料类别 + 规范化内容哈希。 */
    private String buildDedupKey(String sourceType, String sourceCategory, String text) {
        return sourceType + "|" + sourceCategory + "|" + deduplicator.canonicalHash(text);
    }

    /** 删除已落盘的上传文件；删除失败只记日志，不能因此让整体流程失败。 */
    private void deleteStoredFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            log.warn("上传文件清理失败（不影响业务）: path={}, error={}", filePath, e.getMessage());
        }
    }

    @Override
    @Transactional
    public int syncCloudKnowledge(CloudSyncRequest request) {
        log.info("同步云知识库: knowledgeBaseCode={}, businessDomain={}",
                request.getKnowledgeBaseCode(), request.getBusinessDomain());

        // 查询已有的云知识库文档
        LambdaQueryWrapper<KnowledgeSourceDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeSourceDocument::getKnowledgeBaseId, request.getKnowledgeBaseCode());
        wrapper.eq(KnowledgeSourceDocument::getStatus, "ACTIVE");

        if (request.getSourceTypes() != null && !request.getSourceTypes().isEmpty()) {
            wrapper.in(KnowledgeSourceDocument::getSourceCategory, request.getSourceTypes());
        }

        List<KnowledgeSourceDocument> existingDocs = knowledgeSourceDocumentMapper.selectList(wrapper);

        // 对未索引的文档进行索引
        int syncedCount = 0;
        for (KnowledgeSourceDocument doc : existingDocs) {
            if (doc.getChunkCount() == null || doc.getChunkCount() == 0) {
                try {
                    indexKnowledgeSource(doc.getId());
                    syncedCount++;
                } catch (Exception e) {
                    log.warn("索引文档失败: documentId={}, error={}", doc.getId(), e.getMessage());
                }
            }
        }

        log.info("云知识库同步完成: syncedCount={}", syncedCount);
        return syncedCount;
    }

    @Override
    @Transactional
    public int indexKnowledgeSource(Long documentId) {
        log.info("索引知识源文档: documentId={}", documentId);

        KnowledgeSourceDocument document = knowledgeSourceDocumentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "知识源文档不存在: " + documentId);
        }

        try {
            Long ragDocumentId = document.getRagDocumentId();
            if (ragDocumentId == null) {
                if (document.getStoragePath() == null || document.getStoragePath().isBlank()) {
                    throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                            "知识源文档未关联 RAG 文档且无可用存储文件: " + documentId);
                }
                linkRagDocument(document, extractText(Path.of(document.getStoragePath())));
                ragDocumentId = document.getRagDocumentId();
            }

            int chunkCount = knowledgeDocumentService.indexDocument(ragDocumentId);
            document.setChunkCount(chunkCount);
            document.setStatus("ACTIVE");
            document.setIndexStatus("INDEXED");
            document.setLastIndexedTime(LocalDateTime.now());
            knowledgeSourceDocumentMapper.updateById(document);
            log.info("知识源文档索引完成: documentId={}, chunkCount={}", documentId, chunkCount);
            return chunkCount;
        } catch (Exception e) {
            /*
             * 面向使用者的文案必须自己写，不能拼 e.getMessage()。
             * 这里曾经直接拼 JDBC 原文，界面上就出现了
             * 「Duplicate entry '1003-0-1' for key 'rag_knowledge_chunk.uk_doc_chunk_revision'」。
             * 原始异常只进日志，给用户的是「哪一步失败 + 能做什么」。
             */
            log.error("知识源文档索引失败: documentId={}, error={}", documentId, e.getMessage(), e);
            String reason = UserFacingMessage.sanitize(
                    e.getMessage(), "材料索引过程中出现异常");
            String userFacing = "材料已保存，但建立检索索引失败（" + reason + "），请稍后在材料管理中重试索引";
            // 独立事务写失败状态：本方法随后会抛异常回滚，同事务写会被一起回滚
            indexStatusUpdater.markFailed(documentId, userFacing);
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, userFacing, e);
        }
    }

    @Override
    public PageResponse<KnowledgeSourceMaterialVO> pageMaterials(KnowledgeSourceMaterialQuery query) {
        KnowledgeSourceMaterialQuery condition = query == null ? new KnowledgeSourceMaterialQuery() : query;
        LambdaQueryWrapper<KnowledgeSourceDocument> wrapper = new LambdaQueryWrapper<>();
        // 只列人工上传的权威材料：云知识库同步、招聘 JD 导入等自动来源不该被人工删除
        wrapper.eq(KnowledgeSourceDocument::getSourceType, "CLOUD_KNOWLEDGE_INTERNAL");
        wrapper.notIn(KnowledgeSourceDocument::getStatus, List.of("DELETED", "ARCHIVED"));
        if (StringUtils.hasText(condition.getSourceCategory())) {
            wrapper.eq(KnowledgeSourceDocument::getSourceCategory, condition.getSourceCategory());
        }
        if (StringUtils.hasText(condition.getKeyword())) {
            wrapper.like(KnowledgeSourceDocument::getTitle, condition.getKeyword().trim());
        }
        if (!Boolean.TRUE.equals(condition.getIncludeEphemeral())) {
            wrapper.eq(KnowledgeSourceDocument::getEphemeral, 0);
        }
        wrapper.orderByDesc(KnowledgeSourceDocument::getId);

        Page<KnowledgeSourceDocument> page = new Page<>(condition.getCurrent(), condition.getSize());
        IPage<KnowledgeSourceDocument> result = knowledgeSourceDocumentMapper.selectPage(page, wrapper);
        return PageResponse.from(result, this::toMaterialVO);
    }

    private KnowledgeSourceMaterialVO toMaterialVO(KnowledgeSourceDocument document) {
        KnowledgeSourceMaterialVO vo = new KnowledgeSourceMaterialVO();
        vo.setDocumentId(document.getId());
        vo.setTitle(document.getTitle());
        vo.setSourceCategory(document.getSourceCategory());
        vo.setSourceCategoryLabel(materialCategoryLabel(document.getSourceCategory()));
        vo.setChunkCount(document.getChunkCount());
        vo.setStatus(document.getStatus());
        vo.setIndexStatus(document.getIndexStatus());
        vo.setEphemeral(Integer.valueOf(1).equals(document.getEphemeral()));
        vo.setUploadedTime(document.getCollectedTime() != null
                ? document.getCollectedTime() : document.getCreatedTime());
        vo.setLastIndexedTime(document.getLastIndexedTime());
        int chunks = document.getChunkCount() == null ? 0 : document.getChunkCount();
        vo.setReadyForAnalysis(chunks > 0 && !"FAILED".equals(document.getIndexStatus()));
        return vo;
    }

    /**
     * 资料类别中文名。
     *
     * <p>刻意在后端维护唯一映射：材料管理列表与上传区下拉若各维护一份，
     * 一旦新增类别就会出现「下拉里叫政策文件、列表里叫 POLICY_DOCUMENT」的错位。
     */
    private String materialCategoryLabel(String sourceCategory) {
        if (sourceCategory == null) {
            return "未分类";
        }
        return switch (sourceCategory) {
            case "POLICY_DOCUMENT" -> "政策文件";
            case "MARKET_REPORT" -> "市场职业报告";
            case "OCCUPATION_STANDARD" -> "职业标准";
            case "INDUSTRY_WHITEPAPER" -> "行业白皮书";
            case "INTERNAL_POLICY" -> "内部规范";
            case "INTERNAL_POST_INFO" -> "岗位信息";
            case "INTERNAL_BUSINESS_UPDATE" -> "业务更新";
            default -> sourceCategory;
        };
    }

    @Override
    public String deleteMaterial(Long documentId) {
        KnowledgeMaterialCleaner.CleanupResult result = materialCleaner.deleteOne(documentId);
        // 事务提交后再删文件：放在事务里删，一旦回滚就成了「记录还在、文件没了」的半残状态
        deleteStoredFile(result.storagePath());
        return "已删除材料「" + (result.title() == null ? documentId : result.title())
                + "」，同时清理了 " + result.removedChunks() + " 个检索片段";
    }

    /**
     * 兜底清理过期的「仅试算」材料。
     *
     * <p>刻意**不加事务**：每份材料由 {@link KnowledgeMaterialCleaner} 用独立事务清理，
     * 一份失败只影响它自己。若整个循环共用一个事务，任何一份抛错都会把事务标成 rollback-only，
     * catch 住也没用 —— 提交时整体回滚，且每轮调度都卡在同一份坏数据上。
     *
     * <p>保留时长取 24 小时（可用 {@code post.trend.ephemeral-retention-hours} 调整）。
     * 这个下限是刻意留够的：趋势解析任务停在 RUNNING 超过 30 分钟就会被判定为僵尸并置为 FAILED，
     * 所以「还在被任务使用的试算材料」不可能存活到 24 小时 —— 清理不会删掉正在解析的材料。
     */
    @Override
    public int purgeExpiredEphemeralMaterials(int olderThanHours) {
        int hours = olderThanHours > 0 ? olderThanHours : 24;
        LocalDateTime deadline = LocalDateTime.now().minusHours(hours);
        LambdaQueryWrapper<KnowledgeSourceDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(KnowledgeSourceDocument::getId);
        wrapper.eq(KnowledgeSourceDocument::getEphemeral, 1);
        wrapper.eq(KnowledgeSourceDocument::getSourceType, "CLOUD_KNOWLEDGE_INTERNAL");
        wrapper.notIn(KnowledgeSourceDocument::getStatus, List.of("DELETED", "ARCHIVED"));
        wrapper.lt(KnowledgeSourceDocument::getCollectedTime, deadline);

        List<KnowledgeSourceDocument> expired = knowledgeSourceDocumentMapper.selectList(wrapper);
        int purged = 0;
        for (KnowledgeSourceDocument document : expired) {
            try {
                KnowledgeMaterialCleaner.CleanupResult result = materialCleaner.deleteOne(document.getId());
                deleteStoredFile(result.storagePath());
                purged++;
            } catch (Exception e) {
                // 单份材料清理失败不能中断整批，否则一份脏数据会让兜底清理永远卡住
                log.warn("试算材料兜底清理失败: documentId={}, error={}", document.getId(), e.getMessage());
            }
        }
        if (purged > 0) {
            log.info("试算材料兜底清理完成: 超过 {} 小时，清理 {} 份", hours, purged);
        }
        return purged;
    }

    /**
     * 保存上传的文件
     */
    private String saveFile(String originalFilename, byte[] content) {
        try {
            // 创建上传目录
            Path uploadDir = Paths.get(UPLOAD_ROOT);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 生成唯一文件名
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString() + extension;

            // 保存文件
            Path filePath = uploadDir.resolve(filename);
            Files.write(filePath, content);

            log.info("文件保存成功: path={}", filePath);
            return filePath.toString();
        } catch (IOException e) {
            log.error("文件保存失败: error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "文件保存失败: " + e.getMessage());
        }
    }

    /**
     * 源文档与 RAG 文档的一对一桥接。RAG 只能索引可读文本，不能以 knowledge_source_document 的主键代替 RAG 文档主键。
     */
    private void linkRagDocument(KnowledgeSourceDocument sourceDocument, String text) {
        KnowledgeDocumentSaveDTO ragDocument = new KnowledgeDocumentSaveDTO();
        ragDocument.setSourceType(sourceDocument.getSourceType());
        ragDocument.setSourceRefId(sourceDocument.getId());
        ragDocument.setTitle(sourceDocument.getTitle());
        ragDocument.setContent(text);

        RagKnowledgeDocument saved = knowledgeDocumentService.saveDocument(ragDocument);
        if (saved == null || saved.getId() == null) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "创建 RAG 知识文档失败");
        }
        sourceDocument.setRagDocumentId(saved.getId());
        knowledgeSourceDocumentMapper.updateById(sourceDocument);
    }

    private String extractText(Path filePath) {
        try {
            return extractText(filePath.getFileName().toString(), Files.readAllBytes(filePath));
        } catch (IOException e) {
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "读取知识源文件失败: " + e.getMessage());
        }
    }

    private String extractText(String fileName, byte[] content) {
        String extension = extensionOf(fileName);
        try {
            String text = switch (extension) {
                case "txt", "md" -> new String(content, StandardCharsets.UTF_8);
                case "pdf" -> extractPdfText(content);
                case "docx" -> extractDocxText(content);
                case "doc" -> extractDocText(content);
                default -> throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "不支持解析的知识源文件类型: " + extension);
            };
            if (text == null || text.isBlank()) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "知识源文件未提取到可索引文本");
            }
            return text.trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "知识源文件文本提取失败: " + e.getMessage());
        }
    }

    private String extractPdfText(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocxText(byte[] content) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                if (paragraph.getText() != null && !paragraph.getText().isBlank()) {
                    text.append(paragraph.getText()).append('\n');
                }
            }
            return text.toString();
        }
    }

    private String extractDocText(byte[] content) throws IOException {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(content))) {
            Range range = document.getRange();
            return range.text();
        }
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int extensionStart = fileName.lastIndexOf('.');
        return extensionStart < 0 ? "" : fileName.substring(extensionStart + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 计算权威度评分
     */
    private BigDecimal calculateAuthorityScore(String trustLevel) {
        if (trustLevel == null) {
            return BigDecimal.valueOf(70);
        }
        switch (trustLevel) {
            case "HIGH":
                return BigDecimal.valueOf(90);
            case "MEDIUM":
                return BigDecimal.valueOf(70);
            case "LOW":
                return BigDecimal.valueOf(50);
            default:
                return BigDecimal.valueOf(70);
        }
    }

    /**
     * 计算时效性评分
     */
    private BigDecimal calculateFreshnessScore(LocalDateTime publishedTime) {
        if (publishedTime == null) {
            return BigDecimal.valueOf(70);
        }

        long daysSincePublished = java.time.Duration.between(publishedTime, LocalDateTime.now()).toDays();
        if (daysSincePublished <= 30) {
            return BigDecimal.valueOf(100);
        } else if (daysSincePublished <= 90) {
            return BigDecimal.valueOf(85);
        } else if (daysSincePublished <= 180) {
            return BigDecimal.valueOf(70);
        } else if (daysSincePublished <= 365) {
            return BigDecimal.valueOf(55);
        } else {
            return BigDecimal.valueOf(40);
        }
    }
}


