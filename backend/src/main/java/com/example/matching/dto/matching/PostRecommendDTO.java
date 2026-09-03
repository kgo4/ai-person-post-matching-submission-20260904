package com.example.matching.dto.matching;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 员工推荐岗位 DTO
 */
public class PostRecommendDTO {

    // ==================== 请求 DTO ====================

    @Data
    public static class Request {
        /** 员工ID */
        private Long empId;
        /** 返回Top K个推荐岗位，默认5 */
        private Integer topK = 5;
        /** 是否启用硬性条件预览 */
        private boolean enableHardConditionPreview = true;
        /** 是否将硬性条件作为严格过滤规则 */
        private boolean strictHardConditionMode = false;
        /** 是否启用L2预评分 */
        private boolean enableL2Preview = true;
    }

    // ==================== 响应 DTO ====================

    @Data
    public static class Response {
        /** 员工ID */
        private Long empId;
        /** 员工姓名 */
        private String empName;
        /** 推荐岗位列表 */
        private List<PostRecommendation> recommendations;
    }

    @Data
    public static class PostRecommendation {
        /** 岗位ID */
        private Long postId;
        /** 岗位名称 */
        private String postName;
        /** 岗位编码 */
        private String postCode;
        /** 所属部门 */
        private String departmentName;
        /** 岗位等级 */
        private String postLevel;
        /** 综合推荐分 */
        private BigDecimal recommendScore;
        /** 向量召回分 */
        private BigDecimal vectorScore;
        /** 预览标记：缺少执行输入（如向量分缺失）时为 true，表示分数为近似值 */
        private boolean approximate;
        /** L2预评分 */
        private BigDecimal l2PreviewScore;
        /** 硬性条件状态：PASS / RISK / FAIL */
        private String hardConditionStatus;
        /** 硬性条件详情 */
        private List<HardConditionDetail> hardConditionDetails;
        /** 核心能力命中数 */
        private Integer coreAbilityHitCount;
        /** 核心能力要求数 */
        private Integer coreAbilityTotalCount;
        /** 核心能力命中率 */
        private Double coreAbilityHitRate;
        /** 证据置信度：STRONG / MEDIUM / WEAK */
        private String evidenceConfidence;
        /** 能力差距摘要 */
        private List<String> gapSummary;
        /** 推荐理由 */
        private String reason;
        /** 岗位能力模型是否完整 */
        private boolean postModelComplete;
    }

    @Data
    public static class HardConditionDetail {
        private String field;
        private String label;
        private String operator;
        private String expectedValue;
        private String actualValue;
        private boolean passed;
        private String source;
    }
}
