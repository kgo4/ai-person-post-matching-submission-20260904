package com.example.matching.dto.evolution.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 爬虫推送的单条市场 JD。
 * <p>
 * 字段命名以主系统内部域名为准，同时兼容爬虫侧的上游命名：
 * {@code jobTitle} → {@code postName}、{@code jobRequirements} → {@code requirements}（文档 §3.2）。
 * 上游文档 §6 提到的原文三字段（{@code originalJobTitle} 等）第一阶段不推送，
 * 未识别字段由 Jackson 静默忽略，便于后续平滑扩展。
 *
 * @author system
 */
@Data
public class CrawlerMarketJdItem {

    /** 来源平台岗位 ID，唯一键首选成分（文档 §4.1） */
    private String externalId;

    /** 岗位名称（已清洗/翻译）；兼容爬虫侧字段名 {@code jobTitle} */
    @JsonAlias("jobTitle")
    private String postName;

    /** 公司名称 */
    private String companyName;

    /** 工作城市或地区 */
    private String city;

    /** 薪资范围 */
    private String salaryRange;

    /** 岗位职责/描述，参与 {@code contentHash} */
    private String jobDescription;

    /** 任职要求，参与 {@code contentHash}；兼容爬虫侧字段名 {@code jobRequirements} */
    @JsonAlias("jobRequirements")
    private String requirements;

    /** 预留：爬虫可能携带的标签串，主系统不依赖它做准入判断 */
    private String skillTags;

    /** 原始岗位链接，用于溯源 */
    private String sourceUrl;

    /** JD 发布时间，例如 {@code 2026-09-04T13:43:55}，可为 null */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime publishedTime;
}
