package com.example.matching.dto.learning.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 资源封面上传请求（application 层不依赖 web 类型，由 Controller 将 MultipartFile 转为该 DTO）。
 *
 * @param content     图片文件字节内容
 * @param contentType 图片 MIME 类型（image/jpeg|png|gif|webp）
 */
@Schema(description = "资源封面上传请求")
public record CoverImageUploadRequest(
        @Schema(description = "图片文件字节内容") byte[] content,
        @Schema(description = "图片 MIME 类型") String contentType
) implements Serializable {
}
