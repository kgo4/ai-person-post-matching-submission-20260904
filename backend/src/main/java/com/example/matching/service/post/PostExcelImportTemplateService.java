package com.example.matching.service.post;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Generates the canonical input workbook for JD batch import. */
@Service
public class PostExcelImportTemplateService {

    private static final String SHEET_NAME = "岗位JD";
    private static final String[] HEADERS = {"岗位名称", "岗位职责", "任职要求", "所属行业"};
    private static final String[] EXAMPLE = {
            "Java AI应用开发工程师",
            "负责 Java 服务端与 AI 应用集成；设计 RAG 和 Agent 调用链路；完成接口、缓存、消息队列与可观测性建设。",
            "熟悉 Java、Spring Boot、MySQL、Redis、Kafka；理解大语言模型、向量检索与 RAG；具备生产项目经验。",
            "人工智能 / 企业软件"
    };

    public byte[] generate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            sheet.createFreezePane(0, 1);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            CellStyle contentStyle = workbook.createCellStyle();
            contentStyle.setVerticalAlignment(VerticalAlignment.TOP);
            contentStyle.setWrapText(true);

            Row headerRow = sheet.createRow(0);
            Row exampleRow = sheet.createRow(1);
            headerRow.setHeightInPoints(24);
            exampleRow.setHeightInPoints(72);
            for (int index = 0; index < HEADERS.length; index++) {
                Cell header = headerRow.createCell(index);
                header.setCellValue(HEADERS[index]);
                header.setCellStyle(headerStyle);

                Cell example = exampleRow.createCell(index);
                example.setCellValue(EXAMPLE[index]);
                example.setCellStyle(contentStyle);
            }
            sheet.setColumnWidth(0, 24 * 256);
            sheet.setColumnWidth(1, 56 * 256);
            sheet.setColumnWidth(2, 56 * 256);
            sheet.setColumnWidth(3, 24 * 256);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate JD import template", exception);
        }
    }
}
