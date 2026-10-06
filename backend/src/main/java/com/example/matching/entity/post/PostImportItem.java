package com.example.matching.entity.post;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Excel批量导入明细表实体
 * <p>
 * 每一行解析出的岗位数据，包含AI结构识别后的字段映射结果。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_import_item")
public class PostImportItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 导入批次ID */
    private Long batchId;

    /** Excel原始行号 */
    private Integer rowIndex;

    /** 解析出的岗位名称 */
    private String postName;

    /** 解析出的岗位描述（拼接后的完整文本） */
    private String postDescription;

    /** 岗位职责文本 */
    private String responsibilityText;

    /** 任职要求文本 */
    private String requirementText;

    /** 行业/方向 */
    private String industry;

    /** 原始行数据JSON（保留未映射列） */
    private String rawRowJson;

    /**
     * 分析状态：
     * 0-待分析
     * 1-分析中
     * 2-分析成功
     * 3-分析失败
     */
    private Integer analysisStatus;

    /** AI能力提取的原始响应 */
    private String aiAnalysisResponse;

    /** 确认导入后创建的岗位ID */
    private Long createdPostId;

    /** 分析失败时的错误信息 */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
