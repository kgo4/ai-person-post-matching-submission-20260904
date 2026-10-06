package com.example.matching.dto.post;

import java.io.Serializable;
import java.util.List;

/**
 * 创建岗位趋势解析任务。
 *
 * @param sourceDocumentIds 已上传（或已存在的）知识源文档ID
 * @param sourceCategories  材料类别，仅用于诊断展示；实际类别以文档自身为准
 * @param taskName          任务名称，为空时后端生成带日期的默认名
 */
public record PostTrendTaskCreateRequest(
        List<Long> sourceDocumentIds,
        List<String> sourceCategories,
        String taskName
) implements Serializable {
}
