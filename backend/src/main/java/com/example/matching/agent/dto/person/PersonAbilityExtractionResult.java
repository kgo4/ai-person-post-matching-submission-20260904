package com.example.matching.agent.dto.person;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 人员能力提取结果DTO
 * <p>
 * 统一输出格式，所有人员能力提取结果都使用该DTO。
 * 包含提取的能力声明列表和元数据。
 *
 * @author system
 */
@Data
public class PersonAbilityExtractionResult {

    /** 员工ID */
    private Long empId;

    /** 来源类型 */
    private String sourceType;

    /** 来源引用ID */
    private Long sourceRefId;

    /** 提取的能力声明列表 */
    @JsonProperty(required = true)
    private List<PersonAbilityClaim> claims;

    /** 提取摘要 */
    private String summary;

    /** 模型输出的整体来源引用列表 */
    private List<String> sourceRefs;

    /** 是否使用降级方案 */
    private boolean fallbackUsed;

    /** 原始模型输出 */
    private String rawModelOutput;

    /** 提取耗时（毫秒） */
    private Long durationMs;

    /** 分块提取失败的分块数（>0 表示部分分块进入 RETRY/REVIEW） */
    private int failedChunkCount;

    /** 简历基础信息（硬条件筛选依赖：学历/工作年限/当前职位） */
    private BasicInfo basicInfo;

    /**
     * 简历基础信息：用于硬条件筛选的确定性字段，由模型从 sourceText 提取，
     * 服务端反序列化并随提取结果持久化。
     */
    @Data
    public static class BasicInfo {
        /** 学历，如 本科/硕士/博士，未知为 null */
        private String degree;
        /** 工作年限（年），未知为 null */
        private Integer yearsOfWork;
        /** 当前职位，未知为 null */
        private String currentTitle;
    }

    /**
     * 获取有效的声明列表（派生计算属性，非持久字段；@JsonIgnore 避免反序列化填充不可变列表）
     *
     * @return 有效声明列表
     */
    @JsonIgnore
    public List<PersonAbilityClaim> getValidClaims() {
        if (claims == null) {
            return List.of();
        }
        return claims.stream()
                .filter(PersonAbilityClaim::isValid)
                .toList();
    }

    /**
     * 获取已匹配正式标签的声明列表（派生计算属性，非持久字段）
     *
     * @return 已匹配声明列表
     */
    @JsonIgnore
    public List<PersonAbilityClaim> getMatchedClaims() {
        if (claims == null) {
            return List.of();
        }
        return claims.stream()
                .filter(PersonAbilityClaim::hasMatchedTag)
                .toList();
    }

    /**
     * 获取需要审核的声明列表（有相似标签但无正式标签，派生计算属性）
     *
     * @return 需要审核声明列表
     */
    @JsonIgnore
    public List<PersonAbilityClaim> getReviewNeededClaims() {
        if (claims == null) {
            return List.of();
        }
        return claims.stream()
                .filter(claim -> !claim.hasMatchedTag() && claim.hasSimilarTag())
                .toList();
    }

    /**
     * 获取无标签匹配的声明列表（派生计算属性，非持久字段）
     *
     * @return 无标签匹配声明列表
     */
    @JsonIgnore
    public List<PersonAbilityClaim> getUnmatchedClaims() {
        if (claims == null) {
            return List.of();
        }
        return claims.stream()
                .filter(claim -> !claim.hasMatchedTag() && !claim.hasSimilarTag())
                .toList();
    }

    /**
     * 获取声明总数
     *
     * @return 声明总数
     */
    public int getClaimCount() {
        return claims != null ? claims.size() : 0;
    }

    /**
     * 获取有效声明总数
     *
     * @return 有效声明总数
     */
    public int getValidClaimCount() {
        return getValidClaims().size();
    }
}
