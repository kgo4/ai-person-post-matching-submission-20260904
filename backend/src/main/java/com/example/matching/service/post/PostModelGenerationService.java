package com.example.matching.service.post;

import com.example.matching.entity.post.PostModelVersion;

/**
 * 岗位模型生成中心服务接口
 * <p>
 * 提供4个入口生成岗位能力模型：
 * 1. 从岗位原型生成
 * 2. 从JD智能生成
 * 3. Excel批量导入
 * 4. 复制已有岗位模型
 * <p>
 * 所有生成方式都会创建草稿版本，用户确认后发布生效。
 */
public interface PostModelGenerationService {

    /**
     * 从岗位原型生成能力模型草稿
     *
     * @param postId      岗位ID
     * @param prototypeId 原型ID
     * @param description 版本说明
     * @return 创建的草稿版本
     */
    PostModelVersion generateFromPrototype(Long postId, Long prototypeId, String description);

    /**
     * 从JD智能生成能力模型草稿
     *
     * @param postId      岗位ID
     * @param jdText      JD文本
     * @param description 版本说明
     * @return 创建的草稿版本
     */
    PostModelVersion generateFromJD(Long postId, String jdText, String description);

    /**
     * 从已有岗位复制能力模型草稿
     *
     * @param sourcePostId 源岗位ID
     * @param targetPostId 目标岗位ID
     * @param description  版本说明
     * @return 创建的草稿版本
     */
    PostModelVersion generateFromCopy(Long sourcePostId, Long targetPostId, String description);
}
