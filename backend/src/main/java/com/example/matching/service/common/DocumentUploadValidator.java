package com.example.matching.service.common;

import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class DocumentUploadValidator {

    private static final Set<String> KNOWLEDGE_SOURCE_TYPES = Set.of("pdf", "doc", "docx", "txt", "md");
    private static final Set<String> RESUME_TYPES = Set.of("pdf", "doc", "docx");
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] OLE_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private DocumentUploadValidator() {
    }

    public static void validateKnowledgeSource(MultipartFile file) {
        validate(file, KNOWLEDGE_SOURCE_TYPES);
    }

    public static void validateResume(MultipartFile file) {
        validate(file, RESUME_TYPES);
    }

    public static void validateKnowledgeSource(String filename, byte[] content) {
        validate(filename, content, KNOWLEDGE_SOURCE_TYPES);
    }

    public static void validateResume(String filename, byte[] content) {
        validate(filename, content, RESUME_TYPES);
    }

    private static void validate(String filename, byte[] content, Set<String> allowedTypes) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Uploaded file is required");
        }
        String extension = extensionOf(filename);
        if (!allowedTypes.contains(extension)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Unsupported file type");
        }
        try {
            switch (extension) {
                case "pdf" -> requireMagic(content, PDF_MAGIC, "PDF");
                case "doc" -> requireMagic(content, OLE_MAGIC, "DOC");
                case "docx" -> requireDocx(content);
                case "txt", "md" -> requireText(content);
                default -> throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Unsupported file type");
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Unable to read uploaded file");
        }
    }

    private static void validate(MultipartFile file, Set<String> allowedTypes) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Uploaded file is required");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!allowedTypes.contains(extension)) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Unsupported file type");
        }
        try {
            switch (extension) {
                case "pdf" -> requireMagic(file, PDF_MAGIC, "PDF");
                case "doc" -> requireMagic(file, OLE_MAGIC, "DOC");
                case "docx" -> requireDocx(file);
                case "txt", "md" -> requireText(file);
                default -> throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Unsupported file type");
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Unable to read uploaded file");
        }
    }

    private static void requireMagic(MultipartFile file, byte[] expected, String type) throws IOException {
        try (InputStream input = file.getInputStream()) {
            byte[] actual = input.readNBytes(expected.length);
            for (int index = 0; index < expected.length; index++) {
                if (index >= actual.length || actual[index] != expected[index]) {
                    throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Invalid " + type + " document");
                }
            }
        }
    }

    private static void requireDocx(MultipartFile file) throws IOException {
        boolean hasContentTypes = false;
        boolean hasDocument = false;
        try (ZipInputStream input = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                hasContentTypes |= "[Content_Types].xml".equals(entry.getName());
                hasDocument |= "word/document.xml".equals(entry.getName());
                if (hasContentTypes && hasDocument) return;
            }
        }
        throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Invalid DOCX document");
    }

    private static void requireText(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            for (byte value : input.readNBytes(8 * 1024)) {
                if (value == 0) throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Binary content is not allowed");
            }
        }
    }

    private static void requireMagic(byte[] content, byte[] expected, String type) {
        if (content.length < expected.length) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Invalid " + type + " document");
        }
        for (int index = 0; index < expected.length; index++) {
            if (content[index] != expected[index]) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Invalid " + type + " document");
            }
        }
    }

    private static void requireDocx(byte[] content) throws IOException {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(content))) {
            boolean hasContentTypes = false;
            boolean hasDocument = false;
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                hasContentTypes |= "[Content_Types].xml".equals(entry.getName());
                hasDocument |= "word/document.xml".equals(entry.getName());
                if (hasContentTypes && hasDocument) return;
            }
        }
        throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Invalid DOCX document");
    }

    private static void requireText(byte[] content) {
        int limit = Math.min(content.length, 8 * 1024);
        for (int index = 0; index < limit; index++) {
            if (content[index] == 0) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Binary content is not allowed");
            }
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null) return "";
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
