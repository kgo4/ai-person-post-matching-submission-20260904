package com.example.matching.service.learning;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.matching.dto.learning.LearningResourceQueryDTO;
import com.example.matching.dto.learning.LearningResourceSaveDTO;
import com.example.matching.dto.learning.api.CoverImageUploadRequest;
import com.example.matching.entity.learning.LearningResource;

import java.util.List;

/**
 * 学习资源服务接口
 *
 * @author system
 */
public interface LearningResourceService {

    /**
     * 保存学习资源
     *
     * @param dto 保存DTO
     * @return 资源实体
     */
    LearningResource saveResource(LearningResourceSaveDTO dto);

    /**
     * 分页查询资源
     *
     * @param page 分页参数
     * @param query 查询条件
     * @return 分页结果
     */
    IPage<LearningResource> pageResources(Page<LearningResource> page, LearningResourceQueryDTO query);

    /**
     * 获取资源详情
     *
     * @param id 资源ID
     * @return 资源实体
     */
    LearningResource getResourceById(Long id);

    /**
     * 删除资源
     *
     * @param id 资源ID
     */
    void deleteResource(Long id);

    /**
     * 更新资源状态（启用/禁用）
     *
     * @param id 资源ID
     * @param status 状态：0禁用，1启用
     */
    void updateStatus(Long id, Integer status);

    /**
     * 批量更新资源状态
     *
     * @param ids 资源ID列表
     * @param status 状态：0禁用，1启用
     */
    void batchUpdateStatus(List<Long> ids, Integer status);

    /**
     * 批量删除资源
     *
     * @param ids 资源ID列表
     */
    void batchDelete(List<Long> ids);

    /**
     * 上传资源封面图片
     *
     * @param request 封面图片内容（字节 + MIME 类型，JPG/PNG/GIF/WebP）
     * @return 可访问的封面URL（/uploads/learning-covers/xxx）
     */
    String uploadCover(CoverImageUploadRequest request);
}
