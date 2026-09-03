package com.example.matching.service.post;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.matching.dto.post.PostAbilityModelConfigDTO;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.vo.post.PostAbilityModelVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 岗位能力模型 服务接口
 */
public interface PostAbilityModelService extends IService<PostAbilityModel> {

    void saveConfig(PostAbilityModelConfigDTO dto);

    PostAbilityModelVO getPostAbilityModel(Long postId);

    List<PostAbilityModel> listByPostId(Long postId);

    /** 查询指定岗位中至少配置了一项能力模型的岗位 ID。 */
    Set<Long> listConfiguredPostIds(List<Long> postIds);

    void batchConfig(List<PostAbilityModelConfigDTO> list);

    /**
     * 计算岗位模型质量评分
     *
     * @param postId 岗位ID
     * @return 质量评分（0-100）
     */
    BigDecimal calculateQualityScore(Long postId);

    void deleteModel(Long modelId);
}
