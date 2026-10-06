package com.example.matching.service.matching;

import com.example.matching.dto.matching.MatchingAbilitySnapshot;
import com.example.matching.dto.matching.MatchingEmployeeProfile;
import com.example.matching.dto.matching.MatchingPostProfile;
import com.example.matching.dto.matching.MatchingRequirementSnapshot;
import com.example.matching.entity.employee.EmpAbility;
import com.example.matching.entity.employee.EmpEmployee;
import com.example.matching.entity.matching.MatchingBlackWhiteList;
import com.example.matching.entity.post.PostAbilityModel;
import com.example.matching.entity.post.PostPost;
import com.example.matching.entity.system.AbilityTag;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 匹配模块共用数据查询服务
 * <p>
 * 封装匹配流程中对 Mapper 的直接查询，避免多个 Service 重复注入相同的 Mapper。
 * 仅负责数据加载，不包含业务逻辑。
 * <p>
 * M-12：匹配专用 DTO 查询方法（{@code findXxxForMatching / *Snapshot}）返回不可变 DTO，
 * 匹配算法与评分层只消费 DTO；旧 Entity 返回方法保留并标记 {@code @Deprecated}，
 * 迁移完成后删除。
 */
public interface MatchingDataQueryService {

    // ==================== M-12 匹配专用 DTO 查询 ====================

    /**
     * 查询员工匹配画像（员工基本信息 + 能力快照）
     *
     * @param empId 员工ID
     * @return 匹配专用员工画像；员工不存在返回 null
     */
    MatchingEmployeeProfile findEmployeeForMatching(Long empId);

    /**
     * 批量查询员工匹配画像
     *
     * @param empIds 员工ID集合
     * @return 员工画像列表（仅包含存在的员工）
     */
    List<MatchingEmployeeProfile> findEmployeesForMatching(Collection<Long> empIds);

    /**
     * 查询指定ID的在职未锁定员工匹配画像（status=1, isLocked=0）
     *
     * @param empIds 员工ID集合
     * @return 员工画像列表
     */
    List<MatchingEmployeeProfile> findActiveEmployeesForMatching(Collection<Long> empIds);

    /**
     * 查询所有在职未锁定员工匹配画像（status=1, isLocked=0），最多500条
     *
     * @return 员工画像列表
     */
    List<MatchingEmployeeProfile> findAllActiveEmployeesForMatching();

    /**
     * 查询岗位匹配画像（岗位基本信息 + 要求快照）
     *
     * @param postId 岗位ID
     * @return 匹配专用岗位画像；岗位不存在返回 null
     */
    MatchingPostProfile findPostForMatching(Long postId);

    /**
     * 批量查询岗位匹配画像
     *
     * @param postIds 岗位ID集合
     * @return 岗位画像列表（仅包含存在的岗位）
     */
    List<MatchingPostProfile> findPostsForMatching(Collection<Long> postIds);

    /**
     * 查询岗位要求快照列表
     *
     * @param postId 岗位ID
     * @return 要求快照列表
     */
    List<MatchingRequirementSnapshot> findPostRequirements(Long postId);

    /**
     * 批量加载员工能力快照（优先融合画像，降级 emp_ability）
     *
     * @param empIds 员工ID集合
     * @return empId -> 能力快照列表
     */
    Map<Long, List<MatchingAbilitySnapshot>> batchLoadAbilitySnapshots(Collection<Long> empIds);

    // ==================== 旧 Entity 方法（M-12 迁移完成前保留） ====================

    /** 根据ID查询员工 */
    @Deprecated
    EmpEmployee getEmployeeById(Long empId);

    /** 根据ID查询岗位 */
    @Deprecated
    PostPost getPostById(Long postId);

    /** Batch-load posts without filtering their status. */
    @Deprecated
    List<PostPost> listPostsByIds(List<Long> postIds);

    /** 根据ID查询能力标签 */
    @Deprecated
    AbilityTag getTagById(Long tagId);

    /** 查询岗位的能力模型要求 */
    @Deprecated
    List<PostAbilityModel> listRequirementsByPostId(Long postId);

    /** 查询岗位的黑白名单 */
    List<MatchingBlackWhiteList> listBlackWhiteListByPostId(Long postId);

    /**
     * 批量加载员工能力数据（优先融合画像，降级 emp_ability）
     */
    @Deprecated
    Map<Long, List<EmpAbility>> batchLoadAbilities(List<Long> empIds);

    /**
     * 批量加载简历基本信息（从最新解析结果的 basicInfo 中提取）
     */
    Map<Long, Map<String, Object>> batchLoadResumeBasicInfo(List<Long> empIds);

    /**
     * 批量查询员工（不过滤状态）
     */
    @Deprecated
    List<EmpEmployee> listEmployeesByIds(List<Long> empIds);

    /**
     * 查询指定ID的在职未锁定员工（status=1, isLocked=0）
     */
    @Deprecated
    List<EmpEmployee> listActiveEmployeesByIds(List<Long> empIds);

    /**
     * 查询所有在职未锁定员工（status=1, isLocked=0），最多500条
     */
    @Deprecated
    List<EmpEmployee> listAllActiveEmployees();

    /** 在职且未锁定员工总数（候选池规模统计用） */
    long countAllActiveEmployees();

    /**
     * 分页查询在职未锁定员工
     */
    @Deprecated
    List<EmpEmployee> listActiveEmployeesPaged(int page, int pageSize);

    /**
     * 批量查询能力标签
     */
    @Deprecated
    List<AbilityTag> listTagsByIds(List<Long> tagIds);

    /**
     * 构建能力标签名称映射
     */
    @Deprecated
    Map<Long, String> buildTagNameMap(List<PostAbilityModel> requirements);
}
