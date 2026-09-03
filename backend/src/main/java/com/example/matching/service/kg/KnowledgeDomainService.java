package com.example.matching.service.kg;

import com.example.matching.entity.kg.KnowledgeDomain;
import com.example.matching.entity.kg.KnowledgeNode;

import java.util.List;

/**
 * 知识领域服务接口
 *
 * @author system
 */
public interface KnowledgeDomainService {

    /**
     * 获取所有知识领域
     *
     * @return 知识领域列表
     */
    List<KnowledgeDomain> getAllDomains();

    /**
     * 根据ID获取知识领域
     *
     * @param id 领域ID
     * @return 知识领域
     */
    KnowledgeDomain getDomainById(Long id);

    /**
     * 根据编码获取知识领域
     *
     * @param domainCode 领域编码
     * @return 知识领域
     */
    KnowledgeDomain getDomainByCode(String domainCode);

    /**
     * 创建知识领域
     *
     * @param domain 知识领域
     * @return 创建后的知识领域
     */
    KnowledgeDomain createDomain(KnowledgeDomain domain);

    /**
     * 更新知识领域
     *
     * @param domain 知识领域
     * @return 更新后的知识领域
     */
    KnowledgeDomain updateDomain(KnowledgeDomain domain);

    /**
     * 删除知识领域
     *
     * @param id 领域ID
     */
    void deleteDomain(Long id);

    /**
     * 获取领域下的所有知识点
     *
     * @param domainId 领域ID
     * @return 知识点列表
     */
    List<KnowledgeNode> getNodesByDomainId(Long domainId);

    /**
     * 根据ID获取知识点
     *
     * @param nodeId 知识点ID
     * @return 知识点
     */
    KnowledgeNode getNodeById(Long nodeId);

    /**
     * 根据编码获取知识点
     *
     * @param nodeCode 知识点编码
     * @return 知识点
     */
    KnowledgeNode getNodeByCode(String nodeCode);

    /**
     * 创建知识点
     *
     * @param node 知识点
     * @return 创建后的知识点
     */
    KnowledgeNode createNode(KnowledgeNode node);

    /**
     * 更新知识点
     *
     * @param node 知识点
     * @return 更新后的知识点
     */
    KnowledgeNode updateNode(KnowledgeNode node);

    /**
     * 删除知识点
     *
     * @param nodeId 知识点ID
     */
    void deleteNode(Long nodeId);

    /**
     * 获取知识点的子知识点
     *
     * @param parentId 父知识点ID
     * @return 子知识点列表
     */
    List<KnowledgeNode> getChildNodes(Long parentId);

    /**
     * 初始化默认知识领域
     */
    void initDefaultDomains();

    /**
     * 初始化默认知识点
     */
    void initDefaultNodes();
}
