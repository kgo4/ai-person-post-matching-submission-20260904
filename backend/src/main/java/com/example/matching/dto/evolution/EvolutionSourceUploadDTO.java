package com.example.matching.dto.evolution;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 演化资料上传DTO
 *
 * @author system
 */
@Data
public class EvolutionSourceUploadDTO {

    /** 文档标题 */
    private String title;

    /** 行业 */
    private String industry;

    /** 业务领域 */
    private String businessDomain;

    /** 资料类别：INDUSTRY_WHITEPAPER/INTERNAL_POST_INFO/INTERNAL_BUSINESS_UPDATE/INTERNAL_POLICY */
    private String sourceCategory;

    /** 文档类型（内部资料用）：岗位信息/业务更新/内部规范/项目资料 */
    private String documentType;

    /** 资料发布日期 */
    private LocalDateTime publishedTime;

    /** 资料生效时间 */
    private LocalDateTime effectiveTime;

    /** 可信等级：HIGH/MEDIUM/LOW */
    private String trustLevel;

    /** 是否参与自动演化 */
    private Boolean evolutionEnabled;

    /** 适用岗位ID（可选） */
    private Long postId;

    /**
     * 是否「仅试算」上传。
     *
     * <p>用于「我只是想看看这份材料能解析出什么岗位」的场景：材料照常切片与索引以支撑本次解析，
     * 但解析任务进入终态后会被系统自动清理，不长期占用知识源。
     * 默认为 false（正式材料，长期保留，可在材料管理中查看与删除）。
     */
    private Boolean ephemeral;
}
