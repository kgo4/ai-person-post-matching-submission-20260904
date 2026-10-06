package com.example.matching.service.post;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PostExcelImportTemplateServiceTest {

    private final PostExcelImportTemplateService service = new PostExcelImportTemplateService();

    @Test
    void generatesCanonicalJdWorkbookWithExample() throws Exception {
        byte[] workbookBytes = service.generate();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            var sheet = workbook.getSheet("岗位JD");
            assertNotNull(sheet);
            assertEquals("岗位名称", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("岗位职责", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("任职要求", sheet.getRow(0).getCell(2).getStringCellValue());
            assertEquals("所属行业", sheet.getRow(0).getCell(3).getStringCellValue());
            assertFalse(sheet.getRow(1).getCell(0).getStringCellValue().isBlank());
            assertFalse(sheet.getRow(1).getCell(1).getStringCellValue().isBlank());
            assertFalse(sheet.getRow(1).getCell(2).getStringCellValue().isBlank());
        }
    }
}
