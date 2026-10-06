package com.example.matching.service.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.matching.common.exception.BusinessException;
import com.example.matching.common.exception.ErrorCodeEnum;
import com.example.matching.config.RedisCacheNames;
import com.example.matching.dto.system.AbilityTagSaveDTO;
import com.example.matching.dto.system.TagAdmissionResult;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.entity.system.AbilityTagAlias;
import com.example.matching.event.AbilityChangeEvent;
import com.example.matching.event.GraphChangeRequestedEvent;
import com.example.matching.event.PostModelChangeEvent;
import com.example.matching.mapper.employee.EmpAbilityMapper;
import com.example.matching.mapper.post.PostAbilityModelMapper;
import com.example.matching.mapper.system.AbilityTagAliasMapper;
import com.example.matching.mapper.system.AbilityTagMapper;
import com.example.matching.service.system.AbilityTagService;
import com.example.matching.service.system.AbilityTagHierarchy;
import com.example.matching.service.system.AbilityTagVectorOperations;
import com.example.matching.service.system.TaxonomyClassifyResult;
import com.example.matching.dto.system.TagAdmissionContext;
import com.example.matching.vo.system.AbilityTagTreeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 能力标签管理服务：标签 CRUD、树、归并、向量批处理。
 * <p>
 * 准入管线 / 语义相似查找 / 标签创建已拆分到 {@link AbilityTagAdmissionEngine}，
 * 本类保持公共接口不变并委托给引擎。
 */
@Slf4j
@Service
public class AbilityTagServiceImpl extends ServiceImpl<AbilityTagMapper, AbilityTag> implements AbilityTagService {

    private final AbilityTagAliasMapper tagAliasMapper;
    private final PostAbilityModelMapper postAbilityModelMapper;
    private final EmpAbilityMapper empAbilityMapper;
    private final AbilityTagVectorOperations vectorOperations;
    private final ApplicationEventPublisher eventPublisher;
    private final AbilityTagAdmissionEngine admissionEngine;
    private final AbilityTagTaxonomyClassifier taxonomyClassifier;

    public AbilityTagServiceImpl(AbilityTagAliasMapper tagAliasMapper,
                                 PostAbilityModelMapper postAbilityModelMapper,
                                 EmpAbilityMapper empAbilityMapper,
                                 AbilityTagVectorOperations vectorOperations,
                                 ApplicationEventPublisher eventPublisher,
                                 AbilityTagAdmissionEngine admissionEngine,
                                 AbilityTagTaxonomyClassifier taxonomyClassifier) {
        this.tagAliasMapper = tagAliasMapper;
        this.postAbilityModelMapper = postAbilityModelMapper;
        this.empAbilityMapper = empAbilityMapper;
        this.vectorOperations = vectorOperations;
        this.eventPublisher = eventPublisher;
        this.admissionEngine = admissionEngine;
        this.taxonomyClassifier = taxonomyClassifier;
    }

    @Override
    @Cacheable(cacheNames = RedisCacheNames.ABILITY_TAG_INFO, key = "#id", unless = "#result == null")
    public AbilityTag getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    public boolean updateById(AbilityTag entity) {
        return super.updateById(entity);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {RedisCacheNames.ABILITY_TAG_TREE, RedisCacheNames.ABILITY_TAG_CATEGORY_LIST,
            RedisCacheNames.EVOLUTION_ACTIVE_ABILITY_TAGS, RedisCacheNames.ABILITY_TAG_INFO}, allEntries = true)
    public Long saveTag(AbilityTagSaveDTO dto) {
        AbilityTag persistedTag;
        if (dto.getId() == null) {
            long count = count(Wrappers.<AbilityTag>lambdaQuery().eq(AbilityTag::getTagCode, dto.getTagCode()));
            if (count > 0) {
                throw new BusinessException(ErrorCodeEnum.ABILITY_TAG_CODE_DUPLICATE);
            }
            AbilityTag tag = new AbilityTag();
            BeanUtils.copyProperties(dto, tag);
            // 系统标签库为平铺能力索引：新建标签一律不挂层级，归层由系统内部按需自动完成。
            tag.setParentId(0L);
            tag.setTagLevel(AbilityTagHierarchy.ROOT_LEVEL);
            validateHierarchy(tag);
            if (tag.getStatus() == null) {
                tag.setStatus(1);
            }
            save(tag);
            // 新标签的canonical_tag_id指向自身
            tag.setCanonicalTagId(tag.getId());
            updateById(tag);
            persistedTag = tag;
        } else {
            AbilityTag tag = getById(dto.getId());
            if (tag == null) {
                throw new BusinessException(ErrorCodeEnum.ABILITY_TAG_NOT_FOUND);
            }
            // 编辑保留原有层级关系，不允许通过编辑面板重新挂载层级。
            BeanUtils.copyProperties(dto, tag, "tagCode", "parentId", "tagLevel");
            validateHierarchy(tag);
            updateById(tag);
            persistedTag = tag;
        }
        eventPublisher.publishEvent(new GraphChangeRequestedEvent("ABILITY_TAG", "ABILITY_TAG", persistedTag.getId(),
                persistedTag.getStatus() != null && persistedTag.getStatus() == 1 ? "UPSERT" : "DISABLE",
                Map.of("trigger", "AbilityTagService.saveTag"), null));
        return persistedTag.getId();
    }

    private void validateHierarchy(AbilityTag tag) {
        if (tag.getTagLevel() == null) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "标签层级不能为空");
        }
        if (tag.getTagLevel() == AbilityTagHierarchy.ROOT_LEVEL) {
            if (tag.getParentId() == null || tag.getParentId() != 0L) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "L0分类根节点必须使用 parentId=0");
            }
            return;
        }
        if (tag.getParentId() == null || tag.getParentId() == 0L) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "L1/L2标签必须挂载到父节点");
        }
        AbilityTag parent = getById(tag.getParentId());
        if (parent == null || parent.getStatus() == null || parent.getStatus() != 1
                || parent.getTagLevel() == null || parent.getTagLevel() != tag.getTagLevel() - 1
                || !java.util.Objects.equals(parent.getTagCategory(), tag.getTagCategory())) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "父标签必须是同分类的上一级启用节点");
        }
    }

    @Override
    @Cacheable(cacheNames = RedisCacheNames.ABILITY_TAG_TREE, key = "'all'", sync = true)
    public List<AbilityTagTreeVO> getTree() {
        List<AbilityTag> all = list(Wrappers.<AbilityTag>lambdaQuery()
                .eq(AbilityTag::getStatus, 1)
                .orderByAsc(AbilityTag::getSortOrder));
        return buildTree(all, 0L);
    }

    @Override
    @Cacheable(cacheNames = RedisCacheNames.ABILITY_TAG_CATEGORY_LIST, key = "'category:' + #category", sync = true)
    public List<AbilityTagTreeVO> getByCategory(String category) {
        List<AbilityTag> list = list(Wrappers.<AbilityTag>lambdaQuery()
                .eq(AbilityTag::getTagCategory, category)
                .eq(AbilityTag::getStatus, 1)
                .orderByAsc(AbilityTag::getSortOrder));
        return buildTree(list, 0L);
    }

    @Override
    public IPage<AbilityTag> pageTags(IPage<AbilityTag> page, String keyword, String category) {
        LambdaQueryWrapper<AbilityTag> wrapper = Wrappers.<AbilityTag>lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(AbilityTag::getTagCode, keyword).or().like(AbilityTag::getTagName, keyword));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(AbilityTag::getTagCategory, category);
        }
        wrapper.orderByAsc(AbilityTag::getSortOrder);
        return page(page, wrapper);
    }

    @Override
    @CacheEvict(cacheNames = {RedisCacheNames.ABILITY_TAG_TREE, RedisCacheNames.ABILITY_TAG_CATEGORY_LIST,
            RedisCacheNames.EVOLUTION_ACTIVE_ABILITY_TAGS, RedisCacheNames.ABILITY_TAG_INFO}, allEntries = true)
    public void updateStatus(Long id, Integer status) {
        AbilityTag tag = getById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCodeEnum.ABILITY_TAG_NOT_FOUND);
        }
        tag.setStatus(status);
        updateById(tag);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {RedisCacheNames.ABILITY_TAG_TREE, RedisCacheNames.ABILITY_TAG_CATEGORY_LIST, RedisCacheNames.EVOLUTION_ACTIVE_ABILITY_TAGS, RedisCacheNames.ABILITY_TAG_INFO}, allEntries = true)
    public void deleteTag(Long id) {
        AbilityTag tag = getById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCodeEnum.ABILITY_TAG_NOT_FOUND);
        }
        // 递归软删除整棵子树，保留岗位/人员历史引用，避免物理删除造成孤儿数据。
        List<Long> subtreeIds = collectSubtreeIds(id);
        for (Long tagId : subtreeIds) {
            AbilityTag node = getById(tagId);
            if (node != null) {
                node.setStatus(0);
                updateById(node);
                removeById(tagId);
                eventPublisher.publishEvent(new GraphChangeRequestedEvent(
                        "ABILITY_TAG", "ABILITY_TAG", tagId, "DISABLE",
                        Map.of("trigger", "AbilityTagService.deleteTag", "rootId", id), null));
            }
        }
        log.info("递归删除标签子树: rootId={}, rootName={}, affected={}", id, tag.getTagName(), subtreeIds.size());
    }

    private List<Long> collectSubtreeIds(Long rootId) {
        List<Long> result = new ArrayList<>();
        List<Long> frontier = new ArrayList<>();
        frontier.add(rootId);
        while (!frontier.isEmpty()) {
            result.addAll(frontier);
            List<AbilityTag> children = list(Wrappers.<AbilityTag>lambdaQuery()
                    .in(AbilityTag::getParentId, frontier));
            frontier = children.stream().map(AbilityTag::getId).filter(java.util.Objects::nonNull).toList();
        }
        return result;
    }

    @Override
    public AbilityTag findByName(String tagName) {
        return admissionEngine.findByName(tagName);
    }

    @Override
    @Transactional
    public AbilityTag findOrCreateByName(String tagName, String tagCategory, String sourceType) {
        return admissionEngine.findOrCreateByName(tagName, tagCategory, sourceType);
    }

    @Override
    public List<AbilityTag> findSimilarTags(String tagName, double threshold) {
        return admissionEngine.findSimilarTags(tagName, threshold);
    }

    @Override
    @Transactional
    public AbilityTag findSimilarTagOrCreate(String tagName, String tagCategory, String sourceType) {
        return admissionEngine.findSimilarTagOrCreate(tagName, tagCategory, sourceType);
    }

    @Override
    @Transactional
    public AbilityTag findSimilarTagOrCreate(String tagName, String tagCategory, String sourceType, String jdText) {
        return admissionEngine.findSimilarTagOrCreate(tagName, tagCategory, sourceType, jdText);
    }

    @Override
    @Transactional
    public AbilityTag findSimilarTagOrCreate(String tagName, String tagCategory, String sourceType,
                                             String jdText, Long sourceRefId) {
        return admissionEngine.findSimilarTagOrCreate(tagName, tagCategory, sourceType, jdText, sourceRefId);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {RedisCacheNames.ABILITY_TAG_TREE, RedisCacheNames.ABILITY_TAG_CATEGORY_LIST,
            RedisCacheNames.EVOLUTION_ACTIVE_ABILITY_TAGS, RedisCacheNames.ABILITY_TAG_INFO,
            RedisCacheNames.TAG_CANONICAL}, allEntries = true)
    public AbilityTag createFormalTag(String tagName, String tagCategory, String domain, String description, String sourceType) {
        return admissionEngine.createFormalTag(tagName, tagCategory, domain, description, sourceType);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {RedisCacheNames.ABILITY_TAG_TREE, RedisCacheNames.ABILITY_TAG_CATEGORY_LIST,
            RedisCacheNames.EVOLUTION_ACTIVE_ABILITY_TAGS, RedisCacheNames.ABILITY_TAG_INFO,
            RedisCacheNames.TAG_CANONICAL}, allEntries = true)
    public AbilityTag createAssessableCapability(String tagName, Long parentDomainId, String tagCategory,
                                                  String domain, String description, String sourceType) {
        AbilityTag tag = new AbilityTag();
        tag.setTagCode("CAP_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        tag.setTagName(tagName);
        if (parentDomainId != null && parentDomainId != 0L) {
            AbilityTag parent = getById(parentDomainId);
            if (parent == null || parent.getStatus() == null || parent.getStatus() != 1
                    || !Integer.valueOf(AbilityTagHierarchy.DOMAIN_LEVEL).equals(parent.getTagLevel())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Target domain must be an enabled L1 node");
            }
            String category = tagCategory != null ? tagCategory : parent.getTagCategory();
            if (!java.util.Objects.equals(category, parent.getTagCategory())) {
                throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "Target L1 domain category does not match the capability");
            }
            tag.setParentId(parentDomainId);
            tag.setTagCategory(category);
            tag.setDomain(domain != null ? domain : parent.getDomain());
            tag.setTagLevel(AbilityTagHierarchy.ASSESSABLE_LEVEL);
        } else {
            // 人工审核不再强制选挂载域：系统自动归层，归层失败降级为平铺标签，绝不阻断入库。
            applyAutoTaxonomy(tag, tagName, tagCategory, domain);
        }
        tag.setDescription(description);
        tag.setSortOrder(999);
        tag.setIsSystem(0);
        tag.setSourceType(sourceType != null ? sourceType : "MANUAL");
        tag.setStatus(1);
        save(tag);
        tag.setCanonicalTagId(tag.getId());
        updateById(tag);
        return tag;
    }

    /**
     * 自动归层：用规则/向量分类器尝试将新标签挂到匹配的 L1 能力域（L2），
     * 未命中时降级为平铺标签（parentId=0, tagLevel=0），保证标签永远能入库。
     */
    private void applyAutoTaxonomy(AbilityTag tag, String tagName, String tagCategory, String domain) {
        try {
            TaxonomyClassifyResult classify = taxonomyClassifier.classify(tagName);
            AbilityTag matched = classify != null ? classify.abilityTag() : null;
            if (matched != null && matched.getId() != null
                    && AbilityTagHierarchy.isAssessable(matched)
                    && matched.getParentId() != null && matched.getParentId() != 0L
                    && StringUtils.hasText(matched.getTagCategory())) {
                tag.setParentId(matched.getParentId());
                tag.setTagCategory(matched.getTagCategory());
                tag.setDomain(matched.getDomain() != null ? matched.getDomain() : domain);
                tag.setTagLevel(AbilityTagHierarchy.ASSESSABLE_LEVEL);
                return;
            }
        } catch (Exception e) {
            log.warn("候选标签自动归层失败，降级为平铺标签: tagName={}, error={}", tagName, e.getMessage());
        }
        tag.setParentId(0L);
        tag.setTagCategory(tagCategory != null ? tagCategory : "TECHNICAL");
        tag.setDomain(domain != null ? domain : "GENERAL");
        tag.setTagLevel(AbilityTagHierarchy.ROOT_LEVEL);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {RedisCacheNames.ABILITY_TAG_TREE, RedisCacheNames.ABILITY_TAG_CATEGORY_LIST,
            RedisCacheNames.EVOLUTION_ACTIVE_ABILITY_TAGS, RedisCacheNames.ABILITY_TAG_INFO,
            RedisCacheNames.TAG_CANONICAL}, allEntries = true)
    public void mergeTags(Long sourceTagId, Long targetTagId) {
        if (sourceTagId.equals(targetTagId)) {
            return;
        }

        AbilityTag sourceTag = getById(sourceTagId);
        AbilityTag targetTag = getById(targetTagId);
        if (sourceTag == null || targetTag == null) {
            throw new BusinessException(ErrorCodeEnum.ABILITY_TAG_NOT_FOUND);
        }

        // 获取target的标准标签ID
        Long targetCanonicalId = targetTag.getCanonicalTagId() != null ? targetTag.getCanonicalTagId() : targetTag.getId();

        // 1. 保存源标签名称为别名
        AbilityTagAlias alias = new AbilityTagAlias();
        alias.setTagId(targetTagId);
        alias.setAliasName(sourceTag.getTagName());
        tagAliasMapper.insert(alias);

        Set<Long> affectedPostIds = new HashSet<>();
        Set<Long> affectedEmpIds = new HashSet<>();

        // 2. 更新岗位能力模型引用
        List<PostAbilityModel> postModels = postAbilityModelMapper.selectList(
                Wrappers.<PostAbilityModel>lambdaQuery()
                        .eq(PostAbilityModel::getTagId, sourceTagId)
        );
        for (PostAbilityModel model : postModels) {
            affectedPostIds.add(model.getPostId());
            PostAbilityModel existing = postAbilityModelMapper.selectOne(
                    Wrappers.<PostAbilityModel>lambdaQuery()
                            .eq(PostAbilityModel::getPostId, model.getPostId())
                            .eq(PostAbilityModel::getTagId, targetTagId)
            );
            if (existing != null) {
                existing.setMinRequiredLevel(maxInt(existing.getMinRequiredLevel(), model.getMinRequiredLevel()));
                existing.setWeight(maxDecimal(existing.getWeight(), model.getWeight()));
                existing.setIsRequired(orInt(existing.getIsRequired(), model.getIsRequired()));
                existing.setIsCore(orInt(existing.getIsCore(), model.getIsCore()));
                existing.setRemark(mergeRemark(existing.getRemark(), model.getRemark(), "tag-merge"));
                postAbilityModelMapper.updateById(existing);
                postAbilityModelMapper.deleteById(model.getId());
            } else {
                model.setTagId(targetTagId);
                postAbilityModelMapper.updateById(model);
            }
        }

        // 3. 更新员工能力引用
        List<EmpAbility> empAbilities = empAbilityMapper.selectList(
                Wrappers.<EmpAbility>lambdaQuery()
                        .eq(EmpAbility::getTagId, sourceTagId)
        );
        for (EmpAbility ability : empAbilities) {
            affectedEmpIds.add(ability.getEmpId());
            EmpAbility existing = empAbilityMapper.selectOne(
                    Wrappers.<EmpAbility>lambdaQuery()
                            .eq(EmpAbility::getEmpId, ability.getEmpId())
                            .eq(EmpAbility::getTagId, targetTagId)
            );
            if (existing != null) {
                BigDecimal existingSourceWeight = existing.getSourceWeight();
                BigDecimal sourceSourceWeight = ability.getSourceWeight();
                existing.setMasteryLevel(maxInt(existing.getMasteryLevel(), ability.getMasteryLevel()));
                existing.setAbilityLevel(maxInt(existing.getAbilityLevel(), ability.getAbilityLevel()));
                existing.setSourceWeight(maxDecimal(existingSourceWeight, sourceSourceWeight));
                if (ability.getEvaluationDate() != null && (existing.getEvaluationDate() == null
                        || ability.getEvaluationDate().isAfter(existing.getEvaluationDate()))) {
                    existing.setEvaluationDate(ability.getEvaluationDate());
                }
                if (sourceSourceWeight != null
                        && (existingSourceWeight == null || sourceSourceWeight.compareTo(existingSourceWeight) > 0)
                        && StringUtils.hasText(ability.getEvaluationSource())) {
                    existing.setEvaluationSource(ability.getEvaluationSource());
                }
                existing.setRemark(mergeRemark(existing.getRemark(), ability.getRemark(), "tag-merge"));
                empAbilityMapper.updateById(existing);
                empAbilityMapper.deleteById(ability.getId());
            } else {
                ability.setTagId(targetTagId);
                empAbilityMapper.updateById(ability);
            }
        }

        // 4. 更新源标签的canonical_tag_id指向target的标准标签
        sourceTag.setCanonicalTagId(targetCanonicalId);
        sourceTag.setStatus(0);
        updateById(sourceTag);

        // 5. 发布变更事件，触发缓存、向量、图谱同步
        for (Long postId : affectedPostIds) {
            eventPublisher.publishEvent(new PostModelChangeEvent(this, "MODEL_CONFIG", postId));
        }
        for (Long empId : affectedEmpIds) {
            eventPublisher.publishEvent(new AbilityChangeEvent(this, "EMP_ABILITY", empId));
        }
        eventPublisher.publishEvent(new GraphChangeRequestedEvent(
                "ABILITY_TAG", "ABILITY_TAG", targetTagId, "UPSERT",
                Map.of("trigger", "AbilityTagService.mergeTags"), null));

        log.info("标签归并完成: source={}, target={}, targetCanonicalId={}, affectedPosts={}, affectedEmps={}",
                sourceTag.getTagName(), targetTag.getTagName(), targetCanonicalId,
                affectedPostIds.size(), affectedEmpIds.size());
    }

    @Override
    @Transactional
    public int batchGenerateVectors() {
        return vectorOperations.batchGenerateVectors();
    }

    @Override
    @Transactional
    public int batchInitCanonicalTagIds() {
        return vectorOperations.batchInitCanonicalTagIds();
    }

    @Override
    public AbilityTag findByAlias(String aliasName) {
        return admissionEngine.findByAlias(aliasName);
    }

    @Override
    @Transactional
    public TagAdmissionResult admitNewTag(TagAdmissionContext context) {
        return admissionEngine.admitNewTag(context);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {RedisCacheNames.ABILITY_TAG_TREE, RedisCacheNames.ABILITY_TAG_CATEGORY_LIST,
            RedisCacheNames.EVOLUTION_ACTIVE_ABILITY_TAGS, RedisCacheNames.ABILITY_TAG_INFO,
            RedisCacheNames.TAG_CANONICAL}, allEntries = true)
    public void addAlias(Long tagId, String aliasName, String sourceType) {
        if (tagId == null || !StringUtils.hasText(aliasName)) {
            return;
        }

        // 检查标签是否存在
        AbilityTag tag = getById(tagId);
        if (tag == null) {
            log.warn("添加别名失败：标签不存在: tagId={}", tagId);
            return;
        }

        // 调用内部方法保存别名
        admissionEngine.saveAlias(tagId, aliasName);
        log.info("为标签添加别名: tagId={}, tagName={}, alias={}, source={}",
                tagId, tag.getTagName(), aliasName, sourceType);
    }

    private static int maxInt(Integer a, Integer b) {
        int va = a != null ? a : 0;
        int vb = b != null ? b : 0;
        return Math.max(va, vb);
    }

    private static BigDecimal maxDecimal(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static int orInt(Integer a, Integer b) {
        boolean ba = a != null && a == 1;
        boolean bb = b != null && b == 1;
        return (ba || bb) ? 1 : 0;
    }

    private static String mergeRemark(String existing, String source, String trigger) {
        if (source == null || source.isBlank()) return existing;
        if (existing == null || existing.isBlank()) return source;
        return existing + "; " + trigger + ": " + source;
    }

    private List<AbilityTagTreeVO> buildTree(List<AbilityTag> list, Long parentId) {
        Map<Long, List<AbilityTag>> childrenMap = list.stream()
                .filter(t -> t.getParentId() != null)
                .collect(Collectors.groupingBy(AbilityTag::getParentId));

        return list.stream()
                .filter(t -> parentId.equals(t.getParentId()))
                .map(tag -> {
                    AbilityTagTreeVO vo = new AbilityTagTreeVO();
                    BeanUtils.copyProperties(tag, vo);
                    List<AbilityTag> children = childrenMap.get(tag.getId());
                    if (children != null && !children.isEmpty()) {
                        vo.setChildren(buildTree(children, tag.getId()));
                    } else {
                        vo.setChildren(new ArrayList<>());
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }
}
