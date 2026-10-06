package com.example.matching.service.kg;

import java.util.List;

/**
 * 岗位图谱演化服务接口
 * <p>
 * 负责在岗位模型版本发布后更新知识图谱。
 * 只更新 Post、Ability、Evidence 节点和关系。
 *
 * @author system
 */
public interface PostGraphEvolutionService {

    /**
     * 图谱更新结果
     */
    record GraphUpdateResult(
            int nodesCreated,
            int nodesUpdated,
            int edgesCreated,
            int edgesUpdated,
            List<String> warnings
    ) {}

    /**
     * 岗位模型版本发布后更新图谱
     *
     * @param postId    岗位ID
     * @param versionId 版本ID
     * @return 更新结果
     */
    GraphUpdateResult updateGraphAfterVersionPublish(Long postId, Long versionId);

    /**
     * 更新岗位-能力关系
     *
     * @param postId      岗位ID
     * @param abilityTagId 能力标签ID
     * @param supportScore 支持分数
     * @param sourceRefs   来源引用列表
     * @param versionId    版本ID
     * @return 是否成功
     */
    boolean updatePostAbilityRelation(Long postId, Long abilityTagId, double supportScore,
                                       List<String> sourceRefs, Long versionId);

    /**
     * 添加证据-能力支持关系
     *
     * @param evidenceId   证据ID
     * @param abilityTagId 能力标签ID
     * @param supportScore 支持分数
     * @param sourceRef    来源引用
     * @return 是否成功
     */
    boolean addEvidenceSupportRelation(Long evidenceId, Long abilityTagId, double supportScore, String sourceRef);

    /**
     * 获取岗位的能力图谱
     *
     * @param postId 岗位ID
     * @return 图谱数据（JSON格式）
     */
    String getPostAbilityGraph(Long postId);

    /**
     * 获取能力的证据链
     *
     * @param abilityTagId 能力标签ID
     * @return 证据链数据（JSON格式）
     */
    String getAbilityEvidenceChain(Long abilityTagId);
}
