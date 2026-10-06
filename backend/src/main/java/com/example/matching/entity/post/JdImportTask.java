package com.example.matching.entity.post;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * JD导入分析任务表实体
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("jd_import_task")
public class JdImportTask implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 岗位ID */
    private Long postId;

    /** 来源类型：PASTE-粘贴文本，FILE-文件上传 */
    private String jdSourceType;

    /** JD原始文本内容 */
    private String jdRawText;

    /** AI提取的岗位摘要 */
    private String jdSummary;

    /** 分析状态：0-待分析，1-分析中，2-分析成功，3-分析失败 */
    private Integer analysisStatus;

    /** AI返回的原始JSON响应 */
    private String aiRawResponse;

    /** 分析失败时的错误信息 */
    private String errorMessage;

    /** 逻辑删除：0未删除，1已删除 */
    @TableLogic
    private Integer isDeleted;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新人ID */
    @TableField(fill = FieldFill.UPDATE)
    private Long updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
