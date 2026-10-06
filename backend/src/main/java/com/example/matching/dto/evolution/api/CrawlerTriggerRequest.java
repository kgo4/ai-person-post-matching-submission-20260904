package com.example.matching.dto.evolution.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 主系统代理触发爬虫抓取时的入参。
 * <p>
 * 字段与爬虫系统 {@code /api/crawler/trigger} 的契约保持一致，主系统只做透传。
 */
@Data
@Schema(description = "触发爬虫抓取请求")
public class CrawlerTriggerRequest {

    @NotEmpty(message = "数据源不能为空")
    @Schema(description = "数据源列表，如 [\"boss\", \"lagou\"]", example = "[\"boss\", \"lagou\"]")
    private List<String> sources;

    @Schema(description = "搜索关键词列表", example = "[\"Java开发工程师\"]")
    private List<String> keywords;

    @Schema(description = "城市列表", example = "[\"北京\", \"上海\"]")
    private List<String> cities;

    @Min(value = 1, message = "最大抓取数量不能小于1")
    @Max(value = 500, message = "最大抓取数量不能大于500")
    @Schema(description = "最大抓取数量，默认100", example = "100")
    private Integer maxItems = 100;
}
