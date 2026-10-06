package com.example.matching.vo.employee.video;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 视频面试详情响应VO
 */
@Data
@Schema(description = "视频面试详情响应，包含会话信息、问题列表、证据列表和提取的能力列表")
public class VideoInterviewDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "会话ID")
    private Long id;

    @Schema(description = "员工ID")
    private Long empId;

    @Schema(description = "岗位ID")
    private Long postId;

    @Schema(description = "会话名称")
    private String sessionName;

    @Schema(description = "面试模式")
    private String interviewMode;

    @Schema(description = "视频文件路径")
    private String videoFilePath;

    @Schema(description = "转录文本")
    private String transcriptText;

    @Schema(description = "总结报告")
    private String summaryReport;

    @Schema(description = "综合得分")
    private BigDecimal overallScore;

    @Schema(description = "状态：0-已创建,1-问题已生成,2-视频已上传,3-转录中,4-分析中,5-已完成,6-已导入,7-失败")
    private Integer status;

    @Schema(description = "视频时长（秒）")
    private Integer durationSeconds;

    @Schema(description = "问题数量")
    private Integer questionCount;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

    @Schema(description = "问题列表")
    private List<QuestionItem> questions;

    @Schema(description = "证据列表")
    private List<EvidenceItem> evidences;

    @Schema(description = "提取的能力列表")
    private List<VideoInterviewAbilityVO> abilities;

    /**
     * 问题条目
     */
    @Data
    @Schema(description = "面试问题详情")
    public static class QuestionItem implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "问题ID")
        private Long id;

        @Schema(description = "问题序号")
        private Integer questionOrder;

        @Schema(description = "问题类型")
        private String questionType;

        @Schema(description = "问题文本")
        private String questionText;

        @Schema(description = "题目难度：EASY-简单(30秒),MEDIUM-中等(60秒),HARD-困难(90秒)")
        private String difficulty;

        @Schema(description = "答题时长（秒）")
        private Integer durationSeconds;

        @Schema(description = "答案转录文本")
        private String answerTranscript;

        @Schema(description = "答案摘要")
        private String answerSummary;

        @Schema(description = "答案开始时间（秒）")
        private Integer startSecond;

        @Schema(description = "答案结束时间（秒）")
        private Integer endSecond;

        @Schema(description = "结束方式：TIMEOUT-超时切题,MANUAL_NEXT-候选人主动切题")
        private String endedBy;

        @Schema(description = "答案得分")
        private BigDecimal answerScore;

        @Schema(description = "分析评语")
        private String analysisComment;
    }

    /**
     * 证据条目
     */
    @Data
    @Schema(description = "面试证据详情")
    public static class EvidenceItem implements Serializable {

        private static final long serialVersionUID = 1L;

        @Schema(description = "证据ID")
        private Long id;

        @Schema(description = "问题ID")
        private Long questionId;

        @Schema(description = "证据类型：TEXT/AUDIO/VISUAL/MULTIMODAL")
        private String evidenceType;

        @Schema(description = "开始时间（秒）")
        private Integer startSecond;

        @Schema(description = "结束时间（秒）")
        private Integer endSecond;

        @Schema(description = "证据文本")
        private String evidenceText;

        @Schema(description = "置信度")
        private BigDecimal confidenceScore;

        @Schema(description = "原始得分")
        private BigDecimal rawScore;
    }
}
