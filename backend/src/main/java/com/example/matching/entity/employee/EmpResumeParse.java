package com.example.matching.entity.employee;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 简历解析记录表实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("emp_resume_parse")
public class EmpResumeParse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 员工ID */
    private Long empId;

    /** 文件名 */
    private String fileName;

    /** 文件存储路径 */
    private String filePath;

    /** 文件类型：PDF/DOC/DOCX */
    private String fileType;

    /** 文件SHA-256哈希值（用于去重） */
    private String fileHash;

    /** 解析出的原始文本内容 */
    private String parsedContent;

    /** AI分析结果，JSON格式 */
    private String aiAnalysisResult;

    /** 状态：0待处理/1处理中/2成功/3最终失败/4等待重试 */
    private Integer status;

    /** 失败原因 */
    private String errorMessage;

    /** 已重试次数 */
    private Integer retryCount;

    /** 下次重试时间（等待重试状态时使用） */
    private LocalDateTime nextRetryTime;

    /** 开始处理时间（用于僵尸任务检测） */
    private LocalDateTime processingStartedAt;

    /** 最后错误类型：RETRYABLE/PERMANENT */
    private String lastErrorType;

    /** 最后错误信息（详细） */
    private String lastErrorMessage;

    /** 是否自动提交提取能力（V90） */
    private Integer autoImport;

    /** 能力导入状态：NOT_REQUESTED/PENDING/SUCCEEDED/REVIEW_REQUIRED/BLOCKED/NO_CLAIMS/FAILED（V90） */
    private String abilityImportStatus;

    /** 能力导入结果摘要（V90） */
    private String abilityImportSummary;

    /** 能力导入完成时间（V90） */
    private LocalDateTime abilityImportedAt;

    /** 创建人ID */
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
