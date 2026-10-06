package com.example.matching.agent.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

/**
 * 开放词表提取/标签准入的结构化指标（Task10）。
 * <p>
 * 指标清单：
 * <ul>
 *   <li>extraction.validation.failed — 提取结果校验失败（tag: scenario=EMPLOYEE/POST）</li>
 *   <li>extraction.evidence.not_locatable — 证据无法在原文定位</li>
 *   <li>extraction.source_ref.invalid — 引用越界/无效</li>
 *   <li>extraction.tag.reused — 标签复用（EXISTING_TAG_REUSED）</li>
 *   <li>extraction.tag.formal_created — 新正式标签自动创建</li>
 *   <li>extraction.tag.candidate_created — 新标签进入候选池</li>
 *   <li>extraction.tag.rejected — 标签被拒绝</li>
 *   <li>extraction.graph_tool_calls — 提取链路中图谱工具被调用（应恒为 0，>0 即回归）</li>
 * </ul>
 */
@Component
public class ExtractionMetrics {

    public static final String SCENARIO_EMPLOYEE = "EMPLOYEE";
    public static final String SCENARIO_POST = "POST";

    private final MeterRegistry meterRegistry;

    public ExtractionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void validationFailed(String scenario) {
        counter("extraction.validation.failed", Tags.of("scenario", scenario)).increment();
    }

    public void evidenceNotLocatable(String scenario) {
        counter("extraction.evidence.not_locatable", Tags.of("scenario", scenario)).increment();
    }

    public void sourceRefInvalid(String scenario) {
        counter("extraction.source_ref.invalid", Tags.of("scenario", scenario)).increment();
    }

    public void tagReused() {
        counter("extraction.tag.reused", Tags.empty()).increment();
    }

    public void tagFormalCreated() {
        counter("extraction.tag.formal_created", Tags.empty()).increment();
    }

    public void tagCandidateCreated() {
        counter("extraction.tag.candidate_created", Tags.empty()).increment();
    }

    public void tagRejected() {
        counter("extraction.tag.rejected", Tags.empty()).increment();
    }

    /** 图谱工具被任何 Agent 调用时记录（提取链路应为 0，>0 说明回归）。 */
    public void graphToolCalled() {
        counter("extraction.graph_tool_calls", Tags.empty()).increment();
    }

    private Counter counter(String name, Tags tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }
}
