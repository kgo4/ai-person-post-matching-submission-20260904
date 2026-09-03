package com.example.matching.service.employee.impl;

import com.example.matching.agent.dto.person.PersonAbilityExtractRequest;
import com.example.matching.agent.dto.person.PersonAbilityExtractionResult;
import com.example.matching.agent.service.EmployeeAbilityAgentService;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.PermanentResumeParseException;
import com.example.matching.common.exception.RetryableResumeParseException;
import com.example.matching.entity.employee.EmpResumeParse;
import com.example.matching.event.AbilityChangeEvent;
import com.example.matching.mapper.employee.EmpResumeParseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/**
 * 简历文件解析器：文件内容提取 + AI 能力分析 + 结果落库。
 * <p>
 * 从 ResumeParseServiceImpl（980+ 行）中拆分的文件处理组件。
 */
@Slf4j
@Component
public class ResumeFileParser {

    private static final int MAX_AI_RESULT_LENGTH = 1_000_000;

    private final EmpResumeParseMapper empResumeParseMapper;
    private final EmployeeAbilityAgentService employeeAbilityAgentService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final BaiduOcrClient baiduOcrClient;

    @Autowired
    public ResumeFileParser(EmpResumeParseMapper empResumeParseMapper,
                            EmployeeAbilityAgentService employeeAbilityAgentService,
                            ObjectMapper objectMapper,
                            ApplicationEventPublisher eventPublisher,
                            BaiduOcrClient baiduOcrClient) {
        this.empResumeParseMapper = empResumeParseMapper;
        this.employeeAbilityAgentService = employeeAbilityAgentService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.baiduOcrClient = baiduOcrClient;
    }

    /** 保持现有单元测试和本地调用的四参数构造方式。 */
    public ResumeFileParser(EmpResumeParseMapper empResumeParseMapper,
                            EmployeeAbilityAgentService employeeAbilityAgentService,
                            ObjectMapper objectMapper,
                            ApplicationEventPublisher eventPublisher) {
        this.empResumeParseMapper = empResumeParseMapper;
        this.employeeAbilityAgentService = employeeAbilityAgentService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.baiduOcrClient = null;
    }

    /**
     * 执行解析：提取文件内容 -> AI 分析 -> 落库完成。
     * 文件类错误为不可重试；AI 调用失败抛可重试异常。
     */
    public void executeParse(EmpResumeParse record) {
        // 1. 解析文件内容
        ParsedResumeContent parsedResume;
        try {
            parsedResume = parseFileContent(record.getFilePath(), record.getFileType());
        } catch (BusinessException be) {
            throw new PermanentResumeParseException("FILE_ERROR", be.getMessage(), be);
        } catch (Exception e) {
            throw new PermanentResumeParseException("FILE_PARSE_ERROR", "文件解析失败: " + e.getMessage(), e);
        }

        String content = parsedResume.content();
        if (content == null || content.isBlank()) {
            throw new PermanentResumeParseException("EMPTY_CONTENT", "简历文件内容为空");
        }
        // 存储完整解析文本（不压缩），AI Agent 内部使用 chunking 处理长文本。
        // Evidence slicing 需要完整原始文本，不能依赖压缩后的截断版本。
        record.setParsedContent(content);
        log.info("简历文件解析完成: fileType={}, contentLength={}", record.getFileType(), content.length());

        // 2. 调用AI分析（失败时抛出可重试异常，不再吞掉错误返回降级 JSON）
        String aiResult = analyzeWithAi(content, record.getEmpId(), record.getId(), parsedResume.ocrDerived());
        record.setAiAnalysisResult(truncateAiResult(aiResult));

        // 3. 更新状态为完成
        record.setStatus(2);
        record.setProcessingStartedAt(null);
        record.setRetryCount(record.getRetryCount() != null ? record.getRetryCount() : 0);
        empResumeParseMapper.updateById(record);

        // 4. 发布能力变更事件，触发员工向量自动同步
        eventPublisher.publishEvent(new AbilityChangeEvent(this, "EMP_ABILITY", record.getEmpId()));

        // 5. 发布简历解析完成事件：评估工作流监听器据此保存证据并推进
        // RESUME_PARSING -> 下一阶段。该事件必须在这里发布——若缺失，
        // 解析成功但工作流永远卡在 RESUME_PARSING（阶段运行 RUNNING）。
        eventPublisher.publishEvent(new com.example.matching.event.ResumeParseCompletedEvent(
                record.getId(), record.getEmpId()));
    }

    public String getFileType(String filename) {
        if (filename == null) return "";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot >= 0) {
            return filename.substring(lastDot + 1);
        }
        return "";
    }

    public String saveFile(String originalFilename, byte[] content, Long empId) {
        try {
            Path uploadDir = Paths.get("uploads/resume/" + empId).toAbsolutePath();
            Files.createDirectories(uploadDir);

            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
            }

            String filename = UUID.randomUUID().toString() + extension;
            Path filePath = uploadDir.resolve(filename).toAbsolutePath();
            Files.write(filePath, content);

            return filePath.toString();
        } catch (Exception e) {
            throw new BusinessException(500, "文件保存失败: " + e.getMessage());
        }
    }

    public String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new BusinessException(500, "计算文件哈希失败: " + e.getMessage());
        }
    }

    private ParsedResumeContent parseFileContent(String filePath, String fileType) throws Exception {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new BusinessException(404, "文件不存在");
        }

        switch (fileType.toUpperCase()) {
            case "PDF":
                return parsePdfContent(filePath);
            case "DOC":
                return new ParsedResumeContent(parseDoc(filePath), false);
            case "DOCX":
                return new ParsedResumeContent(parseDocx(filePath), false);
            default:
                throw new BusinessException(400, "不支持的文件类型: " + fileType);
        }
    }

    private ParsedResumeContent parsePdfContent(String filePath) throws Exception {
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            // OCR fallback detection: 纯扫描件通常提取不到有意义文本
            if (text == null || text.trim().length() < 50) {
                int pageCount = document.getNumberOfPages();
                log.warn("PDF 提取文本过短（{}字符，{}页），可能为扫描件需要 OCR: {}",
                        text != null ? text.trim().length() : 0, pageCount, filePath);
                String ocrText = baiduOcrClient == null ? "" : baiduOcrClient.recognizePdf(Paths.get(filePath), pageCount);
                if (ocrText != null && ocrText.trim().length() >= 20) {
                    log.info("PDF 百度 OCR 完成: pages={}, contentLength={}", pageCount, ocrText.trim().length());
                    return new ParsedResumeContent(ocrText, true);
                }
                throw new PermanentResumeParseException("EMPTY_OR_IMAGE_ONLY_CONTENT",
                        String.format("PDF 文本内容不足且 OCR 未返回有效内容（%d字符，%d页）",
                                text != null ? text.trim().length() : 0, pageCount));
            }
            return new ParsedResumeContent(text, false);
        }
    }

    private String parseDocx(String filePath) throws Exception {
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            StringBuilder content = new StringBuilder();
            // 1. 段落文本
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    content.append(text).append("\n");
                }
            }
            // 2. 表格内容（按行、按单元格提取，保持文档顺序）
            for (XWPFTable table : document.getTables()) {
                content.append("\n");
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.trim().isEmpty()) {
                            content.append(cellText.trim()).append("  ");
                        }
                    }
                    content.append("\n");
                }
            }
            return content.toString();
        }
    }

    private String parseDoc(String filePath) throws Exception {
        try (FileInputStream fis = new FileInputStream(filePath);
             HWPFDocument document = new HWPFDocument(fis)) {
            Range range = document.getRange();
            return range.text();
        }
    }

    private String truncateAiResult(String aiResult) {
        if (aiResult == null) {
            return null;
        }
        if (aiResult.length() <= MAX_AI_RESULT_LENGTH) {
            return aiResult;
        }
        log.warn("AI分析结果超出安全上限，截断: {} -> {}", aiResult.length(), MAX_AI_RESULT_LENGTH);
        return aiResult.substring(0, MAX_AI_RESULT_LENGTH);
    }

    private String analyzeWithAi(String content, Long empId, Long parseId) {
        return analyzeWithAi(content, empId, parseId, false);
    }

    private String analyzeWithAi(String content, Long empId, Long parseId, boolean ocrDerived) {
        try {
            PersonAbilityExtractRequest request = new PersonAbilityExtractRequest();
            request.setEmpId(empId);
            request.setSourceType("RESUME_PARSE");
            request.setSourceRefId(parseId);
            request.setSourceRefs(List.of("source:RESUME_PARSE:" + parseId));
            request.setSourceText(content);
            request.setOcrDerived(ocrDerived);

            log.info("通过Agent提取简历能力: empId={}", empId);
            PersonAbilityExtractionResult result = employeeAbilityAgentService.extractAbilities(request);

            return objectMapper.writeValueAsString(result);

        } catch (RetryableResumeParseException | PermanentResumeParseException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI简历分析失败，可重试: empId={}, parseId={}, error={}", empId, parseId, e.getMessage());
            throw new RetryableResumeParseException("AI_CALL_FAILED", "AI分析失败: " + e.getMessage(), e);
        }
    }

    private record ParsedResumeContent(String content, boolean ocrDerived) {
    }

}
