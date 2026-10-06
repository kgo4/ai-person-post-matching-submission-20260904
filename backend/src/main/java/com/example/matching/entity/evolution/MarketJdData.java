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

    /** 入库通道：爬虫推送 */
    public static final String CHANNEL_CRAWLER = "CRAWLER";
    /** 入库通道：人工上传（粘贴文本 / Excel） */
    public static final String CHANNEL_MANUAL_UPLOAD = "MANUAL_UPLOAD";
    /** 入库通道：岗位导入连带的市场 JD */
    public static final String CHANNEL_POST_IMPORT = "POST_IMPORT";

    /** 主键，自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 导入批次号 */
    private String batchNo;

    /**
     * 入库通道：CRAWLER / MANUAL_UPLOAD / POST_IMPORT。
     * <p>
     * 批次列表要靠它把「爬虫推来的」和「人工上传的」分开 —— 这是运维判断
     * 「这批数据该不该信、该不该留」的第一依据。历史数据由 V167 回填。
     */
    private String ingestChannel;

    /** 源站岗位外部ID，用于爬虫重试幂等 */
    private String externalId;

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

    /**
     * AI 原始提取结果（JSON 数组），**未经标签库匹配与准入门禁**。
     * <p>
     * 解析链路上 AI 提取的能力项要经过「与系统标签库匹配 → 准入门禁（新能力需跨 JD/跨公司
     * 双阈值）→ Harness 审核」，只有全部通过的才以标签ID形式写进 {@link #skillTags}。
     * 未命中 / 未达阈值 / 未过审的提取结果原本在库里没有任何痕迹，用户看到的是
     * 「解析完成了但一条结果都没有」。本列在提取出来时即写入，用于回答「AI 到底读出了什么」。
     * <p>
     * <b>仅供查看</b>：不参与任何下游计算，{@code skill_tags} 的语义与按标签ID解析它的
     * 下游消费方均不受影响。编解码见
     * {@code service.evolution.crawler.MarketJdAiSkillTagCodec}。
     */
    private String aiSkillTags;

    /** 来源平台：BOSS/ZHILIN/LIEPIN/OTHER */
    private String sourcePlatform;

    /** 源站岗位详情URL */
    private String sourceUrl;

    /** JD发布时间 */
    private LocalDateTime publishedTime;

    /** 文本哈希，用于去重（口径：岗位名 + 公司 + 描述 + 要求） */
    private String textHash;

    /**
     * 正文内容哈希：{@code SHA-256(jobDescription + "\n" + requirements)}（对齐爬虫对接文档 §4.2）。
     * <p>
     * 仅用于判定「同源岗位（source_platform + external_id）的正文是否变化」，
     * 与 {@link #textHash} 职责分离——后者继续服务跨批次精确去重与 SimHash 近似去重。
     */
    private String contentHash;

    /** 首次接收时间 */
    private LocalDateTime firstSeenTime;

    /** 正文最近变更时间 */
    private LocalDateTime lastUpdatedTime;

    /** 内容分类，爬虫推送的 JD 固定为 RECRUITMENT_JD */
    private String contentCategory;

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
