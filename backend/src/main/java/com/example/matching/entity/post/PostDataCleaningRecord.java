package com.example.matching.entity.post;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位数据清洗记录实体
 * <p>
 * 记录每次岗位导入时的自动清洗、去噪、去重结果，用于追溯和排查。
 * 清洗是系统内部流水线，不是用户操作入口；记录是治理入口，不是处理入口。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName(value = "post_data_cleaning_record", autoResultMap = true)
public class PostDataCleaningRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    // ========== 来源信息 ==========

    /** 来源类型：JD_TEXT / EXCEL_IMPORT / EMERGING_POST / API */
    private String sourceType;

    /** 来源关联ID（如导入批次ID、岗位ID等） */
    private Long sourceRefId;

    // ========== 原始数据 ==========

    /** 原始岗位名称 */
    private String rawPostName;

    /** 原始岗位描述文本 */
    private String rawText;

    // ========== 清洗后数据 ==========

    /** 清洗后岗位名称 */
    private String cleanedPostName;

    /** 清洗后岗位描述文本 */
    private String cleanedText;

    /** 被移除的噪声内容 */
    private String removedNoiseText;

    /** 结构化职责列表（JSON数组） */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<String> responsibilities;

    /** 结构化要求列表（JSON数组） */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private List<String> requirements;

    // ========== 质量评估 ==========

    /** 质量评分 0.00-1.00 */
    private BigDecimal qualityScore;

    /** 质量评估详情（JSON） */
    private String qualityDetails;

    // ========== 去重检测 ==========

    /** 重复状态：NONE / SUSPECTED / DUPLICATE_BLOCKED */
    private String duplicateStatus;

    /** 疑似重复的岗位ID */
    private Long duplicatePostId;

    /** 与疑似重复岗位的相似度 */
    private BigDecimal duplicateScore;

    /** 疑似重复岗位名称（冗余，便于展示） */
    private String duplicatePostName;

    // ========== 阻断信息 ==========

    /** 是否被阻断：0-否 1-是 */
    private Integer blocked;

    /** 阻断原因 */
    private String blockReason;

    // ========== Agent 调用信息 ==========

    /** 是否进入了能力提取Agent：0-否 1-是 */
    private Integer enteredAgent;

    /** Agent输入快照（JSON） */
    private String agentInputSnapshot;

    // ========== 清洗耗时 ==========

    /** 清洗耗时（毫秒） */
    private Integer cleaningDurationMs;

    // ========== 通用字段 ==========

    /** 创建人 */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    // ========== 重复状态枚举常量 ==========

    public static final String DUPLICATE_STATUS_NONE = "NONE";
    public static final String DUPLICATE_STATUS_SUSPECTED = "SUSPECTED";
    public static final String DUPLICATE_STATUS_DUPLICATE_BLOCKED = "DUPLICATE_BLOCKED";

    // ========== 来源类型枚举常量 ==========

    public static final String SOURCE_TYPE_JD_TEXT = "JD_TEXT";
    public static final String SOURCE_TYPE_EXCEL_IMPORT = "EXCEL_IMPORT";
    public static final String SOURCE_TYPE_EMERGING_POST = "EMERGING_POST";
    public static final String SOURCE_TYPE_API = "API";
}
