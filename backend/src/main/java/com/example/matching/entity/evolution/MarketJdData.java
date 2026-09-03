package com.example.matching.entity.evolution;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 市场JD数据实体
 * <p>
 * 存储批量导入的市场招聘JD，用于岗位演化分析。
 *
 * @author system
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("market_jd_data")
public class MarketJdData implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 导入批次号 */
    private String batchNo;

    /** 岗位名称 */
    private String postName;

    /** 公司名称 */
    private String companyName;

    /** 城市 */
    private String city;

    /** 薪资范围 */
    private String salaryRange;

    /** 岗位描述 */
    private String jobDescription;

    /** 任职要求 */
    private String requirements;

    /** 技能标签，JSON数组 */
    private String skillTags;

    /** High-confidence vector recommendations. These are drafts and never formal model input. */
    private String recommendedSkillTags;

    /** 来源平台：BOSS/ZHILIN/LIEPIN/OTHER */
    private String sourcePlatform;

    /** JD发布时间 */
    private LocalDateTime publishedTime;

    /** 文本哈希，用于去重 */
    private String textHash;

    /** 相似JD分组ID */
    private String similarityGroupId;

    /** JD质量分 */
    private BigDecimal qualityScore;

    /** 是否重复：0否，1是 */
    private Integer isDuplicate;

    /** 规范文档ID（去重后的代表文档） */
    private Long canonicalDocumentId;

    /** 最后出现时间 */
    private LocalDateTime lastSeenTime;

    /** 时效性评分：0-100 */
    private BigDecimal freshnessScore;

    /** 噪声评分：0-100（越高越可能是噪声） */
    private BigDecimal noiseScore;

    /** 公司多样性键（用于去重统计） */
    private String companyDiversityKey;

    /** 匹配到的系统岗位ID */
    private Long matchedPostId;

    /** 分析状态：0待分析，1已分析，2跳过 */
    private Integer analysisStatus;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
