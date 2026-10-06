package com.example.matching.service.employee;

import com.example.matching.dto.employee.api.PmsRosterResponse;
import com.example.matching.entity.system.PmsAnalysisTask;
import com.example.matching.entity.system.PmsUserMapping;

import java.util.List;
import java.util.Map;

/**
 * PMS 项目分析服务接口。
 *
 * <p>【2026-09-04 定位变化】本服务原先挂在员工档案的逐行「项目分析」按钮上，
 * 以 {@code empId} 为唯一入口。但真实场景是：PMS 平台上有数据的人**先于**本系统存在，
 * 同步过来时他们还没有本系统账号，等员工注册之后 HR 才把两边绑成一个员工。
 * 因此主键改为 **PMS 人员（pmsUserId）**，服务被提升为 HR 的独立功能：
 * <ul>
 *   <li>{@link #syncPmsUsers()} 只落「PMS 人员 → 映射」行（{@code emp_id} 为空 = 已同步未绑定），
 *       <b>不再写 {@code emp_employee}</b>，因此不会污染人员库、也不影响非 PMS 员工；</li>
 *   <li>{@link #listRoster()} 把「PMS 侧的人」与「绑定到谁 / 分析过几次」放在同一行；</li>
 *   <li>{@link #analyzeByPmsUser(Long, int)} 允许在未绑定时就分析（分析只依赖 PMS 数据），
 *       {@code pms_analysis_task.emp_id} 记 null；只有 {@link #importAbilities} 写员工档案时才要求绑定。</li>
 * </ul>
 *
 * <p>以 {@code empId} 为入口的三个旧方法（{@link #autoMapUser}、{@link #analyzeEmployee}、
 * {@link #getAnalysisHistory}）保留，用于内部复用与兼容，不再是页面主路径。
 */
public interface PmsAbilityAnalysisService {

    /**
     * 自动映射PMS用户（通过工号匹配）
     *
     * @param empId 本地员工ID
     * @return 映射结果，未找到返回null
     */
    PmsUserMapping autoMapUser(Long empId);

    /**
     * 手动映射PMS用户
     *
     * @param empId     本地员工ID
     * @param pmsUserId PMS用户ID
     * @return 映射结果
     */
    PmsUserMapping manualMapUser(Long empId, Long pmsUserId);

    /**
     * 获取员工的PMS用户映射
     *
     * @param empId 本地员工ID
     * @return 映射信息
     */
    PmsUserMapping getMapping(Long empId);

    /**
     * 分析员工项目数据并提取能力
     *
     * @param empId            本地员工ID
     * @param dateRangeMonths  分析时间范围（月）
     * @return 分析任务
     */
    PmsAnalysisTask analyzeEmployee(Long empId, int dateRangeMonths);

    /**
     * 获取员工的PMS分析历史
     *
     * @param empId 本地员工ID
     * @return 分析任务列表
     */
    List<PmsAnalysisTask> getAnalysisHistory(Long empId);

    /**
     * 获取PMS用户列表（用于手动映射）
     *
     * @return PMS用户列表
     */
    List<Map<String, Object>> listPmsUsers();

    /**
     * 测试PMS数据库连接
     *
     * @return 连接是否成功
     */
    boolean testConnection();

    /**
     * 同步 PMS 人员到匹配系统。
     *
     * <p>【与旧实现的区别】旧实现会在循环里 {@code findOrCreateEmployee(pmsUser)}，
     * 真的往 {@code emp_employee} 插一行（工号兜底成 {@code PMS_&lt;id&gt;}），
     * 于是 PMS 上的人混进了本系统人员列表，且按姓名匹配还有重名认错人的风险。
     * 现在只写 {@code pms_user_mapping}，{@code emp_id} 留空表示「已同步未绑定」——
     * 语义与「PMS 上的人与本平台无关」一致。
     *
     * <p>顺带做一次**保守的自动绑定**：只有当 PMS 工号在本系统里唯一命中一个员工、
     * 且该员工尚未绑定别的 PMS 人员时才绑定；命中多个或零个一律留空等 HR 手工绑定。
     *
     * @return 同步结果：[0]=本次新落映射数, [1]=PMS 用户总数, [2]=此前已同步数, [3]=本次自动绑定数
     */
    int[] syncPmsUsers();

    /**
     * PMS 人员花名册：PMS 用户 为主键，纵向合并「本地映射 / 绑定的员工 / 分析情况」。
     *
     * @return 花名册（含页面口径统计）；PMS 库不可用时返回带 message 的空结果，绝不静默返回空数组
     */
    PmsRosterResponse listRoster();

    /**
     * 把 PMS 人员绑定到本系统员工（HR 动作，用于「员工注册后与 PMS 人员绑成一个员工」）。
     *
     * @param pmsUserId PMS 用户ID；未同步时会先补一行映射再绑定
     * @param empId     本系统员工ID
     * @return 绑定后的映射
     */
    PmsUserMapping bindRosterUser(Long pmsUserId, Long empId);

    /**
     * 解除 PMS 人员与员工的绑定。
     * <p>只解除关系，**不会**删除已导入的员工能力 —— 删数据是不可逆动作，交给员工能力档案显式处理。
     *
     * @param pmsUserId PMS 用户ID
     */
    void unbindRosterUser(Long pmsUserId);

    /**
     * 以 PMS 人员为入口发起项目数据分析（未绑定时也允许，任务 emp_id 记 null）。
     *
     * @param pmsUserId        PMS 用户ID
     * @param dateRangeMonths  分析时间范围（月）
     * @return 分析任务
     */
    PmsAnalysisTask analyzeByPmsUser(Long pmsUserId, int dateRangeMonths);

    /**
     * 以 PMS 人员为入口查分析历史。
     *
     * @param pmsUserId PMS 用户ID
     * @return 分析任务列表（按创建时间倒序）
     */
    List<PmsAnalysisTask> getAnalysisHistoryByPmsUser(Long pmsUserId);

    /**
     * 获取分析结果详情（解析AI返回的能力列表）
     *
     * @param taskId 分析任务ID
     * @return 解析后的能力列表
     */
    Map<String, Object> getAnalysisDetail(Long taskId);

    /**
     * 导入选中的能力到员工档案。
     *
     * <p>这是整条链路里**唯一**需要绑定关系的动作：能力最终落在 {@code emp_ability}，
     * 而它必须挂在某个 {@code empId} 上。未绑定的 PMS 人员可以分析、可以看结果，
     * 但调用本方法会得到 400「请先绑定员工」。
     *
     * @param empId   员工ID
     * @param taskId  分析任务ID
     * @param indexes 选中的能力索引列表（null或空表示导入全部）
     * @return 导入的能力数量
     */
    int importAbilities(Long empId, Long taskId, List<Integer> indexes);
}
