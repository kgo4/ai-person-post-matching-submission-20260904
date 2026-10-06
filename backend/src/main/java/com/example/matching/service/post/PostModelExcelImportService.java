package com.example.matching.service.post;

import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.dto.post.PostModelExcelRowDTO;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 岗位能力模型Excel导入服务
 * <p>
 * 支持两种导入模式：
 * <ul>
 *   <li>模板A（AI补齐）：导入岗位编码/名称/描述，AI自动生成能力项和权重</li>
 *   <li>模板B（直接导入）：导入完整的岗位能力模型配置</li>
 * </ul>
 */
public interface PostModelExcelImportService {

    /**
     * 解析Excel文件，返回解析结果
     *
     * @param file Excel文件
     * @return 解析后的行数据列表
     */
    List<PostModelExcelRowDTO> parseExcel(InputStream inputStream);

    /**
     * 批量导入岗位能力模型（模板B：直接导入）
     * <p>
     * 按岗位编码分组，对每个岗位调用 batchConfig 生成能力模型。
     * 如果岗位不存在，自动创建岗位。
     *
     * @param rows Excel行数据
     * @return 导入结果：postId -> 导入的能力项数量
     */
    Map<Long, Integer> batchImportFromTemplateB(List<PostModelExcelRowDTO> rows);

    /**
     * 批量导入岗位能力模型（模板A：AI补齐）
     * <p>
     * 按岗位编码分组，对每个岗位调用AI生成能力模型草稿。
     *
     * @param rows Excel行数据
     * @return 导入结果：postId -> 生成的能力项数量
     */
    Map<Long, Integer> batchImportFromTemplateA(List<PostModelExcelRowDTO> rows);

    /**
     * 一键归一化权重到100%
     * <p>
     * 将指定岗位的所有能力项权重按比例缩放，使总和为100。
     *
     * @param postId 岗位ID
     * @return 归一化后的权重列表
     */
    List<PostAbilityModelConfigDTO> normalizeWeights(Long postId);

    /**
     * 复制已有岗位模型到新岗位
     *
     * @param sourcePostId 源岗位ID
     * @param targetPostId 目标岗位ID
     * @return 复制的能力项数量
     */
    int copyPostModel(Long sourcePostId, Long targetPostId);
}
