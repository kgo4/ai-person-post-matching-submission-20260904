package com.example.matching.service.kg.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.matching.entity.kg.AbilityTagDomainRel;
import com.example.matching.entity.kg.KnowledgeDomain;
import com.example.matching.entity.kg.KnowledgeNode;
import com.example.matching.mapper.kg.AbilityTagDomainRelMapper;
import com.example.matching.mapper.kg.KnowledgeDomainMapper;
import com.example.matching.mapper.kg.KnowledgeNodeMapper;
import com.example.matching.service.kg.KnowledgeDomainService;
import com.example.matching.service.kg.GraphChangeSetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 知识领域服务实现
 *
 * @author system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDomainServiceImpl implements KnowledgeDomainService {

    private final KnowledgeDomainMapper domainMapper;
    private final KnowledgeNodeMapper nodeMapper;
    private final AbilityTagDomainRelMapper relMapper;
    private final GraphChangeSetService graphChangeSetService;

    @Override
    public List<KnowledgeDomain> getAllDomains() {
        return domainMapper.selectList(
                Wrappers.<KnowledgeDomain>lambdaQuery()
                        .eq(KnowledgeDomain::getIsDeleted, 0)
                        .orderByAsc(KnowledgeDomain::getSortOrder));
    }

    @Override
    public KnowledgeDomain getDomainById(Long id) {
        return domainMapper.selectById(id);
    }

    @Override
    public KnowledgeDomain getDomainByCode(String domainCode) {
        return domainMapper.selectOne(
                Wrappers.<KnowledgeDomain>lambdaQuery()
                        .eq(KnowledgeDomain::getDomainCode, domainCode)
                        .eq(KnowledgeDomain::getIsDeleted, 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDomain createDomain(KnowledgeDomain domain) {
        domain.setIsDeleted(0);
        domain.setVersion(1);
        domainMapper.insert(domain);
        graphChangeSetService.requestChange("KNOWLEDGE_DOMAIN", "KNOWLEDGE_DOMAIN", domain.getId(), "UPSERT",
                Map.of("trigger", "KnowledgeDomainService.createDomain"), domain.getCreatedBy());
        return domain;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDomain updateDomain(KnowledgeDomain domain) {
        domainMapper.updateById(domain);
        graphChangeSetService.requestChange("KNOWLEDGE_DOMAIN", "KNOWLEDGE_DOMAIN", domain.getId(), "UPSERT",
                Map.of("trigger", "KnowledgeDomainService.updateDomain"), domain.getUpdatedBy());
        return domain;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDomain(Long id) {
        KnowledgeDomain domain = new KnowledgeDomain();
        domain.setId(id);
        domain.setIsDeleted(1);
        domainMapper.updateById(domain);
        graphChangeSetService.requestChange("KNOWLEDGE_DOMAIN", "KNOWLEDGE_DOMAIN", id, "DELETE",
                Map.of("trigger", "KnowledgeDomainService.deleteDomain"), null);
    }

    @Override
    public List<KnowledgeNode> getNodesByDomainId(Long domainId) {
        return nodeMapper.selectList(
                Wrappers.<KnowledgeNode>lambdaQuery()
                        .eq(KnowledgeNode::getDomainId, domainId)
                        .eq(KnowledgeNode::getIsDeleted, 0)
                        .orderByAsc(KnowledgeNode::getSortOrder));
    }

    @Override
    public KnowledgeNode getNodeById(Long nodeId) {
        return nodeMapper.selectById(nodeId);
    }

    @Override
    public KnowledgeNode getNodeByCode(String nodeCode) {
        return nodeMapper.selectOne(
                Wrappers.<KnowledgeNode>lambdaQuery()
                        .eq(KnowledgeNode::getNodeCode, nodeCode)
                        .eq(KnowledgeNode::getIsDeleted, 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeNode createNode(KnowledgeNode node) {
        node.setIsDeleted(0);
        node.setVersion(1);
        nodeMapper.insert(node);
        graphChangeSetService.requestChange("KNOWLEDGE_NODE", "KNOWLEDGE_NODE", node.getId(), "UPSERT",
                Map.of("trigger", "KnowledgeDomainService.createNode"), node.getCreatedBy());
        return node;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeNode updateNode(KnowledgeNode node) {
        nodeMapper.updateById(node);
        graphChangeSetService.requestChange("KNOWLEDGE_NODE", "KNOWLEDGE_NODE", node.getId(), "UPSERT",
                Map.of("trigger", "KnowledgeDomainService.updateNode"), node.getUpdatedBy());
        return node;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(Long nodeId) {
        KnowledgeNode node = new KnowledgeNode();
        node.setId(nodeId);
        node.setIsDeleted(1);
        nodeMapper.updateById(node);
        graphChangeSetService.requestChange("KNOWLEDGE_NODE", "KNOWLEDGE_NODE", nodeId, "DELETE",
                Map.of("trigger", "KnowledgeDomainService.deleteNode"), null);
    }

    @Override
    public List<KnowledgeNode> getChildNodes(Long parentId) {
        return nodeMapper.selectList(
                Wrappers.<KnowledgeNode>lambdaQuery()
                        .eq(KnowledgeNode::getParentId, parentId)
                        .eq(KnowledgeNode::getIsDeleted, 0)
                        .orderByAsc(KnowledgeNode::getSortOrder));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initDefaultDomains() {
        log.info("初始化默认知识领域");

        // 检查是否已有数据
        Long count = domainMapper.selectCount(
                Wrappers.<KnowledgeDomain>lambdaQuery().eq(KnowledgeDomain::getIsDeleted, 0));
        if (count > 0) {
            log.info("知识领域数据已存在，跳过初始化");
            return;
        }

        // 创建默认知识领域
        createDomain("PROGRAMMING", "编程基础", "Code2", "#3b82f6", 18, "Python核心编程能力，能写、能读、能调试");
        createDomain("BACKEND", "后端工程", "Server", "#8b5cf6", 14, "FastAPI + 数据库 + 缓存后端开发能力");
        createDomain("AI_BASIC", "AI基础理论", "Brain", "#06b6d4", 10, "ML/DL/Transformer理论基础");
        createDomain("LLM", "LLM技术栈", "Sparkles", "#f59e0b", 12, "大模型训练、推理、Prompt工程");
        createDomain("RAG", "RAG技术栈", "Database", "#ec4899", 16, "检索增强生成全流程");
        createDomain("AGENT", "Agent技术栈", "Bot", "#10b981", 14, "AI Agent架构与实现");
        createDomain("DEVOPS", "工程化部署", "Cloud", "#f97316", 12, "容器化、CI/CD、监控运维");
        createDomain("DATA", "数据工程", "HardDrive", "#6366f1", 10, "数据采集、清洗、存储、处理");

        log.info("默认知识领域初始化完成");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initDefaultNodes() {
        log.info("初始化默认知识点");

        // 检查是否已有数据
        Long count = nodeMapper.selectCount(
                Wrappers.<KnowledgeNode>lambdaQuery().eq(KnowledgeNode::getIsDeleted, 0));
        if (count > 0) {
            log.info("知识点数据已存在，跳过初始化");
            return;
        }

        // 获取领域ID
        KnowledgeDomain programming = getDomainByCode("PROGRAMMING");
        KnowledgeDomain backend = getDomainByCode("BACKEND");
        KnowledgeDomain aiBasic = getDomainByCode("AI_BASIC");
        KnowledgeDomain llm = getDomainByCode("LLM");

        if (programming != null) {
            initProgrammingNodes(programming.getId());
        }
        if (backend != null) {
            initBackendNodes(backend.getId());
        }
        if (aiBasic != null) {
            initAiBasicNodes(aiBasic.getId());
        }
        if (llm != null) {
            initLlmNodes(llm.getId());
        }

        log.info("默认知识点初始化完成");
    }

    private void createDomain(String code, String name, String icon, String color, int weight, String description) {
        KnowledgeDomain domain = new KnowledgeDomain();
        domain.setDomainCode(code);
        domain.setDomainName(name);
        domain.setDomainIcon(icon);
        domain.setDomainColor(color);
        domain.setDomainWeight(weight);
        domain.setDomainDescription(description);
        domain.setSortOrder(weight);
        domain.setStatus("ACTIVE");
        domain.setIsDeleted(0);
        domain.setVersion(1);
        domainMapper.insert(domain);
    }

    private void initProgrammingNodes(Long domainId) {
        // 一级知识点
        Long dataTypeId = createNode(domainId, null, "DATA_TYPE", "数据类型", 1, "Python数据类型系统");
        Long functionId = createNode(domainId, null, "FUNCTION", "函数", 1, "Python函数编程");
        Long classId = createNode(domainId, null, "CLASS", "类", 1, "Python面向对象编程");
        Long decoratorId = createNode(domainId, null, "DECORATOR", "装饰器", 1, "Python装饰器");
        Long generatorId = createNode(domainId, null, "GENERATOR", "生成器", 1, "Python生成器");
        Long asyncId = createNode(domainId, null, "ASYNC", "异步编程", 1, "Python异步编程");

        // 二级知识点
        createNode(domainId, dataTypeId, "MUTABLE", "可变类型", 2, "list, dict, set");
        createNode(domainId, dataTypeId, "IMMUTABLE", "不可变类型", 2, "int, str, tuple");
        createNode(domainId, functionId, "ARGS", "参数类型", 2, "位置参数、默认参数、可变参数");
        createNode(domainId, functionId, "CLOSURE", "闭包", 2, "函数闭包和作用域");
        createNode(domainId, classId, "INHERITANCE", "继承", 2, "类继承和多态");
        createNode(domainId, classId, "MAGIC_METHOD", "魔术方法", 2, "__init__, __str__, __repr__");
    }

    private void initBackendNodes(Long domainId) {
        // 一级知识点
        Long fastapiId = createNode(domainId, null, "FASTAPI", "FastAPI", 1, "FastAPI框架");
        Long databaseId = createNode(domainId, null, "DATABASE", "数据库", 1, "数据库技术");
        Long redisId = createNode(domainId, null, "REDIS", "Redis缓存", 1, "Redis缓存技术");

        // 二级知识点
        createNode(domainId, fastapiId, "ROUTER", "路由", 2, "FastAPI路由系统");
        createNode(domainId, fastapiId, "MIDDLEWARE", "中间件", 2, "FastAPI中间件");
        createNode(domainId, fastapiId, "DEPENDENCY", "依赖注入", 2, "FastAPI依赖注入");
        createNode(domainId, databaseId, "MYSQL", "MySQL", 2, "MySQL数据库");
        createNode(domainId, databaseId, "POSTGRESQL", "PostgreSQL", 2, "PostgreSQL数据库");
        createNode(domainId, redisId, "STRING", "String", 2, "Redis字符串类型");
        createNode(domainId, redisId, "HASH", "Hash", 2, "Redis哈希类型");
    }

    private void initAiBasicNodes(Long domainId) {
        // 一级知识点
        Long mlId = createNode(domainId, null, "ML", "机器学习", 1, "机器学习基础");
        Long dlId = createNode(domainId, null, "DL", "深度学习", 1, "深度学习基础");
        Long transformerId = createNode(domainId, null, "TRANSFORMER", "Transformer", 1, "Transformer架构");

        // 二级知识点
        createNode(domainId, mlId, "SUPERVISED", "监督学习", 2, "分类和回归");
        createNode(domainId, mlId, "UNSUPERVISED", "无监督学习", 2, "聚类和降维");
        createNode(domainId, dlId, "CNN", "CNN", 2, "卷积神经网络");
        createNode(domainId, dlId, "RNN", "RNN", 2, "循环神经网络");
        createNode(domainId, transformerId, "ATTENTION", "注意力机制", 2, "Self-Attention");
        createNode(domainId, transformerId, "ENCODER", "编码器", 2, "Transformer编码器");
    }

    private void initLlmNodes(Long domainId) {
        // 一级知识点
        Long coreId = createNode(domainId, null, "LLM_CORE", "LLM核心", 1, "大语言模型核心概念");
        Long promptId = createNode(domainId, null, "PROMPT", "Prompt工程", 1, "提示词工程");
        Long finetuneId = createNode(domainId, null, "FINETUNE", "微调技术", 1, "模型微调技术");

        // 二级知识点
        createNode(domainId, coreId, "PRETRAIN", "预训练", 2, "模型预训练");
        createNode(domainId, coreId, "SFT", "SFT", 2, "监督微调");
        createNode(domainId, coreId, "RLHF", "RLHF", 2, "人类反馈强化学习");
        createNode(domainId, promptId, "ZERO_SHOT", "Zero Shot", 2, "零样本提示");
        createNode(domainId, promptId, "FEW_SHOT", "Few Shot", 2, "少样本提示");
        createNode(domainId, promptId, "COT", "Chain of Thought", 2, "思维链提示");
        createNode(domainId, finetuneId, "LORA", "LoRA", 2, "低秩适应");
        createNode(domainId, finetuneId, "QLORA", "QLoRA", 2, "量化LoRA");
    }

    private Long createNode(Long domainId, Long parentId, String code, String name, int level, String description) {
        KnowledgeNode node = new KnowledgeNode();
        node.setDomainId(domainId);
        node.setParentId(parentId);
        node.setNodeCode(code);
        node.setNodeName(name);
        node.setNodeLevel(level);
        node.setNodeDescription(description);
        node.setSortOrder(level);
        node.setStatus("ACTIVE");
        node.setIsDeleted(0);
        node.setVersion(1);
        nodeMapper.insert(node);
        return node.getId();
    }
}
