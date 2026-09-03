package com.example.matching.entity.post;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Excel批量导入任务表实体
 * <p>
 * 追踪Excel岗位批量导入的完整生命周期。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("post_import_batch")
public class PostImportBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 上传的文件名 */
    private String fileName;

    /** 文件存储路径 */
    private String filePath;

    /** 解析出的岗位总行数 */
    private Integer totalRows;

    /** 成功导入数 */
    private Integer successCount;

    /** 失败数 */
    private Integer failCount;

    /** AI结构识别的原始响应 */
    private String aiStructureResponse;

    /**
     * 导入状态：
     * 0-待解析
     * 1-AI解析中
     * 2-待确认
     * 3-导入中
     * 4-导入完成
     * 5-导入失败
     */
    private Integer importStatus;

    /** 错误信息 */
    private String errorMessage;

    /** 取消标志：0正常，1已取消 */
    private Integer cancelFlag;

    /** 本次处理开始时间（僵尸恢复依据） */
    private LocalDateTime processingStartedAt;

    /** 重试次数 */
    private Integer retryCount;

    /** 最近一次错误类型，如 AI_OUTPUT_INVALID */
    private String lastErrorType;

    /** 最近一次错误信息 */
    private String lastErrorMessage;

    /** 异步确认导入请求载荷（JSON） */
    private String confirmPayload;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    @Version
    private Integer version;
}
