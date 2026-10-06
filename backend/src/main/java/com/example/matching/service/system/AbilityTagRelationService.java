package com.example.matching.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.entity.system.AbilityTagRelation;

import java.util.List;

/**
 * 能力标签关系 服务接口
 */
public interface AbilityTagRelationService extends IService<AbilityTagRelation> {

    /**
     * 分页查询标签关系
     *
     * @param page         分页参数
     * @param sourceTagId  源标签ID（可选）
     * @param targetTagId  目标标签ID（可选）
     * @param relationType 关系类型（可选）
     * @param status       状态（可选）
     * @return 分页结果
     */
    IPage<AbilityTagRelation> pageRelations(IPage<AbilityTagRelation> page,
                                             Long sourceTagId, Long targetTagId,
                                             String relationType, String status);

    /**
     * 创建标签关系（待审核状态）
     *
     * @param sourceTagId    源标签ID
     * @param targetTagId    目标标签ID
     * @param relationType   关系类型
     * @param similarityScore 相似度分数（可选）
     * @param evidenceSource  证据来源
     * @param remark         备注（可选）
     * @param createdBy      创建人ID（可选）
     * @return 创建的关系记录
     */
    AbilityTagRelation createRelation(Long sourceTagId, Long targetTagId,
                                       String relationType, Double similarityScore,
                                       String evidenceSource, String remark, Long createdBy);

    /**
     * 审核通过标签关系
     * <p>
     * 审核通过后：
     * - 若为SAME_AS，将两个标签归一到同一标准标签
     * - 若为SIMILAR，保留独立标准标签，允许匹配折扣命中
     *
     * @param id        关系ID
     * @param updatedBy 审核人ID（可选）
     */
    void approveRelation(Long id, Long updatedBy);

    /**
     * 审核拒绝标签关系
     *
     * @param id        关系ID
     * @param updatedBy 审核人ID（可选）
     */
    void rejectRelation(Long id, Long updatedBy);

    /**
     * 查找两个标签之间的所有关系（双向）
     *
     * @param tagId1 标签1
     * @param tagId2 标签2
     * @return 关系列表
     */
    List<AbilityTagRelation> findRelationsBetween(Long tagId1, Long tagId2);

    /**
     * 批量创建候选关系（向量发现模式）
     * <p>
     * 跳过已存在的关系对。
     *
     * @param sourceTagId     源标签ID
     * @param similarTagIds   相似标签ID列表
     * @param similarityScores 相似度分数列表（与similarTagIds一一对应）
     * @return 实际创建的数量
     */
    int batchCreateCandidateRelations(Long sourceTagId, List<Long> similarTagIds,
                                       List<Double> similarityScores);

    /**
     * 全量扫描标签向量，自动发现相似标签关系
     * <p>
     * 遍历所有有向量的标签，两两计算余弦相似度，
     * 超过阈值的创建PENDING状态的SIMILAR关系。
     *
     * @param threshold 相似度阈值（建议0.7）
     * @return 新建的关系数量
     */
    int discoverRelations(double threshold);
}
