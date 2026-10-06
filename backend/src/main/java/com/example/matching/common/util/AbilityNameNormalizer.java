package com.example.matching.common.util;

/**
 * 能力名称标准化工具
 * <p>
 * 用于在评测时统一比较能力名称：去除空白、转小写、移除常见分隔符。
 * </p>
 *
 * @author system
 */
public final class AbilityNameNormalizer {

    private AbilityNameNormalizer() {
    }

    /**
     * 标准化能力名称
     * <ul>
     *   <li>null -> 空字符串</li>
     *   <li>trim 空白</li>
     *   <li>转小写</li>
     *   <li>移除空格、下划线、短横线、斜杠、括号、点号及常见全角/中英文变体</li>
     * </ul>
     *
     * @param name 原始能力名称
     * @return 标准化后的名称
     */
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.trim()
                .toLowerCase()
                .replace(" ", "")
                .replace("　", "")
                .replace("_", "")
                .replace("-", "")
                .replace("－", "")
                .replace("—", "")
                .replace("–", "")
                .replace("/", "")
                .replace("(", "")
                .replace(")", "")
                .replace("（", "")
                .replace("）", "")
                .replace("[", "")
                .replace("]", "")
                .replace("【", "")
                .replace("】", "")
                .replace(".", "")
                .replace("．", "")
                .replace("·", "")
                .replace("、", "")
                .replace("，", "")
                .replace(",", "");
    }
}
