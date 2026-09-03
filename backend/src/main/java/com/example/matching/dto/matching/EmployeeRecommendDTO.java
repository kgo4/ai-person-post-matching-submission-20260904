package com.example.matching.dto.matching;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 岗位推荐员工 DTO（与 PostRecommendDTO 对称）
 */
public class EmployeeRecommendDTO {

    // ==================== 请求 DTO ====================

    @Data
    public static class Request {
        /** 岗位ID */
        private Long postId;
        /** 返回Top K个推荐员工，默认5 */
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
        /** 岗位ID */
        private Long postId;
        /** 岗位名称 */
        private String postName;
        /** 推荐员工列表 */
        private List<EmployeeRecommendation> recommendations;
    }

    @Data
    public static class EmployeeRecommendation {
        /** 员工ID */
        private Long empId;
        /** 员工姓名 */
        private String empName;
        /** 员工编码 */
        private String empCode;
        /** 综合推荐分 */
        private BigDecimal recommendScore;
        /** 向量召回分 */
        private BigDecimal vectorScore;
        /** L2预评分 */
        private BigDecimal l2PreviewScore;
        /** 硬性条件状态：PASS / RISK / FAIL */
        private String hardConditionStatus;
        /** 硬性条件详情 */
        private List<PostRecommendDTO.HardConditionDetail> hardConditionDetails;
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
    }
}
