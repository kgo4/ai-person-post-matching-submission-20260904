package com.example.matching.entity.rag;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识来源文档实体
 * <p>
 * 统一管理行业白皮书、云知识库、招聘数据等外部文档来源。
 * 每个文档经过治理后生成 sourceRef，供岗位演化和人岗诊断使用。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("knowledge_source_document")
public class KnowledgeSourceDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 来源类型：INDUSTRY_WHITEPAPER/POLICY_DOCUMENT/OCCUPATION_STANDARD/CLOUD_KNOWLEDGE_INTERNAL/RECRUITMENT_JD */
    private String sourceType;

    /** 资料类别：INDUSTRY_WHITEPAPER/INTERNAL_POST_INFO/INTERNAL_BUSINESS_UPDATE/INTERNAL_POLICY/MARKET_JD/EXTERNAL_SIGNAL */
    private String sourceCategory;

    /** 来源引用ID（关联知识库文档ID或导入批次ID） */
    private Long sourceRefId;

    /** 文档标题 */
    private String title;

    /** 文档版本 */
    private String documentVersion;

    /** 行业 */
    private String industry;

    /** 业务领域 */
    private String businessDomain;

    /** 上传人ID */
    private Long uploaderId;

    /** 上传人角色：ADMIN/HRBP/BUSINESS_LEADER/PROJECT_LEADER */
    private String uploaderRole;

    /** 资料负责人/上传人 */
    private String sourceOwner;

    /** 权威等级：HIGH/MEDIUM/LOW */
    private String authorityLevel;

    /** 权威度评分：0-100 */
    private BigDecimal authorityScore;

    /** 发布时间 */
    private LocalDateTime publishedTime;

    /** 采集时间 */
    private LocalDateTime collectedTime;

    /** 资料生效时间 */
    private LocalDateTime effectiveTime;

    /** 可信等级：HIGH/MEDIUM/LOW */
    private String trustLevel;

    /** 时效性评分：0-100 */
    private BigDecimal freshnessScore;

    /** 质量评分：0-100 */
    private BigDecimal qualityScore;

    /** 内容哈希（用于去重） */
    private String contentHash;

    /** 近似哈希（用于近重复检测） */
    private String simHash;

    /** 重复组键（相同内容文档归为一组） */
    private String duplicateGroupKey;

    /** 知识库ID（云知识库关联） */
    private String knowledgeBaseId;

    /** 云文档ID */
    private String cloudDocumentId;

    /** 可见范围：PUBLIC/INTERNAL/RESTRICTED */
    private String visibility;

    /** 文档状态：PENDING/ACTIVE/ARCHIVED/DELETED */
    private String status;

    /** 是否参与岗位演化：0否，1是 */
    private Integer evolutionEnabled;

    /** 切片数量 */
    private Integer chunkCount;

    /** 最后索引时间 */
    private LocalDateTime lastIndexedTime;

    /** 关联的 RAG 文档ID */
    private Long ragDocumentId;

    /** 存储路径 */
    private String storagePath;

    /** 索引状态 */
    private String indexStatus;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
