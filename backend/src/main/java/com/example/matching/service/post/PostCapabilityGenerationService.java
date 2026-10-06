package com.example.matching.service.post;

import com.example.matching.dto.post.AbilityExtractResultDTO;
import com.example.matching.dto.post.JdAbilityItemDTO;
import com.example.matching.dto.post.PostAnalysisResultDTO;
import com.example.matching.entity.system.AbilityTag;

import java.util.List;
import java.util.Map;

/**
 * 统一岗位能力生成服务接口
 * <p>
 * 接收三类输入（JD文本、Excel解析岗位对象、新兴岗位描述），
 * 统一调用AI提取能力需求并与标签库匹配，输出岗位能力模型草稿。
 * <p>
 * 这是岗位能力提取的核心服务，被以下场景共用：
 * - 单条JD分析（JdAbilityExtractService）
 * - Excel批量导入分析（PostExcelAiImportService）
 * - 新兴岗位定义（PostEmergingPostService）
 */
public interface PostCapabilityGenerationService {

    /**
     * 从岗位文本中AI分析提取能力项（核心方法）
     * <p>
     * 流程：
     * 1. 查询系统已有标签传入prompt供AI参考
     * 2. 调用AI分析文本，提取能力需求
     * 3. 将AI返回的能力名称与已有标签做向量相似度匹配
     * 4. 返回分析结果（含匹配状态标记）
     *
     * @param postName  岗位名称
     * @param postText  岗位描述文本（可以是JD全文、职责+要求拼接等）
     * @return 能力项列表（含匹配状态）
     */
    List<JdAbilityItemDTO> analyzePostText(String postName, String postText);

    /**
     * 从岗位文本中AI分析提取能力项（带Harness来源上下文）
     * <p>
     * 在 analyzePostText 基础上，允许调用方指定 Harness 审计所需的来源信息，
     * 确保 Harness 判定日志中 sourceType/sourceRefId/sourceRefs 准确反映实际业务来源。
     *
     * @param postName          岗位名称
     * @param postText          岗位描述文本
     * @param harnessSourceType Harness claim sourceType 覆盖（如 "JD_IMPORT"、"POST_EVOLUTION_TASK"）
     * @param sourceRefId       来源关联ID（如 JD 任务 ID、演化任务 ID）
     * @param harnessSourceRefs 额外来源引用列表
     * @return 能力项列表（含匹配状态）
     */
    default List<JdAbilityItemDTO> analyzePostText(String postName, String postText,
                                                    String harnessSourceType, Long sourceRefId,
                                                    List<String> harnessSourceRefs) {
        // 默认回退到无来源上下文的版本
        return analyzePostText(postName, postText);
    }

    /**
     * 从岗位文本中AI分析提取能力项（带摘要，单次 Agent 调用）
     * <p>
     * 与 5 参 {@link #analyzePostText(String, String, String, Long, List)} 共享同一条
     * Agent 提取链路（PostCapabilityExtractionSupport -> PostAbilityAgentService），
     * 额外返回 Agent 生成的岗位摘要，避免调用方为摘要发起第二次 LLM 调用。
     * 旧入口（JdAbilityExtractServiceImpl）应使用本方法。
     *
     * @param postName          岗位名称
     * @param postText          岗位描述文本
     * @param harnessSourceType Harness claim sourceType 覆盖（如 "JD_IMPORT"）
     * @param sourceRefId       来源关联ID（如 JD 任务 ID）
     * @param harnessSourceRefs 额外来源引用列表
     * @return 能力项列表 + 岗位摘要
     */
    PostAbilityAnalysisResult analyzePostTextWithResult(String postName, String postText,
                                                        String harnessSourceType, Long sourceRefId,
                                                        List<String> harnessSourceRefs);

    /**
     * 市场JD专用能力提取（延迟准入模式）
     * <p>
     * 与 5 参 {@link #analyzePostText(String, String, String, Long, List)} 的区别：
     * <ol>
     *   <li>接收<strong>已清洗文本</strong>，内部不得再调用 {@code cleanAndDetect}；
     *       数据清洗由 MarketJdImportServiceImpl 完成并传入 cleanedText</li>
     *       幻觉防护由 MarketJdCapabilityAdmissionService 以批量
     *       {@code AiTrustHarnessService.verifyBatch} 方式在事务外延迟执行</li>
     *   <li>claim sourceType 固定为 {@code MARKET_JD}</li>
     * </ol>
     * 本方法仍执行 Agent 提取、schema/grounding 校验、正式标签匹配与证据字段端到端保留。
     *
     * @param postName        岗位名称（已清洗）
     * @param cleanedPostText 已清洗的岗位文本
     * @param sourceRefId     市场JD ID（market_jd_data.id）
     * @param sourceRefs      来源引用列表（source:MARKET_JD:&lt;jdId&gt;）
     * @return 能力项列表（含证据字段与匹配状态）
     */
    List<JdAbilityItemDTO> analyzeMarketJdText(String postName, String cleanedPostText,
                                               Long sourceRefId, List<String> sourceRefs);

    /**
     * 岗位分析结果：能力项 + 摘要（同一 Agent 调用的两个产物）
     */
    record PostAbilityAnalysisResult(List<JdAbilityItemDTO> items, String summary) {
    }

    /**
     * 从岗位文本中AI分析提取能力项（双轨输出版本）
     * <p>
     * 返回两类结果：
     * 1. mappedAbilities: 匹配到正式标签库的能力（可直接进入岗位模型）
     * 2. candidateAbilities: 未匹配的新能力（进入候选标签池审核）
     *
     * @param postName  岗位名称
     * @param postText  岗位描述文本
     * @param sourceType 来源类型（JD_IMPORT, EXCEL_IMPORT等）
     * @param sourceRefId 来源关联ID
     * @return 双轨输出结果
     */
    AbilityExtractResultDTO analyzePostTextDualTrack(String postName, String postText, String sourceType, Long sourceRefId);

    /**
     * 从岗位文本中AI分析提取完整结构化结果（增强版）
     * <p>
     * 在 analyzePostText 基础上，额外返回：
     * - 核心职责列表
     * - 必备技能列表
     * - 加分技能列表
     * - 典型行业应用场景
     *
     * @param postName  岗位名称
     * @param postText  岗位描述文本
     * @return 完整分析结果（含结构化岗位定义和能力项列表）
     */
    PostAnalysisResultDTO analyzePostTextFull(String postName, String postText);

    /**
     * 将AI建议的能力名称与已有标签做匹配
     * <p>
     * 匹配策略（按优先级）：
     * 1. 精确名称匹配
     * 2. 别名匹配（通过ability_tag_alias表）
     * 3. 向量相似度匹配（高阈值0.85 -> MATCHED，低阈值0.6 -> SIMILAR）
     * 4. 未命中 -> NEW
     *
     * @param item         AI建议的能力项
     * @param existingTags 系统已有标签列表
     */
    void matchWithExistingTags(JdAbilityItemDTO item, List<AbilityTag> existingTags);

    /**
     * 确认并应用能力项到岗位能力模型
     * <p>
     * 将用户确认后的能力项列表：
     * 1. 匹配到已有标签的直接使用tagId
     * 2. 标记为新标签的自动创建
     * 3. 批量写入post_ability_model表
     *
     * @param postId 岗位ID
     * @param items  用户确认的能力项列表
     */
    void applyAbilityItemsToPost(Long postId, List<JdAbilityItemDTO> items);

    /**
     * 确认并应用能力项到岗位能力模型（使用预加载的标签Map）
     * <p>
     * 重载版本，接受预加载的标签名称Map，避免批量导入时每个NEW标签都查DB。
     *
     * @param postId      岗位ID
     * @param items       用户确认的能力项列表
     * @param tagNameMap  预加载的标签名称Map（tagName -> AbilityTag）
     */
    void applyAbilityItemsToPost(Long postId, List<JdAbilityItemDTO> items, Map<String, AbilityTag> tagNameMap);
}
