package com.example.matching.service.evolution.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.dto.evolution.CloudSyncRequest;
import com.example.matching.dto.evolution.EvolutionSourceUploadDTO;
import com.example.matching.dto.rag.KnowledgeDocumentSaveDTO;
import com.example.matching.entity.rag.KnowledgeSourceDocument;
import com.example.matching.entity.rag.RagKnowledgeDocument;
import com.example.matching.mapper.rag.KnowledgeSourceDocumentMapper;
import com.example.matching.service.evolution.EvolutionSourceIngestionService;
import com.example.matching.service.common.DocumentUploadValidator;
import com.example.matching.service.rag.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional
    public KnowledgeSourceDocument uploadInternalDocument(
            String fileName, byte[] content, EvolutionSourceUploadDTO dto, Long operatorId) {
        DocumentUploadValidator.validateKnowledgeSource(fileName, content);
        log.info("上传内部资料: title={}, category={}", dto.getTitle(), dto.getSourceCategory());

        // 1. 保存文件
        String filePath = saveFile(fileName, content);

        // 2. 创建知识源文档记录
        KnowledgeSourceDocument document = new KnowledgeSourceDocument();
        document.setSourceType("CLOUD_KNOWLEDGE_INTERNAL");
        document.setSourceCategory(dto.getSourceCategory() != null ? dto.getSourceCategory() : "INTERNAL_BUSINESS_UPDATE");
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
        document.setChunkCount(0);
        document.setStoragePath(filePath);

        knowledgeSourceDocumentMapper.insert(document);
        linkRagDocument(document, extractText(fileName, content));

        log.info("内部资料上传成功: documentId={}, title={}", document.getId(), dto.getTitle());
        return document;
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

        java.util.List<KnowledgeSourceDocument> existingDocs = knowledgeSourceDocumentMapper.selectList(wrapper);

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
            document.setLastIndexedTime(LocalDateTime.now());
            knowledgeSourceDocumentMapper.updateById(document);
            log.info("知识源文档索引完成: documentId={}, chunkCount={}", documentId, chunkCount);
            return chunkCount;
        } catch (Exception e) {
            log.error("知识源文档索引失败: documentId={}, error={}", documentId, e.getMessage(), e);
            document.setStatus("FAILED");
            knowledgeSourceDocumentMapper.updateById(document);
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "文档索引失败: " + e.getMessage());
        }
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
