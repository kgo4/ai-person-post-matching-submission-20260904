package com.example.matching.dto.post;

import lombok.Builder;
import lombok.Data;

/**
 * 岗位原始输入
 * <p>
 * 封装岗位清洗服务的输入参数，支持不同来源（JD文本、Excel、新兴岗位等）。
 */
@Data
@Builder
public class PostRawInput {

    /** 岗位名称 */
    private String postName;

    /** 岗位描述文本（原始） */
    private String rawText;

    /** 来源类型：JD_TEXT / EXCEL_IMPORT / EMERGING_POST / API */
    private String sourceType;

    /** 来源关联ID（如导入批次ID、岗位ID等） */
    private Long sourceRefId;
}
