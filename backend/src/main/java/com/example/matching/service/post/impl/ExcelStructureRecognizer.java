package com.example.matching.service.post.impl;

import com.example.matching.entity.post.PostImportBatch;
import com.example.matching.ai.service.LangChain4jChatService;
import com.example.matching.ai.service.PromptTemplateService;
import com.example.matching.dto.post.ExcelStructureDTO;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.post.PostImportBatchVO;
import com.example.matching.entity.post.PostImportItem;
import com.example.matching.dto.post.PostImportPreviewDTO;
import com.example.matching.entity.post.PostImportBatch;
import com.example.matching.resilience.AiServiceResilience;
import com.example.matching.service.post.PostCapabilityGenerationService;
import com.example.matching.service.system.AbilityTagService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Excel 岗位结构识别：原始数据读取、AI 结构识别、预览构建。
 * <p>
 * 从 PostExcelAiImportServiceImpl（660 行）中拆分的 Excel 识别组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelStructureRecognizer {

    private final PostCapabilityGenerationService capabilityGenerationService;
    private final AbilityTagService abilityTagService;
    private final LangChain4jChatService langChain4jChatService;
    private final AiServiceResilience aiServiceResilience;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;
    private final com.example.matching.infrastructure.llm.LlmResponseParser llmResponseParser;
    public List<List<String>> readExcelRaw(InputStream inputStream) {
        List<List<String>> allRows = new ArrayList<>();
        try {
            EasyExcel.read(inputStream, new AnalysisEventListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    List<String> row = new ArrayList<>();
                    int maxCol = data.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
                    for (int i = 0; i <= maxCol; i++) {
                        row.add(data.getOrDefault(i, ""));
                    }
                    allRows.add(row);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // 读取完成
                }
            }).sheet().doRead();
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "读取Excel文件失败");
        }
        return allRows;
    }

    /**
     * 调用AI识别Excel结构
     */
    public String callAiForStructureRecognition(List<List<String>> rawData, String fileName) {
        try {
            // 只取前20行给AI分析
            List<List<String>> sampleRows = rawData.stream().limit(20).collect(Collectors.toList());

            Map<String, Object> dataModel = new HashMap<>();
            dataModel.put("fileName", fileName);
            dataModel.put("totalRows", rawData.size());
            dataModel.put("sampleRows", sampleRows);

            String prompt = promptTemplateService.render("excel-structure-recognize-prompt", dataModel);

            return langChain4jChatService.chat("excel-structure-recognize", prompt,
                    () -> "{}");
        } catch (Exception e) {
            throw new BusinessException(ErrorCodeEnum.AI_SERVICE_ERROR, "AI结构识别失败");
        }
    }

    /**
     * 解析AI返回的结构信息
     */
    public ExcelStructureDTO parseStructureResponse(String aiResponse) {
        try {
            if (aiResponse == null || aiResponse.isBlank()) {
                aiResponse = "{}";
            }
            String json = llmResponseParser.extractJson(aiResponse);
            return objectMapper.readValue(json, ExcelStructureDTO.class);
        } catch (Exception e) {
            log.warn("解析AI结构响应失败: {}", e.getMessage());
            // 返回默认结构：第一行为表头，岗位名称在第一列
            ExcelStructureDTO defaultStructure = new ExcelStructureDTO();
            ExcelStructureDTO.SheetStructure sheet = new ExcelStructureDTO.SheetStructure();
            sheet.setSheetName("Sheet1");
            sheet.setHeaderRowIndex(0);
            sheet.setDataStartRowIndex(1);
            defaultStructure.setSheets(List.of(sheet));
            return defaultStructure;
        }
    }

    /**
     * 根据AI识别的结构组装岗位对象
     */
    public List<PostImportItem> assemblePostItems(List<List<String>> rawData, ExcelStructureDTO structure) {
        List<PostImportItem> items = new ArrayList<>();

        if (structure == null || structure.getSheets() == null || structure.getSheets().isEmpty()) {
            // 默认处理：跳过第一行表头，每行一个岗位
            for (int i = 1; i < rawData.size(); i++) {
                List<String> row = rawData.get(i);
                if (row.isEmpty() || (row.get(0) != null && row.get(0).isBlank())) continue;

                PostImportItem item = new PostImportItem();
                item.setRowIndex(i);
                item.setPostName(row.get(0));
                item.setPostDescription(String.join(" ", row.stream().filter(s -> s != null && !s.isBlank()).collect(Collectors.toList())));
                item.setAnalysisStatus(0);
                items.add(item);
            }
            return items;
        }

        // 使用AI识别的结构
        ExcelStructureDTO.SheetStructure sheet = structure.getSheets().get(0);
        int dataStart = sheet.getDataStartRowIndex() != null ? sheet.getDataStartRowIndex() : 1;

        for (int i = dataStart; i < rawData.size(); i++) {
            List<String> row = rawData.get(i);
            if (row.isEmpty()) continue;

            PostImportItem item = new PostImportItem();
            item.setRowIndex(i);

            // 根据列映射组装岗位对象
            if (sheet.getColumnInfos() != null) {
                List<String> descParts = new ArrayList<>();
                for (ExcelStructureDTO.ColumnInfo colInfo : sheet.getColumnInfos()) {
                    int colIdx = colInfo.getColumnIndex();
                    if (colIdx >= row.size()) continue;
                    String cellValue = row.get(colIdx);
                    if (cellValue == null || cellValue.isBlank()) continue;

                    String field = colInfo.getMappedField();
                    if ("postName".equals(field)) {
                        item.setPostName(cellValue);
                    } else if ("responsibility".equals(field)) {
                        item.setResponsibilityText(cellValue);
                        descParts.add(cellValue);
                    } else if ("requirement".equals(field)) {
                        item.setRequirementText(cellValue);
                        descParts.add(cellValue);
                    } else if ("industry".equals(field)) {
                        item.setIndustry(cellValue);
                    } else if ("description".equals(field)) {
                        descParts.add(cellValue);
                    }
                }
                item.setPostDescription(String.join("\n", descParts));
            } else {
                // 无列映射，使用默认逻辑
                if (!row.isEmpty()) {
                    item.setPostName(row.get(0));
                }
                item.setPostDescription(String.join(" ", row.stream().filter(s -> s != null && !s.isBlank()).collect(Collectors.toList())));
            }

            // 跳过没有岗位名称的行
            if (item.getPostName() == null || item.getPostName().isBlank()) {
                continue;
            }

            item.setRawRowJson(toJson(row));
            item.setAnalysisStatus(0);
            items.add(item);
        }

        return items;
    }

    /**
     * 构建预览结果
     */
    public PostImportPreviewDTO buildPreview(PostImportBatch batch, List<PostImportItem> items, ExcelStructureDTO structure) {
        PostImportPreviewDTO preview = new PostImportPreviewDTO();
        preview.setBatchId(batch.getId());
        preview.setFileName(batch.getFileName());
        preview.setTotalRows(batch.getTotalRows());
        preview.setStructure(structure);
        preview.setImportStatus(batch.getImportStatus());
        preview.setSuccessCount(batch.getSuccessCount());
        preview.setFailCount(batch.getFailCount());
        preview.setErrorMessage(batch.getErrorMessage());

        List<PostImportPreviewDTO.PostImportItemPreview> itemPreviews = new ArrayList<>();
        for (PostImportItem item : items) {
            PostImportPreviewDTO.PostImportItemPreview itemPreview = new PostImportPreviewDTO.PostImportItemPreview();
            itemPreview.setItemId(item.getId());
            itemPreview.setRowIndex(item.getRowIndex());
            itemPreview.setPostName(item.getPostName());
            itemPreview.setPostDescription(item.getPostDescription());
            itemPreview.setAnalysisStatus(item.getAnalysisStatus());
            itemPreview.setErrorMessage(item.getErrorMessage());

            // 解析AI分析结果
            if (item.getAiAnalysisResponse() != null) {
                try {
                    List<JdAbilityItemDTO> abilities = objectMapper.readValue(item.getAiAnalysisResponse(),
                            new TypeReference<List<JdAbilityItemDTO>>() {});
                    itemPreview.setAbilities(abilities);
                } catch (Exception e) {
                    log.warn("解析能力分析结果失败: itemId={}", item.getId());
                }
            }

            itemPreviews.add(itemPreview);
        }
        preview.setItems(itemPreviews);

        return preview;
    }

    public String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    // ===== Redis 操作 =====
}
