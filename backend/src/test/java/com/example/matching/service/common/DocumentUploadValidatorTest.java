package com.example.matching.service.common;

import com.example.matching.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentUploadValidatorTest {

    @Test
    void rejects_a_pdf_name_with_non_pdf_bytes() {
        assertThatThrownBy(() -> DocumentUploadValidator.validateKnowledgeSource(
                "payload.pdf", "not a PDF".getBytes()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejects_a_pdf_name_with_non_pdf_content() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "payload.pdf", "application/pdf", "not a PDF".getBytes());

        assertThatThrownBy(() -> DocumentUploadValidator.validateKnowledgeSource(file))
                .isInstanceOf(BusinessException.class);
    }
}
