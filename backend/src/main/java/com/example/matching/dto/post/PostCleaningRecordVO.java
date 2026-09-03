package com.example.matching.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位数据清洗记录 VO（View Object）
 * <p>
 * 用于前端展示清洗记录列表和详情。
 */
@Data
@Schema(description = "岗位数据清洗记录")
public class PostCleaningRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "记录ID")
    private Long id;

    // ========== 来源信息 ==========

    @Schema(description = "来源类型：JD_TEXT / EXCEL_IMPORT / EMERGING_POST / API")
    private String sourceType;

    @Schema(description = "来源关联ID")
    private Long sourceRefId;

    // ========== 原始数据 ==========

    @Schema(description = "原始岗位名称")
    private String rawPostName;

    @Schema(description = "原始岗位描述文本")
    private String rawText;

    // ========== 清洗后数据 ==========

    @Schema(description = "清洗后岗位名称")
    private String cleanedPostName;

    @Schema(description = "清洗后岗位描述文本")
    private String cleanedText;

    @Schema(description = "被移除的噪声内容")
    private String removedNoiseText;

    @Schema(description = "结构化职责列表")
    private List<String> responsibilities;

    @Schema(description = "结构化要求列表")
    private List<String> requirements;

    // ========== 质量评估 ==========

    @Schema(description = "质量评分 0.00-1.00")
    private BigDecimal qualityScore;

    @Schema(description = "质量评估详情（JSON）")
    private String qualityDetails;

    // ========== 去重检测 ==========

    @Schema(description = "重复状态：NONE / SUSPECTED / DUPLICATE_BLOCKED")
    private String duplicateStatus;

    @Schema(description = "疑似重复的岗位ID")
    private Long duplicatePostId;

    @Schema(description = "与疑似重复岗位的相似度")
    private BigDecimal duplicateScore;

    @Schema(description = "疑似重复岗位名称")
    private String duplicatePostName;

    // ========== 阻断信息 ==========

    @Schema(description = "是否被阻断")
    private Boolean blocked;

    @Schema(description = "阻断原因")
    private String blockReason;

    // ========== Agent 调用信息 ==========

    @Schema(description = "是否进入了能力提取Agent")
    private Boolean enteredAgent;

    @Schema(description = "Agent输入快照（JSON）")
    private String agentInputSnapshot;

    // ========== 其他 ==========

    @Schema(description = "清洗耗时（毫秒）")
    private Integer cleaningDurationMs;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    // ========== 重复状态常量 ==========

    public static final String DUPLICATE_STATUS_NONE = "NONE";
    public static final String DUPLICATE_STATUS_SUSPECTED = "SUSPECTED";
    public static final String DUPLICATE_STATUS_DUPLICATE_BLOCKED = "DUPLICATE_BLOCKED";
}
