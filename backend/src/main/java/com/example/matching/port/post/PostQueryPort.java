package com.example.matching.port.post;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 岗位查询端口 — 公开只读接口。
 */
public interface PostQueryPort {

    /** 岗位基本信息 DTO */
    record PostDTO(
            Long id,
            String postName,
            String postCode,
            String postLevel,
            Long departmentId,
            Integer status,
            String jobDescription
    ) {
        public static PostDTO from(com.example.matching.entity.post.PostPost p) {
            return new PostDTO(p.getId(), p.getPostName(), p.getPostCode(),
                    p.getPostLevel(), p.getDepartmentId(), p.getStatus(), p.getJobDescription());
        }
    }

    /** 岗位能力要求 DTO */
    record PostAbilityDTO(
            Long id,
            Long postId,
            Long tagId,
            Integer minRequiredLevel,
            BigDecimal weight,
            Integer isRequired,
            Integer isCore,
            String modelVersion,
            String remark,
            String abilityName,
            String techStack,
            String skillPointKey
    ) {
        public PostAbilityDTO(Long id, Long postId, Long tagId, Integer minRequiredLevel, BigDecimal weight,
                              Integer isRequired, Integer isCore, String modelVersion, String remark,
                              String abilityName) {
            this(id, postId, tagId, minRequiredLevel, weight, isRequired, isCore, modelVersion, remark,
                    abilityName, null, null);
        }

        public static PostAbilityDTO from(com.example.matching.entity.post.PostAbilityModel m) {
            return new PostAbilityDTO(m.getId(), m.getPostId(), m.getTagId(),
                    m.getMinRequiredLevel(), m.getWeight(), m.getIsRequired(), m.getIsCore(),
                    m.getModelVersion(), m.getRemark(), m.getAbilityName(), m.getTechStack(), m.getSkillPointKey());
        }
    }

    PostDTO getPostById(Long postId);

    List<PostDTO> batchGetPosts(List<Long> postIds);

    List<PostAbilityDTO> listRequirementsByPostId(Long postId);

    List<PostAbilityDTO> listRequirementsByPostIds(Set<Long> postIds);

    /** 按 ID 查询单条岗位能力建模记录，未找到返回 null */
    PostAbilityDTO getPostAbilityModelById(Long modelId);

    /** 按岗位+标签查询单条岗位能力建模记录，未找到返回 null */
    PostAbilityDTO getRequirementByPostAndTag(Long postId, Long tagId);

    /** 按标签查询岗位能力建模记录 */
    List<PostAbilityDTO> listRequirementsByTagId(Long tagId);

    /** 分页列出活跃的岗位（用于批量回填） */
    List<PostDTO> listActivePosts(int limit);

    /** 分页列出活跃的岗位能力模型 */
    List<PostAbilityDTO> listActivePostAbilityModels(int limit);

    /** 分页列出尚未关联系统标签的岗位能力模型，用于标签库旁路回填。 */
    List<PostAbilityDTO> listUntaggedPostAbilityModels(int limit);

    /** 岗位原型摘要 DTO */
    record PostPrototypeDTO(
            Long id,
            String prototypeName,
            String industry,
            String category,
            String description
    ) {
        public static PostPrototypeDTO from(com.example.matching.entity.post.PostPrototype p) {
            return new PostPrototypeDTO(p.getId(), p.getPrototypeName(),
                    p.getIndustry(), p.getCategory(), p.getDescription());
        }
    }

    /** 分页列出活跃的岗位原型 */
    List<PostPrototypeDTO> listActivePrototypes(int limit);

    /** 列出全部岗位原型标签关联 */
    List<PostPrototypeTagDTO> listAllPrototypeTags();

    /** 岗位原型标签关联 DTO */
    record PostPrototypeTagDTO(
            Long id,
            Long prototypeId,
            Long tagId
    ) {
        public static PostPrototypeTagDTO from(com.example.matching.entity.post.PostPrototypeTag t) {
            return new PostPrototypeTagDTO(t.getId(), t.getPrototypeId(), t.getTagId());
        }
    }

    /** JD导入任务 DTO */
    record JdImportTaskDTO(
            Long id,
            Long postId,
            String jdRawText,
            String jdSourceType
    ) {
        public static JdImportTaskDTO from(com.example.matching.entity.post.JdImportTask t) {
            return new JdImportTaskDTO(t.getId(), t.getPostId(), t.getJdRawText(), t.getJdSourceType());
        }
    }

    /** 分页列出已分析完成的JD导入任务（analysisStatus=2） */
    List<JdImportTaskDTO> listAnalyzedJdImportTasks(int limit);

    /** 全量岗位数量（报表统计用） */
    long countAllPosts();

    /** 全量岗位（报表统计用，仅需 id/postName） */
    List<PostDTO> listAllPosts();

    /** 全量岗位能力模型（报表统计用） */
    List<PostAbilityDTO> listAllPostAbilityModels();

    /** 按岗位统计能力模型数量 */
    long countRequirementsByPostId(Long postId);

    /** 按标签统计引用的岗位能力模型数量(isDeleted=0) */
    long countRequirementsByTagId(Long tagId);

    /** 按标签统计关联的岗位原型数量 */
    long countPrototypeTagsByTagId(Long tagId);
}
