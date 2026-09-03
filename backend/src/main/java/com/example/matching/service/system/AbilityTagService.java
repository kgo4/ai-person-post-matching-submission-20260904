package com.example.matching.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.system.AbilityTagSaveDTO;
import com.example.matching.dto.system.TagAdmissionContext;
import com.example.matching.dto.system.TagAdmissionResult;
import com.example.matching.entity.system.AbilityTag;
import com.example.matching.vo.system.AbilityTagTreeVO;

import java.util.List;

/**
 * 能力标签 服务接口
 */
public interface AbilityTagService extends IService<AbilityTag> {

    /** 保存标签，返回标签ID */
    Long saveTag(AbilityTagSaveDTO dto);

    /** 获取树形结构 */
    List<AbilityTagTreeVO> getTree();

    /** 根据分类查询 */
    List<AbilityTagTreeVO> getByCategory(String category);

    /** 分页查询（平铺） */
    IPage<AbilityTag> pageTags(IPage<AbilityTag> page, String keyword, String category);

    /** 修改状态 */
    void updateStatus(Long id, Integer status);

    /** 删除标签（清除缓存） */
    void deleteTag(Long id);

    /**
     * 按名称精确查找能力标签
     *
     * @param tagName 标签名称
     * @return 匹配的标签，不存在则返回null
     */
    AbilityTag findByName(String tagName);

    /**
     * 按名称查找或创建能力标签（用于AI分析结果确认时自动创建新标签）
     *
     * @param tagName     标签名称
     * @param tagCategory 标签分类：TECHNICAL/SOFT/BUSINESS
     * @param sourceType  来源标记：AI_JD / AI_RESUME
     * @return 已有或新创建的标签
     */
    AbilityTag findOrCreateByName(String tagName, String tagCategory, String sourceType);

    /**
     * 查找语义相似的标签（使用向量相似度）
     *
     * @param tagName   待查找的标签名称
     * @param threshold 相似度阈值（0-1），默认0.8
     * @return 相似标签列表
     */
    List<AbilityTag> findSimilarTags(String tagName, double threshold);

    /**
     * 查找语义相似标签或创建新标签
     * <p>
     * 先精确匹配，再别名匹配，最后向量相似度匹配。
     * 如果都没有匹配，则创建新标签并生成向量。
     *
     * @param tagName     标签名称
     * @param tagCategory 标签分类
     * @param sourceType  来源标记
     * @return 匹配到的或新创建的标签
     */
    AbilityTag findSimilarTagOrCreate(String tagName, String tagCategory, String sourceType);

    /**
     * 查找语义相似标签或创建新标签（带幻觉防护）
     * <p>
     * 先精确匹配，再别名匹配，最后向量相似度匹配。
     * 如果都没有匹配，则调用幻觉防护服务验证后决定是否创建新标签。
     *
     * @param tagName     标签名称
     * @param tagCategory 标签分类
     * @param sourceType  来源标记
     * @param jdText      JD文本或简历内容（用于幻觉防护验证）
     * @return 匹配到的或新创建的标签，如果被幻觉防护阻止则返回null
     */
    AbilityTag findSimilarTagOrCreate(String tagName, String tagCategory, String sourceType, String jdText);

    /**
     * 查找语义相似标签或创建新标签（带幻觉防护+Harness来源追踪）
     * <p>
     * 在 findSimilarTagOrCreate(tagName, tagCategory, sourceType, jdText) 基础上，
     * 额外传入 sourceRefId 用于 Harness 审计日志的来源关联。
     *
     * @param tagName     标签名称
     * @param tagCategory 标签分类
     * @param sourceType  来源标记（同时作为Harness sourceType）
     * @param jdText      JD文本或简历内容（用于幻觉防护验证）
     * @param sourceRefId 来源关联ID（如简历解析记录ID）
     * @return 匹配到的或新创建的标签，如果被Harness阻止则返回null
     */
    default AbilityTag findSimilarTagOrCreate(String tagName, String tagCategory, String sourceType,
                                               String jdText, Long sourceRefId) {
        // 默认回退到不带sourceRefId的版本
        return findSimilarTagOrCreate(tagName, tagCategory, sourceType, jdText);
    }

    /**
     * 合并标签（将源标签归并到目标标签）
     * <p>
     * 会将源标签名称保存为别名，并更新所有引用。
     *
     * @param sourceTagId 源标签ID（被归并的标签）
     * @param targetTagId 目标标签ID（保留的标签）
     */
    void mergeTags(Long sourceTagId, Long targetTagId);

    /**
     * 批量为现有标签生成向量嵌入
     *
     * @return 生成向量的标签数量
     */
    int batchGenerateVectors();

    /**
     * 批量初始化标签的canonical_tag_id
     * <p>
     * 将所有canonical_tag_id为null的标签设置为指向自身ID。
     * 用于历史数据迁移。
     *
     * @return 初始化的标签数量
     */
    int batchInitCanonicalTagIds();

    /**
     * 通过别名查找标签
     * <p>
     * 在ability_tag_alias表中查找匹配的别名，返回对应的主标签。
     *
     * @param aliasName 别名
     * @return 匹配的标签，不存在则返回null
     */
    AbilityTag findByAlias(String aliasName);

    /**
     * 创建正式能力标签（统一入口）
     * <p>
     * 包含完整的标签创建流程：
     * 1. 生成tagCode
     * 2. 生成向量嵌入
     * 3. 设置canonicalTagId
     * 4. 写入RAG知识库
     * <p>
     * 该方法应被以下场景共用：
     * - AI分析结果确认时创建新标签
     * - 候选标签审核通过时升级为正式标签
     * - 手动创建标签
     *
     * @param tagName     标签名称
     * @param tagCategory 标签分类：TECHNICAL/SOFT/BUSINESS
     * @param domain      领域分类
     * @param description 标签描述
     * @param sourceType  来源标记：AI_JD/AI_RESUME/AI_CANDIDATE/MANUAL
     * @return 创建的正式标签
     */
    AbilityTag createFormalTag(String tagName, String tagCategory, String domain, String description, String sourceType);

    AbilityTag createAssessableCapability(String tagName, Long parentDomainId, String tagCategory,
                                          String domain, String description, String sourceType);

    /**
     * 新标签准入决策（统一入口）
     * <p>
     * 所有业务服务（简历解析、AI测试、AI面试、PMS等）提取能力时，统一调用此方法。
     * 内部按以下顺序决策：
     * 1. 精确匹配已有标签
     * 2. 别名匹配已有标签
     * 3. 相似标签匹配，足够近就复用
     * 4. 都没有命中时，进入"新标签准入判断"
     * <p>
     * 新标签分三类处理：
     * - 自动入库：Harness PASS + 质量合格 + 有证据
     * - 进入候选池：Harness REVIEW 或证据不足
     * - 直接拒绝：Harness BLOCK 或质量不合格
     *
     * @param context 准入上下文（包含标签名、来源、证据等）
     * @return 准入结果（决策类型 + 标签/候选/拒绝原因）
     */
    TagAdmissionResult admitNewTag(TagAdmissionContext context);

    /**
     * 为标签添加别名
     * <p>
     * 如果别名已存在，则不重复添加。
     *
     * @param tagId      标签ID
     * @param aliasName  别名
     * @param sourceType 来源类型（可选）
     */
    void addAlias(Long tagId, String aliasName, String sourceType);
}
