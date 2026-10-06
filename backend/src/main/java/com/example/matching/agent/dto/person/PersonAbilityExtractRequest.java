package com.example.matching.agent.dto.person;

import lombok.Data;

import java.util.List;

/**
 * 人员能力提取请求DTO
 * <p>
 * 用于从指定来源材料中提取能力声明。
 * 必须包含原始来源文本，Agent 才能从中抽取能力。
 *
 * @author system
 */
@Data
public class PersonAbilityExtractRequest {

    /** 员工ID */
    private Long empId;

    /** 统一来源类型，旧编码在服务入口自动转换 */
    private String sourceType;

    /** 来源引用ID */
    private Long sourceRefId;

    /** 来源文本：简历文本、测试结果、面试分析等原始材料 */
    private String sourceText;

    /**
     * 来源文本是否由 OCR 生成。
     * <p>
     * 该字段仅用于服务端证据定位的字符标准化，不参与业务来源、持久化或正式能力计算。
     */
    private boolean ocrDerived;

    /** 证据文本：与来源相关的证据内容 */
    private String evidenceText;

    /** 来源引用列表 */
    private List<String> sourceRefs;

    /** 分块索引（长文本分块时为 0..n-1，单块为 0） */
    private Integer chunkIndex;

    /** 分块在原文中的起始偏移（长文本分块时用于修正证据偏移） */
    private Integer chunkStartOffset;

    /** 上下文哈希 */
    private String contextHash;

    /** 上下文快照ID */
    private Long contextSnapshotId;

    /** 员工已有能力列表（用于避免重复提取） */
    private List<ExistingAbility> existingAbilities;

    /**
     * 已有能力
     */
    @Data
    public static class ExistingAbility {
        /** 能力标签ID */
        private Long abilityTagId;
        /** 能力名称 */
        private String abilityName;
        /** 当前等级 */
        private Integer currentLevel;
    }

    /**
     * 验证请求是否有效
     *
     * @return 是否有效
     */
    public boolean isValid() {
        return empId != null
                && sourceType != null && !sourceType.isBlank()
                && sourceText != null && !sourceText.isBlank();
    }
}
