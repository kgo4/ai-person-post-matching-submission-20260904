package com.example.matching.service.post;

import com.example.matching.dto.post.api.UnmatchedAbilityDTO;
import com.example.matching.entity.post.PostModelUnmatchedAbility;

import java.util.List;

/**
 * 岗位模型未匹配能力标签服务（M-07）
 * <p>
 * AI 提取能力无法匹配已有标签时，持久化并展示未匹配项，
 * 支持管理员绑定已有标签或忽略。
 */
public interface PostModelUnmatchedAbilityService {

    /**
     * 按版本查询未匹配能力列表
     *
     * @param versionId 版本ID
     * @return 未匹配能力列表
     */
    List<PostModelUnmatchedAbility> listByVersionId(Long versionId);

    /**
     * 按主键查询未匹配能力
     *
     * @param id 未匹配记录ID
     * @return 未匹配能力或 null
     */
    PostModelUnmatchedAbility getById(Long id);

    /**
     * 按版本批量保存未匹配能力（生成流程调用）
     *
     * @param versionId 版本ID
     * @param abilities 未匹配能力列表
     */
    void saveAll(Long versionId, List<PostModelUnmatchedAbility> abilities);

    /**
     * 更新标签候选ID（生成流程创建候选后回写）
     *
     * @param id          未匹配记录ID
     * @param candidateId 标签候选ID
     */
    void updateCandidateId(Long id, Long candidateId);

    /**
     * 绑定未匹配能力到已有标签：
     * <ol>
     *   <li>校验标签存在且启用</li>
     *   <li>生成版本明细</li>
     *   <li>更新未匹配记录状态为 TAG_BOUND</li>
     *   <li>发布岗位模型变更事件</li>
     * </ol>
     *
     * @param id    未匹配记录ID
     * @param tagId 正式标签ID
     */
    void bind(Long id, Long tagId);

    /**
     * 忽略未匹配能力（状态置为 IGNORED）
     *
     * @param id 未匹配记录ID
     */
    void ignore(Long id);

    /**
     * 将实体转换为响应 DTO（含推荐标签信息）
     */
    UnmatchedAbilityDTO toDto(PostModelUnmatchedAbility ability);
}
