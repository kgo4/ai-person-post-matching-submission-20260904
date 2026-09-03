package com.example.matching.service.post;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.entity.post.PostModelVersion;
import com.example.matching.entity.post.PostModelVersionItem;

import java.math.BigDecimal;
import java.util.List;

/**
 * 岗位能力模型版本管理服务接口
 */
public interface PostModelVersionService extends IService<PostModelVersion> {

    /**
     * 创建草稿版本
     *
     * @param postId      岗位ID
     * @param sourceType  来源类型
     * @param description 版本说明
     * @return 创建的版本
     */
    PostModelVersion createDraft(Long postId, String sourceType, String description);

    /**
     * 保存版本明细（能力项配置）
     *
     * @param versionId 版本ID
     * @param items     能力项列表
     */
    void saveVersionItems(Long versionId, List<PostModelVersionItem> items);

    /**
     * Atomically add one item to an editable version's summary.
     *
     * @return {@code true} when the version was still editable and the summary was updated
     */
    boolean incrementStatisticsForBinding(Long versionId, BigDecimal itemWeight);

    /**
     * 发布版本
     * <p>
     * 将版本明细写入 post_ability_model 表，调用 batchConfig 生效。
     * 同时将其他草稿版本归档。
     *
     * @param versionId 版本ID
     */
    void publishVersion(Long versionId);

    /**
     * 回滚到指定版本
     * <p>
     * 将指定版本重新发布到 post_ability_model 表。
     *
     * @param versionId 版本ID
     */
    void rollbackToVersion(Long versionId);

    /**
     * 获取岗位的所有版本列表
     *
     * @param postId 岗位ID
     * @return 版本列表
     */
    List<PostModelVersion> listVersions(Long postId);

    /**
     * 获取版本详情（含明细）
     *
     * @param versionId 版本ID
     * @return 版本详情
     */
    PostModelVersion getVersionDetail(Long versionId);

    /**
     * 获取版本的明细列表
     *
     * @param versionId 版本ID
     * @return 明细列表
     */
    List<PostModelVersionItem> getVersionItems(Long versionId);

    /**
     * 删除草稿版本
     *
     * @param versionId 版本ID
     */
    void deleteDraft(Long versionId);
}
