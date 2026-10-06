package com.example.matching.dto.evolution.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 切换「爬虫推送批次是否自动解析」的请求体。
 * <p>
 * {@code enabled} 刻意用包装类型 + {@code @NotNull}：漏传字段时必须 400 拒绝，
 * 若用原始 {@code boolean} 会默认 false —— 「不小心把开关关掉」是这类运维开关最坏的失败方式，
 * 因为它看起来是成功的。
 *
 * @author system
 */
@Data
public class CrawlerAutoAnalyzeUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "enabled 不能为空")
    @Schema(description = "true = 推送的批次入库后自动解析；false = 只入库，等人工解析", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enabled;
}
