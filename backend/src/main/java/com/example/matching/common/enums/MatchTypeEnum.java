package com.example.matching.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 匹配命中类型枚举
 * <p>
 * 定义了标签匹配的层级关系，优先级从高到低。
 *
 * @author system
 */
@Getter
@AllArgsConstructor
public enum MatchTypeEnum {

    /** 精确命中：相同tagId */
    EXACT("EXACT", "精确命中", 1.00),

    /** 归一命中：tagId不同但canonical_tag_id相同 */
    CANONICAL("CANONICAL", "归一命中", 1.00),

    /** 已确认相近：不同标准标签，但存在人工确认的SIMILAR关系 */
    CONFIRMED_SIMILAR("CONFIRMED_SIMILAR", "已确认相近", 0.92),

    /**
     * 语义兜底：运行时根据标签向量计算得到的高相似命中
     * 注意：defaultCoefficient为-1表示系数为运行时实时相似度值，非固定值
     */
    SEMANTIC_FALLBACK("SEMANTIC_FALLBACK", "语义兜底", -1.00),

    /** 未命中 */
    NONE("NONE", "未命中", 0.00);

    /** 类型编码 */
    private final String code;

    /** 类型描述 */
    private final String description;

    /** 默认命中系数 */
    private final double defaultCoefficient;

    /**
     * 根据编码查找枚举
     */
    public static MatchTypeEnum fromCode(String code) {
        if (code == null) return NONE;
        for (MatchTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return NONE;
    }
}
